package ovh.devcraft.vogonpoet.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ovh.devcraft.vogonpoet.domain.BabelfishClient
import ovh.devcraft.vogonpoet.domain.BackendRepository
import ovh.devcraft.vogonpoet.domain.HardwareDevice
import ovh.devcraft.vogonpoet.domain.Microphone
import ovh.devcraft.vogonpoet.domain.model.ConnectionState
import ovh.devcraft.vogonpoet.domain.model.EngineEvent
import ovh.devcraft.vogonpoet.domain.model.EngineMode
import ovh.devcraft.vogonpoet.domain.model.ProtocolMessage
import ovh.devcraft.vogonpoet.domain.model.ServerStatus
import ovh.devcraft.vogonpoet.domain.model.TranscriptionState
import ovh.devcraft.vogonpoet.domain.model.VadState
import ovh.devcraft.vogonpoet.domain.model.VogonConfig
import ovh.devcraft.vogonpoet.infrastructure.VogonLogger
import ovh.devcraft.vogonpoet.ui.constants.vogonLoadingStrings
import kotlin.random.Random

class MainViewModel(
    private val babelfishClient: BabelfishClient,
    private val backendRepository: BackendRepository,
) : ViewModel() {
    val connectionState: StateFlow<ConnectionState> = babelfishClient.connectionState
    val vadState: StateFlow<VadState> = babelfishClient.vadState
    val engineMode: StateFlow<EngineMode> = babelfishClient.engineMode
    val messages: StateFlow<List<ProtocolMessage>> = babelfishClient.messages
    val config: StateFlow<VogonConfig?> = babelfishClient.config
    val transcription: StateFlow<TranscriptionState> = babelfishClient.transcription

    private val _draftConfig = MutableStateFlow<VogonConfig?>(null)
    val draftConfig: StateFlow<VogonConfig?> = _draftConfig.asStateFlow()

    private var activationCount = 0
    private val _listeningText = MutableStateFlow("Listening...")
    val listeningText: StateFlow<String> = _listeningText.asStateFlow()

    private val _displayedEvent = MutableStateFlow<String?>(null)
    val displayedEvent: StateFlow<String?> = _displayedEvent.asStateFlow()

    private val allPossibleStates = vogonLoadingStrings + "Listening..."

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
            babelfishClient.events.collect { event ->
                _displayedEvent.value =
                    when (event) {
                        EngineEvent.WakewordDetected -> "wakeword detected"
                        EngineEvent.StopWordDetected -> "stop word detected"
                    }
                delay(2000)
                _displayedEvent.value = null
            }
        }

        viewModelScope.launch {
            config.collectLatest { remoteConfig ->
                if (remoteConfig != null) {
                    val currentDraft = _draftConfig.value
                    if (currentDraft == null) {
                        _draftConfig.value = remoteConfig
                    } else {
                        // Merge runtime-populated fields from remote back into draft.
                        // This ensures that VRAM stats and calibrated performance profiles
                        // are reflected in the UI even if the user is currently editing.
                        _draftConfig.value =
                            currentDraft.copy(
                                hardware =
                                    currentDraft.hardware.copy(
                                        activeDevice = remoteConfig.hardware.activeDevice,
                                        activeDeviceName = remoteConfig.hardware.activeDeviceName,
                                        vramTotalGb = remoteConfig.hardware.vramTotalGb,
                                        vramUsedBaselineGb = remoteConfig.hardware.vramUsedBaselineGb,
                                        vramUsedModelGb = remoteConfig.hardware.vramUsedModelGb,
                                    ),
                                pipeline =
                                    currentDraft.pipeline.copy(
                                        performance = remoteConfig.pipeline.performance,
                                    ),
                            )
                    }
                }
            }
        }

        viewModelScope.launch {
            backendRepository.serverStatus.collectLatest { status ->
                if (status == ServerStatus.INITIALIZING) {
                    activationCount = 0
                    _listeningText.value = "Listening..."
                    _draftConfig.value = null // Clear draft to sync with new server state
                }
            }
        }

        viewModelScope.launch {
            vadState.collectLatest { state ->
                if (state == VadState.Listening) {
                    activationCount++
                    if (activationCount > 10) {
                        _listeningText.value = allPossibleStates[Random.nextInt(allPossibleStates.size)]
                    } else {
                        _listeningText.value = "Listening..."
                    }
                }
            }
        }
    }

    fun updateDraft(newConfig: VogonConfig) {
        _draftConfig.value = newConfig
    }

    fun reconnect() {
        viewModelScope.launch {
            babelfishClient.connect()
        }
    }

    fun restartBackend() {
        babelfishClient.notifyBootstrap()
        viewModelScope.launch {
            backendRepository.restart()
        }
    }

    fun saveConfig(config: VogonConfig? = _draftConfig.value) {
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

    fun forceListen() {
        viewModelScope.launch {
            try {
                babelfishClient.forceListen()
                VogonLogger.i("Force listen command sent")
            } catch (e: Exception) {
                VogonLogger.e("Failed to send force listen command", e)
            }
        }
    }

    fun toggleListening() {
        viewModelScope.launch {
            try {
                babelfishClient.toggleListening()
                VogonLogger.i("Toggle listening command sent")
            } catch (e: Exception) {
                VogonLogger.e("Failed to send toggle listening command", e)
            }
        }
    }

    fun saveAndRestart(config: VogonConfig) {
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

    override fun onCleared() {
        super.onCleared()
        babelfishClient.disconnect()
    }
}
