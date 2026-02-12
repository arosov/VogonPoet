package ovh.devcraft.vogonpoet.infrastructure

import ovh.devcraft.vogonpoet.domain.VogonSettings

expect object SettingsRepository {
    suspend fun load(): VogonSettings

    suspend fun save(settings: VogonSettings)
}
