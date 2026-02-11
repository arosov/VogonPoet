package ovh.devcraft.vogonpoet.infrastructure

import kotlinx.coroutines.flow.StateFlow

actual object BackendController {
    actual val serverStatus: StateFlow<ServerStatus> = BackendManager.serverStatus

    actual fun restart() {
        BackendManager.restartBackend()
    }
}
