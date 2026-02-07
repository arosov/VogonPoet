package ovh.devcraft.vogonpoet.infrastructure

import io.github.arosov.kwtransport.WebTransportClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ovh.devcraft.vogonpoet.domain.BabelfishClient
import ovh.devcraft.vogonpoet.domain.model.ConnectionState
import ovh.devcraft.vogonpoet.domain.model.VadState

class KwBabelfishClient(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : BabelfishClient {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _vadState = MutableStateFlow(VadState.Idle)
    override val vadState: StateFlow<VadState> = _vadState.asStateFlow()

    private var client: WebTransportClient? = null

    override suspend fun connect() {
        if (_connectionState.value is ConnectionState.Connected || _connectionState.value is ConnectionState.Connecting) return

        _connectionState.value = ConnectionState.Connecting
        
        try {
            val newClient = WebTransportClient("https://localhost:8123")
            newClient.connect()
            client = newClient
            _connectionState.value = ConnectionState.Connected
            
            // TODO: Start listening for VAD updates from streams/datagrams
            
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
            client = null
        }
    }

    override fun disconnect() {
        client?.close()
        client = null
        _connectionState.value = ConnectionState.Disconnected
    }
}
