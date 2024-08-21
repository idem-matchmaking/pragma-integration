package idemmatchmaking.integration

import idemmatchmaking.client.IdemClient
import idemmatchmaking.client.IdemConfig
import idemmatchmaking.client.schemas.CompleteMatchActionPayload
import idemmatchmaking.client.ws.IdemEvent
import io.ktor.util.collections.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.slf4j.LoggerFactory

class IdemIntegrator(
    config: IdemConfig,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val client = IdemClient(config)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val trackerMap = ConcurrentMap<String, ModeTracker>()
    private val actionChannel = Channel<Pair<String, ModeAction>>(Channel.UNLIMITED)

    fun refreshParties(mode: String, parties: List<Party>) {
        getModeTracker(mode).refreshParties(parties)
    }

    fun tryConfirmAndGetNextMatchSuggestion(mode: String): MatchSuggestion? {
        return getModeTracker(mode).tryConfirmAndGetNextMatchSuggestion()
    }

    fun completeMatch(
        mode: String,
        matchId: String,
        teams: List<CompleteMatchActionPayload.Team>,
        gameLength: Double,
    ) {
        getModeTracker(mode).completeMatch(matchId, teams, gameLength)
    }

    fun start() {
        client.start()
        // Sender
        scope.launch {
            for ((mode, action) in actionChannel) {
                getModeTracker(mode).handleModeAction(client, action)
            }
        }
        // Receiver
        scope.launch {
            for (event in client.incoming) {
                dispatchEvent(event)
            }
        }
    }

    fun close() {
        client.close()
        scope.cancel()
    }

    suspend fun waitSyncFinish(mode: String) {
        getModeTracker(mode).waitSyncFinish()
    }

    suspend fun<R> execute(mode: String, f: suspend (client: IdemClient) -> R): R {
        return getModeTracker(mode).execute(f)
    }

    private fun getModeTracker(mode: String): ModeTracker {
        return trackerMap.getOrPut(mode) { ModeTracker(scope, mode, object : ModeActionQueue {
            override fun pushAction(action: ModeAction) {
                actionChannel.trySend(mode to action) // Never fails because the channel is UNLIMITED
            }
        }) }
    }

    private fun dispatchEvent(event: IdemEvent) {
        when (event) {
            is IdemEvent.AddPlayerAck -> {
                getModeTracker(event.payload.gameId).addPlayerAck(event.payload.players.map { it.playerId })
            }
            is IdemEvent.RemovedPlayerAck -> {
                getModeTracker(event.payload.gameId).removePlayerAck(event.payload.playerId)
            }
            is IdemEvent.FailMatchAck -> {
                getModeTracker(event.payload.gameId).failMatchAck(event.payload.matchId)
            }
            is IdemEvent.ConfirmMatchAck -> {
                getModeTracker(event.payload.gameId).confirmMatchAck(event.payload.matchId)
            }
            is IdemEvent.CompleteMatchAck -> {
                getModeTracker(event.payload.gameId).completeMatchAck(event.payload.matchId)
            }
            is IdemEvent.MatchSuggestion -> {
                getModeTracker(event.payload.gameId).addMatchSuggestion(event.payload.match)
            }
            is IdemEvent.MatchSuggestionDeliveryAck -> {}
            is IdemEvent.Disconnected -> {
                logger.warn("Disconnected from IDEM API: ${event.reason}")
            }
            is IdemEvent.UnknownResponse -> {
                logger.debug("Unknown response: ${event.action}")
            }
            is IdemEvent.UnknownErrorResponse -> {
                logger.error("Unknown error response: ${event.error}")
            }
        }
    }
}