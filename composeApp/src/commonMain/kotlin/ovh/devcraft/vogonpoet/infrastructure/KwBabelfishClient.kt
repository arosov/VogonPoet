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
    private val endpointProvider: EndpointProvider = EndpointProvider {
        // Hash for local dev cert
        val certHash = "dd:c9:8f:b8:e5:33:79:67:8e:cd:0c:fe:59:c4:91:43:ad:11:09:d4:70:3f:4b:10:d2:27:bc:23:27:9b:3d:ef"
        val clientEndpoint = Endpoint.createClientEndpoint(
            certificateHashes = listOf(certHash),
            acceptAllCerts = true,
            maxIdleTimeoutMillis = 0L,
            keepAliveIntervalMillis = 10_000L
        )
        RealBabelfishEndpoint(clientEndpoint)
    },
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : BabelfishClient {

    private val json = Json {
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

        connectionJob = scope.launch {
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

    private suspend fun listenForUpdates(connection: BabelfishConnection) = coroutineScope {
        launch {
            try {
                println("Opening bidirectional stream for control/status...")
                // Client initiates the control stream
                val streamPair = connection.openBi()
                streamPair.recv.chunks().collect { chunk ->
                    val message = chunk.decodeToString()
                    // Handle messages that might be bundled in one chunk or split
                    message.lines().filter { it.isNotBlank() }.forEach { line ->
                        try {
                            val element = json.parseToJsonElement(line)
                            if (element is JsonObject) {
                                val type = element["type"]?.jsonPrimitive?.contentOrNull
                                if (type == "config") {
                                    val configData = element["data"]
                                    if (configData != null) {
                                        val config = json.decodeFromJsonElement<Babelfish>(configData)
                                        println("Received Babelfish Configuration:\n${json.encodeToString(Babelfish.serializer(), config)}")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Fallback to old format or ignore non-JSON
                            if (line.startsWith("VAD:")) {
                                val isListening = line.substringAfter("VAD:").trim() == "1"
                                _vadState.value = if (isListening) VadState.Listening else VadState.Idle
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                println("Stream error or closed: ${e.message}")
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