package ovh.devcraft.vogonpoet.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class DomainModelTest {
    @Test
    fun testConnectionState() {
        val state = ConnectionState.Connected
        assertEquals(ConnectionState.Connected, state)
    }

    @Test
    fun testVadState() {
        val state = VadState.Listening
        assertEquals(VadState.Listening, state)
    }

    @Test
    fun testVogonConfigDefaults() {
        val config = VogonConfig()
        // Concrete platform default (ADR 0007), never "auto"
        assertEquals(platformDefaultDevice(), config.hardware.device)
        assertEquals(400L, config.pipeline.silenceThresholdMs)
        assertEquals("127.0.0.1", config.server.host)
        assertEquals(8123L, config.server.port)
    }
}
