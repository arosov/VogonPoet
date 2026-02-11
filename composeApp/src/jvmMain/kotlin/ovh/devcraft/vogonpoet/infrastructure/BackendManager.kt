package ovh.devcraft.vogonpoet.infrastructure

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

object BackendManager {
    private var process: Process? = null

    fun startBackend() {
        if (process != null && process!!.isAlive) {
            println("Backend already running.")
            return
        }

        val workingDir = File(System.getProperty("user.dir"))
        println("BackendManager: Current working directory: ${workingDir.absolutePath}")

        // --- Locate UV ---
        // 1. Check bundled bin/uv (Prod)
        // 2. Check system PATH (Dev)
        var uvPath = "uv" // Default to system PATH
        val bundledUv = File(workingDir, "bin/uv")
        val bundledUvExe = File(workingDir, "bin/uv.exe")

        if (bundledUv.exists()) {
            uvPath = bundledUv.absolutePath
        } else if (bundledUvExe.exists()) {
            uvPath = bundledUvExe.absolutePath
        }

        // --- Locate Bootstrap Script ---
        // 1. Check scripts/bootstrap.py (Prod)
        // 2. Check src/jvmMain/resources/scripts/bootstrap.py (Dev - fragile but works for now)
        // 3. Fallback: Copy from resource stream to temp file?

        var bootstrapPath: File? = File(workingDir, "scripts/bootstrap.py")
        if (!bootstrapPath!!.exists()) {
            // Try dev path relative to composeApp
            val devPath = File("src/jvmMain/resources/scripts/bootstrap.py")
            if (devPath.exists()) {
                bootstrapPath = devPath
            } else {
                // Try looking up one level (if CWD is inside something deep)
                val devPathUp = File("../composeApp/src/jvmMain/resources/scripts/bootstrap.py")
                if (devPathUp.exists()) {
                    bootstrapPath = devPathUp
                } else {
                    // Last resort: Extract from resources
                    // For now, we assume dev setup is correct or prod file layout is correct.
                    println("BackendManager Error: Could not find bootstrap.py")
                    return
                }
            }
        }

        println("BackendManager: Using uv at $uvPath")
        println("BackendManager: Using bootstrap at ${bootstrapPath!!.absolutePath}")

        val settings = SettingsRepository.load()

        // --- Execute ---
        try {
            val cmd = mutableListOf(uvPath, "run", bootstrapPath!!.absolutePath)
            settings.modelsDir?.let {
                cmd.add("--models-dir")
                cmd.add(it)
            }

            val pb = ProcessBuilder(cmd)
            pb.directory(workingDir) // Run in app root so relative paths work
            pb.redirectErrorStream(true)

            // Pass custom UV cache dir if set
            settings.uvCacheDir?.let {
                pb.environment()["UV_CACHE_DIR"] = it
            }

            // Ensure Python output is not buffered so we see logs in real-time
            pb.environment()["PYTHONUNBUFFERED"] = "1"

            process = pb.start()
            println("BackendManager: Backend process started (PID: ${process!!.pid()})")

            // Determine stdout handling
            // We can pipe it to stdout for debugging
            thread(start = true, isDaemon = true) {
                process!!.inputStream.bufferedReader().use { reader ->
                    reader.forEachLine { line ->
                        println("[BACKEND] $line")
                    }
                }
            }

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(
                Thread {
                    stopBackend()
                },
            )
        } catch (e: Exception) {
            println("BackendManager Error: Failed to start backend: ${e.message}")
            e.printStackTrace()
        }
    }

    fun stopBackend() {
        process?.let {
            if (it.isAlive) {
                println("BackendManager: Stopping backend...")
                it.destroy()
                if (!it.waitFor(5, TimeUnit.SECONDS)) {
                    println("BackendManager: Backend did not stop, forcing...")
                    it.destroyForcibly()
                }
            }
        }
        process = null
    }

    fun restartBackend() {
        println("BackendManager: Restarting backend...")
        stopBackend()
        startBackend()
    }
}
