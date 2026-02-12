package ovh.devcraft.vogonpoet.infrastructure

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ovh.devcraft.vogonpoet.domain.model.ServerStatus
import java.io.File
import java.io.PrintWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlin.concurrent.thread

object BackendManager {
    private var process: Process? = null

    private val _serverStatus = MutableStateFlow(ServerStatus.STOPPED)
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()

    private var vogonLog: PrintWriter? = null
    private var babelLog: PrintWriter? = null
    private var mixedLog: PrintWriter? = null

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private val dirFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

    private fun initLogging() {
        val now = LocalDateTime.now()
        val logsRoot = File(SettingsRepository.appDataDir, "logs")
        if (!logsRoot.exists()) {
            logsRoot.mkdirs()
        }

        val logDir = File(logsRoot, now.format(dirFormatter))
        logDir.mkdirs()

        // Cleanup old logs: Keep the last 10 runs
        val allLogDirs =
            logsRoot
                .listFiles { file -> file.isDirectory }
                ?.sortedByDescending { it.name } ?: emptyList()

        if (allLogDirs.size > 10) {
            allLogDirs.drop(10).forEach { oldDir ->
                oldDir.deleteRecursively()
            }
        }

        vogonLog = PrintWriter(File(logDir, "vogon.log").bufferedWriter(), true)
        babelLog = PrintWriter(File(logDir, "babelfish.log").bufferedWriter(), true)
        mixedLog = PrintWriter(File(logDir, "mixed.log").bufferedWriter(), true)

        logVogon("Logging initialized in ${logDir.absolutePath}")
        if (allLogDirs.size > 10) {
            logVogon("Cleaned up ${allLogDirs.size - 10} old log directories.")
        }
    }

    private fun log(
        tag: String,
        message: String,
        writer: PrintWriter?,
    ) {
        val timestamp = LocalDateTime.now().format(timeFormatter)
        val formatted = "[$timestamp] [$tag] $message"

        // Write to specific log
        writer?.println(formatted)

        // Write to mixed log
        mixedLog?.println(formatted)

        // Write to stdout
        println(formatted)
    }

    fun logVogon(message: String) {
        log("VOGON", message, vogonLog)
    }

    private fun logBabel(message: String) {
        log("BABEL", message, babelLog)
    }

    private fun getVersionFromPyProject(file: File): String? {
        if (!file.exists()) return null
        return file.useLines { lines ->
            lines
                .find { it.trim().startsWith("version =") }
                ?.split("=")
                ?.get(1)
                ?.trim()
                ?.removeSurrounding("\"")
                ?.removeSurrounding("'")
        }
    }

    private fun getBundledVersion(): String =
        javaClass
            .getResourceAsStream("/babelfish_version.txt")
            ?.bufferedReader()
            ?.use { it.readText().trim() } ?: "0.0.0"

    private fun extractBabelfish(destDir: File) {
        val zipStream = javaClass.getResourceAsStream("/babelfish.zip")
        if (zipStream == null) {
            logVogon("Error: Could not find babelfish.zip in resources.")
            return
        }

        logVogon("Preparing Babelfish installation (v${getBundledVersion()})...")

        // We preserve the models directory if it exists
        val tempDir = File(destDir.parentFile, "babelfish_temp")
        if (tempDir.exists()) tempDir.deleteRecursively()
        tempDir.mkdirs()

        var totalSize: Long = 0
        ZipInputStream(zipStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val outFile = File(tempDir, entry.name)
                    outFile.parentFile.mkdirs()
                    outFile.outputStream().use { zis.copyTo(it) }
                    totalSize += entry.size
                }
                entry = zis.nextEntry
            }
        }

        logVogon("Extraction complete:")
        logVogon("  - Uncompressed size: ${totalSize / 1024} KB")

        // Atomic swap (except for models)
        if (destDir.exists()) {
            destDir.listFiles()?.forEach { file ->
                if (file.name != "models") file.deleteRecursively()
            }
        } else {
            destDir.mkdirs()
        }

        tempDir.listFiles()?.forEach { file ->
            file.renameTo(File(destDir, file.name))
        }
        tempDir.deleteRecursively()

        logVogon("Babelfish installed to ${destDir.absolutePath}")
    }

    private fun findDevBackend(workingDir: File): File? {
        var current: File? = workingDir
        for (i in 0..3) {
            if (current == null) break
            // Check if babelfish is a sibling of the current directory
            val candidate = File(current.parentFile, "babelfish")
            if (candidate.exists() && File(candidate, "pyproject.toml").exists()) {
                return candidate
            }
            current = current.parentFile
        }
        return null
    }

    private fun findDevBootstrap(workingDir: File): File? {
        var current: File? = workingDir
        for (i in 0..3) {
            if (current == null) break
            // Check typical locations in the source tree
            val candidates =
                listOf(
                    File(current, "src/jvmMain/resources/scripts/bootstrap.py"),
                    File(current, "composeApp/src/jvmMain/resources/scripts/bootstrap.py"),
                )
            candidates.find { it.exists() }?.let { return it }
            current = current.parentFile
        }
        return null
    }

    fun startBackend() {
        if (process != null && process!!.isAlive) {
            logVogon("Backend already running.")
            return
        }

        if (vogonLog == null) {
            initLogging()
        }

        val workingDir = File(System.getProperty("user.dir"))
        logVogon("Current working directory: ${workingDir.absolutePath}")

        // --- Locate Backend & Bootstrap ---
        var backendDir: File? = null
        var actualBootstrap: File? = null

        // 1. Try Dev Mode
        val devBackend = findDevBackend(workingDir)
        if (devBackend != null) {
            val devBootstrap = findDevBootstrap(workingDir)
            if (devBootstrap != null) {
                logVogon("Dev environment detected.")
                logVogon("  - Backend: ${devBackend.absolutePath}")
                logVogon("  - Bootstrap: ${devBootstrap.absolutePath}")
                backendDir = devBackend
                actualBootstrap = devBootstrap
            }
        }

        // 2. Prod Mode fallback
        if (backendDir == null) {
            val prodBackend = File(SettingsRepository.appCacheDir, "babelfish")
            val installedPyProject = File(prodBackend, "pyproject.toml")

            val bundledVersion = getBundledVersion()
            val installedVersion = getVersionFromPyProject(installedPyProject)

            if (installedVersion != bundledVersion) {
                if (installedVersion != null) {
                    logVogon("Update detected: $installedVersion -> $bundledVersion")
                }
                extractBabelfish(prodBackend)
            }
            backendDir = prodBackend
            actualBootstrap = File(prodBackend, "scripts/bootstrap.py")
        }

        if (actualBootstrap == null || !actualBootstrap.exists()) {
            logVogon("Error: Could not find bootstrap.py")
            return
        }

        // --- Locate UV ---
        var uvPath = "uv"
        val bundledUv = File(workingDir, "bin/uv")
        val bundledUvExe = File(workingDir, "bin/uv.exe")

        if (bundledUv.exists()) {
            uvPath = bundledUv.absolutePath
        } else if (bundledUvExe.exists()) {
            uvPath = bundledUvExe.absolutePath
        }

        logVogon("Using uv at $uvPath")
        logVogon("Using backend at ${backendDir.absolutePath}")

        val settings = SettingsRepository.load()

        _serverStatus.value = ServerStatus.INITIALIZING

        try {
            val cmd = mutableListOf(uvPath, "run", actualBootstrap.absolutePath)
            settings.modelsDir?.let {
                cmd.add("--models-dir")
                cmd.add(it)
            }

            val pb = ProcessBuilder(cmd)
            pb.directory(backendDir) // Run from backend dir
            pb.redirectErrorStream(true)

            settings.uvCacheDir?.let {
                pb.environment()["UV_CACHE_DIR"] = it
            }

            pb.environment()["PYTHONUNBUFFERED"] = "1"
            pb.environment()["VOGON_APP_DATA_DIR"] = SettingsRepository.appDataDir.absolutePath

            process = pb.start()
            logVogon("Backend process started (PID: ${process!!.pid()})")

            thread(start = true, isDaemon = true) {
                process!!.inputStream.bufferedReader().use { reader ->
                    reader.forEachLine { line ->
                        logBabel(line)

                        // Parse for status
                        if (line.contains("BOOTSTRAP SERVER STARTED")) {
                            _serverStatus.value = ServerStatus.BOOTSTRAPPING
                        } else if (line.contains("Exec-ing Babelfish")) {
                            _serverStatus.value = ServerStatus.STARTING
                        } else if (line.contains("SERVER: WebSockets running on") ||
                            line.contains("babelfish_stt.server:Starting WebSocket server")
                        ) {
                            // If we see this, it's the real Babelfish server.
                            _serverStatus.value = ServerStatus.READY
                        }
                    }
                }
                _serverStatus.value = ServerStatus.STOPPED
                logVogon("Backend process exited.")
            }

            Runtime.getRuntime().addShutdownHook(
                Thread {
                    stopBackend()
                },
            )
        } catch (e: Exception) {
            logVogon("Error: Failed to start backend: ${e.message}")
            _serverStatus.value = ServerStatus.STOPPED
        }
    }

    fun stopBackend() {
        process?.let {
            if (it.isAlive) {
                logVogon("Stopping backend...")
                it.destroy()
                if (!it.waitFor(5, TimeUnit.SECONDS)) {
                    logVogon("Backend did not stop, forcing...")
                    it.destroyForcibly()
                }
            }
        }
        process = null
        _serverStatus.value = ServerStatus.STOPPED
    }

    fun restartBackend() {
        logVogon("Restarting backend...")
        stopBackend()
        startBackend()
    }
}
