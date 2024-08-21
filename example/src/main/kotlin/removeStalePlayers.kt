import idemmatchmaking.client.*
import idemmatchmaking.client.schemas.*
import idemmatchmaking.client.ws.IdemEvent
import io.github.cdimascio.dotenv.dotenv
import org.slf4j.LoggerFactory

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

    val players = client.getPlayers(mode).players
    players.forEach {
        client.removePlayer(
            RemovePlayerActionPayload(
                gameId = mode,
                playerId = it.playerId,
                force = true
            )
        )
    }

    while (true) {
        client.incoming.receive().let { event ->
            logger.info("Event: $event")
        }
    }
}