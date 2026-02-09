package ovh.devcraft.vogonpoet.domain.model

enum class MessageDirection {
    Sent,
    Received,
}

data class ProtocolMessage(
    val timestamp: Long,
    val direction: MessageDirection,
    val content: String,
)
