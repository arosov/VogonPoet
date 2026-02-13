package ovh.devcraft.vogonpoet.domain.model

data class TranscriptionState(
    val finalizedText: List<String> = emptyList(),
    val ghostText: String = "",
)
