package idem.client.schemas

data class RemovePlayerActionPayload(
    val gameId: String,
    val playerId: String,
    val force: Boolean? = null
)
