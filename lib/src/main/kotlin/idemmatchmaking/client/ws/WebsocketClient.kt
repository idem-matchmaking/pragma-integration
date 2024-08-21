package idemmatchmaking.client.ws

import com.fasterxml.jackson.databind.JsonNode
import idemmatchmaking.client.AuthTokenProvider
import idemmatchmaking.client.schemas.Envelope
import idemmatchmaking.client.schemas.Error
import idemmatchmaking.client.schemas.SubscribeActionPayload
import idemmatchmaking.client.utils.JsonUtils
import idemmatchmaking.client.ws.commands.Request
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.time.withTimeout
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException

class WebsocketClient(
    private val endpoint: String,
    private val authTokenProvider: AuthTokenProvider,
    private val gameModes: List<String>,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.Default)
    private val incomingChannel = Channel<IdemEvent>(Channel.UNLIMITED)
    private val requestChannel = Channel<Request>(10)
    private val requestMap = ConcurrentHashMap<String, CompletableDeferred<JsonNode>>()
    private var workerJob: Job? = null

    private val reconnectDelayMillis = 1000L
    private val requestTimeout = Duration.ofSeconds(60)

    fun start() {
        if (workerJob != null) {
            throw IllegalStateException("IdemWebsocketClient already started")
        }

        // Make sure the subscribeAction is processed before any other actions
        val subscribeRequest = Request.Subscribe(
            payload = SubscribeActionPayload(
                gameIds = gameModes
            )
        )
        requestChannel.trySend(subscribeRequest)
        scope.launch {
            try {
                subscribeRequest.deferred.await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.error("Failed to subscribe", e)
                throw e
            }
        }

        workerJob = scope.launchWorker()
    }

    val incoming get(): ReceiveChannel<IdemEvent> = incomingChannel

    internal suspend fun request(request: Request) {
        requestChannel.send(request)
    }

    fun close() {
        logger.debug("Shutting down IDEM API websocket")
        incomingChannel.close()
    }

    private suspend fun handleCommand(session: DefaultClientWebSocketSession, request: Request) {
        val messageId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<JsonNode>()
        requestMap[messageId] = deferred
        session.send(
            JsonUtils.toJson(
                Envelope(
                    action = request.action,
                    messageId = messageId,
                    payload = request.actionPayload
                )
            ).also {
                logger.debug("Sending: {}", it)
            }
        )
        scope.launch {
            try {
                val responsePayload = withTimeout(requestTimeout) {
                    deferred.await()
                }
                request.complete(responsePayload)
            } catch (e: Throwable) {
                requestMap.remove(messageId)
                request.completeExceptionally(e)
            }
        }
    }

    private fun CoroutineScope.launchWorker(): Job {
        return launch {
            var isActive = true
            while (isActive) {
                val listenerScope = CoroutineScope(Dispatchers.Default)
                val client = HttpClient {
                    install(WebSockets)
                }
                requestMap.clear()
                try {
                    val token = authTokenProvider.getAuthToken()
                    client.webSocket("$endpoint?authorization=$token") {
                        logger.debug("Connected to IDEM API websocket")
                        val session = this
                        var isClosed = false
                        while (!isClosed) {
                            select<Unit> {
                                requestChannel.onReceiveCatching {
                                    if (it.isSuccess) {
                                        val command = it.getOrThrow()
                                        handleCommand(session, command)
                                    } else {
                                        isActive = false
                                        throw IllegalStateException("Failed to receive request")
                                    }
                                }
                                incoming.onReceiveCatching { result ->
                                    if (result.isClosed) {
                                        logger.debug("IDEM API websocket closed")
                                        incomingChannel.send(IdemEvent.Disconnected(IdemEvent.Disconnected.Reason.CLOSED))
                                        isClosed = true
                                        return@onReceiveCatching
                                    }
                                    if (result.isSuccess) {
                                        val message = result.getOrNull() as? Frame.Text
                                        if (message != null) {
                                            val json = message.readText()
                                            logger.debug("Received: {}", json)
                                            val deserialized = try {
                                                JsonUtils.fromJson<Message>(json)
                                            } catch (e: Exception) {
                                                throw RuntimeException("Failed to deserialize message", e)
                                            }

                                            if (deserialized.messageId != null) {
                                                val deferred = requestMap.remove(deserialized.messageId)
                                                if (deferred != null) {
                                                    if (deserialized.error != null) {
                                                        deferred.completeExceptionally(
                                                            IllegalStateException(
                                                                deserialized.error.message
                                                            )
                                                        )
                                                    } else {
                                                        if (deserialized.payload == null) {
                                                            deferred.completeExceptionally(IllegalStateException("Received empty payload"))
                                                        } else {
                                                            deferred.complete(deserialized.payload)
                                                        }
                                                    }
                                                } else {
                                                    logger.warn("Received response for unknown message ID: {}", deserialized.messageId)
                                                }
                                            } else {
                                                if (deserialized.error != null) {
                                                    incomingChannel.send(
                                                        IdemEvent.UnknownErrorResponse(
                                                            deserialized.action,
                                                            deserialized.error
                                                        )
                                                    )
                                                } else {
                                                    when (deserialized.action) {
                                                        "keepAlive" -> {}
                                                        else -> incomingChannel.send(
                                                            IdemEvent.from(
                                                                deserialized.action,
                                                                deserialized.payload
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            throw IllegalStateException("Received non-text frame")
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Websocket error", e)
                    incomingChannel.send(IdemEvent.Disconnected(IdemEvent.Disconnected.Reason.ERROR))
                } finally {
                    client.close()
                    listenerScope.cancel()
                }
                delay(reconnectDelayMillis)
            }
            logger.debug("Main worker stopped")
        }
    }

    data class Message(
        val action: String,
        val messageId: String?,
        val payload: JsonNode?,
        val error: Error? = null,
    )
}