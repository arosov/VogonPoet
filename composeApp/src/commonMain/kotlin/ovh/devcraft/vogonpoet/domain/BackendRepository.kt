package ovh.devcraft.vogonpoet.domain

import kotlinx.coroutines.flow.StateFlow
import ovh.devcraft.vogonpoet.domain.model.ServerStatus

interface BackendRepository {
    val serverStatus: StateFlow<ServerStatus>

    fun start()

    fun stop()

    fun restart()
}
