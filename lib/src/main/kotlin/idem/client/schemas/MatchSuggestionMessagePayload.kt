package idem.client.schemas

data class MatchSuggestionMessagePayload(
    val gameId: String,
    val match: Match,
)
