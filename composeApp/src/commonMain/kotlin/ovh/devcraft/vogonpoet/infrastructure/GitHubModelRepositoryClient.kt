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
 * Uses the GitHub Trees API to fetch all repository contents in a single request,
 * avoiding rate limiting issues.
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
     * Uses the recursive Trees API to get all files in a single API call,
     * then parses locally to find models with both .onnx and .tflite files.
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

        VogonLogger.i("Fetching models from ${repoInfo.owner}/${repoInfo.repo} under path '$path'")

        // Try to fetch from main branch first, then master
        val (branch, tree) =
            when {
                fetchTree(repoInfo, "main") != null -> {
                    Pair("main", fetchTree(repoInfo, "main")!!)
                }

                fetchTree(repoInfo, "master") != null -> {
                    Pair("master", fetchTree(repoInfo, "master")!!)
                }

                else -> {
                    VogonLogger.e("Failed to fetch tree from both main and master branches")
                    return emptyList()
                }
            }

        VogonLogger.i("Successfully fetched tree from '$branch' branch")
        return parseModelsFromTree(tree, branch, path, repoInfo, source)
    }

    /**
     * Fetches the recursive tree for a specific branch.
     * Returns null if the branch doesn't exist or request fails.
     */
    private suspend fun fetchTree(
        repoInfo: GitHubRepoInfo,
        branch: String,
    ): GitHubTreeResponse? {
        val treeUrl = "$GITHUB_API_BASE/repos/${repoInfo.owner}/${repoInfo.repo}/git/trees/$branch?recursive=1"

        return try {
            VogonLogger.i("Fetching tree from branch '$branch': $treeUrl")

            val response =
                httpClient.get(treeUrl) {
                    header("User-Agent", "VogonPoet-ModelBrowser/1.0")
                    header("Accept", "application/vnd.github.v3+json")
                }

            if (response.status.value == 200) {
                val tree = response.body<GitHubTreeResponse>()
                VogonLogger.i("Successfully fetched ${tree.tree.size} items from tree")
                tree
            } else {
                VogonLogger.i("Tree fetch failed with status: ${response.status}")
                null
            }
        } catch (e: Exception) {
            VogonLogger.i("Tree fetch failed for branch '$branch': ${e.message}")
            null
        }
    }

    /**
     * Parses the tree response to find models with both .onnx and .tflite files.
     */
    private fun parseModelsFromTree(
        tree: GitHubTreeResponse,
        branch: String,
        path: String,
        repoInfo: GitHubRepoInfo,
        source: RemoteModelSource,
    ): List<RemoteModel> {
        // Filter for blobs (files) under the specified path
        val pathPrefix = "$path/"
        val relevantFiles =
            tree.tree.filter { item ->
                item.type == "blob" && item.path.startsWith(pathPrefix)
            }

        VogonLogger.i("Found ${relevantFiles.size} files under '$path'")

        // Group files by model directory
        // Path format: en/modelName/filename.ext
        val filesByModel =
            relevantFiles
                .groupBy { item ->
                    val relativePath = item.path.removePrefix(pathPrefix)
                    val parts = relativePath.split("/")
                    if (parts.size >= 1) parts[0] else null
                }.filterKeys { it != null }

        VogonLogger.i("Found ${filesByModel.size} potential model directories")

        // Process each model directory
        val models = mutableListOf<RemoteModel>()

        filesByModel.forEach { (modelName, files) ->
            if (modelName == null) return@forEach

            VogonLogger.i("Processing model '$modelName' with ${files.size} files")

            val model =
                extractModelFromFiles(
                    modelName = modelName,
                    files = files,
                    branch = branch,
                    path = path,
                    repoInfo = repoInfo,
                    source = source,
                )

            if (model != null) {
                models.add(model)
                VogonLogger.i("Added model: ${model.name} (version: ${model.version ?: "unversioned"})")
            }
        }

        return models.sortedBy { it.name }
    }

    /**
     * Extracts a model from the list of files in its directory.
     * Prefers non-versioned files over versioned ones.
     */
    private fun extractModelFromFiles(
        modelName: String,
        files: List<GitHubTreeItem>,
        branch: String,
        path: String,
        repoInfo: GitHubRepoInfo,
        source: RemoteModelSource,
    ): RemoteModel? {
        // Find all .onnx and .tflite files
        val onnxFiles = files.filter { it.path.endsWith(".onnx") }
        val tfliteFiles = files.filter { it.path.endsWith(".tflite") }

        if (onnxFiles.isEmpty() || tfliteFiles.isEmpty()) {
            VogonLogger.i("Model '$modelName' missing required file types: ${onnxFiles.size} .onnx, ${tfliteFiles.size} .tflite")
            return null
        }

        // Check for non-versioned files first (preferred)
        val nonVersionedOnnxPath = "$path/$modelName/$modelName.onnx"
        val nonVersionedTflitePath = "$path/$modelName/$modelName.tflite"

        val hasNonVersionedOnnx = onnxFiles.any { it.path == nonVersionedOnnxPath }
        val hasNonVersionedTflite = tfliteFiles.any { it.path == nonVersionedTflitePath }

        if (hasNonVersionedOnnx && hasNonVersionedTflite) {
            VogonLogger.i("Model '$modelName' using non-versioned files")
            val (onnxUrl, tfliteUrl) = generateRawUrls(repoInfo, branch, nonVersionedOnnxPath, nonVersionedTflitePath)
            return RemoteModel(
                name = modelName,
                version = null,
                onnxUrl = onnxUrl,
                tfliteUrl = tfliteUrl,
                languageTag = source.language,
            )
        }

        // Fall back to versioned files
        val latestVersion = findLatestVersion(files, modelName)

        return if (latestVersion != null) {
            VogonLogger.i("Model '$modelName' using version $latestVersion")
            val versionedOnnxPath = "$path/$modelName/${modelName}_v$latestVersion.onnx"
            val versionedTflitePath = "$path/$modelName/${modelName}_v$latestVersion.tflite"
            val (onnxUrl, tfliteUrl) = generateRawUrls(repoInfo, branch, versionedOnnxPath, versionedTflitePath)
            RemoteModel(
                name = modelName,
                version = latestVersion,
                onnxUrl = onnxUrl,
                tfliteUrl = tfliteUrl,
                languageTag = source.language,
            )
        } else {
            VogonLogger.i("Model '$modelName' has no valid versioned files")
            null
        }
    }

    /**
     * Parses version number from filename pattern {model_name}_v{N}.onnx or {model_name}_v{N}.tflite
     */
    fun parseVersionFromFilename(filename: String): Int? {
        val pattern = Regex("""_v(\d+)\.(onnx|tflite)$""")
        val match = pattern.find(filename)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * Finds the highest version number from a list of tree items for a given model.
     */
    private fun findLatestVersion(
        files: List<GitHubTreeItem>,
        modelName: String,
    ): Int? =
        files
            .filter { item ->
                val filename = item.path.substringAfterLast("/")
                filename.startsWith(modelName) &&
                    (filename.endsWith(".onnx") || filename.endsWith(".tflite"))
            }.mapNotNull { parseVersionFromFilename(it.path.substringAfterLast("/")) }
            .maxOrNull()

    /**
     * Generates raw download URLs for the given file paths.
     * These URLs use raw.githubusercontent.com and bypass API rate limits.
     */
    private fun generateRawUrls(
        repoInfo: GitHubRepoInfo,
        branch: String,
        onnxPath: String,
        tflitePath: String,
    ): Pair<String, String> {
        // Use the branch name (main/master) for the URL
        // This bypasses API rate limits since raw.githubusercontent.com is not rate-limited
        val baseUrl = "$RAW_GITHUB_BASE/${repoInfo.owner}/${repoInfo.repo}/$branch"

        val onnxUrl = "$baseUrl/$onnxPath"
        val tfliteUrl = "$baseUrl/$tflitePath"

        return Pair(onnxUrl, tfliteUrl)
    }

    /**
     * Parses a GitHub URL to extract owner and repository name.
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
 * GitHub repository information.
 */
private data class GitHubRepoInfo(
    val owner: String,
    val repo: String,
)

/**
 * Response from the GitHub Trees API.
 */
@Serializable
data class GitHubTreeResponse(
    val sha: String,
    val url: String,
    val tree: List<GitHubTreeItem>,
    val truncated: Boolean = false,
)

/**
 * Individual item in a GitHub tree response.
 */
@Serializable
data class GitHubTreeItem(
    val path: String,
    val mode: String,
    val type: String,
    val sha: String,
    val size: Long? = null,
    val url: String? = null,
)
