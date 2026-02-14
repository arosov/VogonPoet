package ovh.devcraft.vogonpoet.infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
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

        VogonLogger.i("Parsing repo: owner=${repoInfo.owner}, repo=${repoInfo.repo}")

        val contentsUrl = "$GITHUB_API_BASE/repos/${repoInfo.owner}/${repoInfo.repo}/contents/$path"

        return try {
            VogonLogger.i("Fetching models from: $contentsUrl")

            val response =
                httpClient.get(contentsUrl) {
                    header("User-Agent", "VogonPoet-ModelBrowser/1.0")
                    header("Accept", "application/vnd.github.v3+json")
                }

            VogonLogger.i("Response status: ${response.status}")

            val items = response.body<List<GitHubContentItem>>()
            VogonLogger.i("Got ${items.size} items from repository")

            val directories = items.filter { it.type == "dir" }
            VogonLogger.i("Found ${directories.size} directories")

            val models =
                directories.mapNotNull { dir ->
                    VogonLogger.i("Processing directory: ${dir.name}")
                    fetchModelFromDirectory(repoInfo, path, dir.name, source)
                }

            VogonLogger.i("Successfully fetched ${models.size} models")
            models
        } catch (e: Exception) {
            VogonLogger.e("Failed to fetch models from $contentsUrl: ${e.message}", e)
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
            VogonLogger.i("Fetching files from: $modelUrl")

            val files =
                httpClient
                    .get(modelUrl) {
                        header("User-Agent", "VogonPoet-ModelBrowser/1.0")
                        header("Accept", "application/vnd.github.v3+json")
                    }.body<List<GitHubContentItem>>()

            VogonLogger.i("Directory $modelName contains ${files.size} files: ${files.map { it.name }}")

            // Find all .onnx and .tflite files
            val onnxFiles = files.filter { it.name.endsWith(".onnx") }
            val tfliteFiles = files.filter { it.name.endsWith(".tflite") }

            VogonLogger.i("$modelName: ${onnxFiles.size} .onnx files, ${tfliteFiles.size} .tflite files")

            if (onnxFiles.isEmpty() || tfliteFiles.isEmpty()) {
                VogonLogger.i("$modelName: Missing required file types (.onnx or .tflite), skipping")
                return null
            }

            // Check for non-versioned files first (preferred)
            val nonVersionedOnnx = onnxFiles.find { it.name == "$modelName.onnx" }
            val nonVersionedTflite = tfliteFiles.find { it.name == "$modelName.tflite" }

            if (nonVersionedOnnx != null && nonVersionedTflite != null) {
                VogonLogger.i("$modelName: Found non-versioned files, using them")
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
            VogonLogger.i("$modelName: No non-versioned files found, looking for versioned files")
            val latestVersion = findLatestVersion(files, modelName)

            if (latestVersion != null) {
                VogonLogger.i("$modelName: Using version $latestVersion")
                val (onnxUrl, tfliteUrl) = generateDownloadUrls(repoInfo, path, modelName, latestVersion)
                RemoteModel(
                    name = modelName,
                    version = latestVersion,
                    onnxUrl = onnxUrl,
                    tfliteUrl = tfliteUrl,
                    languageTag = source.language,
                )
            } else {
                VogonLogger.i("$modelName: No valid versioned files found")
                null
            }
        } catch (e: Exception) {
            VogonLogger.e("Failed to fetch model from $modelUrl: ${e.message}", e)
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
     * Uses "main" branch as default, with fallback to "master".
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
        // Use main branch - most modern repos use this
        val branch = "main"
        val baseUrl = "$RAW_GITHUB_BASE/${repoInfo.owner}/${repoInfo.repo}/$branch/$path/$modelName"

        val onnxFilename = if (version != null) "${modelName}_v$version.onnx" else "$modelName.onnx"
        val tfliteFilename = if (version != null) "${modelName}_v$version.tflite" else "$modelName.tflite"

        val onnxUrl = "$baseUrl/$onnxFilename"
        val tfliteUrl = "$baseUrl/$tfliteFilename"

        VogonLogger.i("Generated URLs for $modelName: ONNX=$onnxUrl, TFLite=$tfliteUrl")

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
