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
     * Only returns the latest version of each model.
     *
     * @param source The remote model source configuration
     * @param path The subdirectory path to search for models (e.g., "en" for English models)
     * @return List of remote models with latest versions only
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
                fetchLatestModelVersion(repoInfo, path, dir.name, source)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Fetches the latest version of a specific model from the repository.
     */
    private suspend fun fetchLatestModelVersion(
        repoInfo: GitHubRepoInfo,
        path: String,
        modelName: String,
        source: RemoteModelSource,
    ): RemoteModel? {
        val modelUrl = "$GITHUB_API_BASE/repos/${repoInfo.owner}/${repoInfo.repo}/contents/$path/$modelName"

        return try {
            val files = httpClient.get(modelUrl).body<List<GitHubContentItem>>()
            val latestVersion = findLatestVersion(files, modelName)

            latestVersion?.let { version ->
                val (onnxUrl, tfliteUrl) = generateDownloadUrls(source, modelName, version, path)
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
            .filter { it.name.startsWith(modelName) && (it.name.endsWith(".onnx") || it.name.endsWith(".tflite")) }
            .mapNotNull { parseVersionFromFilename(it.name) }
            .maxOrNull()

    /**
     * Generates raw download URLs for ONNX and TFLite model files.
     *
     * @param source The remote model source
     * @param modelName The name of the model
     * @param version The version number
     * @param path The path within the repository
     * @return Pair of (onnxUrl, tfliteUrl)
     */
    fun generateDownloadUrls(
        source: RemoteModelSource,
        modelName: String,
        version: Int,
        path: String,
    ): Pair<String, String> {
        val repoInfo =
            parseGitHubUrl(source.url)
                ?: throw IllegalArgumentException("Invalid GitHub URL: ${source.url}")

        val baseUrl = "$RAW_GITHUB_BASE/${repoInfo.owner}/${repoInfo.repo}/main/$path/$modelName"
        val onnxUrl = "$baseUrl/${modelName}_v$version.onnx"
        val tfliteUrl = "$baseUrl/${modelName}_v$version.tflite"

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
