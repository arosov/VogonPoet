package ovh.devcraft.vogonpoet.infrastructure

import kotlinx.coroutines.flow.StateFlow
import ovh.devcraft.vogonpoet.domain.model.ServerStatus

actual object BackendController {
    actual val serverStatus: StateFlow<ServerStatus> = BackendManager.serverStatus

    actual fun restart() {
        BackendManager.restartBackend()
    }
}
