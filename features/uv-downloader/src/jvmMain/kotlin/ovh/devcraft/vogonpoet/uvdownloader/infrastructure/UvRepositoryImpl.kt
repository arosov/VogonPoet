package ovh.devcraft.vogonpoet.uvdownloader.infrastructure

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import ovh.devcraft.vogonpoet.uvdownloader.domain.model.UvDownloadStatus
import ovh.devcraft.vogonpoet.uvdownloader.domain.repository.UvRepository
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class UvRepositoryImpl(
    private val httpClient: HttpClient
) : UvRepository {

    override fun getInstalledUv(targetDir: File): File? {
        val exe = File(targetDir, "uv.exe")
        return if (exe.exists() && exe.isFile) exe else null
    }

    override fun downloadUv(targetDir: File): Flow<UvDownloadStatus> = flow {
        val url = "https://github.com/astral-sh/uv/releases/latest/download/uv-x86_64-pc-windows-msvc.zip"
        val tempZip = File(targetDir, "uv.zip.tmp")
        
        try {
            if (!targetDir.exists()) targetDir.mkdirs()

            emit(UvDownloadStatus.Downloading(0, 0, 0))

            val response = httpClient.get(url)

            // For simplicity and better progress tracking, we'll download to a file first
            val channel = response.bodyAsChannel()
            val totalBytes = response.contentLength() ?: 0L
            var bytesDownloaded = 0L

            FileOutputStream(tempZip).use { output ->
                val buffer = ByteArray(8192)
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    bytesDownloaded += read
                    val progress = if (totalBytes > 0) (bytesDownloaded * 100 / totalBytes).toInt() else 0
                    emit(UvDownloadStatus.Downloading(progress, bytesDownloaded, totalBytes))
                }
            }

            emit(UvDownloadStatus.Extracting)

            ZipInputStream(tempZip.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == "uv.exe") {
                        val outFile = File(targetDir, "uv.exe")
                        FileOutputStream(outFile).use { fos ->
                            zis.copyTo(fos)
                        }
                        zis.closeEntry()
                        break
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val finalExe = File(targetDir, "uv.exe")
            if (finalExe.exists()) {
                emit(UvDownloadStatus.Success(finalExe))
            } else {
                emit(UvDownloadStatus.Error("uv.exe not found in downloaded zip"))
            }

        } catch (e: Exception) {
            emit(UvDownloadStatus.Error(e.message ?: "Unknown error during uv download", e))
        } finally {
            if (tempZip.exists()) tempZip.delete()
        }
    }.flowOn(Dispatchers.IO)
}
