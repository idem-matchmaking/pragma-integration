package idem.integration
data class MatchSuggestion(
    val matchId: String,
    val teams: List<Team>,
    val parties: List<Party>,
    val server: String,
) {
    class Team(
        val playerIds: List<String>,
    )
}
