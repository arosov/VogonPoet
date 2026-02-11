package ovh.devcraft.vogonpoet.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ovh.devcraft.vogonpoet.domain.BabelfishClient
import ovh.devcraft.vogonpoet.domain.HardwareDevice
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

    private val _draftConfig = MutableStateFlow<Babelfish?>(null)
    val draftConfig: StateFlow<Babelfish?> = _draftConfig.asStateFlow()

    // Microphone test state
    private val _microphoneList = MutableStateFlow<List<Microphone>>(emptyList())
    val microphoneList: StateFlow<List<Microphone>> = _microphoneList

    private val _hardwareList = MutableStateFlow<List<HardwareDevice>>(emptyList())
    val hardwareList: StateFlow<List<HardwareDevice>> = _hardwareList

    private val _wakewordList = MutableStateFlow<List<String>>(emptyList())
    val wakewordList: StateFlow<List<String>> = _wakewordList

    private val _isMicTesting = MutableStateFlow(false)
    val isMicTesting: StateFlow<Boolean> = _isMicTesting

    init {
        viewModelScope.launch {
            babelfishClient.connect()
        }

        viewModelScope.launch {
            config.collectLatest { remoteConfig ->
                if (remoteConfig != null && _draftConfig.value == null) {
                    _draftConfig.value = remoteConfig
                }
            }
        }
    }

    fun updateDraft(newConfig: Babelfish) {
        _draftConfig.value = newConfig
    }

    fun reconnect() {
        viewModelScope.launch {
            babelfishClient.connect()
        }
    }

    fun restartBackend() {
        babelfishClient.notifyBootstrap()
        BackendController.restart()
    }

    fun saveConfig(config: Babelfish? = _draftConfig.value) {
        val configToSave = config ?: return
        viewModelScope.launch {
            try {
                babelfishClient.saveConfig(configToSave)
                // Once saved, the remote config will eventually update and we'll sync back
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

    fun loadHardware() {
        viewModelScope.launch {
            try {
                val hardware = babelfishClient.listHardware()
                _hardwareList.value = hardware
                println("Loaded ${hardware.size} hardware devices")
            } catch (e: Exception) {
                println("Failed to load hardware: ${e.message}")
            }
        }
    }

    fun loadWakewords() {
        viewModelScope.launch {
            try {
                val words = babelfishClient.listWakewords()
                _wakewordList.value = words
                println("Loaded ${words.size} wakewords")
            } catch (e: Exception) {
                println("Failed to load wakewords: ${e.message}")
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
