package idemmatchmaking.client.schemas

data class AddPlayerActionPayload(
    val gameId: String,
    val players: List<Player>,
    val partyName: String? = null,
) {
    data class Player(
        val playerId: String,
        val servers: List<String>,
        val reference: String? = null,
    )
}
