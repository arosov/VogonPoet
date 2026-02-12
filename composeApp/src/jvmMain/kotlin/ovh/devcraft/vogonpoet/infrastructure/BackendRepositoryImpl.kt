package ovh.devcraft.vogonpoet.infrastructure

import kotlinx.coroutines.flow.StateFlow
import ovh.devcraft.vogonpoet.domain.BackendRepository
import ovh.devcraft.vogonpoet.domain.model.ServerStatus

actual object BackendRepositoryImpl : BackendRepository {
    override val serverStatus: StateFlow<ServerStatus> = BackendManager.serverStatus

    override fun start() {
        BackendManager.startBackend()
    }

    override fun stop() {
        BackendManager.stopBackend()
    }

    override fun restart() {
        BackendManager.restartBackend()
    }
}
