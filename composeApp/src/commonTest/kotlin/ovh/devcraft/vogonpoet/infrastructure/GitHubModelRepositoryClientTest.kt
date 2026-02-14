package ovh.devcraft.vogonpoet.infrastructure

import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitHubModelRepositoryClientTest {
    @Test
    fun `parseVersionFromFilename should extract version number correctly`() {
        val githubClient = GitHubModelRepositoryClient(HttpClient())

        assertEquals(1, githubClient.parseVersionFromFilename("computer_v1.onnx"))
        assertEquals(2, githubClient.parseVersionFromFilename("computer_v2.tflite"))
        assertEquals(10, githubClient.parseVersionFromFilename("hey_siri_v10.onnx"))
        assertNull(githubClient.parseVersionFromFilename("invalid_file.onnx"))
        assertNull(githubClient.parseVersionFromFilename("no_version.onnx"))
        assertNull(githubClient.parseVersionFromFilename("computer.onnx"))
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

    @Test
    fun `findLatestVersion should ignore non-versioned files`() {
        val files =
            listOf(
                GitHubContentItem("computer.onnx", "file", 100),
                GitHubContentItem("computer.tflite", "file", 50),
                GitHubContentItem("computer_v1.onnx", "file", 110),
                GitHubContentItem("computer_v1.tflite", "file", 55),
            )

        val githubClient = GitHubModelRepositoryClient(HttpClient())
        val latest = githubClient.findLatestVersion(files, "computer")

        // Should only find versioned files
        assertEquals(1, latest)
    }

    @Test
    fun `parseVersionFromFilename should handle edge cases`() {
        val githubClient = GitHubModelRepositoryClient(HttpClient())

        // Various version formats
        assertEquals(1, githubClient.parseVersionFromFilename("model_v1.onnx"))
        assertEquals(10, githubClient.parseVersionFromFilename("model_v10.onnx"))
        assertEquals(100, githubClient.parseVersionFromFilename("model_v100.onnx"))

        // Should return null for non-versioned files
        assertNull(githubClient.parseVersionFromFilename("model.onnx"))
        assertNull(githubClient.parseVersionFromFilename("model_v.onnx"))
        assertNull(githubClient.parseVersionFromFilename("v1_model.onnx"))
    }
}
