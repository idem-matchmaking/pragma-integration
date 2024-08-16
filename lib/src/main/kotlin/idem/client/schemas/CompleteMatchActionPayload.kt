package idem.client.schemas

data class CompleteMatchActionPayload(
    val gameId: String,
    val matchId: String,
    val teams: List<Team>,
    val gameLength: Double,
    val server: String? = null,
) {
    data class Team(
        val rank: Int,
        val players: List<Player>,
    )

    data class Player(
        val playerId: String,
        val score: Double,
    )
}
