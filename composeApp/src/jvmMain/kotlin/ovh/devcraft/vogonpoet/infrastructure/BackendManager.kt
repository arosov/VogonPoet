package ovh.devcraft.vogonpoet.infrastructure

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ovh.devcraft.vogonpoet.domain.model.ServerStatus
import ovh.devcraft.vogonpoet.uvdownloader.domain.model.UvDownloadStatus
import ovh.devcraft.vogonpoet.uvdownloader.domain.usecase.DownloadUvUseCase
import ovh.devcraft.vogonpoet.uvdownloader.infrastructure.UvRepositoryImpl
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.io.File
import java.io.PrintWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

object BackendManager {
    private var process: Process? = null
    private val startMutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _serverStatus = MutableStateFlow(ServerStatus.STOPPED)
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()

    private var vogonLog: PrintWriter? = null
    private var babelLog: PrintWriter? = null
    private var mixedLog: PrintWriter? = null

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private val dirFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
    private val uvDownloader = UvRepositoryImpl(httpClient)
    private val uvDownloadUseCase = DownloadUvUseCase(uvDownloader)

    private val shutdownHook =
        Thread {
            runBlocking { stopBackend() }
        }

    init {
        Runtime.getRuntime().addShutdownHook(shutdownHook)
    }

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

    private fun closeLogging() {
        vogonLog?.flush()
        vogonLog?.close()
        vogonLog = null

        babelLog?.flush()
        babelLog?.close()
        babelLog = null

        mixedLog?.flush()
        mixedLog?.close()
        mixedLog = null
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

    private fun cleanupStaleProcess() {
        val pidFile = File(SettingsRepository.appDataDir, "babelfish.pid")
        if (pidFile.exists()) {
            try {
                val pidStr = pidFile.readText().trim()
                if (pidStr.isNotBlank()) {
                    val pid = pidStr.toLong()
                    logVogon("Found stale PID file: $pid")

                    ProcessHandle.of(pid).ifPresent { handle ->
                        if (handle.isAlive) {
                            val cmd = handle.info().command().orElse("").lowercase()
                            // Basic safety check to ensure we don't kill a random system process
                            // Python processes often look like "python.exe" or "uv.exe"
                            if (cmd.contains("python") || cmd.contains("uv") || cmd.contains("babelfish")) {
                                logVogon("Killing stale process $pid ($cmd)...")
                                try {
                                    handle.descendants().forEach { it.destroy() }
                                    handle.destroy()
                                    if (!handle.onExit().get(5, TimeUnit.SECONDS).isAlive) {
                                        logVogon("Stale process killed gracefully.")
                                    } else {
                                        handle.descendants().forEach { it.destroyForcibly() }
                                        handle.destroyForcibly()
                                        logVogon("Stale process killed forcibly.")
                                    }
                                } catch (e: Exception) {
                                    logVogon("Error killing stale process: ${e.message}")
                                }
                            } else {
                                logVogon("Process $pid exists but command '$cmd' does not match target. Skipping.")
                            }
                        } else {
                            logVogon("Process $pid is not alive.")
                        }
                    }
                }
            } catch (e: Exception) {
                logVogon("Error reading/processing PID file: ${e.message}")
            } finally {
                pidFile.delete()
            }
        }

        // Port-based cleanup for Windows
        if (System.getProperty("os.name").lowercase().contains("win")) {
            cleanupPortWindows(8123)
        }
    }

    private fun cleanupPortWindows(port: Int) {
        try {
            val process = Runtime.getRuntime().exec("cmd /c netstat -ano | findstr :$port")
            val output = process.inputStream.bufferedReader().readText()
            output.lines().forEach { line ->
                if (line.contains("LISTENING")) {
                    val parts = line.trim().split(Regex("\\s+"))
                    val pid = parts.lastOrNull()
                    if (pid != null && pid != "0" && pid.all { it.isDigit() }) {
                        logVogon("Port $port is occupied by PID $pid. Attempting to terminate...")
                        Runtime.getRuntime().exec("taskkill /F /PID $pid")
                    }
                }
            }
        } catch (e: Exception) {
            logVogon("Warning: Failed to cleanup port $port: ${e.message}")
        }
    }

    private suspend fun ensureUvInstalled(): File? {
        logVogon("Ensuring uv is installed...")
        val targetDir = SettingsRepository.uvBinDir
        var installedFile: File? = null

        uvDownloadUseCase(targetDir).collect { status ->
            when (status) {
                is UvDownloadStatus.Downloading -> {
                    if (status.progress % 10 == 0 || status.progress == 1) {
                        logVogon("Downloading uv: ${status.progress}% (${status.bytesDownloaded / 1024} KB)")
                    }
                }
                is UvDownloadStatus.Extracting -> {
                    logVogon("Extracting uv...")
                }
                is UvDownloadStatus.Success -> {
                    logVogon("uv is ready at ${status.executable.absolutePath}")
                    installedFile = status.executable
                }
                is UvDownloadStatus.Error -> {
                    logVogon("Error installing uv: ${status.message}")
                }
                UvDownloadStatus.Idle -> {}
            }
        }
        return installedFile
    }

    private fun checkVenvIsolation(backendDir: File) {
        val venvDir = File(backendDir, ".venv")
        val cfgFile = File(venvDir, "pyvenv.cfg")
        if (cfgFile.exists()) {
            try {
                val content = cfgFile.readText()
                // If the venv points to the old Roaming path, it's not isolated
                if (content.contains("AppData\\Roaming\\uv")) {
                    logVogon("Detected non-isolated virtual environment. Wiping for migration...")
                    venvDir.deleteRecursively()
                    val lockFile = File(backendDir, "uv.lock")
                    if (lockFile.exists()) lockFile.delete()
                }
            } catch (e: Exception) {
                logVogon("Warning: Failed to check venv isolation: ${e.message}")
            }
        }
    }

    private suspend fun ensurePythonInstalled(
        uvPath: String,
        backendDir: File,
        force: Boolean = false,
    ): Boolean =
        withContext(Dispatchers.IO) {
            logVogon("Ensuring Python 3.12.8 is available for uv (force=$force)...")
            try {
                if (force) {
                    // Remove local steering files that might confuse uv
                    val steeringFiles = listOf(".python-version", "uv.lock", ".last_hw_mode")
                    steeringFiles.forEach { fileName ->
                        val file = File(backendDir, fileName)
                        if (file.exists()) {
                            logVogon("Removing $fileName to avoid version conflicts.")
                            file.delete()
                        }
                    }

                    // Also check in the cache dir
                    val markerInCache = File(SettingsRepository.appCacheDir, ".last_hw_mode")
                    if (markerInCache.exists()) {
                        markerInCache.delete()
                    }
                }

                val args = mutableListOf("python", "install", "3.12.8")
                if (force) {
                    args.add("--reinstall")
                }

                val pb = UvExecutor.createProcess(args, backendDir)
                pb.redirectErrorStream(true)

                val process = pb.start()
                process.inputStream.bufferedReader().use { reader ->
                    reader.forEachLine { line -> logBabel("[uv-python] $line") }
                }
                val exitCode = process.waitFor()
                logVogon("Python installation finished with exit code $exitCode")
                exitCode == 0
            } catch (e: Exception) {
                logVogon("Error ensuring Python: ${e.message}")
                false
            }
        }

    private suspend fun warmupCache(backendDir: File) =
        withContext(Dispatchers.IO) {
            val marker = File(UvExecutor.getEffectiveCachePath(), ".warmup_done")
            if (marker.exists()) {
                return@withContext
            }

            logVogon("Hardware cache not found. Priming for all modes (this may take a few minutes)...")
            try {
                // Phase 1: CUDA
                logVogon("  - Pre-fetching NVIDIA/CUDA libraries...")
                val pbCuda =
                    UvExecutor.createProcess(
                        listOf("sync", "--extra", "nvidia-win", "--no-install-project", "--refresh"),
                        backendDir,
                    )
                pbCuda.redirectErrorStream(true)
                val procCuda = pbCuda.start()
                procCuda.inputStream.bufferedReader().use { reader ->
                    reader.forEachLine { line -> logBabel("[warmup-cuda] $line") }
                }
                val exitCuda = procCuda.waitFor()

                // Phase 2: DirectML
                logVogon("  - Pre-fetching DirectML libraries...")
                val pbDml =
                    UvExecutor.createProcess(
                        listOf("sync", "--extra", "windows-gpu", "--no-install-project", "--refresh"),
                        backendDir,
                    )
                pbDml.redirectErrorStream(true)
                val procDml = pbDml.start()
                procDml.inputStream.bufferedReader().use { reader ->
                    reader.forEachLine { line -> logBabel("[warmup-dml] $line") }
                }
                val exitDml = procDml.waitFor()

                if (exitCuda == 0 && exitDml == 0) {
                    marker.createNewFile()
                    logVogon("Hardware cache primed successfully.")
                } else {
                    logVogon("Warning: Hardware cache priming incomplete (CUDA: $exitCuda, DML: $exitDml).")
                }
            } catch (e: Exception) {
                logVogon("Warning: Hardware cache warmup failed: ${e.message}")
            }
        }

    suspend fun startBackend() =
        startMutex.withLock {
            withContext(Dispatchers.IO) {
                if (process != null && process!!.isAlive) {
                    logVogon("Backend already running.")
                    return@withContext
                }

                if (vogonLog == null) {
                    initLogging()
                }

                // Proactive cleanup of previous instances using PID file
                cleanupStaleProcess()

                // Load settings early to prime the cache
                val settings = SettingsRepository.load()

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
                return@withContext
            }

            // --- Locate UV ---
            var uvPath = "uv"
            val bundledUv = File(workingDir, "bin/uv")
            val bundledUvExe = File(workingDir, "bin/uv.exe")
            val cachedUv = File(SettingsRepository.uvBinDir, "uv.exe")

            if (bundledUv.exists()) {
                uvPath = bundledUv.absolutePath
            } else if (bundledUvExe.exists()) {
                uvPath = bundledUvExe.absolutePath
            } else if (cachedUv.exists()) {
                uvPath = cachedUv.absolutePath
            } else {
                // Not found in typical locations, try to download
                val downloadedUv = ensureUvInstalled()
                if (downloadedUv != null) {
                    uvPath = downloadedUv.absolutePath
                } else {
                    logVogon("uv not found and download failed. Attempting to use system 'uv'...")
                }
            }

            logVogon("Using uv at $uvPath")
            logVogon("Using backend at ${backendDir.absolutePath}")

            // Enforce isolation by wiping old venvs
            checkVenvIsolation(backendDir)

            // Pre-run toolchain cleanup and installation
            ensurePythonInstalled(uvPath, backendDir)

            // Priming the hardware cache (internal check for marker)
            warmupCache(backendDir)

            _serverStatus.value = ServerStatus.INITIALIZING

            var attempt = 1
            val maxAttempts = 2
            var success = false

            while (attempt <= maxAttempts && !success) {
                if (attempt > 1) {
                    logVogon("Retry attempt $attempt/$maxAttempts...")
                    // If retry, force python reinstall to fix potentially broken global toolchain metadata
                    ensurePythonInstalled(uvPath, backendDir, force = true)
                }

                try {
                    val args = mutableListOf("run", "--no-project", "--python", "3.12.8", actualBootstrap.absolutePath)
                    settings.modelsDir?.let {
                        args.add("--models-dir")
                        args.add(it)
                    }

                    val pb = UvExecutor.createProcess(args, backendDir)
                    pb.redirectErrorStream(true)

                    process = pb.start()
                    logVogon("Backend process started (PID: ${process!!.pid()})")

                    var detectedBrokenPython = false
                    val job = scope.launch {
                        process!!.inputStream.bufferedReader().use { reader ->
                            reader.forEachLine { line ->
                                logBabel(line)

                                if (line.contains("No Python at") || line.contains("not found in path")) {
                                    detectedBrokenPython = true
                                }

                                // Parse for status
                                if (line.contains("BOOTSTRAP SERVER STARTED")) {
                                    _serverStatus.value = ServerStatus.BOOTSTRAPPING
                                } else if (line.contains("Exec-ing Babelfish") || line.contains("Launching Babelfish")) {
                                    _serverStatus.value = ServerStatus.STARTING
                                } else if (line.contains("SERVER: WebSockets running on")) {
                                    _serverStatus.value = ServerStatus.READY
                                }
                            }
                        }
                        _serverStatus.value = ServerStatus.STOPPED
                        logVogon("Backend process exited.")
                    }

                    // Wait a bit to see if it crashes immediately
                    delay(2000)

                    if (!process!!.isAlive && detectedBrokenPython && attempt < maxAttempts) {
                        logVogon("Detected broken Python environment. Cleaning up .venv and retrying...")
                        val venvDir = File(backendDir, ".venv")
                        if (venvDir.exists()) {
                            venvDir.deleteRecursively()
                        }
                        val uvLock = File(backendDir, "uv.lock")
                        if (uvLock.exists()) {
                            uvLock.delete()
                        }
                        attempt++
                    } else {
                        success = true
                    }
                } catch (e: Exception) {
                    logVogon("Error: Failed to start backend: ${e.message}")
                    _serverStatus.value = ServerStatus.STOPPED
                    break
                }
            }
        }
    }

    suspend fun stopBackend() =
        withContext(Dispatchers.IO) {
            process?.let { proc ->
                if (proc.isAlive) {
                    logVogon("Stopping backend...")

                    // 1. Kill the entire process tree (critical for Windows)
                    // graceful attempt first, then forceful
                    proc.toHandle().descendants().forEach { child ->
                        child.destroy()
                    }

                    // 2. Kill the parent process
                    proc.destroy()

                    // 3. Wait and Force if necessary
                    if (!proc.waitFor(5, TimeUnit.SECONDS)) {
                        logVogon("Backend did not stop, forcing...")
                        // Force kill tree again just in case
                        proc.toHandle().descendants().forEach { it.destroyForcibly() }
                        proc.destroyForcibly()
                    }
                }
            }
            process = null
            _serverStatus.value = ServerStatus.STOPPED
            closeLogging()
        }

    suspend fun restartBackend() {
        logVogon("Restarting backend...")
        stopBackend()
        startBackend()
    }
}
