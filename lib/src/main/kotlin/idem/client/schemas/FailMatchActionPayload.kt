package idem.client.schemas

data class FailMatchActionPayload(
    val gameId: String,
    val matchId: String,
    val remove: List<String>,
    val requeue: List<String>,
)