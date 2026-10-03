package ovh.devcraft.vogonpoet.domain.model

actual fun platformDefaultDevice(): String {
    val osName = System.getProperty("os.name").lowercase()
    return when {
        osName.contains("win") -> "dml"
        osName.contains("linux") -> "cuda"
        else -> "cpu"
    }
}
