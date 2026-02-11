package ovh.devcraft.vogonpoet.infrastructure

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ServerStatus {
    STOPPED,
    INITIALIZING,
    BOOTSTRAPPING,
    STARTING,
    READY,
}

expect object BackendController {
    val serverStatus: StateFlow<ServerStatus>

    fun restart()
}
