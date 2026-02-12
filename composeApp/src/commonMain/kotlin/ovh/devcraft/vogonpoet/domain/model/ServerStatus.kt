package ovh.devcraft.vogonpoet.domain.model

enum class ServerStatus {
    STOPPED,
    INITIALIZING,
    BOOTSTRAPPING,
    STARTING,
    READY,
}
