package ovh.devcraft.vogonpoet.domain.model

/**
 * The concrete backend a fresh config starts on (babelfish ADR 0007).
 *
 * `hardware.device` never holds "auto": Windows defaults to DirectML,
 * Linux to CUDA, anything else to CPU (babelfish refines the Linux case
 * with NVIDIA-driver detection at config creation).
 */
expect fun platformDefaultDevice(): String
