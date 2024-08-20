package idem.integration

import idem.client.IdemClient
import idem.client.IdemConfig
import idem.client.getPlayers
import idem.client.schemas.CompleteMatchActionPayload
import idem.client.ws.IdemEvent
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

    private val stateMap = ConcurrentMap<String, ModeTracker>()
    private val actionChannel = Channel<Pair<String, ModeAction>>(Channel.UNLIMITED)

    fun refreshParties(mode: String, parties: List<Party>) {
        getModeState(mode).refreshParties(parties)
    }

    fun tryConfirmAndGetNextMatchSuggestion(mode: String): MatchSuggestion? {
        return getModeState(mode).tryConfirmAndGetNextMatchSuggestion()
    }

    fun completeMatch(
        mode: String,
        matchId: String,
        teams: List<CompleteMatchActionPayload.Team>,
        gameLength: Double,
    ) {
        getModeState(mode).completeMatch(matchId, teams, gameLength)
    }

    fun start() {
        client.start()
        // Sender
        scope.launch {
            for ((mode, action) in actionChannel) {
                client.sendModeAction(mode, action)
            }
        }
        // Receiver
        scope.launch {
            for (event in client.incoming) {
                dispatchEvent(event)
            }
        }
    }

    suspend fun removeStalePlayers(mode: String) {
        val players = client.getPlayers(mode).players.filter { it.state == "waiting" }
        if (players.isNotEmpty()) {
            logger.info("Removing stale players")
            getModeState(mode)
        }
    }

    fun close() {
        client.close()
        scope.cancel()
    }

    private fun getModeState(mode: String): ModeTracker {
        return stateMap.getOrPut(mode) { ModeTracker(mode, object : ModeActionQueue {
            override fun pushAction(action: ModeAction) {
                actionChannel.trySend(mode to action) // Never fails because the channel is UNLIMITED
            }
        }) }
    }

    private fun dispatchEvent(event: IdemEvent) {
        when (event) {
            is IdemEvent.AddPlayerAck -> {
                getModeState(event.payload.gameId).addPlayerAck(event.payload.players.map { it.playerId })
            }
            is IdemEvent.RemovedPlayerAck -> {
                getModeState(event.payload.gameId).removePlayerAck(event.payload.playerId)
            }
            is IdemEvent.FailMatchAck -> {
                getModeState(event.payload.gameId).failMatchAck(event.payload.matchId)
            }
            is IdemEvent.ConfirmMatchAck -> {
                getModeState(event.payload.gameId).confirmMatchAck(event.payload.matchId)
            }
            is IdemEvent.CompleteMatchAck -> {
                getModeState(event.payload.gameId).completeMatchAck(event.payload.matchId)
            }
            is IdemEvent.MatchSuggestion -> {
                getModeState(event.payload.gameId).addMatchSuggestion(event.payload.match)
            }
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