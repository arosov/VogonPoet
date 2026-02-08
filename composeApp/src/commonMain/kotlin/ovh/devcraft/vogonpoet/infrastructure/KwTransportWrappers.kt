package ovh.devcraft.vogonpoet.infrastructure

import io.github.arosov.kwtransport.Connection
import io.github.arosov.kwtransport.Endpoint
import io.github.arosov.kwtransport.RecvStream
import io.github.arosov.kwtransport.SendStream
import io.github.arosov.kwtransport.StreamPair
import kotlinx.coroutines.flow.Flow

interface BabelfishConnection {
    val isClosed: Boolean
    suspend fun receiveDatagram(): ByteArray
    suspend fun acceptBi(): BabelfishStreamPair
    suspend fun openBi(): BabelfishStreamPair
    fun close()
}

interface BabelfishStreamPair {
    val send: BabelfishSendStream
    val recv: BabelfishRecvStream
}

interface BabelfishSendStream {
    // Add send methods if needed later
}

interface BabelfishRecvStream {
    fun chunks(): Flow<ByteArray>
}

interface BabelfishEndpoint {
    suspend fun connect(url: String): BabelfishConnection
    fun close()
}

class RealBabelfishConnection(private val connection: Connection) : BabelfishConnection {
    override val isClosed: Boolean get() = connection.isClosed()
    override suspend fun receiveDatagram(): ByteArray = connection.receiveDatagram()
    override suspend fun acceptBi(): BabelfishStreamPair = RealBabelfishStreamPair(connection.acceptBi())
    override suspend fun openBi(): BabelfishStreamPair = RealBabelfishStreamPair(connection.openBi())
    override fun close() = connection.close()
}

class RealBabelfishStreamPair(private val streamPair: StreamPair) : BabelfishStreamPair {
    override val send: BabelfishSendStream = RealBabelfishSendStream(streamPair.send)
    override val recv: BabelfishRecvStream = RealBabelfishRecvStream(streamPair.recv)
}

class RealBabelfishSendStream(private val stream: SendStream) : BabelfishSendStream

class RealBabelfishRecvStream(private val stream: RecvStream) : BabelfishRecvStream {
    override fun chunks(): Flow<ByteArray> = stream.chunks()
}

class RealBabelfishEndpoint(private val endpoint: Endpoint) : BabelfishEndpoint {
    override suspend fun connect(url: String): BabelfishConnection {
        return RealBabelfishConnection(endpoint.connect(url))
    }
    override fun close() = endpoint.close()
}