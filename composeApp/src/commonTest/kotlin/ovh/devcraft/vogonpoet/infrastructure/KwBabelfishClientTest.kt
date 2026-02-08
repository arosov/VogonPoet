package ovh.devcraft.vogonpoet.infrastructure

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
    fun testVadStateUpdatesViaStream() = runTest {
        val chunksFlow = MutableSharedFlow<ByteArray>()
        
        val fakeRecvStream = object : BabelfishRecvStream {
            override fun chunks(): Flow<ByteArray> = chunksFlow
        }
        
        val fakeStreamPair = object : BabelfishStreamPair {
            override val send = object : BabelfishSendStream {}
            override val recv = fakeRecvStream
        }

        val fakeConnection = object : BabelfishConnection {
            var closed = false
            override val isClosed: Boolean get() = closed
            override suspend fun receiveDatagram(): ByteArray = ByteArray(0)
            override suspend fun acceptBi(): BabelfishStreamPair = fakeStreamPair
            override suspend fun openBi(): BabelfishStreamPair = fakeStreamPair
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
        advanceTimeBy(100)
        
        // Send VAD:1
        chunksFlow.emit("VAD:1\n".encodeToByteArray())
        advanceTimeBy(100)
        assertEquals(VadState.Listening, client.vadState.value)

        // Send VAD:0
        chunksFlow.emit("VAD:0\n".encodeToByteArray())
        advanceTimeBy(100)
        assertEquals(VadState.Idle, client.vadState.value)

        client.disconnect()
    }

    @Test
    fun testJsonConfigUpdates() = runTest {
        val chunksFlow = MutableSharedFlow<ByteArray>()
        
        val fakeRecvStream = object : BabelfishRecvStream {
            override fun chunks(): Flow<ByteArray> = chunksFlow
        }
        
        val fakeStreamPair = object : BabelfishStreamPair {
            override val send = object : BabelfishSendStream {}
            override val recv = fakeRecvStream
        }

        val fakeConnection = object : BabelfishConnection {
            override val isClosed: Boolean get() = false
            override suspend fun receiveDatagram(): ByteArray = ByteArray(0)
            override suspend fun acceptBi(): BabelfishStreamPair = fakeStreamPair
            override suspend fun openBi(): BabelfishStreamPair = fakeStreamPair
            override fun close() {}
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
        advanceTimeBy(100)
        
        // Send JSON config
        val jsonConfig = """{"type": "config", "data": {"hardware": {"device": "cuda"}}}"""
        chunksFlow.emit((jsonConfig + "\n").encodeToByteArray())
        advanceTimeBy(100)
        
        // Verification is mainly visual in logs as per request, but we ensure it doesn't crash
        // and handles the message.
        
        client.disconnect()
    }
}
