package ovh.devcraft.vogonpoet.infrastructure

expect object VogonLogger {
    fun i(message: String)

    fun e(
        message: String,
        throwable: Throwable? = null,
    )
}
