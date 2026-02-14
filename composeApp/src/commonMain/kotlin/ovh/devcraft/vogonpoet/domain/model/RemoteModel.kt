package ovh.devcraft.vogonpoet.domain.model

/**
 * Represents the type of model available in a remote repository.
 */
enum class ModelType {
    /** Wake word detection models */
    WAKEWORD,

    /** Stop word detection models */
    STOPWORD,
}

/**
 * Represents a remote source for model repositories.
 *
 * @property name The display name of the source (e.g., "OpenWakeWord Community - EN")
 * @property url The base URL of the repository
 * @property type The type of models available in this source
 * @property language The ISO 639-1 language code for models in this source (e.g., "en", "fr")
 */
data class RemoteModelSource(
    val name: String,
    val url: String,
    val type: ModelType,
    val language: String,
)

/**
 * Represents a remote wake word or stop word model available for download.
 *
 * @property name The name of the model (e.g., "computer", "hey_siri")
 * @property version The version number of the model (e.g., 1, 2), or null if unversioned
 * @property onnxUrl The URL to download the .onnx model file
 * @property tfliteUrl The URL to download the .tflite model file
 * @property languageTag The ISO 639-1 language code for this model (e.g., "en", "fr")
 */
data class RemoteModel(
    val name: String,
    val version: Int?,
    val onnxUrl: String,
    val tfliteUrl: String,
    val languageTag: String,
) {
    /**
     * Returns the display name with language tag suffix.
     * Example: "computer [en]"
     */
    val displayName: String
        get() = "$name [$languageTag]"

    /**
     * Returns the filename for the ONNX file (with version suffix if applicable).
     */
    val onnxFilename: String
        get() = if (version != null) "${name}_v$version.onnx" else "$name.onnx"

    /**
     * Returns the filename for the TFLite file (with version suffix if applicable).
     */
    val tfliteFilename: String
        get() = if (version != null) "${name}_v$version.tflite" else "$name.tflite"
}
