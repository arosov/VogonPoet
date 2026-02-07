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
}
