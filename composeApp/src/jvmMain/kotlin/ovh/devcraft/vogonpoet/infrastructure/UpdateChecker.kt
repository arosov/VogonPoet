package ovh.devcraft.vogonpoet.infrastructure

import dev.hydraulic.conveyor.control.SoftwareUpdateController
import java.net.URL
import java.util.Properties

class UpdateChecker {
    private val repositoryUrl: String? =
        System.getProperty("app.repositoryUrl")
            ?: run {
                // Fallback for development: derive from vcs-url or use a default
                val vcsUrl = System.getProperty("app.vcs-url")
                if (vcsUrl != null && vcsUrl.contains("github.com")) {
                    // Convert https://github.com/user/repo to https://user.github.io/repo/releases/latest/download
                    val match = Regex("github\\.com/([^/]+)/([^/]+)").find(vcsUrl)
                    if (match != null) {
                        val user = match.groupValues[1]
                        val repo = match.groupValues[2]
                        "https://$user.github.io/$repo/releases/latest/download"
                    } else {
                        null
                    }
                } else {
                    null
                }
            }

    fun getCurrentVersion(): String =
        System.getProperty("app.version")
            ?: System.getProperty("app.version.from.conveyor")
            ?: "unknown"

    fun canTriggerUpdate(): Boolean {
        val controller = SoftwareUpdateController.getInstance()
        return controller?.canTriggerUpdateCheckUI() == SoftwareUpdateController.Availability.AVAILABLE
    }

    suspend fun checkForUpdate(): UpdateInfo? {
        VogonLogger.i("[UpdateChecker] Starting update check...")
        VogonLogger.i("[UpdateChecker] Repository URL: $repositoryUrl")
        VogonLogger.i("[UpdateChecker] Current app version: ${getCurrentVersion()}")

        val controller = SoftwareUpdateController.getInstance()
        val result =
            if (controller != null) {
                VogonLogger.i("[UpdateChecker] Using Control API (Windows/macOS)")
                checkViaControlApi(controller)
            } else {
                VogonLogger.i("[UpdateChecker] Using HTTP polling (Linux/fallback)")
                checkViaHttp()
            }

        if (result != null) {
            VogonLogger.i("[UpdateChecker] Update available: ${result.currentVersion} -> ${result.latestVersion}")
            VogonLogger.i("[UpdateChecker] Auto-update supported: ${result.canAutoUpdate}")
        } else {
            VogonLogger.i("[UpdateChecker] No update available (already on latest version)")
        }

        return result
    }

    private suspend fun checkViaControlApi(controller: SoftwareUpdateController): UpdateInfo? {
        return try {
            val current = controller.getCurrentVersion()
            if (current == null) {
                VogonLogger.i("[UpdateChecker] Failed to get current version from Control API")
                return null
            }

            val latest = controller.getCurrentVersionFromRepository()
            if (latest == null) {
                VogonLogger.i("[UpdateChecker] Failed to get latest version from repository")
                return null
            }

            if (latest.compareTo(current) > 0) {
                UpdateInfo(
                    currentVersion = current.toString(),
                    latestVersion = latest.toString(),
                    siteUrl = repositoryUrl ?: "",
                    canAutoUpdate = true,
                )
            } else {
                null
            }
        } catch (e: Exception) {
            VogonLogger.e("[UpdateChecker] Control API error: ${e.message}", e)
            null
        }
    }

    private suspend fun checkViaHttp(): UpdateInfo? {
        val url = repositoryUrl
        if (url == null) {
            VogonLogger.i("[UpdateChecker] No repository URL configured (app.repositoryUrl not set)")
            return null
        }

        return try {
            val metadataUrl = "$url/metadata.properties"
            VogonLogger.i("[UpdateChecker] Fetching $metadataUrl")
            val props = Properties()
            props.load(URL(metadataUrl).openStream())
            val latestVersion = props.getProperty("app.version")
            if (latestVersion == null) {
                VogonLogger.i("[UpdateChecker] metadata.properties missing app.version key")
                return null
            }
            val currentVersion = getCurrentVersion()

            VogonLogger.i("[UpdateChecker] Latest version from server: $latestVersion")

            if (compareVersions(latestVersion, currentVersion) > 0) {
                UpdateInfo(currentVersion, latestVersion, url, canAutoUpdate = false)
            } else {
                null
            }
        } catch (e: Exception) {
            VogonLogger.e("[UpdateChecker] HTTP polling error: ${e.message}", e)
            null
        }
    }

    private fun compareVersions(
        a: String,
        b: String,
    ): Int {
        val av = a.split(".").map { it.toIntOrNull() ?: 0 }
        val bv = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(av.size, bv.size)) {
            val ad = av.getOrElse(i) { 0 }
            val bd = bv.getOrElse(i) { 0 }
            if (ad != bd) return ad - bd
        }
        return 0
    }

    fun triggerUpdate() {
        VogonLogger.i("[UpdateChecker] Triggering update via Control API...")
        val controller = SoftwareUpdateController.getInstance()
        if (controller != null) {
            controller.triggerUpdateCheckUI()
        } else {
            VogonLogger.i("[UpdateChecker] Cannot trigger update - Control API not available (Linux)")
        }
    }
}

data class UpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val siteUrl: String,
    val canAutoUpdate: Boolean,
)
