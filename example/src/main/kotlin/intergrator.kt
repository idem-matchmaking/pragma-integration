import idem.client.*
import idem.integration.IdemIntegrator
import idem.integration.Party
import idem.integration.Player
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

    integrator.removeStalePlayers(mode)

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

    delay(10000)
}

data class ExampleParty(
    override val id: UUID,
    override val players: List<ExamplePlayer>
): Party

data class ExamplePlayer(
    override val id: UUID,
    override val servers: List<String>
): Player