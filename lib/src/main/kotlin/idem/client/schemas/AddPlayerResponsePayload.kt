package idem.client.schemas

data class AddPlayerResponsePayload(
    val gameId: String,
    val players: List<Player>,
)
