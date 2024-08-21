package idemmatchmaking.client.schemas

data class SubscribeActionPayload(
    val gameIds: List<String>,
    val priority: Int? = null,
    val rateLimit: Int? = null,
)
