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
        assertEquals("auto", config.hardware.device)
        assertEquals(true, config.hardware.autoDetect)
        assertEquals(400L, config.pipeline.silenceThresholdMs)
        assertEquals("127.0.0.1", config.server.host)
        assertEquals(8123L, config.server.port)
    }
}
