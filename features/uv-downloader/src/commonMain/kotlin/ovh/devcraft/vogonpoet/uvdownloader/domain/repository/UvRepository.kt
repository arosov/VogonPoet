package ovh.devcraft.vogonpoet.uvdownloader.domain.repository

import kotlinx.coroutines.flow.Flow
import ovh.devcraft.vogonpoet.uvdownloader.domain.model.UvDownloadStatus
import java.io.File

interface UvRepository {
    /**
     * Checks if uv is already installed in the specified directory.
     * @param targetDir The directory to check for uv.exe
     * @return The File pointing to uv.exe if it exists, null otherwise.
     */
    fun getInstalledUv(targetDir: File): File?

    /**
     * Downloads and extracts uv for the current platform.
     * @param targetDir The directory where uv should be installed.
     * @return A Flow of UvDownloadStatus updates.
     */
    fun downloadUv(targetDir: File): Flow<UvDownloadStatus>
}
