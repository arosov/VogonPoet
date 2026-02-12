package ovh.devcraft.vogonpoet.infrastructure

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import ovh.devcraft.vogonpoet.domain.BackendRepository
import ovh.devcraft.vogonpoet.domain.model.ConnectionState
import ovh.devcraft.vogonpoet.domain.model.EngineEvent
import ovh.devcraft.vogonpoet.domain.model.ServerStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BabelfishClientTest {
    private class FakeBackendRepository : BackendRepository {
        override val serverStatus: StateFlow<ServerStatus> = MutableStateFlow(ServerStatus.READY)

        override suspend fun start() {}

        override suspend fun stop() {}

        override suspend fun restart() {}
    }

    @Test
    fun testInitialState() =
        runTest {
            val client = BabelfishClient(backendRepository = FakeBackendRepository(), scope = this)
            assertTrue(client.connectionState.value is ConnectionState.Disconnected)
        }

    @Test
    fun testEventParsing() =
        runTest {
            val client = BabelfishClient(backendRepository = FakeBackendRepository(), scope = this)
            val events = mutableListOf<EngineEvent>()
            val job =
                launch {
                    client.events.collect { events.add(it) }
                }

            client.handleIncomingLine("""{"type": "event", "event": "wakeword_detected"}""")
            runCurrent()
            assertEquals(1, events.size)
            assertEquals(EngineEvent.WakewordDetected, events[0])

            client.handleIncomingLine("""{"type": "event", "event": "stop_word_detected"}""")
            runCurrent()
            assertEquals(2, events.size)
            assertEquals(EngineEvent.StopWordDetected, events[1])

            job.cancel()
        }

    @Test
    fun testModeParsing() =
        runTest {
            val client = BabelfishClient(backendRepository = FakeBackendRepository(), scope = this)

            // Test transition to Active mode
            client.handleIncomingLine("""{"type": "status", "vad_state": "idle", "engine_state": "ready", "mode": "active"}""")
            runCurrent()
            assertEquals(ovh.devcraft.vogonpoet.domain.model.EngineMode.Active, client.engineMode.value)

            // Test transition to Wakeword mode
            client.handleIncomingLine("""{"type": "status", "vad_state": "idle", "engine_state": "ready", "mode": "wakeword"}""")
            runCurrent()
            assertEquals(ovh.devcraft.vogonpoet.domain.model.EngineMode.Wakeword, client.engineMode.value)
        }
}
