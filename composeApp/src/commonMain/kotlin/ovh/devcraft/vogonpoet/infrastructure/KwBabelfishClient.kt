package ovh.devcraft.vogonpoet.infrastructure

import io.github.arosov.kwtransport.Endpoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ovh.devcraft.vogonpoet.domain.BabelfishClient
import ovh.devcraft.vogonpoet.domain.model.ConnectionState
import ovh.devcraft.vogonpoet.domain.model.VadState

class KwBabelfishClient(
    private val endpointProvider: EndpointProvider = EndpointProvider { RealBabelfishEndpoint(Endpoint.createClientEndpoint()) },
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : BabelfishClient {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _vadState = MutableStateFlow(VadState.Idle)
    override val vadState: StateFlow<VadState> = _vadState.asStateFlow()

    private var endpoint: BabelfishEndpoint? = null
    private var connection: BabelfishConnection? = null
    private var connectionJob: Job? = null

    override suspend fun connect() {
        if (connectionJob?.isActive == true) return

        connectionJob = scope.launch {
            var retryDelay = 1000L
            val maxDelay = 30000L

            while (isActive) {
                _connectionState.value = ConnectionState.Connecting
                try {
                    val newEndpoint = endpointProvider.createClientEndpoint()
                    endpoint = newEndpoint
                    val newConnection = newEndpoint.connect("https://localhost:8123")
                    connection = newConnection
                    _connectionState.value = ConnectionState.Connected
                    
                    listenForUpdates(newConnection)
                    
                    break // Connection successful
                } catch (e: Exception) {
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
                // Babelfish initiates a bidirectional stream for control/status
                val streamPair = connection.acceptBi()
                streamPair.recv.chunks().collect { chunk ->
                    val message = chunk.decodeToString()
                    // Handle messages that might be bundled in one chunk or split
                    message.lines().forEach { line ->
                        if (line.startsWith("VAD:")) {
                            val isListening = line.substringAfter("VAD:").trim() == "1"
                            _vadState.value = if (isListening) VadState.Listening else VadState.Idle
                        }
                    }
                }
            } catch (e: Exception) {
                // Stream closed or error
            }
        }
    }

    override fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        cleanup()
        _connectionState.value = ConnectionState.Disconnected
        _vadState.value = VadState.Idle
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