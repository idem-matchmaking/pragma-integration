package idem.client.schemas

data class GetPlayersResponsePayload(
    val gameId: String,
    val players: List<Player>,
) {
    data class Player(
        val playerId: String,
        val state: String,
        val reference: String?,
    )
}
