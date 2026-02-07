package ovh.devcraft.vogonpoet.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import ovh.devcraft.vogonpoet.domain.BabelfishClient
import ovh.devcraft.vogonpoet.domain.model.ConnectionState
import ovh.devcraft.vogonpoet.domain.model.VadState
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
        
        var connectCalled = 0

        override suspend fun connect() {
            connectCalled++
            _connectionState.value = ConnectionState.Connected
        }

        override fun disconnect() {
            _connectionState.value = ConnectionState.Disconnected
        }
        
        fun emitVad(state: VadState) {
            _vadState.value = state
        }
    }

    @Test
    fun testInitializationStartsConnection() = runTest {
        val fakeClient = FakeBabelfishClient()
        val viewModel = MainViewModel(fakeClient)
        
        runCurrent()
        assertEquals(1, fakeClient.connectCalled)
    }

    @Test
    fun testExposesClientStates() = runTest {
        val fakeClient = FakeBabelfishClient()
        val viewModel = MainViewModel(fakeClient)
        
        runCurrent()
        fakeClient.emitVad(VadState.Listening)
        assertEquals(VadState.Listening, viewModel.vadState.value)
        
        assertEquals(ConnectionState.Connected, viewModel.connectionState.value)
    }
}
