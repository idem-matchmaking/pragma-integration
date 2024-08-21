package idemmatchmaking.client.schemas

data class FailMatchResponsePayload(
    val gameId: String,
    val matchId: String,
    val requeued: List<Player>,
    val removed: List<Player>,
)