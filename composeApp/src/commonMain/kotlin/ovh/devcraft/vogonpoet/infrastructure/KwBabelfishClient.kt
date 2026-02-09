package ovh.devcraft.vogonpoet.infrastructure

import io.github.arosov.kwtransport.Endpoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import ovh.devcraft.vogonpoet.domain.BabelfishClient
import ovh.devcraft.vogonpoet.domain.Microphone
import ovh.devcraft.vogonpoet.domain.model.ConnectionState
import ovh.devcraft.vogonpoet.domain.model.MessageDirection
import ovh.devcraft.vogonpoet.domain.model.ProtocolMessage
import ovh.devcraft.vogonpoet.domain.model.VadState
import ovh.devcraft.vogonpoet.infrastructure.model.Babelfish
import java.time.Instant

class KwBabelfishClient(
    private val endpointProvider: EndpointProvider =
        EndpointProvider {
            val clientEndpoint =
                Endpoint.createClientEndpoint(
                    certificateHashes = emptyList(), // Disable hash pinning for bootstrap/local dev
                    acceptAllCerts = true,
                    maxIdleTimeoutMillis = 3000L,
                    keepAliveIntervalMillis = 1000L,
                )
            RealBabelfishEndpoint(clientEndpoint)
        },
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
) : BabelfishClient {
    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            isLenient = true
        }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _vadState = MutableStateFlow(VadState.Idle)
    override val vadState: StateFlow<VadState> = _vadState.asStateFlow()

    private val _messages = MutableStateFlow<List<ProtocolMessage>>(emptyList())
    override val messages: StateFlow<List<ProtocolMessage>> = _messages.asStateFlow()

    private val _config = MutableStateFlow<Babelfish?>(null)
    override val config: StateFlow<Babelfish?> = _config.asStateFlow()

    private var endpoint: BabelfishEndpoint? = null
    private var connection: BabelfishConnection? = null
    private var connectionJob: Job? = null

    private val SERVER_URL = "https://127.0.0.1:8123/config"

    override suspend fun connect() {
        if (connectionJob?.isActive == true) return

        connectionJob =
            scope.launch {
                var retryDelay = 1000L
                val maxDelay = 30000L

                while (isActive) {
                    _connectionState.value = ConnectionState.Connecting
                    println("Attempting to connect to Babelfish at $SERVER_URL...")
                    try {
                        val newEndpoint = endpointProvider.createClientEndpoint()
                        endpoint = newEndpoint
                        val newConnection = newEndpoint.connect(SERVER_URL)
                        connection = newConnection
                        _connectionState.value = ConnectionState.Connected
                        println("Successfully connected to Babelfish.")

                        listenForUpdates(newConnection)

                        println("Connection session ended. Cleaning up and retrying...")
                        cleanup()
                    } catch (e: Exception) {
                        println("Connection failed or lost: ${e.message}. Retrying in ${retryDelay}ms...")
                        _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
                        cleanup()

                        delay(retryDelay)
                        retryDelay = (retryDelay * 2).coerceAtMost(maxDelay)
                    }
                }
            }
    }

    private suspend fun listenForUpdates(connection: BabelfishConnection) =
        coroutineScope {
            try {
                println("[DEBUG] Opening bidirectional stream for control/status...")
                val streamPair = connection.openBi()
                println("[DEBUG] Bidirectional stream established.")

                println("[DEBUG] Sending HELLO to trigger server response...")
                val helloMsg = "{\"type\":\"hello\"}\n"
                streamPair.send.write(helloMsg.encodeToByteArray())
                logMessage(MessageDirection.Sent, helloMsg.trim())

                val stats = connection.getStats()
                println("[DEBUG] Connection Stats: RTT=${stats.rtt}ms, maxData=${stats.maxData}, maxStreamData=${stats.maxStreamData}")

                var buffer = ""
                try {
                    streamPair.recv.chunks().collect { chunk ->
                        val text = chunk.decodeToString()
                        buffer += text

                        while (buffer.contains("\n")) {
                            val line = buffer.substringBefore("\n")
                            buffer = buffer.substringAfter("\n")

                            if (line.isNotBlank()) {
                                handleIncomingLine(line)
                            }
                        }
                    }
                } finally {
                    if (buffer.isNotBlank()) {
                        handleIncomingLine(buffer)
                    }
                }
            } catch (e: Exception) {
                println("[DEBUG] Stream error or closed: ${e.message}")
            }
        }

    private fun handleIncomingLine(line: String) {
        logMessage(MessageDirection.Received, line)
        try {
            val element = json.parseToJsonElement(line)

            if (element is JsonObject) {
                val type = element["type"]?.jsonPrimitive?.contentOrNull

                if (type == "config") {
                    val configData = element["data"]
                    if (configData != null) {
                        val config = json.decodeFromJsonElement<Babelfish>(configData)
                        _config.value = config
                    }
                } else if (type == "status") {
                    val message = element["message"]?.jsonPrimitive?.contentOrNull
                    val vadStateStr = element["vad_state"]?.jsonPrimitive?.contentOrNull
                    val engineState = element["engine_state"]?.jsonPrimitive?.contentOrNull

                    if (vadStateStr == "listening" || vadStateStr == "idle" || engineState == "ready" || message == "Engine Ready!") {
                        _connectionState.value = ConnectionState.Connected
                        if (vadStateStr == "listening") {
                            _vadState.value = VadState.Listening
                        } else if (vadStateStr == "idle") {
                            _vadState.value = VadState.Idle
                        }
                    } else if (message != null) {
                        _connectionState.value = ConnectionState.Bootstrapping(message)
                    }
                }
            }
        } catch (e: Exception) {
            if (line.startsWith("VAD:")) {
                val isListening = line.substringAfter("VAD:").trim() == "1"
                _vadState.value = if (isListening) VadState.Listening else VadState.Idle
            }
        }
    }

    override fun disconnect() {
        println("Disconnecting from Babelfish...")
        connectionJob?.cancel()
        connectionJob = null
        cleanup()
        _connectionState.value = ConnectionState.Disconnected
        _vadState.value = VadState.Idle
    }

    override suspend fun saveConfig(config: Babelfish) {
        val currentConnection = connection ?: throw IllegalStateException("Not connected")

        try {
            val configJson = json.encodeToString(Babelfish.serializer(), config)
            val message = """{"type":"update_config","data":$configJson}"""

            // Open a new stream to send the config update
            val streamPair = currentConnection.openBi()
            streamPair.send.write(message.encodeToByteArray())
            streamPair.send.close()

            logMessage(MessageDirection.Sent, message)
            println("Config sent to backend: $configJson")
        } catch (e: Exception) {
            println("Failed to save config: ${e.message}")
            throw e
        }
    }

    override suspend fun listMicrophones(): List<Microphone> {
        val currentConnection = connection ?: throw IllegalStateException("Not connected")

        return try {
            val message = """{"type":"list_microphones"}"""

            // Open a new stream to send the request
            val streamPair = currentConnection.openBi()
            streamPair.send.write(message.encodeToByteArray())
            streamPair.send.close()

            logMessage(MessageDirection.Sent, message)
            println("Requesting microphone list...")

            // Collect all chunks into buffer and parse line by line
            var buffer = ""
            var result: List<Microphone>? = null

            streamPair.recv.chunks().collect { chunk ->
                if (result != null) return@collect

                val text = chunk.decodeToString()
                buffer += text

                while (buffer.contains("\n") && result == null) {
                    val line = buffer.substringBefore("\n")
                    buffer = buffer.substringAfter("\n")

                    if (line.isNotBlank()) {
                        logMessage(MessageDirection.Received, line)
                        try {
                            val element = json.parseToJsonElement(line)
                            if (element is JsonObject && element["type"]?.jsonPrimitive?.content == "microphones_list") {
                                val data = element["data"]?.jsonArray
                                result = data?.map { item ->
                                    val obj = item.jsonObject
                                    Microphone(
                                        index = obj["index"]?.jsonPrimitive?.int ?: 0,
                                        name = obj["name"]?.jsonPrimitive?.content ?: "Unknown",
                                        isDefault = obj["is_default"]?.jsonPrimitive?.boolean ?: false,
                                    )
                                } ?: emptyList()
                            }
                        } catch (e: Exception) {
                            println("Failed to parse line: ${e.message}")
                        }
                    }
                }
            }

            // Handle any remaining buffer
            if (result == null && buffer.isNotBlank()) {
                logMessage(MessageDirection.Received, buffer)
                try {
                    val element = json.parseToJsonElement(buffer)
                    if (element is JsonObject && element["type"]?.jsonPrimitive?.content == "microphones_list") {
                        val data = element["data"]?.jsonArray
                        result = data?.map { item ->
                            val obj = item.jsonObject
                            Microphone(
                                index = obj["index"]?.jsonPrimitive?.int ?: 0,
                                name = obj["name"]?.jsonPrimitive?.content ?: "Unknown",
                                isDefault = obj["is_default"]?.jsonPrimitive?.boolean ?: false,
                            )
                        } ?: emptyList()
                    }
                } catch (e: Exception) {
                    println("Failed to parse remaining buffer: ${e.message}")
                }
            }

            result ?: emptyList()
        } catch (e: Exception) {
            println("Failed to list microphones: ${e.message}")
            throw e
        }
    }

    override suspend fun setMicTest(enabled: Boolean) {
        val currentConnection = connection ?: throw IllegalStateException("Not connected")

        try {
            val message = """{"type":"set_mic_test","enabled":$enabled}"""

            // Open a new stream to send the command
            val streamPair = currentConnection.openBi()
            streamPair.send.write(message.encodeToByteArray())
            streamPair.send.close()

            logMessage(MessageDirection.Sent, message)
            println("Setting microphone test mode: $enabled")
        } catch (e: Exception) {
            println("Failed to set mic test mode: ${e.message}")
            throw e
        }
    }

    private fun logMessage(
        direction: MessageDirection,
        content: String,
    ) {
        val now = System.currentTimeMillis()
        val msg =
            ProtocolMessage(
                timestamp = now,
                direction = direction,
                content = content,
            )
        _messages.value = (_messages.value + msg).takeLast(100)
    }

    private fun cleanup() {
        try {
            connection?.close()
        } catch (e: Exception) {
        }
        connection = null

        try {
            endpoint?.close()
        } catch (e: Exception) {
        }
        endpoint = null
    }
}
