package ovh.devcraft.vogonpoet.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class RemoteModelTest {
    @Test
    fun `RemoteModelSource should store all properties correctly`() {
        val source =
            RemoteModelSource(
                name = "OpenWakeWord Community - EN",
                url = "https://github.com/fwartner/home-assistant-wakewords-collection",
                type = ModelType.WAKEWORD,
                language = "en",
            )

        assertEquals("OpenWakeWord Community - EN", source.name)
        assertEquals("https://github.com/fwartner/home-assistant-wakewords-collection", source.url)
        assertEquals(ModelType.WAKEWORD, source.type)
        assertEquals("en", source.language)
    }

    @Test
    fun `RemoteModel should store all properties correctly`() {
        val model =
            RemoteModel(
                name = "computer",
                version = 1,
                onnxUrl = "https://example.com/computer_v1.onnx",
                tfliteUrl = "https://example.com/computer_v1.tflite",
                languageTag = "en",
            )

        assertEquals("computer", model.name)
        assertEquals(1, model.version)
        assertEquals("https://example.com/computer_v1.onnx", model.onnxUrl)
        assertEquals("https://example.com/computer_v1.tflite", model.tfliteUrl)
        assertEquals("en", model.languageTag)
    }

    @Test
    fun `RemoteModel displayName should include language tag`() {
        val model =
            RemoteModel(
                name = "computer",
                version = 1,
                onnxUrl = "https://example.com/computer_v1.onnx",
                tfliteUrl = "https://example.com/computer_v1.tflite",
                languageTag = "en",
            )

        assertEquals("computer [en]", model.displayName)
    }

    @Test
    fun `ModelType should have correct enum values`() {
        assertEquals(ModelType.WAKEWORD, ModelType.valueOf("WAKEWORD"))
        assertEquals(ModelType.STOPWORD, ModelType.valueOf("STOPWORD"))
    }

    @Test
    fun `RemoteModelSource should support equality comparison`() {
        val source1 =
            RemoteModelSource(
                name = "Test Source",
                url = "https://example.com",
                type = ModelType.WAKEWORD,
                language = "en",
            )

        val source2 =
            RemoteModelSource(
                name = "Test Source",
                url = "https://example.com",
                type = ModelType.WAKEWORD,
                language = "en",
            )

        val source3 =
            RemoteModelSource(
                name = "Different Source",
                url = "https://example.com",
                type = ModelType.WAKEWORD,
                language = "en",
            )

        assertEquals(source1, source2)
        assertNotEquals(source1, source3)
    }

    @Test
    fun `RemoteModel should support equality comparison`() {
        val model1 =
            RemoteModel(
                name = "computer",
                version = 1,
                onnxUrl = "https://example.com/computer_v1.onnx",
                tfliteUrl = "https://example.com/computer_v1.tflite",
                languageTag = "en",
            )

        val model2 =
            RemoteModel(
                name = "computer",
                version = 1,
                onnxUrl = "https://example.com/computer_v1.onnx",
                tfliteUrl = "https://example.com/computer_v1.tflite",
                languageTag = "en",
            )

        val model3 =
            RemoteModel(
                name = "hey_siri",
                version = 1,
                onnxUrl = "https://example.com/hey_siri_v1.onnx",
                tfliteUrl = "https://example.com/hey_siri_v1.tflite",
                languageTag = "en",
            )

        assertEquals(model1, model2)
        assertNotEquals(model1, model3)
    }

    @Test
    fun `RemoteModel should support different versions`() {
        val modelV1 =
            RemoteModel(
                name = "computer",
                version = 1,
                onnxUrl = "https://example.com/computer_v1.onnx",
                tfliteUrl = "https://example.com/computer_v1.tflite",
                languageTag = "en",
            )

        val modelV2 =
            RemoteModel(
                name = "computer",
                version = 2,
                onnxUrl = "https://example.com/computer_v2.onnx",
                tfliteUrl = "https://example.com/computer_v2.tflite",
                languageTag = "en",
            )

        assertNotEquals(modelV1, modelV2)
        assertEquals(1, modelV1.version)
        assertEquals(2, modelV2.version)
    }

    @Test
    fun `RemoteModel should support null version for unversioned models`() {
        val unversionedModel =
            RemoteModel(
                name = "computer",
                version = null,
                onnxUrl = "https://example.com/computer.onnx",
                tfliteUrl = "https://example.com/computer.tflite",
                languageTag = "en",
            )

        assertEquals(null, unversionedModel.version)
        assertEquals("computer.onnx", unversionedModel.onnxFilename)
        assertEquals("computer.tflite", unversionedModel.tfliteFilename)
    }

    @Test
    fun `RemoteModel should generate correct filenames for versioned models`() {
        val versionedModel =
            RemoteModel(
                name = "computer",
                version = 1,
                onnxUrl = "https://example.com/computer_v1.onnx",
                tfliteUrl = "https://example.com/computer_v1.tflite",
                languageTag = "en",
            )

        assertEquals("computer_v1.onnx", versionedModel.onnxFilename)
        assertEquals("computer_v1.tflite", versionedModel.tfliteFilename)
    }
}
