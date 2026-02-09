package ovh.devcraft.vogonpoet.domain

import kotlinx.coroutines.flow.StateFlow
import ovh.devcraft.vogonpoet.domain.model.ConnectionState
import ovh.devcraft.vogonpoet.domain.model.ProtocolMessage
import ovh.devcraft.vogonpoet.domain.model.VadState
import ovh.devcraft.vogonpoet.infrastructure.model.Babelfish

interface BabelfishClient {
    val connectionState: StateFlow<ConnectionState>
    val vadState: StateFlow<VadState>
    val messages: StateFlow<List<ProtocolMessage>>
    val config: StateFlow<Babelfish?>

    suspend fun connect()

    fun disconnect()

    suspend fun saveConfig(config: Babelfish)
}
