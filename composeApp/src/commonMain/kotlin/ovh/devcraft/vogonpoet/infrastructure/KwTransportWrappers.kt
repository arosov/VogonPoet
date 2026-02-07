package ovh.devcraft.vogonpoet.infrastructure

import io.github.arosov.kwtransport.Connection
import io.github.arosov.kwtransport.Endpoint

interface BabelfishConnection {
    val isClosed: Boolean
    suspend fun receiveDatagram(): ByteArray
    fun close()
}

interface BabelfishEndpoint {
    suspend fun connect(url: String): BabelfishConnection
    fun close()
}

class RealBabelfishConnection(private val connection: Connection) : BabelfishConnection {
    override val isClosed: Boolean get() = connection.isClosed()
    override suspend fun receiveDatagram(): ByteArray = connection.receiveDatagram()
    override fun close() = connection.close()
}

class RealBabelfishEndpoint(private val endpoint: Endpoint) : BabelfishEndpoint {
    override suspend fun connect(url: String): BabelfishConnection {
        return RealBabelfishConnection(endpoint.connect(url))
    }
    override fun close() = endpoint.close()
}
