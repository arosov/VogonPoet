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

    private val settingsFile: File by lazy {
        val appDataDir =
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
        if (!appDataDir.exists()) {
            appDataDir.mkdirs()
        }
        File(appDataDir, "settings.json")
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
            println("Error loading settings: ${e.message}")
            VogonSettings()
        }

    actual fun save(settings: VogonSettings) {
        try {
            val content = json.encodeToString(VogonSettings.serializer(), settings)
            settingsFile.writeText(content)
        } catch (e: Exception) {
            println("Error saving settings: ${e.message}")
        }
    }
}
