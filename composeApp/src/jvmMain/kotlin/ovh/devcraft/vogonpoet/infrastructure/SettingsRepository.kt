package ovh.devcraft.vogonpoet.infrastructure

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ovh.devcraft.vogonpoet.domain.VogonSettings
import java.awt.Desktop
import java.io.File
import java.io.IOException

actual object SettingsRepository {
    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
        }

    val appDataDir: File by lazy {
        val dir =
            when {
                System.getProperty("os.name").lowercase().contains("win") -> {
                    File(System.getenv("APPDATA"), "VogonPoet")
                }

                System.getProperty("os.name").lowercase().contains("mac") -> {
                    File(System.getProperty("user.home"), "Library/Application Support/VogonPoet")
                }

                else -> {
                    File(System.getProperty("user.home"), ".config/vogonpoet")
                }
            }
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    val appCacheDir: File by lazy {
        val dir =
            when {
                System.getProperty("os.name").lowercase().contains("win") -> {
                    File(System.getenv("LOCALAPPDATA"), "VogonPoet/Cache")
                }

                System.getProperty("os.name").lowercase().contains("mac") -> {
                    File(System.getProperty("user.home"), "Library/Caches/VogonPoet")
                }

                else -> {
                    val xdgCache = System.getenv("XDG_CACHE_HOME")
                    if (xdgCache != null) {
                        File(xdgCache, "vogonpoet")
                    } else {
                        File(System.getProperty("user.home"), ".cache/vogonpoet")
                    }
                }
            }
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    val openwakewordModelsDir: File by lazy {
        val dir = File(appDataDir, "openwakeword_models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        // Create start and stop subdirectories
        File(dir, "start").mkdirs()
        File(dir, "stop").mkdirs()
        dir
    }

    /**
     * Opens the wake word models directory in the system's file manager.
     * @return true if successful, false otherwise
     */
    fun openModelsFolder(): Boolean =
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(openwakewordModelsDir)
                true
            } else {
                VogonLogger.e("Desktop not supported on this platform")
                false
            }
        } catch (e: IOException) {
            VogonLogger.e("Failed to open models folder", e)
            false
        } catch (e: Exception) {
            VogonLogger.e("Unexpected error opening models folder", e)
            false
        }

    private val settingsFile: File by lazy {
        File(appDataDir, "vogon.config.json")
    }

    actual suspend fun load(): VogonSettings =
        withContext(Dispatchers.IO) {
            try {
                if (settingsFile.exists()) {
                    val content = settingsFile.readText()
                    json.decodeFromString<VogonSettings>(content)
                } else {
                    VogonSettings()
                }
            } catch (e: Exception) {
                VogonLogger.e("Error loading settings", e)
                VogonSettings()
            }
        }

    actual suspend fun save(settings: VogonSettings) {
        withContext(Dispatchers.IO) {
            try {
                val content = json.encodeToString(VogonSettings.serializer(), settings)
                settingsFile.writeText(content)
            } catch (e: Exception) {
                VogonLogger.e("Error saving settings", e)
            }
        }
    }
}
