package ovh.devcraft.vogonpoet.domain.model

data class VogonConfig(
    val hardware: Hardware = Hardware(),
    val pipeline: Pipeline = Pipeline(),
    val systemInput: SystemInput = SystemInput(),
    val voice: Voice = Voice(),
    val ui: Ui = Ui(),
    val server: Server = Server(),
    val cache: Cache = Cache(),
) {
    data class Hardware(
        val device: String = "auto",
        val autoDetect: Boolean = true,
        val microphoneName: String? = null,
        val onnxModelDir: String? = null,
        val onnxExecutionProvider: String? = null,
        val quantization: String? = null,
        val activeDevice: String? = null,
        val activeDeviceName: String? = null,
        val vramTotalGb: Double? = null,
        val vramUsedBaselineGb: Double? = null,
        val vramUsedModelGb: Double? = null,
    )

    data class Pipeline(
        val silenceThresholdMs: Long = 400,
        val updateIntervalMs: Long = 100,
        val performance: Performance = Performance(),
    )

    data class Performance(
        val ghostThrottleMs: Long = 100,
        val ghostWindowS: Double? = null,
        val minPaddingS: Double? = null,
        val tier: String = "auto",
    )

    data class SystemInput(
        val enabled: Boolean = false,
        val typeGhost: Boolean = false,
    )

    data class Voice(
        val wakeword: String? = null,
        val stopWakeword: String? = null,
        val wakewordSensitivity: Double? = null,
        val stopWakewordSensitivity: Double? = null,
        val stopWords: List<String>? = null,
    )

    data class Ui(
        val verbose: Boolean = false,
        val showTimestamps: Boolean = true,
        val notifications: Boolean = true,
        val shortcuts: Shortcuts = Shortcuts(),
        val activationDetection: ActivationDetection = ActivationDetection(),
        val transcriptionWindow: TranscriptionWindow = TranscriptionWindow(),
    )

    data class Shortcuts(
        val toggleListening: String = "Ctrl+Space",
        val forceListen: String = "Left Ctrl",
    )

    data class ActivationDetection(
        val iconOnly: Boolean = false,
        val overlayMode: Boolean = false,
    )

    data class TranscriptionWindow(
        val alwaysOnTop: Boolean = true,
    )

    data class Server(
        val host: String = "127.0.0.1",
        val port: Long = 8123,
    )

    data class Cache(
        val cacheDir: String? = null,
    )
}
