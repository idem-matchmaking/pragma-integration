package idemmatchmaking.client.schemas

data class SubscribeResponsePayload(
    val gameIds: List<String>,
    val priority: Int? = null,
    val rateLimit: Int? = null,
)
