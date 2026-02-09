package ovh.devcraft.vogonpoet.infrastructure

import io.github.arosov.kwtransport.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class BabelfishCongestionController {
    Default,
    NewReno,
    Cubic,
    Bbr,
}

data class BabelfishQuicConfig(
    val maxConcurrentBiStreams: Long? = 100L,
    val maxConcurrentUniStreams: Long? = 100L,
    val initialMaxData: Long? = 100 * 1024 * 1024L, // 100 MiB
    val initialMaxStreamDataBidiLocal: Long? = 10 * 1024 * 1024L, // 10 MiB
    val initialMaxStreamDataBidiRemote: Long? = 10 * 1024 * 1024L, // 10 MiB
    val initialMaxStreamDataUni: Long? = 10 * 1024 * 1024L, // 10 MiB
    val datagramReceiveBufferSize: Long? = 10 * 1024 * 1024L,
    val datagramSendBufferSize: Long? = 10 * 1024 * 1024L,
    val congestionController: BabelfishCongestionController? = null
)

fun BabelfishQuicConfig.toQuicConfig() = QuicConfig(
    maxConcurrentBiStreams = maxConcurrentBiStreams,
    maxConcurrentUniStreams = maxConcurrentUniStreams,
    initialMaxData = initialMaxData,
    initialMaxStreamDataBidiLocal = initialMaxStreamDataBidiLocal,
    initialMaxStreamDataBidiRemote = initialMaxStreamDataBidiRemote,
    initialMaxStreamDataUni = initialMaxStreamDataUni,
    datagramReceiveBufferSize = datagramReceiveBufferSize,
    datagramSendBufferSize = datagramSendBufferSize,
    congestionController = congestionController?.let { CongestionController.valueOf(it.name) }
)

data class BabelfishConnectionStats(
    val rtt: Long,
    val bytesSent: Long,
    val bytesReceived: Long,
    val packetsLost: Long,
    val congestionWindow: Long?,
    val maxData: Long?,
    val maxStreamData: Long?
)

fun ConnectionStats.toBabelfishStats() = BabelfishConnectionStats(
    rtt = rtt,
    bytesSent = bytesSent,
    bytesReceived = bytesReceived,
    packetsLost = packetsLost,
    congestionWindow = congestionWindow,
    maxData = maxData,
    maxStreamData = maxStreamData
)

interface BabelfishCertificate {
    fun getHash(): String
    fun close()
}

class RealBabelfishCertificate(internal val certificate: Certificate) : BabelfishCertificate {
    override fun getHash(): String = certificate.getHash()
    override fun close() = certificate.close()
}

interface BabelfishConnection {
    val isClosed: Boolean
    val maxDatagramSize: Long?
    
    suspend fun openUni(): BabelfishSendStream
    suspend fun openBi(): BabelfishStreamPair
    suspend fun acceptUni(): BabelfishRecvStream
    suspend fun acceptBi(): BabelfishStreamPair
    
    fun sendDatagram(data: ByteArray)
    suspend fun receiveDatagram(): ByteArray
    
    fun getStats(): BabelfishConnectionStats
    
    fun close(code: Long = 0, reason: String = "")
    fun close()
}

interface BabelfishStreamPair {
    val send: BabelfishSendStream
    val recv: BabelfishRecvStream
    fun close()
}

interface BabelfishSendStream {
    suspend fun write(data: ByteArray)
    suspend fun write(data: String)
    suspend fun setPriority(priority: Int)
    suspend fun getPriority(): Int
    fun close()
}

interface BabelfishRecvStream {
    fun chunks(chunkSize: Int = 8192): Flow<ByteArray>
    suspend fun read(buffer: ByteArray): Int
    fun close()
}

interface BabelfishEndpoint {
    suspend fun connect(url: String): BabelfishConnection
    fun incomingSessions(): Flow<BabelfishConnection>
    fun close()
}

class RealBabelfishConnection(private val connection: Connection) : BabelfishConnection {
    override val isClosed: Boolean get() = connection.isClosed()
    override val maxDatagramSize: Long? get() = connection.maxDatagramSize
    
    override suspend fun openUni(): BabelfishSendStream {
        println("[DEBUG-TRANSPORT] Opening UniStream...")
        return RealBabelfishSendStream(connection.openUni())
    }
    override suspend fun openBi(): BabelfishStreamPair {
        println("[DEBUG-TRANSPORT] Opening BiStream...")
        return RealBabelfishStreamPair(connection.openBi())
    }
    override suspend fun acceptUni(): BabelfishRecvStream {
        println("[DEBUG-TRANSPORT] Accepting UniStream...")
        return RealBabelfishRecvStream(connection.acceptUni())
    }
    override suspend fun acceptBi(): BabelfishStreamPair {
        println("[DEBUG-TRANSPORT] Accepting BiStream...")
        return RealBabelfishStreamPair(connection.acceptBi())
    }
    
    override fun sendDatagram(data: ByteArray) {
        println("[DEBUG-TRANSPORT] Sending datagram: ${data.size} bytes")
        connection.sendDatagram(data)
    }
    override suspend fun receiveDatagram(): ByteArray {
        val data = connection.receiveDatagram()
        println("[DEBUG-TRANSPORT] Received datagram: ${data.size} bytes")
        return data
    }
    
    override fun getStats(): BabelfishConnectionStats = connection.getStats().toBabelfishStats()
    
    override fun close(code: Long, reason: String) {
        println("[DEBUG-TRANSPORT] Closing connection (code=$code, reason=$reason)")
        connection.close(code, reason)
    }
    override fun close() {
        println("[DEBUG-TRANSPORT] Closing connection")
        connection.close()
    }
}

class RealBabelfishStreamPair(private val streamPair: StreamPair) : BabelfishStreamPair {
    override val send: BabelfishSendStream = RealBabelfishSendStream(streamPair.send)
    override val recv: BabelfishRecvStream = RealBabelfishRecvStream(streamPair.recv)
    override fun close() {
        println("[DEBUG-TRANSPORT] Closing StreamPair")
        send.close()
        recv.close()
    }
}

class RealBabelfishSendStream(private val stream: SendStream) : BabelfishSendStream {
    override suspend fun write(data: ByteArray) {
        println("[DEBUG-TRANSPORT] Writing ${data.size} bytes to stream")
        stream.write(data)
    }
    override suspend fun write(data: String) {
        println("[DEBUG-TRANSPORT] Writing string to stream: \"$data\"")
        stream.write(data)
    }
    override suspend fun setPriority(priority: Int) = stream.setPriority(priority)
    override suspend fun getPriority(): Int = stream.getPriority()
    override fun close() {
        println("[DEBUG-TRANSPORT] Closing SendStream")
        stream.close()
    }
}

class RealBabelfishRecvStream(private val stream: RecvStream) : BabelfishRecvStream {
    override fun chunks(chunkSize: Int): Flow<ByteArray> {
        println("[DEBUG-TRANSPORT] Creating chunks flow (size=$chunkSize)")
        return stream.chunks(chunkSize).map { 
            println("[DEBUG-TRANSPORT] Flow emitted chunk: ${it.size} bytes")
            it
        }
    }
    override suspend fun read(buffer: ByteArray): Int {
        val read = stream.read(buffer)
        println("[DEBUG-TRANSPORT] Read $read bytes into buffer")
        return read
    }
    override fun close() {
        println("[DEBUG-TRANSPORT] Closing RecvStream")
        stream.close()
    }
}

class RealBabelfishEndpoint(private val endpoint: Endpoint) : BabelfishEndpoint {
    override suspend fun connect(url: String): BabelfishConnection {
        println("[DEBUG-TRANSPORT] Connecting to $url ...")
        return RealBabelfishConnection(endpoint.connect(url))
    }
    
    override fun incomingSessions(): Flow<BabelfishConnection> {
        println("[DEBUG-TRANSPORT] Monitoring incoming sessions...")
        return endpoint.incomingSessions().map { 
            println("[DEBUG-TRANSPORT] Incoming session received")
            RealBabelfishConnection(it) 
        }
    }
    
    override fun close() {
        println("[DEBUG-TRANSPORT] Closing Endpoint")
        endpoint.close()
    }
}