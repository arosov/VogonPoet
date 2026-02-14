package ovh.devcraft.vogonpoet.infrastructure

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import ovh.devcraft.vogonpoet.domain.model.RemoteModel
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelDownloadServiceTest {
    private lateinit var tempDir: File
    private lateinit var downloadService: ModelDownloadService

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "vogonpoet_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        downloadService = ModelDownloadService(tempDir)
    }

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `isModelAlreadyDownloaded should return false when model files don't exist`() {
        val model =
            RemoteModel(
                name = "test_model",
                version = 1,
                onnxUrl = "https://example.com/test_v1.onnx",
                tfliteUrl = "https://example.com/test_v1.tflite",
                languageTag = "en",
            )

        val result = downloadService.isModelAlreadyDownloaded(model)
        assertFalse(result)
    }

    @Test
    fun `isModelAlreadyDownloaded should return true when both files exist`() {
        val model =
            RemoteModel(
                name = "test_model",
                version = 1,
                onnxUrl = "https://example.com/test_v1.onnx",
                tfliteUrl = "https://example.com/test_v1.tflite",
                languageTag = "en",
            )

        // Create the model directory and files with content
        val modelDir = File(tempDir, "start/en/test_model")
        modelDir.mkdirs()
        File(modelDir, "test_model_v1.onnx").writeText("dummy onnx content")
        File(modelDir, "test_model_v1.tflite").writeText("dummy tflite content")

        val result = downloadService.isModelAlreadyDownloaded(model)
        assertTrue(result)
    }

    @Test
    fun `getModelDirectory should return correct path`() {
        val model =
            RemoteModel(
                name = "computer",
                version = 2,
                onnxUrl = "https://example.com/computer_v2.onnx",
                tfliteUrl = "https://example.com/computer_v2.tflite",
                languageTag = "en",
            )

        val modelDir = downloadService.getModelDirectory(model)
        assertTrue(modelDir.path.contains("start/en/computer"))
    }

    @Test
    fun `DownloadState should have correct initial state`() {
        val model =
            RemoteModel(
                name = "test",
                version = 1,
                onnxUrl = "https://example.com/test.onnx",
                tfliteUrl = "https://example.com/test.tflite",
                languageTag = "en",
            )

        val state = DownloadState(model)

        assertEquals(model, state.model)
        assertEquals(0, state.progress)
        assertFalse(state.isComplete)
        assertFalse(state.isFailed)
        assertEquals(null, state.error)
    }

    @Test
    fun `DownloadState should track progress correctly`() {
        val model =
            RemoteModel(
                name = "test",
                version = 1,
                onnxUrl = "https://example.com/test.onnx",
                tfliteUrl = "https://example.com/test.tflite",
                languageTag = "en",
            )

        val state =
            DownloadState(model)
                .copy(progress = 50, bytesDownloaded = 512, totalBytes = 1024)

        assertEquals(50, state.progress)
        assertEquals(512, state.bytesDownloaded)
        assertEquals(1024, state.totalBytes)
    }

    @Test
    fun `DownloadState should track completion correctly`() {
        val model =
            RemoteModel(
                name = "test",
                version = 1,
                onnxUrl = "https://example.com/test.onnx",
                tfliteUrl = "https://example.com/test.tflite",
                languageTag = "en",
            )

        val state =
            DownloadState(model).copy(
                progress = 100,
                isComplete = true,
                bytesDownloaded = 1024,
                totalBytes = 1024,
            )

        assertTrue(state.isComplete)
        assertFalse(state.isFailed)
        assertEquals(100, state.progress)
    }

    @Test
    fun `DownloadState should track failure correctly`() {
        val model =
            RemoteModel(
                name = "test",
                version = 1,
                onnxUrl = "https://example.com/test.onnx",
                tfliteUrl = "https://example.com/test.tflite",
                languageTag = "en",
            )

        val error = Exception("Network error")
        val state =
            DownloadState(model).copy(
                isFailed = true,
                error = error,
            )

        assertFalse(state.isComplete)
        assertTrue(state.isFailed)
        assertEquals(error, state.error)
    }

    @Test
    fun `isModelAlreadyDownloaded should work with unversioned models`() {
        val unversionedModel =
            RemoteModel(
                name = "unversioned_model",
                version = null,
                onnxUrl = "https://example.com/unversioned_model.onnx",
                tfliteUrl = "https://example.com/unversioned_model.tflite",
                languageTag = "en",
            )

        // Initially should return false
        assertFalse(downloadService.isModelAlreadyDownloaded(unversionedModel))

        // Create the model directory and files with content
        val modelDir = File(tempDir, "start/en/unversioned_model")
        modelDir.mkdirs()
        File(modelDir, "unversioned_model.onnx").writeText("dummy onnx content")
        File(modelDir, "unversioned_model.tflite").writeText("dummy tflite content")

        // Now should return true
        assertTrue(downloadService.isModelAlreadyDownloaded(unversionedModel))
    }

    @Test
    fun `getModelDirectory should return same path regardless of version`() {
        val versionedModel =
            RemoteModel(
                name = "model",
                version = 1,
                onnxUrl = "https://example.com/model_v1.onnx",
                tfliteUrl = "https://example.com/model_v1.tflite",
                languageTag = "en",
            )

        val unversionedModel =
            RemoteModel(
                name = "model",
                version = null,
                onnxUrl = "https://example.com/model.onnx",
                tfliteUrl = "https://example.com/model.tflite",
                languageTag = "en",
            )

        // Both should have the same directory (based on name, not version)
        val versionedDir = downloadService.getModelDirectory(versionedModel)
        val unversionedDir = downloadService.getModelDirectory(unversionedModel)
        assertEquals(versionedDir.path, unversionedDir.path)
    }
}
