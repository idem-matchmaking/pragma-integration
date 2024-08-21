package idemmatchmaking.client.schemas

data class AddPlayerResponsePayload(
    val gameId: String,
    val players: List<Player>,
)
