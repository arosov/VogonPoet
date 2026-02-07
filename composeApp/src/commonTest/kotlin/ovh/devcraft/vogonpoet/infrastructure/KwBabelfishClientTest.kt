package ovh.devcraft.vogonpoet.infrastructure

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import ovh.devcraft.vogonpoet.domain.model.ConnectionState
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class KwBabelfishClientTest {

    @Test
    fun testInitialState() = runTest {
        val client = KwBabelfishClient()
        assertTrue(client.connectionState.value is ConnectionState.Disconnected)
    }

    @Test
    fun testConnectionFailure() = runTest {
        val client = KwBabelfishClient()
        client.connect()
        // Should fail because no server is running at localhost:8123
        assertTrue(client.connectionState.value is ConnectionState.Error)
    }
}