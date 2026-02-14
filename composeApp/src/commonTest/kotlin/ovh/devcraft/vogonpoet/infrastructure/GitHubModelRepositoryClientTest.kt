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
