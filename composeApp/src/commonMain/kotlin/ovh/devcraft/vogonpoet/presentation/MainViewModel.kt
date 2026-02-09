package ovh.devcraft.vogonpoet.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ovh.devcraft.vogonpoet.domain.BabelfishClient
import ovh.devcraft.vogonpoet.domain.Microphone
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

    // Microphone test state
    private val _microphoneList = MutableStateFlow<List<Microphone>>(emptyList())
    val microphoneList: StateFlow<List<Microphone>> = _microphoneList

    private val _isMicTesting = MutableStateFlow(false)
    val isMicTesting: StateFlow<Boolean> = _isMicTesting

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

    fun saveConfig(config: Babelfish) {
        viewModelScope.launch {
            try {
                babelfishClient.saveConfig(config)
                println("Configuration saved successfully")
            } catch (e: Exception) {
                println("Failed to save configuration: ${e.message}")
            }
        }
    }

    fun loadMicrophones() {
        viewModelScope.launch {
            try {
                val mics = babelfishClient.listMicrophones()
                _microphoneList.value = mics
                println("Loaded ${mics.size} microphones")
            } catch (e: Exception) {
                println("Failed to load microphones: ${e.message}")
            }
        }
    }

    fun toggleMicTest(enabled: Boolean) {
        viewModelScope.launch {
            try {
                babelfishClient.setMicTest(enabled)
                _isMicTesting.value = enabled
                println("Microphone test mode: $enabled")
            } catch (e: Exception) {
                println("Failed to toggle mic test: ${e.message}")
            }
        }
    }
}
