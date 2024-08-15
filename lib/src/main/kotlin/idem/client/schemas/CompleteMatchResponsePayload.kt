package idem.client.schemas

data class CompleteMatchResponsePayload(
    val gameId: String,
    val matchId: String,
    val players: List<Player>
) {
    data class Player(
        val playerId: String,
        val reference: String?,
        val totalWins: Int,
        val totalLosses: Int,
        val totalMatchesPlayed: Int,
        val season: String,
        val seasonWins: Int,
        val seasonLosses: Int,
        val seasonMatchesPlayed: Int,
        val rating: Double,
        val ratingUncertainty: Double,
        val rankingPoints: Double,
        val ratingDeltaLastGame: Double,
        val rankingDeltaLastGame: Double,
        val wins: Int,
        val losses: Int,
        val matchesPlayed: Int,
        val winRatio: Double,
    )
}
