package ovh.devcraft.vogonpoet.domain

import kotlinx.serialization.Serializable

@Serializable
data class VogonSettings(
    val isFirstBoot: Boolean = true,
    val uvCacheDir: String? = null,
    val modelsDir: String? = null,
)
