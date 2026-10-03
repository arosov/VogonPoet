package ovh.devcraft.vogonpoet.domain.model

/** JVM actual: DirectML is the Windows default; Linux defaults to CUDA
 * (babelfish refines that with NVIDIA-driver detection at config creation);
 * anything else — including macOS — defaults to CPU. */
actual fun platformDefaultDevice(): String {
    val osName = System.getProperty("os.name").lowercase()
    return when {
        osName.contains("win") -> "dml"
        osName.contains("linux") -> "cuda"
        else -> "cpu"
    }
}
