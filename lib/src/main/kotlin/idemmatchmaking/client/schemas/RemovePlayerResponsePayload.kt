package idemmatchmaking.client.schemas

data class RemovePlayerResponsePayload(
    val gameId: String,
    val playerId: String,
    val reference: String?,
)
