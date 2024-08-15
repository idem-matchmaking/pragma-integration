package idem.client.schemas

data class Envelope(
    val action: String,
    val messageId: String?,
    val payload: Any?,
    val error: Error? = null,
)