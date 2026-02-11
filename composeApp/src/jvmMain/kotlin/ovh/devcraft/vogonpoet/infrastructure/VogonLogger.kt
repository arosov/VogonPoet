package ovh.devcraft.vogonpoet.infrastructure

actual object VogonLogger {
    actual fun i(message: String) {
        BackendManager.logVogon(message)
    }

    actual fun e(
        message: String,
        throwable: Throwable?,
    ) {
        val msg = if (throwable != null) "$message: ${throwable.message}" else message
        BackendManager.logVogon("ERROR: $msg")
    }
}
