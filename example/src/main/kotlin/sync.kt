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

    val playerId1 = "00000000-0000-0000-0000-000000000001"

    client.addPlayerAction(
        AddPlayerActionPayload(
            mode, listOf(
                AddPlayerActionPayload.Player(playerId1, listOf("server1")),
            )
        )
    )

    client.removePlayerAction(
        RemovePlayerActionPayload(
            mode, playerId1
        )
    )

    client.addPlayerAction(
        AddPlayerActionPayload(
            mode, listOf(
                AddPlayerActionPayload.Player(playerId1, listOf("server1")),
            )
        )
    )

    client.removePlayerAction(
        RemovePlayerActionPayload(
            mode, playerId1
        )
    )

    val players = client.getPlayers(mode).players
    logger.info("Players: ${players.map { it.playerId }}")

    while (true) {
        client.incoming.receive().let { event ->
            when (event) {
                is IdemEvent.AddPlayerAck -> {
                    logger.info("Players added: ${event.payload.players.map { it.playerId }}")
                }

                is IdemEvent.RemovedPlayerAck -> {
                    logger.info("Players removed: ${event.payload.playerId}")
                }

                else -> logger.info("Event: $event")
            }
        }
    }
}