package ovh.devcraft.vogonpoet.infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ovh.devcraft.vogonpoet.domain.model.ModelType
import ovh.devcraft.vogonpoet.domain.model.RemoteModel
import ovh.devcraft.vogonpoet.domain.model.RemoteModelSource

/**
 * Client for fetching model information from GitHub repositories.
 * Uses the GitHub Contents API to discover and list available models.
 */
class GitHubModelRepositoryClient(
    private val httpClient: HttpClient = createDefaultClient(),
) {
    companion object {
        private const val GITHUB_API_BASE = "https://api.github.com"
        private const val RAW_GITHUB_BASE = "https://raw.githubusercontent.com"

        private fun createDefaultClient(): HttpClient =
            HttpClient {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            }
    }

    /**
     * Fetches all available models from a GitHub repository source.
     * Returns models that have both .onnx and .tflite files.
     * Prefers non-versioned files over versioned ones if both exist.
     *
     * @param source The remote model source configuration
     * @param path The subdirectory path to search for models (e.g., "en" for English models)
     * @return List of remote models
     */
    suspend fun fetchModels(
        source: RemoteModelSource,
        path: String = "en",
    ): List<RemoteModel> {
        val repoInfo =
            parseGitHubUrl(source.url)
                ?: throw IllegalArgumentException("Invalid GitHub URL: ${source.url}")

        val contentsUrl = "$GITHUB_API_BASE/repos/${repoInfo.owner}/${repoInfo.repo}/contents/$path"

        return try {
            val directories =
                httpClient
                    .get(contentsUrl)
                    .body<List<GitHubContentItem>>()
                    .filter { it.type == "dir" }

            directories.mapNotNull { dir ->
                fetchModelFromDirectory(repoInfo, path, dir.name, source)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Fetches model information from a specific directory.
     * Looks for both versioned and non-versioned .onnx and .tflite files.
     * Prefers non-versioned files if both exist.
     */
    private suspend fun fetchModelFromDirectory(
        repoInfo: GitHubRepoInfo,
        path: String,
        modelName: String,
        source: RemoteModelSource,
    ): RemoteModel? {
        val modelUrl = "$GITHUB_API_BASE/repos/${repoInfo.owner}/${repoInfo.repo}/contents/$path/$modelName"

        return try {
            val files = httpClient.get(modelUrl).body<List<GitHubContentItem>>()

            // Find all .onnx and .tflite files
            val onnxFiles = files.filter { it.name.endsWith(".onnx") }
            val tfliteFiles = files.filter { it.name.endsWith(".tflite") }

            if (onnxFiles.isEmpty() || tfliteFiles.isEmpty()) {
                return null // Model must have both file types
            }

            // Check for non-versioned files first (preferred)
            val nonVersionedOnnx = onnxFiles.find { it.name == "$modelName.onnx" }
            val nonVersionedTflite = tfliteFiles.find { it.name == "$modelName.tflite" }

            if (nonVersionedOnnx != null && nonVersionedTflite != null) {
                // Use non-versioned files
                val (onnxUrl, tfliteUrl) = generateDownloadUrls(repoInfo, path, modelName, null)
                return RemoteModel(
                    name = modelName,
                    version = null,
                    onnxUrl = onnxUrl,
                    tfliteUrl = tfliteUrl,
                    languageTag = source.language,
                )
            }

            // Fall back to versioned files - find the highest version
            val latestVersion = findLatestVersion(files, modelName)
            latestVersion?.let { version ->
                val (onnxUrl, tfliteUrl) = generateDownloadUrls(repoInfo, path, modelName, version)
                RemoteModel(
                    name = modelName,
                    version = version,
                    onnxUrl = onnxUrl,
                    tfliteUrl = tfliteUrl,
                    languageTag = source.language,
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parses version number from filename pattern {model_name}_v{N}.onnx or {model_name}_v{N}.tflite
     *
     * @param filename The filename to parse
     * @return The version number, or null if pattern doesn't match
     */
    fun parseVersionFromFilename(filename: String): Int? {
        val pattern = Regex("""_v(\d+)\.(onnx|tflite)$""")
        val match = pattern.find(filename)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * Finds the highest version number from a list of GitHub files for a given model.
     * Only considers versioned files (e.g., model_v1.onnx, not model.onnx).
     *
     * @param files List of GitHub content items
     * @param modelName The base name of the model to search for
     * @return The highest version number, or null if no valid versions found
     */
    fun findLatestVersion(
        files: List<GitHubContentItem>,
        modelName: String,
    ): Int? =
        files
            .filter {
                it.name.startsWith(modelName) &&
                    (it.name.endsWith(".onnx") || it.name.endsWith(".tflite"))
            }.mapNotNull { parseVersionFromFilename(it.name) }
            .maxOrNull()

    /**
     * Generates raw download URLs for ONNX and TFLite model files.
     *
     * @param repoInfo The GitHub repository information
     * @param path The path within the repository
     * @param modelName The name of the model
     * @param version The version number, or null for non-versioned files
     * @return Pair of (onnxUrl, tfliteUrl)
     */
    private fun generateDownloadUrls(
        repoInfo: GitHubRepoInfo,
        path: String,
        modelName: String,
        version: Int?,
    ): Pair<String, String> {
        val baseUrl = "$RAW_GITHUB_BASE/${repoInfo.owner}/${repoInfo.repo}/main/$path/$modelName"

        val onnxFilename = if (version != null) "${modelName}_v$version.onnx" else "$modelName.onnx"
        val tfliteFilename = if (version != null) "${modelName}_v$version.tflite" else "$modelName.tflite"

        val onnxUrl = "$baseUrl/$onnxFilename"
        val tfliteUrl = "$baseUrl/$tfliteFilename"

        return Pair(onnxUrl, tfliteUrl)
    }

    /**
     * Parses a GitHub URL to extract owner and repository name.
     *
     * @param url The GitHub URL (e.g., "https://github.com/owner/repo")
     * @return GitHubRepoInfo containing owner and repo, or null if parsing fails
     */
    private fun parseGitHubUrl(url: String): GitHubRepoInfo? {
        val pattern = Regex("""github\.com/([^/]+)/([^/]+)""")
        val match = pattern.find(url)
        return match?.let {
            GitHubRepoInfo(
                owner = it.groupValues[1],
                repo = it.groupValues[2].removeSuffix(".git"),
            )
        }
    }
}

/**
 * Data class representing parsed GitHub repository information.
 */
private data class GitHubRepoInfo(
    val owner: String,
    val repo: String,
)

/**
 * Data class representing a GitHub content item from the API.
 */
@Serializable
data class GitHubContentItem(
    val name: String,
    val type: String,
    val size: Long? = null,
)
