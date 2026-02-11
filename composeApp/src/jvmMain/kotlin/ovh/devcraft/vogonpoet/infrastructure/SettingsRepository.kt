package ovh.devcraft.vogonpoet.infrastructure

import kotlinx.serialization.json.Json
import ovh.devcraft.vogonpoet.domain.VogonSettings
import java.io.File

actual object SettingsRepository {
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
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

    private val settingsFile: File by lazy {
        File(appDataDir, "vogon.config.json")
    }

    actual fun load(): VogonSettings =
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

    actual fun save(settings: VogonSettings) {
        try {
            val content = json.encodeToString(VogonSettings.serializer(), settings)
            settingsFile.writeText(content)
        } catch (e: Exception) {
            VogonLogger.e("Error saving settings", e)
        }
    }
}
