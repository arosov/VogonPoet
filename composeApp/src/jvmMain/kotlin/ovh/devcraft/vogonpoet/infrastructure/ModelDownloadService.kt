package ovh.devcraft.vogonpoet.infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import ovh.devcraft.vogonpoet.domain.model.RemoteModel
import java.io.File
import java.io.FileOutputStream

/**
 * Service for downloading remote models to local storage.
 * Supports progress tracking, duplicate detection, and file validation.
 */
class ModelDownloadService(
    private val baseDirectory: File,
    private val httpClient: HttpClient = createDefaultClient(),
) {
    companion object {
        private const val MAX_RETRIES = 3

        private fun createDefaultClient(): HttpClient =
            HttpClient {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            }
    }

    /**
     * Downloads a remote model including both ONNX and TFLite files.
     * Emits progress updates through the returned Flow.
     *
     * @param model The remote model to download
     * @return Flow of DownloadState updates
     */
    fun downloadModel(model: RemoteModel): Flow<DownloadState> =
        flow {
            val modelDir = getModelDirectory(model)
            var state = DownloadState(model)

            emit(state)

            // Check if model already exists
            if (isModelAlreadyDownloaded(model)) {
                VogonLogger.i("Model ${model.name} v${model.version} already exists, skipping download")
                state =
                    state.copy(
                        progress = 100,
                        isComplete = true,
                        bytesDownloaded = getExistingModelSize(model),
                        totalBytes = getExistingModelSize(model),
                    )
                emit(state)
                return@flow
            }

            // Create directory if needed
            if (!modelDir.exists()) {
                modelDir.mkdirs()
            }

            try {
                // Download ONNX file
                state = state.copy(status = "Downloading ONNX file...")
                emit(state)

                val onnxFile = File(modelDir, "${model.name}_v${model.version}.onnx")
                val onnxSize =
                    downloadFileWithProgress(model.onnxUrl, onnxFile) { currentBytes, totalBytes ->
                        val onnxProgress =
                            if (totalBytes > 0) {
                                (currentBytes * 50 / totalBytes).toInt()
                            } else {
                                0
                            }
                        state =
                            state.copy(
                                progress = onnxProgress,
                                bytesDownloaded = currentBytes,
                                totalBytes = totalBytes * 2, // Approximate total (ONNX + TFLite)
                            )
                    }
                emit(state)

                // Download TFLite file
                state = state.copy(status = "Downloading TFLite file...", progress = 50)
                emit(state)

                val tfliteFile = File(modelDir, "${model.name}_v${model.version}.tflite")
                val tfliteSize =
                    downloadFileWithProgress(model.tfliteUrl, tfliteFile) { currentBytes, totalBytes ->
                        val tfliteProgress =
                            if (totalBytes > 0) {
                                50 + (currentBytes * 50 / totalBytes).toInt()
                            } else {
                                50
                            }
                        state =
                            state.copy(
                                progress = tfliteProgress.coerceAtMost(99),
                                bytesDownloaded = onnxSize + currentBytes,
                                totalBytes = onnxSize + totalBytes,
                            )
                    }
                emit(state)

                // Validate downloaded files
                if (!validateDownloadedFiles(model, modelDir)) {
                    throw Exception("Downloaded files failed validation")
                }

                // Mark as complete
                state =
                    state.copy(
                        progress = 100,
                        isComplete = true,
                        bytesDownloaded = onnxSize + tfliteSize,
                        totalBytes = onnxSize + tfliteSize,
                        status = "Download complete",
                    )
                VogonLogger.i("Successfully downloaded model ${model.name} v${model.version}")
            } catch (e: Exception) {
                VogonLogger.e("Failed to download model ${model.name}", e)
                state =
                    state.copy(
                        isFailed = true,
                        error = e,
                        status = "Download failed: ${e.message}",
                    )
                cleanupPartialDownload(model)
            }

            emit(state)
        }

    /**
     * Downloads a single file from a URL to the specified destination.
     * Supports retry logic for network failures.
     *
     * @param url The URL to download from
     * @param destination The file to save to
     * @param onProgress Callback for progress updates (non-suspending)
     * @return The total bytes downloaded
     */
    private suspend fun downloadFileWithProgress(
        url: String,
        destination: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
    ): Long {
        var lastException: Exception? = null

        repeat(MAX_RETRIES) { attempt ->
            try {
                val response: HttpResponse = httpClient.get(url)
                val totalBytes = response.headers["Content-Length"]?.toLongOrNull() ?: -1
                val bytes = response.body<ByteArray>()

                FileOutputStream(destination).use { output ->
                    output.write(bytes)
                    onProgress(bytes.size.toLong(), totalBytes)
                }

                return destination.length()
            } catch (e: Exception) {
                lastException = e
                VogonLogger.i("Download attempt ${attempt + 1}/$MAX_RETRIES failed for $url: ${e.message}")
                if (attempt < MAX_RETRIES - 1) {
                    kotlinx.coroutines.delay(1000L * (attempt + 1))
                }
            }
        }

        throw lastException ?: Exception("Download failed after $MAX_RETRIES attempts")
    }

    /**
     * Checks if a model has already been downloaded.
     *
     * @param model The model to check
     * @return True if both ONNX and TFLite files exist
     */
    fun isModelAlreadyDownloaded(model: RemoteModel): Boolean {
        val modelDir = getModelDirectory(model)
        val onnxFile = File(modelDir, "${model.name}_v${model.version}.onnx")
        val tfliteFile = File(modelDir, "${model.name}_v${model.version}.tflite")

        return onnxFile.exists() && onnxFile.length() > 0 &&
            tfliteFile.exists() && tfliteFile.length() > 0
    }

    /**
     * Gets the directory where a model should be stored.
     *
     * @param model The model
     * @return The directory File
     */
    fun getModelDirectory(model: RemoteModel): File = File(baseDirectory, "start/${model.languageTag}/${model.name}")

    /**
     * Validates that downloaded files exist and have reasonable sizes.
     *
     * @param model The model that was downloaded
     * @param modelDir The directory containing the files
     * @return True if validation passes
     */
    private fun validateDownloadedFiles(
        model: RemoteModel,
        modelDir: File,
    ): Boolean {
        val onnxFile = File(modelDir, "${model.name}_v${model.version}.onnx")
        val tfliteFile = File(modelDir, "${model.name}_v${model.version}.tflite")

        if (!onnxFile.exists() || !tfliteFile.exists()) {
            return false
        }

        if (onnxFile.length() == 0L || tfliteFile.length() == 0L) {
            return false
        }

        val minSize = 1024L
        if (onnxFile.length() < minSize || tfliteFile.length() < minSize) {
            VogonLogger.i("Warning: Downloaded files for ${model.name} seem too small, may be corrupted")
            return false
        }

        return true
    }

    /**
     * Gets the total size of already downloaded model files.
     *
     * @param model The model
     * @return Total size in bytes
     */
    private fun getExistingModelSize(model: RemoteModel): Long {
        val modelDir = getModelDirectory(model)
        val onnxFile = File(modelDir, "${model.name}_v${model.version}.onnx")
        val tfliteFile = File(modelDir, "${model.name}_v${model.version}.tflite")

        var totalSize = 0L
        if (onnxFile.exists()) totalSize += onnxFile.length()
        if (tfliteFile.exists()) totalSize += tfliteFile.length()

        return totalSize
    }

    /**
     * Cleans up partial or failed downloads.
     *
     * @param model The model to clean up
     */
    private fun cleanupPartialDownload(model: RemoteModel) {
        val modelDir = getModelDirectory(model)
        val onnxFile = File(modelDir, "${model.name}_v${model.version}.onnx")
        val tfliteFile = File(modelDir, "${model.name}_v${model.version}.tflite")

        try {
            if (onnxFile.exists()) onnxFile.delete()
            if (tfliteFile.exists()) tfliteFile.delete()
            if (modelDir.exists() && modelDir.listFiles()?.isEmpty() == true) {
                modelDir.delete()
            }
        } catch (e: Exception) {
            VogonLogger.i("Warning: Failed to cleanup partial download for ${model.name}: ${e.message}")
        }
    }
}

/**
 * Represents the current state of a model download operation.
 *
 * @property model The model being downloaded
 * @property progress Download progress from 0 to 100
 * @property bytesDownloaded Number of bytes downloaded so far
 * @property totalBytes Total expected size in bytes
 * @property isComplete Whether the download completed successfully
 * @property isFailed Whether the download failed
 * @property error The error if download failed
 * @property status Human-readable status message
 */
data class DownloadState(
    val model: RemoteModel,
    val progress: Int = 0,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val isComplete: Boolean = false,
    val isFailed: Boolean = false,
    val error: Throwable? = null,
    val status: String = "Starting download...",
)
