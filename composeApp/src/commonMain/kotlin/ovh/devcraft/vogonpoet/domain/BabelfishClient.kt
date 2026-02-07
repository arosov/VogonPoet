package ovh.devcraft.vogonpoet.domain

import kotlinx.coroutines.flow.StateFlow
import ovh.devcraft.vogonpoet.domain.model.ConnectionState
import ovh.devcraft.vogonpoet.domain.model.VadState

interface BabelfishClient {
    val connectionState: StateFlow<ConnectionState>
    val vadState: StateFlow<VadState>

    suspend fun connect()
    fun disconnect()
}
