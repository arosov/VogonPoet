package ovh.devcraft.vogonpoet.domain.model

/** JVM actual: CPU is the default on every platform (babelfish ADR 0014) —
 * served by the Parakeet Redux int4 card, with GPUs an explicit opt-in.
 * Mirrors babelfish's `_platform_default_device`. */
actual fun platformDefaultDevice(): String = "cpu"
