package idemmatchmaking.client

import idemmatchmaking.client.utils.configureJackson
import idemmatchmaking.client.ws.IdemEvent
import idemmatchmaking.client.ws.WebsocketClient
import idemmatchmaking.client.ws.commands.Request
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import kotlinx.coroutines.channels.ReceiveChannel
import org.slf4j.LoggerFactory

class IdemClient(
    private val config: IdemConfig
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            jackson {
                configureJackson()
            }
            jackson(ContentType.parse("application/x-amz-json-1.1")) {
                configureJackson()
            }
        }
    }
    private val authTokenProvider = AuthTokenProvider(config, client)
    private lateinit var ws: WebsocketClient

    fun start() {
        logger.debug("Authenticating with IDEM API")
        ws = WebsocketClient(config.wsEndpoint, authTokenProvider, config.gameModes)
        ws.start()
    }

    val incoming get(): ReceiveChannel<IdemEvent> {
        return ws.incoming
    }

    internal suspend fun request(request: Request) {
        ws.request(request)
    }

    fun close() {
        if (::ws.isInitialized) {
            ws.close()
        }
        client.close()
    }
}
