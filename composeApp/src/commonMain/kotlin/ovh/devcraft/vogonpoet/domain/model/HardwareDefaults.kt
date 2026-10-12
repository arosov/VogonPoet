package ovh.devcraft.vogonpoet.domain.model

/**
 * The concrete backend a fresh config starts on (babelfish ADR 0007).
 *
 * `hardware.device` never holds "auto": since babelfish ADR 0014 the
 * default is CPU on every platform (served by the Parakeet Redux int4
 * card), with GPUs an explicit opt-in the user picks from the device
 * list or writes into the config.
 */
expect fun platformDefaultDevice(): String
