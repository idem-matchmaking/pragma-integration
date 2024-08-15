import idem.client.*
import idem.client.schemas.*
import idem.client.ws.IdemEvent
import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.delay
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
            client.removePlayerAction(
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
            client.failMatchAction(
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

    val playerId1 = UUID.randomUUID().toString()
    val playerId2 = UUID.randomUUID().toString()

    // Add players
    logger.info("Adding players: $playerId1, $playerId2")
    client.addPlayerAction(
        AddPlayerActionPayload(
            mode, listOf(
                AddPlayerActionPayload.Player(playerId1, listOf("server1")),
            )
        )
    )

    client.addPlayerAction(
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

                    delay(2000)

                    client.confirmMatchAction(
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

                    client.completeMatchAction(
                        CompleteMatchActionPayload(
                            gameId = event.payload.gameId,
                            matchId = matchId,
                            gameLength = 10.0,
                            teams = listOf(
                                CompleteMatchActionPayload.Team(
                                    players = listOf(
                                        CompleteMatchActionPayload.Player(playerId1, 1.0),
                                    )
                                ),
                                CompleteMatchActionPayload.Team(
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