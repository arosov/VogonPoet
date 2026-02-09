package ovh.devcraft.vogonpoet.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ovh.devcraft.vogonpoet.domain.BabelfishClient
import ovh.devcraft.vogonpoet.domain.model.ConnectionState
import ovh.devcraft.vogonpoet.domain.model.ProtocolMessage
import ovh.devcraft.vogonpoet.domain.model.VadState
import ovh.devcraft.vogonpoet.infrastructure.BackendController
import ovh.devcraft.vogonpoet.infrastructure.model.Babelfish

class MainViewModel(
    private val babelfishClient: BabelfishClient,
) : ViewModel() {
    val connectionState: StateFlow<ConnectionState> = babelfishClient.connectionState
    val vadState: StateFlow<VadState> = babelfishClient.vadState
    val messages: StateFlow<List<ProtocolMessage>> = babelfishClient.messages
    val config: StateFlow<Babelfish?> = babelfishClient.config

    init {
        viewModelScope.launch {
            babelfishClient.connect()
        }
    }

    fun reconnect() {
        viewModelScope.launch {
            babelfishClient.connect()
        }
    }

    fun restartBackend() {
        BackendController.restart()
    }
}
