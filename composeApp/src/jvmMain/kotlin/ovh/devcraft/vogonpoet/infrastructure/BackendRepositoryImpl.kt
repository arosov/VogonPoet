package ovh.devcraft.vogonpoet.infrastructure

import kotlinx.coroutines.flow.StateFlow
import ovh.devcraft.vogonpoet.domain.BackendRepository
import ovh.devcraft.vogonpoet.domain.model.ServerStatus

actual object BackendRepositoryImpl : BackendRepository {
    actual override val serverStatus: StateFlow<ServerStatus> = BackendManager.serverStatus

    actual override suspend fun start() {
        BackendManager.startBackend()
    }

    actual override suspend fun stop() {
        BackendManager.stopBackend()
    }

    actual override suspend fun restart() {
        BackendManager.restartBackend()
    }
}
