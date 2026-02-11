package ovh.devcraft.vogonpoet.infrastructure

import ovh.devcraft.vogonpoet.domain.VogonSettings

expect object SettingsRepository {
    fun load(): VogonSettings

    fun save(settings: VogonSettings)
}
