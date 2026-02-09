package ovh.devcraft.vogonpoet.infrastructure

import io.github.arosov.kwtransport.Endpoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*
import ovh.devcraft.vogonpoet.domain.BabelfishClient
import ovh.devcraft.vogonpoet.domain.model.ConnectionState
import ovh.devcraft.vogonpoet.domain.model.VadState
import ovh.devcraft.vogonpoet.infrastructure.model.Babelfish

class KwBabelfishClient(
    private val endpointProvider: EndpointProvider =
        EndpointProvider {
            // Hash for local dev cert
            val certHash = "2b:b0:77:27:54:3b:8a:ed:4c:3b:46:c1:55:6a:6c:d2:dd:d8:fa:64:72:3a:7a:6b:cf:e9:3f:39:f7:5b:10:71"
            val clientEndpoint =
                Endpoint.createClientEndpoint(
                    certificateHashes = listOf(certHash),
                    acceptAllCerts = true,
                    maxIdleTimeoutMillis = 0L,
                    keepAliveIntervalMillis = 10_000L,
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

                        break // Connection successful
                    } catch (e: Exception) {
                        println("Connection failed: ${e.message}. Retrying in ${retryDelay}ms...")
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
            launch {
                try {
                    println("[DEBUG] Opening bidirectional stream for control/status...")
                    // Client initiates the control stream
                    val streamPair = connection.openBi()
                    println("[DEBUG] Bidirectional stream established.")

                    // Send a "HELLO" message to ensure the server detects the stream
                    // (aioquic might not fire events just for the stream header)
                    println("[DEBUG] Sending HELLO to trigger server response...")
                    streamPair.send.write("{\"type\":\"hello\"}\n".encodeToByteArray())

                    val stats = connection.getStats()
                    println("[DEBUG] Connection Stats: RTT=${stats.rtt}ms, maxData=${stats.maxData}, maxStreamData=${stats.maxStreamData}")

                    streamPair.recv.chunks().collect { chunk ->
                        val message = chunk.decodeToString()
                        println("[DEBUG] Received chunk (${chunk.size} bytes): \"$message\"")
                        // println("[DEBUG] Hex: " + chunk.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') })

                        // Handle messages that might be bundled in one chunk or split
                        message.lines().filter { it.isNotBlank() }.forEach { line ->
                            println("[DEBUG] Processing line: \"$line\"")
                            try {
                                val element = json.parseToJsonElement(line)
                                println("[DEBUG] JSON parsed successfully")

                                if (element is JsonObject) {
                                    val type = element["type"]?.jsonPrimitive?.contentOrNull
                                    println("[DEBUG] Message type identified: $type")

                                    if (type == "config") {
                                        println("[DEBUG] Processing config message")
                                        val configData = element["data"]
                                        if (configData != null) {
                                            println("[DEBUG] Config data found, attempting to decode")
                                            val config = json.decodeFromJsonElement<Babelfish>(configData)
                                            println("Received Babelfish Configuration:")
                                            println("- Hardware: ${config.hardware}")
                                            println("- Pipeline: ${config.pipeline}")
                                            println("- Voice: ${config.voice}")
                                            println("- UI: ${config.ui}")
                                            println("- Server: ${config.server}")
                                            println("Full config: ${json.encodeToString(Babelfish.serializer(), config)}")
                                        } else {
                                            println("[DEBUG] 'config' message received but 'data' field is missing.")
                                        }
                                    } else {
                                        println("[DEBUG] Ignoring message type: $type")
                                    }
                                } else {
                                    println("[DEBUG] Parsed JSON is not an object: $line")
                                }
                            } catch (e: Exception) {
                                println("[DEBUG] JSON parse failed for line: \"$line\". Error: ${e.message}")
                                println("[DEBUG] Exception details: ${e.stackTraceToString()}")

                                // Fallback to old format or ignore non-JSON
                                if (line.startsWith("VAD:")) {
                                    println("[DEBUG] Processing VAD line: $line")
                                    val isListening = line.substringAfter("VAD:").trim() == "1"
                                    _vadState.value = if (isListening) VadState.Listening else VadState.Idle
                                } else {
                                    println("[DEBUG] Ignoring unknown message format: $line")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("[DEBUG] Stream error or closed: ${e.message}")
                    println("[DEBUG] Exception stack trace:")
                    e.printStackTrace()
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
        println("Disconnected.")
    }

    private fun cleanup() {
        try {
            connection?.close()
        } catch (e: Exception) {
            // Ignore
        }
        connection = null

        try {
            endpoint?.close()
        } catch (e: Exception) {
            // Ignore
        }
        endpoint = null
    }
}
