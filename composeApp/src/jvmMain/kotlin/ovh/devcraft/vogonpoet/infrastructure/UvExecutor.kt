package ovh.devcraft.vogonpoet.infrastructure

import kotlinx.coroutines.runBlocking
import java.io.File

object UvExecutor {
    private fun resolveUvPath(): String {
        val workingDir = File(System.getProperty("user.dir"))
        val bundledUv = File(workingDir, "bin/uv")
        val bundledUvExe = File(workingDir, "bin/uv.exe")
        val cachedUv = File(SettingsRepository.uvBinDir, "uv.exe")

        return when {
            bundledUv.exists() -> bundledUv.absolutePath
            bundledUvExe.exists() -> bundledUvExe.absolutePath
            cachedUv.exists() -> cachedUv.absolutePath
            else -> "uv" // Fallback to system PATH
        }
    }

    fun getEffectiveCachePath(): String {
        val settings = runBlocking { SettingsRepository.load() }
        return settings.uvCacheDir ?: File(SettingsRepository.appCacheDir, "uv").absolutePath
    }

    fun createProcess(args: List<String>, workingDir: File? = null): ProcessBuilder {
        val uvPath = resolveUvPath()
        val cmd = mutableListOf(uvPath)
        cmd.addAll(args)

        val pb = ProcessBuilder(cmd)
        val env = pb.environment()

        // Force consistent cache and environment
        val effectiveCache = getEffectiveCachePath()
        val cacheDir = File(effectiveCache)
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        // Total Isolation: Redirect all uv storage to the app cache
        env["UV_CACHE_DIR"] = effectiveCache
        env["UV_DATA_DIR"] = File(cacheDir, "data").absolutePath
        env["UV_CONFIG_DIR"] = File(cacheDir, "config").absolutePath
        env["UV_PYTHON_INSTALL_DIR"] = File(cacheDir, "python").absolutePath
        env["UV_TOOL_DIR"] = File(cacheDir, "tools").absolutePath
        env["UV_INSTALL_DIR"] = File(cacheDir, "bin").absolutePath

        // Prevent system leaks
        env["UV_NO_SYSTEM_PYTHON"] = "1"
        env["UV_PYTHON_PREFERENCE"] = "managed"
        env["UV_PYTHON_DOWNLOADS"] = "true"

        env["UV_CMD"] = uvPath
        env["PYTHONUNBUFFERED"] = "1"
        env["VOGON_APP_DATA_DIR"] = SettingsRepository.appDataDir.absolutePath

        val settings = runBlocking { SettingsRepository.load() }
        val userCacheDir = settings.modelsDir?.let { File(it).parentFile?.absolutePath }
        env["VOGON_APP_CACHE_DIR"] = userCacheDir ?: SettingsRepository.appCacheDir.absolutePath

        workingDir?.let { pb.directory(it) }

        return pb
    }
}
