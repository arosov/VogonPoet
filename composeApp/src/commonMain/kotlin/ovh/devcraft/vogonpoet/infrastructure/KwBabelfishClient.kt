package ovh.devcraft.vogonpoet.infrastructure

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*
import ovh.devcraft.vogonpoet.domain.BabelfishClient
import ovh.devcraft.vogonpoet.domain.HardwareDevice
import ovh.devcraft.vogonpoet.domain.Microphone
import ovh.devcraft.vogonpoet.domain.model.ConnectionState
import ovh.devcraft.vogonpoet.domain.model.MessageDirection
import ovh.devcraft.vogonpoet.domain.model.ProtocolMessage
import ovh.devcraft.vogonpoet.domain.model.VadState
import ovh.devcraft.vogonpoet.infrastructure.model.Babelfish
import java.util.concurrent.ConcurrentHashMap

class KwBabelfishClient(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
) : BabelfishClient {
    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            isLenient = true
        }

    private val client =
        HttpClient(CIO) {
            install(WebSockets) {
                pingIntervalMillis = 15000
            }
        }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _vadState = MutableStateFlow(VadState.Idle)
    override val vadState: StateFlow<VadState> = _vadState.asStateFlow()

    private val _messages = MutableStateFlow<List<ProtocolMessage>>(emptyList())
    override val messages: StateFlow<List<ProtocolMessage>> = _messages.asStateFlow()

    private val _config = MutableStateFlow<Babelfish?>(null)
    override val config: StateFlow<Babelfish?> = _config.asStateFlow()

    private var isBootstrapping = true

    private var session: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null

    private val responseHandlers = ConcurrentHashMap<String, CompletableDeferred<JsonObject>>()

    private val SERVER_URL = "ws://127.0.0.1:8123/config"

    override suspend fun connect() {
        if (connectionJob?.isActive == true) return

        connectionJob =
            scope.launch {
                var retryDelay = 1000L
                val maxDelay = 30000L

                // Initial delay during bootstrap to let the server start binding
                if (isBootstrapping) {
                    _connectionState.value = ConnectionState.Bootstrapping("Initializing...")
                    delay(2000)
                }

                while (isActive) {
                    if (isBootstrapping) {
                        _connectionState.value = ConnectionState.Bootstrapping("Initializing...")
                    } else {
                        _connectionState.value = ConnectionState.Connecting
                    }
                    println("Attempting to connect to Babelfish at $SERVER_URL...")
                    try {
                        client.webSocket(SERVER_URL) {
                            session = this
                            // Don't immediately set Connected if we are still bootstrapping
                            if (!isBootstrapping) {
                                _connectionState.value = ConnectionState.Connected
                            }
                            println("Successfully connected to Babelfish.")

                            // Send HELLO
                            val helloMsg = "{\"type\":\"hello\"}"
                            send(helloMsg)
                            logMessage(MessageDirection.Sent, helloMsg)

                            for (frame in incoming) {
                                when (frame) {
                                    is Frame.Text -> {
                                        val text = frame.readText()
                                        handleIncomingLine(text)
                                    }

                                    else -> {}
                                }
                            }
                        }
                        println("Connection session ended. Cleaning up and retrying...")
                    } catch (e: Exception) {
                        println("Connection failed or lost: ${e.message}. Retrying in ${retryDelay}ms...")
                        isBootstrapping = true
                        _connectionState.value = ConnectionState.Bootstrapping("Reconnecting...")
                    } finally {
                        session = null
                        delay(retryDelay)
                        retryDelay = (retryDelay * 2).coerceAtMost(maxDelay)
                    }
                }
            }
    }

    private fun handleIncomingLine(line: String) {
        if (line.isBlank()) return
        // println("DEBUG: Received from Babelfish: $line")
        logMessage(MessageDirection.Received, line)
        try {
            val element = json.parseToJsonElement(line)
            if (element is JsonObject) {
                val type = element["type"]?.jsonPrimitive?.contentOrNull ?: return

                // Check if any pending deferred is waiting for this type of response
                // For simplicity, we match by response type (e.g., microphones_list)
                responseHandlers[type]?.complete(element)

                when (type) {
                    "config" -> {
                        val configData = element["data"]
                        if (configData != null) {
                            val config = json.decodeFromJsonElement<Babelfish>(configData)
                            _config.value = config
                        }
                    }

                    "status" -> {
                        val message = element["message"]?.jsonPrimitive?.contentOrNull
                        val vadStateStr = element["vad_state"]?.jsonPrimitive?.contentOrNull
                        val engineState = element["engine_state"]?.jsonPrimitive?.contentOrNull

                        if (vadStateStr == "listening" || vadStateStr == "idle" || engineState == "ready" || message == "Engine Ready!") {
                            isBootstrapping = false
                            _connectionState.value = ConnectionState.Connected
                            if (vadStateStr == "listening") {
                                _vadState.value = VadState.Listening
                            } else if (vadStateStr == "idle") {
                                _vadState.value = VadState.Idle
                            }
                        } else if (message != null) {
                            isBootstrapping = true
                            _connectionState.value = ConnectionState.Bootstrapping(message)
                        }
                    }

                    "transcription" -> {
                        // Forward transcription messages to the message log
                        // and potentially update a transcription flow if we add one
                    }
                }
            }
        } catch (e: Exception) {
            println("Error parsing message: ${e.message}")
        }
    }

    override fun disconnect() {
        println("Disconnecting from Babelfish...")
        connectionJob?.cancel()
        connectionJob = null
        _connectionState.value = ConnectionState.Disconnected
        _vadState.value = VadState.Idle
        session = null
    }

    override suspend fun saveConfig(config: Babelfish) {
        val currentSession = session ?: throw IllegalStateException("Not connected")
        val configJson = json.encodeToString(Babelfish.serializer(), config)
        val message = """{"type":"update_config","data":$configJson}"""
        currentSession.send(message)
        logMessage(MessageDirection.Sent, message)
    }

    private suspend fun <T> requestResponse(
        requestType: String,
        responseType: String,
        payload: String = "{}",
        timeoutMs: Long = 5000,
        parser: (JsonObject) -> T,
    ): T {
        val currentSession = session ?: throw IllegalStateException("Not connected")
        val deferred = CompletableDeferred<JsonObject>()
        responseHandlers[responseType] = deferred

        try {
            val request = if (payload == "{}") "{\"type\":\"$requestType\"}" else payload
            currentSession.send(request)
            logMessage(MessageDirection.Sent, request)

            return withTimeout(timeoutMs) {
                val response = deferred.await()
                parser(response)
            }
        } finally {
            responseHandlers.remove(responseType)
        }
    }

    override suspend fun listMicrophones(): List<Microphone> =
        requestResponse("list_microphones", "microphones_list") { response ->
            response["data"]?.jsonArray?.map { item ->
                val obj = item.jsonObject
                Microphone(
                    index = obj["index"]?.jsonPrimitive?.int ?: 0,
                    name = obj["name"]?.jsonPrimitive?.content ?: "Unknown",
                    isDefault = obj["is_default"]?.jsonPrimitive?.boolean ?: false,
                )
            } ?: emptyList()
        }

    override suspend fun listHardware(): List<HardwareDevice> =
        requestResponse("list_hardware", "hardware_list") { response ->
            response["data"]?.jsonArray?.map { item ->
                val obj = item.jsonObject
                HardwareDevice(
                    id = obj["id"]?.jsonPrimitive?.content ?: "unknown",
                    name = obj["name"]?.jsonPrimitive?.content ?: "Unknown Device",
                )
            } ?: emptyList()
        }

    override suspend fun listWakewords(): List<String> =
        requestResponse("list_wakewords", "wakewords_list") { response ->
            response["data"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        }

    override suspend fun setMicTest(enabled: Boolean) {
        val currentSession = session ?: throw IllegalStateException("Not connected")
        val message = """{"type":"set_mic_test","enabled":$enabled}"""
        currentSession.send(message)
        logMessage(MessageDirection.Sent, message)
    }

    override fun notifyBootstrap() {
        isBootstrapping = true
        _connectionState.value = ConnectionState.Bootstrapping("Initializing...")
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
}
