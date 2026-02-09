package ovh.devcraft.vogonpoet.infrastructure

actual object BackendController {
    actual fun restart() {
        BackendManager.restartBackend()
    }
}
