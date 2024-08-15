package idem.client.schemas

data class GetMatchesResponsePayload(
    val gameId: String,
    val matches: List<Match>,
)
