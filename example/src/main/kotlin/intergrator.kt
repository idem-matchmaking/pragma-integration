import idemmatchmaking.client.*
import idemmatchmaking.client.schemas.CompleteMatchActionPayload
import idemmatchmaking.integration.IdemIntegrator
import idemmatchmaking.integration.Party
import idemmatchmaking.integration.Player
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

    val integrator = IdemIntegrator(
        IdemConfig(username, password, listOf(mode))
    )
    integrator.start()

    integrator.waitSyncFinish(mode)
    logger.info("Sync finished")

    val playerId1 = UUID.fromString("00000000-0000-0000-0000-000000000001")
    val playerId2 = UUID.fromString("00000000-0000-0000-0000-000000000002")
    val playerId3 = UUID.fromString("00000000-0000-0000-0000-000000000003")
    val playerId4 = UUID.fromString("00000000-0000-0000-0000-000000000004")

    // Add a party
    integrator.refreshParties(mode, listOf(
        ExampleParty(UUID.randomUUID(), listOf(
            ExamplePlayer(playerId1, listOf("server1")),
            ExamplePlayer(playerId2, listOf("server1"))
        )),
    ))

    // Remove it
    integrator.refreshParties(mode, listOf())

    // Add again
    integrator.refreshParties(mode, listOf(
        ExampleParty(UUID.randomUUID(), listOf(
            ExamplePlayer(playerId1, listOf("server1")),
            ExamplePlayer(playerId2, listOf("server1"))
        )),
    ))

    // Replace it
    integrator.refreshParties(mode, listOf(
        ExampleParty(UUID.randomUUID(), listOf(
            ExamplePlayer(playerId3, listOf("server1")),
            ExamplePlayer(playerId4, listOf("server1"))
        )),
    ))

    // Add 1 & 2
    integrator.refreshParties(mode, listOf(
        ExampleParty(UUID.randomUUID(), listOf(
            ExamplePlayer(playerId3, listOf("server1")),
            ExamplePlayer(playerId4, listOf("server1"))
        )),
        ExampleParty(UUID.randomUUID(), listOf(
            ExamplePlayer(playerId1, listOf("server1")),
            ExamplePlayer(playerId2, listOf("server1"))
        )),
    ))

    // Replace again with matchable parties
    integrator.refreshParties(mode, listOf(
        ExampleParty(UUID.randomUUID(), listOf(
            ExamplePlayer(playerId1, listOf("server1")),
        )),
        ExampleParty(UUID.randomUUID(), listOf(
            ExamplePlayer(playerId2, listOf("server1")),
        )),
    ))

    integrator.execute(mode) {
        val players = it.getPlayers(mode).players
        for (player in players) {
            logger.info("Player: ${player}")
        }
    }

    while (true) {
        val match = integrator.tryConfirmAndGetNextMatchSuggestion(mode)
        if (match != null) {
            logger.info("Match: $match")
            integrator.completeMatch(
                mode,
                matchId = match.matchId,
                gameLength = 0.0,
                teams = listOf(
                    CompleteMatchActionPayload.Team(
                        rank = 0,
                        players = listOf(
                            CompleteMatchActionPayload.Player(playerId1.toString(), 1.0),
                        )
                    ),
                    CompleteMatchActionPayload.Team(
                        rank = 1,
                        players = listOf(
                            CompleteMatchActionPayload.Player(playerId2.toString(), 0.0),
                        )
                    )
                )
            ).await()
            break
        }
        delay(1000)
    }
}

data class ExampleParty(
    override val id: UUID,
    override val players: List<ExamplePlayer>
): Party

data class ExamplePlayer(
    override val id: UUID,
    override val servers: List<String>
): Player