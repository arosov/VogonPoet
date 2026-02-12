package ovh.devcraft.vogonpoet.infrastructure

import kotlinx.coroutines.flow.StateFlow
import ovh.devcraft.vogonpoet.domain.BackendRepository
import ovh.devcraft.vogonpoet.domain.model.ServerStatus

expect object BackendRepositoryImpl : BackendRepository {
    override val serverStatus: StateFlow<ServerStatus>

    override fun start()

    override fun stop()

    override fun restart()
}
