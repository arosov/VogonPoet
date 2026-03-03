package ovh.devcraft.vogonpoet.uvdownloader.domain.model

import java.io.File

sealed class UvDownloadStatus {
    data object Idle : UvDownloadStatus()
    data class Downloading(val progress: Int, val bytesDownloaded: Long, val totalBytes: Long) : UvDownloadStatus()
    data object Extracting : UvDownloadStatus()
    data class Success(val executable: File) : UvDownloadStatus()
    data class Error(val message: String, val throwable: Throwable? = null) : UvDownloadStatus()
}
