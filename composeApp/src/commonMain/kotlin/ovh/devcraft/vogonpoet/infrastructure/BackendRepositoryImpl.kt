package ovh.devcraft.vogonpoet.infrastructure

import kotlinx.coroutines.flow.StateFlow
import ovh.devcraft.vogonpoet.domain.BackendRepository
import ovh.devcraft.vogonpoet.domain.model.ServerStatus

expect object BackendRepositoryImpl : BackendRepository {
    override val serverStatus: StateFlow<ServerStatus>

    override suspend fun start()

    override suspend fun stop()

    override suspend fun restart()
}
