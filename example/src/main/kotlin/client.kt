import idemmatchmaking.client.*
import idemmatchmaking.client.schemas.*
import idemmatchmaking.client.ws.IdemEvent
import io.github.cdimascio.dotenv.dotenv
import org.slf4j.LoggerFactory
import java.util.UUID

suspend fun main() {
    val env = dotenv {
        ignoreIfMissing = true
        systemProperties = true
        System.getProperty("project.root")?.let { directory = it }
    }

    val logger = LoggerFactory.getLogger("main")

    val username = env.get("IDEM_API_USERNAME")
    val password = env.get("IDEM_API_PASSWORD")
    val mode = "1v1"

    val client = IdemClient(IdemConfig(username, password, listOf(mode)))
    client.start()

    val players = client.getPlayers(mode).players.filter { it.state == "waiting" }
    if (players.isNotEmpty()) {
        logger.info("Removing stale players")

        val waitPlayerIds = players.map { it.playerId }.toMutableSet()

        for (player in players) {
            client.removePlayer(
                RemovePlayerActionPayload(
                    gameId = mode,
                    playerId = player.playerId
                )
            )
        }

        while(waitPlayerIds.isNotEmpty()) {
            client.incoming.receive().let { event ->
                when (event) {
                    is IdemEvent.RemovedPlayerAck -> {
                        logger.info("Remove player ack received: ${event.payload.playerId}")
                        waitPlayerIds.remove(event.payload.playerId)
                    }
                    else -> logger.info("Event: $event")
                }
            }
        }
    }

    val matches = client.getMatches(mode)
    if (matches.matches.isNotEmpty()) {
        logger.info("Failing stale matches")

        val waitMatchIds = matches.matches.map { it.uuid }.toMutableSet()

        for (match in matches.matches) {
            client.failMatch(
                FailMatchActionPayload(
                    gameId = mode,
                    matchId = match.uuid,
                    remove = match.teams.flatMap { it.players.map { player -> player.playerId } },
                    requeue = emptyList()
                )
            )
        }

        while(waitMatchIds.isNotEmpty()) {
            client.incoming.receive().let { event ->
                when (event) {
                    is IdemEvent.FailMatchAck -> {
                        logger.info("Fail match ack received: ${event.payload.matchId}")
                        waitMatchIds.remove(event.payload.matchId)
                    }
                    else -> logger.info("Event: $event")
                }
            }
        }

        logger.debug("Stale matches cleared")
    }

    val playerId1 = "00000000-0000-0000-0000-000000000001"
    val playerId2 = "00000000-0000-0000-0000-000000000002"

    // Add players
    logger.info("Adding players: $playerId1, $playerId2")
    client.addPlayer(
        AddPlayerActionPayload(
            mode, listOf(
                AddPlayerActionPayload.Player(playerId1, listOf("server1")),
            )
        )
    )

    client.addPlayer(
        AddPlayerActionPayload(
            mode, listOf(
                AddPlayerActionPayload.Player(playerId2, listOf("server1")),
            )
        )
    )

    var matchId = ""

    while (true) {
        client.incoming.receive().let { event ->
            when (event) {
                is IdemEvent.AddPlayerAck -> {
                    logger.info("Players added: ${event.payload.players.map { it.playerId }}")
                }

                is IdemEvent.MatchSuggestion -> {
                    logger.info("Match suggestion received: ${event.payload}")

                    val matchPlayers =
                        event.payload.match.teams.flatMap { it.players.map { player -> player.playerId } }
                            .toSet()

                    if (matchPlayers != setOf(playerId1, playerId2)) {
                        logger.error("Match suggestion does not contain both players")
                        return@main
                    }

                    matchId = event.payload.match.uuid

                    client.confirmMatch(
                        ConfirmMatchActionPayload(
                            gameId = event.payload.gameId,
                            matchId = matchId
                        )
                    )
                }

                is IdemEvent.ConfirmMatchAck -> {
                    logger.info("Match confirmed: ${event.payload.matchId}")

                    require(event.payload.matchId == matchId) {
                        "Match ID mismatch: ${event.payload.matchId} != $matchId"
                    }

                    client.completeMatch(
                        CompleteMatchActionPayload(
                            gameId = event.payload.gameId,
                            matchId = matchId,
                            gameLength = 0.0,
                            teams = listOf(
                                CompleteMatchActionPayload.Team(
                                    rank = 0,
                                    players = listOf(
                                        CompleteMatchActionPayload.Player(playerId1, 1.0),
                                    )
                                ),
                                CompleteMatchActionPayload.Team(
                                    rank = 1,
                                    players = listOf(
                                        CompleteMatchActionPayload.Player(playerId2, 0.0),
                                    )
                                )
                            )
                        )
                    )
                }

                is IdemEvent.CompleteMatchAck -> {
                    logger.info("Match completed: ${event.payload.matchId}")

                    require(event.payload.matchId == matchId) {
                        "Match ID mismatch: ${event.payload.matchId} != $matchId"
                    }

                    for (player in event.payload.players) {
                        logger.info("Player: $player")
                    }

                    return@main
                }

                else -> logger.info("Event: $event")
            }
        }
    }
}