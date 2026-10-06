package ovh.devcraft.vogonpoet.infrastructure

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import ovh.devcraft.vogonpoet.domain.BackendRepository
import ovh.devcraft.vogonpoet.domain.model.ServerStatus
import ovh.devcraft.vogonpoet.domain.model.VadState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests the telemetry reducer inside [BabelfishClient.handleIncomingLine]:
 * payload parsing, utterance resets on `transcription_reset` / listening
 * edges, final arrival keeping (not replacing) the increments, and local
 * arrival offset stamping.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UtteranceTelemetryTest {
    private class FakeBackendRepository : BackendRepository {
        override val serverStatus: StateFlow<ServerStatus> = MutableStateFlow(ServerStatus.READY)

        override suspend fun start() {}

        override suspend fun stop() {}

        override suspend fun restart() {}
    }

    private fun kotlinx.coroutines.test.TestScope.client() =
        BabelfishClient(backendRepository = FakeBackendRepository(), scope = this)

    @Test
    fun testGhostPayloadParsing() =
        runTest {
            val client = client()

            client.handleIncomingLine(
                """
                {
                  "type": "transcription",
                  "text": "",
                  "refined": "",
                  "ghost": "hello wor",
                  "composed": "hello wor",
                  "committed": "",
                  "live": "hello wor",
                  "window": [0, 16000],
                  "inference_ms": 210.5,
                  "onset_ms": 230.0,
                  "final": false
                }
                """.trimIndent(),
            )
            runCurrent()

            val telemetry = client.utteranceTelemetry.value
            assertEquals(1, telemetry.increments.size)
            assertEquals("hello wor", telemetry.increments[0].raw)
            assertEquals(210.5f, telemetry.increments[0].inferenceMs)
            assertEquals(230.0f, telemetry.firstGhostDelayMs)
            assertEquals("", telemetry.committed)
            assertEquals("hello wor", telemetry.live)
            assertEquals(16000f / 16000f, telemetry.utteranceDurationMs)
        }

    @Test
    fun testMultipleIncrementsAppended() =
        runTest {
            val client = client()

            client.handleIncomingLine("""{"type": "transcription", "ghost": "one", "final": false}""")
            client.handleIncomingLine("""{"type": "transcription", "ghost": "one two", "final": false}""")

            val telemetry = client.utteranceTelemetry.value
            assertEquals(2, telemetry.increments.size)
            assertEquals("one", telemetry.increments[0].raw)
            assertEquals("one two", telemetry.increments[1].raw)
        }

    @Test
    fun testFinalKeepsIncrements() =
        runTest {
            val client = client()

            client.handleIncomingLine("""{"type": "transcription", "ghost": "hello wor", "final": false}""")
            client.handleIncomingLine(
                """
                {"type": "transcription", "text": "Hello word.", "final_pass_ms": 480.5, "final": true}
                """.trimIndent(),
            )

            val telemetry = client.utteranceTelemetry.value
            assertEquals(1, telemetry.increments.size) // kept, not replaced
            assertEquals("Hello word.", telemetry.finalText)
            assertEquals(480.5f, telemetry.finalComputeMs)
        }

    @Test
    fun testTranscriptionResetKeepsTelemetry() =
        runTest {
            val client = client()

            client.handleIncomingLine(
                """{"type": "transcription", "ghost": "hello wor", "committed": "hello wor", "live": "", "final": false}""",
            )
            client.handleIncomingLine("""{"type": "transcription", "text": "Hello word.", "final": true}""")
            client.handleIncomingLine("""{"type": "transcription_reset"}""")

            // babelfish resets right after the final: the whole completed
            // utterance — increments, composed split and final — stays
            // readable until the next listening edge starts a new one.
            val telemetry = client.utteranceTelemetry.value
            assertEquals(1, telemetry.increments.size)
            assertEquals("hello wor", telemetry.committed)
            assertEquals("Hello word.", telemetry.finalText)
        }

    @Test
    fun testListeningEdgeResetsForNewUtterance() =
        runTest {
            val client = client()

            client.handleIncomingLine("""{"type": "transcription", "text": "Hello word.", "final": true}""")
            client.handleIncomingLine("""{"type": "status", "vad_state": "listening", "engine_state": "ready"}""")
            runCurrent()

            val telemetry = client.utteranceTelemetry.value
            assertEquals(1, telemetry.utteranceId) // bumped by the listening edge
            assertEquals(null, telemetry.finalText)
            assertTrue(telemetry.voiceDetectedArrivalMs != null)

            // A repeated listening status is not an edge: no extra reset.
            client.handleIncomingLine("""{"type": "transcription", "ghost": "next", "final": false}""")
            client.handleIncomingLine("""{"type": "status", "vad_state": "listening", "engine_state": "ready"}""")
            val after = client.utteranceTelemetry.value
            assertEquals(1, after.utteranceId)
            assertEquals(1, after.increments.size)
        }

    @Test
    fun testUtteranceIdBumpsOnlyOnListeningEdge() =
        runTest {
            val client = client()

            client.handleIncomingLine("""{"type": "transcription", "ghost": "hi", "final": false}""")
            client.handleIncomingLine("""{"type": "transcription_reset"}""")
            client.handleIncomingLine("""{"type": "transcription_reset"}""")
            // Resets are post-utterance cleanup: they no longer bump the id.
            assertEquals(0, client.utteranceTelemetry.value.utteranceId)

            client.handleIncomingLine("""{"type": "status", "vad_state": "listening", "engine_state": "ready"}""")
            client.handleIncomingLine("""{"type": "status", "vad_state": "listening", "engine_state": "ready"}""")
            // Only true listening edges bump, repeats don't.
            assertEquals(1, client.utteranceTelemetry.value.utteranceId)
        }

    @Test
    fun testArrivalOffsetsStamped() =
        runTest {
            val client = client()

            client.handleIncomingLine("""{"type": "status", "vad_state": "listening", "engine_state": "ready"}""")
            client.handleIncomingLine("""{"type": "transcription", "ghost": "one", "final": false}""")
            client.handleIncomingLine("""{"type": "transcription", "ghost": "one two", "final": false}""")

            val telemetry = client.utteranceTelemetry.value
            assertEquals(2, telemetry.increments.size)
            telemetry.increments.forEach { increment ->
                assertTrue(increment.arrivalOffsetMs >= 0)
            }
        }

    @Test
    fun testGhostWithoutWindowKeepsPreviousDuration() =
        runTest {
            val client = client()

            client.handleIncomingLine("""{"type": "transcription", "ghost": "one", "window": [0, 32000], "final": false}""")
            client.handleIncomingLine("""{"type": "transcription", "ghost": "one two", "final": false}""")

            assertEquals(32000f / 16000f, client.utteranceTelemetry.value.utteranceDurationMs)
        }
}
