package ovh.devcraft.vogonpoet.uvdownloader.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import ovh.devcraft.vogonpoet.uvdownloader.domain.model.UvDownloadStatus
import ovh.devcraft.vogonpoet.uvdownloader.domain.repository.UvRepository
import java.io.File

class DownloadUvUseCase(private val repository: UvRepository) {
    operator fun invoke(targetDir: File): Flow<UvDownloadStatus> {
        val installed = repository.getInstalledUv(targetDir)
        return if (installed != null) {
            flowOf(UvDownloadStatus.Success(installed))
        } else {
            repository.downloadUv(targetDir)
        }
    }
}
