package ovh.devcraft.vogonpoet.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import ovh.devcraft.vogonpoet.domain.BabelfishClient
import ovh.devcraft.vogonpoet.domain.HardwareDevice
import ovh.devcraft.vogonpoet.domain.Microphone
import ovh.devcraft.vogonpoet.domain.model.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeBabelfishClient : BabelfishClient {
        private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        override val connectionState: StateFlow<ConnectionState> = _connectionState

        private val _vadState = MutableStateFlow(VadState.Idle)
        override val vadState: StateFlow<VadState> = _vadState

        private val _engineMode = MutableStateFlow(EngineMode.Wakeword)
        override val engineMode: StateFlow<EngineMode> = _engineMode

        private val _events = MutableSharedFlow<EngineEvent>()
        override val events: SharedFlow<EngineEvent> = _events

        private val _messages = MutableStateFlow<List<ProtocolMessage>>(emptyList())
        override val messages: StateFlow<List<ProtocolMessage>> = _messages

        private val _config = MutableStateFlow<VogonConfig?>(null)
        override val config: StateFlow<VogonConfig?> = _config

        var connectCalled = 0

        override suspend fun connect() {
            connectCalled++
            _connectionState.value = ConnectionState.Connected
        }

        override fun disconnect() {
            _connectionState.value = ConnectionState.Disconnected
        }

        override suspend fun saveConfig(config: VogonConfig) {
            _config.value = config
        }

        override suspend fun listMicrophones(): List<Microphone> = emptyList()

        override suspend fun listHardware(): List<HardwareDevice> = emptyList()

        override suspend fun listWakewords(): List<String> = emptyList()

        override suspend fun setMicTest(enabled: Boolean) {}

        override suspend fun forceListen() {}

        override suspend fun toggleListening() {}

        override fun notifyBootstrap() {}

        fun emitVad(state: VadState) {
            _vadState.value = state
        }

        suspend fun emitEvent(event: EngineEvent) {
            _events.emit(event)
        }
    }

    @Test
    fun testInitializationStartsConnection() =
        runTest {
            val fakeClient = FakeBabelfishClient()
            val viewModel = MainViewModel(fakeClient)

            runCurrent()
            assertEquals(1, fakeClient.connectCalled)
        }

    @Test
    fun testExposesClientStates() =
        runTest {
            val fakeClient = FakeBabelfishClient()
            val viewModel = MainViewModel(fakeClient)

            runCurrent()
            fakeClient.emitVad(VadState.Listening)
            assertEquals(VadState.Listening, viewModel.vadState.value)

            assertEquals(ConnectionState.Connected, viewModel.connectionState.value)
        }

    @Test
    fun testEventTimeout() =
        runTest {
            val fakeClient = FakeBabelfishClient()
            val viewModel = MainViewModel(fakeClient)

            runCurrent()
            fakeClient.emitEvent(EngineEvent.WakewordDetected)
            runCurrent()

            assertEquals("wakeword detected", viewModel.displayedEvent.value)

            testDispatcher.scheduler.advanceTimeBy(2001)
            runCurrent()
            assertEquals(null, viewModel.displayedEvent.value)
        }
}
