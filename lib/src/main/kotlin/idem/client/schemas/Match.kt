package idem.client.schemas

data class Match(
    val uuid: String,
    val teams: List<Team>,
)