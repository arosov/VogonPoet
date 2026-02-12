package ovh.devcraft.vogonpoet.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
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
import ovh.devcraft.vogonpoet.infrastructure.ServerStatus
import ovh.devcraft.vogonpoet.infrastructure.VogonLogger
import ovh.devcraft.vogonpoet.infrastructure.model.Babelfish
import ovh.devcraft.vogonpoet.ui.constants.vogonLoadingStrings
import kotlin.random.Random

class MainViewModel(
    private val babelfishClient: BabelfishClient,
) : ViewModel() {
    val connectionState: StateFlow<ConnectionState> = babelfishClient.connectionState
    val vadState: StateFlow<VadState> = babelfishClient.vadState
    val messages: StateFlow<List<ProtocolMessage>> = babelfishClient.messages
    val config: StateFlow<Babelfish?> = babelfishClient.config

    private val _draftConfig = MutableStateFlow<Babelfish?>(null)
    val draftConfig: StateFlow<Babelfish?> = _draftConfig.asStateFlow()

    private var activationCount = 0
    private val _transcribingText = MutableStateFlow("Transcribing...")
    val transcribingText: StateFlow<String> = _transcribingText.asStateFlow()

    private val allPossibleStates = vogonLoadingStrings + "Transcribing..."

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

        viewModelScope.launch {
            BackendController.serverStatus.collectLatest { status ->
                if (status == ServerStatus.INITIALIZING) {
                    activationCount = 0
                    _transcribingText.value = "Transcribing..."
                    _draftConfig.value = null // Clear draft to sync with new server state
                }
            }
        }

        viewModelScope.launch {
            vadState.collectLatest { state ->
                if (state == VadState.Listening) {
                    activationCount++
                    if (activationCount > 10) {
                        _transcribingText.value = allPossibleStates[Random.nextInt(allPossibleStates.size)]
                    } else {
                        _transcribingText.value = "Transcribing..."
                    }
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
                VogonLogger.i("Configuration saved successfully")
            } catch (e: Exception) {
                VogonLogger.e("Failed to save configuration", e)
            }
        }
    }

    fun loadMicrophones() {
        viewModelScope.launch {
            try {
                val mics = babelfishClient.listMicrophones()
                _microphoneList.value = mics
                VogonLogger.i("Loaded ${mics.size} microphones")
            } catch (e: Exception) {
                VogonLogger.e("Failed to load microphones", e)
            }
        }
    }

    fun loadHardware() {
        viewModelScope.launch {
            try {
                val hardware = babelfishClient.listHardware()
                _hardwareList.value = hardware
                VogonLogger.i("Loaded ${hardware.size} hardware devices")
            } catch (e: Exception) {
                VogonLogger.e("Failed to load hardware", e)
            }
        }
    }

    fun loadWakewords() {
        viewModelScope.launch {
            try {
                val words = babelfishClient.listWakewords()
                _wakewordList.value = words
                VogonLogger.i("Loaded ${words.size} wakewords")
            } catch (e: Exception) {
                VogonLogger.e("Failed to load wakewords", e)
            }
        }
    }

    fun toggleMicTest(enabled: Boolean) {
        viewModelScope.launch {
            try {
                babelfishClient.setMicTest(enabled)
                _isMicTesting.value = enabled
                VogonLogger.i("Microphone test mode: $enabled")
            } catch (e: Exception) {
                VogonLogger.e("Failed to toggle mic test", e)
            }
        }
    }

    fun saveAndRestart(config: Babelfish) {
        viewModelScope.launch {
            VogonLogger.i("Saving config and restarting backend...")
            _draftConfig.value = config
            try {
                // Save first
                babelfishClient.saveConfig(config)
                // Give it a moment to flush the websocket frame before killing the process
                delay(500)
            } catch (e: Exception) {
                VogonLogger.e("Failed to save config before restart", e)
            }
            // Then restart
            restartBackend()
        }
    }
}
