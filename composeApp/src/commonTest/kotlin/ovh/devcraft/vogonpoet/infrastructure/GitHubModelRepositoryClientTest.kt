package ovh.devcraft.vogonpoet.infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import ovh.devcraft.vogonpoet.domain.model.ModelType
import ovh.devcraft.vogonpoet.domain.model.RemoteModelSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitHubModelRepositoryClientTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parseVersionFromFilename should extract version number correctly`() {
        val githubClient = GitHubModelRepositoryClient(HttpClient())

        assertEquals(1, githubClient.parseVersionFromFilename("computer_v1.onnx"))
        assertEquals(2, githubClient.parseVersionFromFilename("computer_v2.tflite"))
        assertEquals(10, githubClient.parseVersionFromFilename("hey_siri_v10.onnx"))
        assertNull(githubClient.parseVersionFromFilename("invalid_file.onnx"))
        assertNull(githubClient.parseVersionFromFilename("no_version.onnx"))
    }

    @Test
    fun `generateDownloadUrls should create correct raw GitHub URLs`() {
        val githubClient = GitHubModelRepositoryClient(HttpClient())
        val source =
            RemoteModelSource(
                name = "OpenWakeWord",
                url = "https://github.com/fwartner/home-assistant-wakewords-collection",
                type = ModelType.WAKEWORD,
                language = "en",
            )

        val (onnxUrl, tfliteUrl) = githubClient.generateDownloadUrls(source, "computer", 2, "en")

        assertTrue(onnxUrl.contains("raw.githubusercontent.com"))
        assertTrue(onnxUrl.contains("computer_v2.onnx"))
        assertTrue(tfliteUrl.contains("computer_v2.tflite"))
    }

    @Test
    fun `findLatestVersion should return highest version from file list`() {
        val files =
            listOf(
                GitHubContentItem("computer_v1.onnx", "file", 100),
                GitHubContentItem("computer_v1.tflite", "file", 50),
                GitHubContentItem("computer_v2.onnx", "file", 110),
                GitHubContentItem("computer_v2.tflite", "file", 55),
                GitHubContentItem("computer_v3.onnx", "file", 120),
                GitHubContentItem("computer_v3.tflite", "file", 60),
            )

        val githubClient = GitHubModelRepositoryClient(HttpClient())
        val latest = githubClient.findLatestVersion(files, "computer")

        assertEquals(3, latest)
    }

    @Test
    fun `findLatestVersion should return null for empty file list`() {
        val githubClient = GitHubModelRepositoryClient(HttpClient())
        val latest = githubClient.findLatestVersion(emptyList(), "computer")

        assertNull(latest)
    }

    @Test
    fun `findLatestVersion should return null when no matching files`() {
        val files =
            listOf(
                GitHubContentItem("other_v1.onnx", "file", 100),
                GitHubContentItem("other_v1.tflite", "file", 50),
            )

        val githubClient = GitHubModelRepositoryClient(HttpClient())
        val latest = githubClient.findLatestVersion(files, "computer")

        assertNull(latest)
    }
}
