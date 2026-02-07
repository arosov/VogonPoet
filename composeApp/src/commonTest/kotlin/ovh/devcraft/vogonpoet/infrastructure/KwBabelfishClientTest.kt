package ovh.devcraft.vogonpoet.infrastructure

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import ovh.devcraft.vogonpoet.domain.model.ConnectionState
import ovh.devcraft.vogonpoet.domain.model.VadState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class KwBabelfishClientTest {

    @Test
    fun testInitialState() = runTest {
        val client = KwBabelfishClient(scope = this)
        assertTrue(client.connectionState.value is ConnectionState.Disconnected)
    }

    @Test
    fun testRetryLogic() = runTest {
        var callCount = 0
        val client = KwBabelfishClient(
            endpointProvider = {
                callCount++
                throw Exception("Connection failed")
            },
            scope = this
        )
        
        client.connect()
        
        // Initial attempt
        advanceTimeBy(500)
        assertTrue(callCount == 1, "Should have 1 attempt, but was $callCount")
        
        // First retry after 1s
        advanceTimeBy(1000)
        assertTrue(callCount >= 2, "Should have at least 2 attempts, but was $callCount")
        
        // Second retry after 2s (total 3s)
        advanceTimeBy(2000)
        assertTrue(callCount >= 3, "Should have at least 3 attempts, but was $callCount")
        
        client.disconnect()
    }

    @Test
    fun testVadStateUpdates() = runTest {
        val fakeConnection = object : BabelfishConnection {
            var closed = false
            val datagrams = mutableListOf<ByteArray>()
            
            override val isClosed: Boolean get() = closed
            override suspend fun receiveDatagram(): ByteArray {
                while (datagrams.isEmpty()) {
                    delay(10)
                }
                return datagrams.removeAt(0)
            }
            override fun close() { closed = true }
        }

        val fakeEndpoint = object : BabelfishEndpoint {
            override suspend fun connect(url: String) = fakeConnection
            override fun close() {}
        }

        val client = KwBabelfishClient(
            endpointProvider = { fakeEndpoint },
            scope = this
        )

        client.connect()
        // Wait for connection and listener to start
        advanceTimeBy(100)
        
        // Send VAD:1
        fakeConnection.datagrams.add("VAD:1".encodeToByteArray())
        // Wait for processing
        advanceTimeBy(200)
        assertEquals(VadState.Listening, client.vadState.value)

        // Send VAD:0
        fakeConnection.datagrams.add("VAD:0".encodeToByteArray())
        // Wait for processing
        advanceTimeBy(200)
        assertEquals(VadState.Idle, client.vadState.value)

        client.disconnect()
    }
}