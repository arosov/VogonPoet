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
    fun testInitialState() =
        runTest {
            val client = KwBabelfishClient(scope = this)
            assertTrue(client.connectionState.value is ConnectionState.Disconnected)
        }

    @Test
    fun testRetryLogic() =
        runTest {
            var callCount = 0
            val client =
                KwBabelfishClient(
                    endpointProvider = {
                        callCount++
                        throw Exception("Connection failed")
                    },
                    scope = this,
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
    fun testJsonConfigUpdates() =
        runTest {
            val jsonConfig = """{"type": "config", "data": {"hardware": {"device": "cuda"}}}"""
            val configBytes = (jsonConfig + "\n").encodeToByteArray()
            var bytesToRead = configBytes

            val fakeRecvStream =
                object : BabelfishRecvStream {
                    override fun chunks(chunkSize: Int): Flow<ByteArray> = MutableSharedFlow()

                    override suspend fun read(buffer: ByteArray): Int {
                        if (bytesToRead.isEmpty()) {
                            delay(100) // Simulate waiting
                            return 0 // Return 0 instead of -1 to keep loop active for test if needed, or -1 to stop
                        }
                        val toCopy = minOf(buffer.size, bytesToRead.size)
                        bytesToRead.copyInto(buffer, 0, 0, toCopy)
                        bytesToRead = bytesToRead.copyOfRange(toCopy, bytesToRead.size)
                        return toCopy
                    }

                    override fun close() {}
                }

            val fakeStreamPair =
                object : BabelfishStreamPair {
                    override val send =
                        object : BabelfishSendStream {
                            override suspend fun write(data: ByteArray) {}

                            override suspend fun write(data: String) {}

                            override suspend fun setPriority(priority: Int) {}

                            override suspend fun getPriority(): Int = 0

                            override fun close() {}
                        }
                    override val recv = fakeRecvStream

                    override fun close() {}
                }

            val fakeConnection =
                object : BabelfishConnection {
                    override val isClosed: Boolean get() = false
                    override val maxDatagramSize: Long? get() = null

                    override suspend fun openUni(): BabelfishSendStream = fakeStreamPair.send

                    override suspend fun openBi(): BabelfishStreamPair = fakeStreamPair

                    override suspend fun acceptUni(): BabelfishRecvStream = fakeStreamPair.recv

                    override suspend fun acceptBi(): BabelfishStreamPair = fakeStreamPair

                    override fun sendDatagram(data: ByteArray) {}

                    override suspend fun receiveDatagram(): ByteArray = ByteArray(0)

                    override fun getStats(): BabelfishConnectionStats = BabelfishConnectionStats(0, 0, 0, 0, null, 0, 0)

                    override fun close(
                        code: Long,
                        reason: String,
                    ) {}

                    override fun close() {}
                }

            val fakeEndpoint =
                object : BabelfishEndpoint {
                    override suspend fun connect(url: String) = fakeConnection

                    override fun incomingSessions(): Flow<BabelfishConnection> = MutableSharedFlow()

                    override fun close() {}
                }

            val client =
                KwBabelfishClient(
                    endpointProvider = { fakeEndpoint },
                    scope = this,
                )

            client.connect()
            advanceTimeBy(200)

            // Verification is mainly visual in logs as per request

            client.disconnect()
        }
}
