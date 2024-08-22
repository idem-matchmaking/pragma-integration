package idemmatchmaking.integration

import idemmatchmaking.client.*
import idemmatchmaking.client.schemas.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.lang.Exception
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.cancellation.CancellationException

internal class ModeTracker(
    scope: CoroutineScope,
    private val mode: String,
    private val actionQueue: ModeActionQueue
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private var status = ModeTrackingStatus.SYNCING
    private val partyTracker = PartyTracker()
    private val matchSuggestions = mutableListOf<MatchSuggestion>()
    private val addingPlayerIds = CounterMap()
    private val removingPlayers = CounterMap()
    private val trackedMatches = mutableMapOf<String, TrackedMatch>()
    private val lock = ReentrantLock()
    private val syncFinishedDeferred = CompletableDeferred<Unit>()

    private enum class ModeTrackingStatus {
        SYNCING,
        READY,
    }

    private data class TrackedMatch(
        val matchId: String,
        val server: String,
    )

    init {
        scope.launch {
            val syncAction = ModeAction.Sync()
            actionQueue.pushAction(syncAction)
            syncAction.deferred.await()
            lock.withLock {
                status = ModeTrackingStatus.READY
            }
            syncFinishedDeferred.complete(Unit)
        }
    }

    internal suspend fun waitSyncFinish() {
        syncFinishedDeferred.await()
    }

    fun refreshParties(parties: List<Party>) {
        lock.withLock {
            if (status == ModeTrackingStatus.SYNCING) {
                return
            }

            val diff = partyTracker.diffParties(parties)
            if (diff.removedParties.isNotEmpty()) {
                diff.removedParties.forEach { party ->
                    party.players.forEach {
                        removingPlayers.increment(it.id.toString())
                    }
                    actionQueue.pushAction(ModeAction.RemoveParty(party))
                }
            }
            if (diff.newParties.isNotEmpty()) {
                diff.newParties.forEach { party ->
                    party.players.forEach {
                        addingPlayerIds.increment(it.id.toString())
                    }
                    actionQueue.pushAction(ModeAction.AddParty(party))
                }
            }
        }
    }

    fun tryConfirmAndGetNextMatchSuggestion(): MatchSuggestion? {
        return lock.withLock {
            if (status == ModeTrackingStatus.SYNCING) {
                return null
            }

            var nextSuggestion: MatchSuggestion? = null
            while (matchSuggestions.isNotEmpty()) {
                nextSuggestion = matchSuggestions.removeFirstOrNull() ?: break
                // Make sure all players are still in the queue
                if (!partyTracker.checkPartiesTracked(nextSuggestion.parties)) {
                    continue
                }

                partyTracker.removeParties(nextSuggestion.parties)
                actionQueue.pushAction(ModeAction.ConfirmMatch(nextSuggestion.matchId))
                break
            }
            nextSuggestion
        }
    }

    fun completeMatch(
        matchId: String,
        teams: List<CompleteMatchActionPayload.Team>,
        gameLength: Double,
    ): CompletableDeferred<CompleteMatchResponsePayload> {
        lock.withLock {
            val tracked = trackedMatches[matchId]
            val deferred = CompletableDeferred<CompleteMatchResponsePayload>()
            if (tracked == null) {
                val e = RuntimeException("Unexpected completeMatch: mode = $mode, matchId = $matchId")
                logger.error(e.message)
                deferred.completeExceptionally(e)
            } else {
                actionQueue.pushAction(
                    ModeAction.CompleteMatch(
                        matchId,
                        teams,
                        gameLength,
                        deferred,
                        tracked.server
                    )
                )
                trackedMatches.remove(matchId)
            }
            return deferred
        }
    }

    fun addMatchSuggestion(match: Match) {
        actionQueue.pushAction(ModeAction.MatchSuggestionDelivery(
            matchId = match.uuid
        ))

        lock.withLock {
            val matchId = match.uuid

            var hasParseError = false

            val parsedPartyIds = match.teams.flatMap { team ->
                team.players.mapNotNull { player ->
                    try {
                        UUID.fromString(player.reference)
                    } catch (e: Exception) {
                        hasParseError = true
                        null
                    }
                }
            }.distinct()

            val activeParties = parsedPartyIds.mapNotNull { partyId ->
                partyTracker.getPartyById(partyId)
            }

            val failMatch = {
                val suggestionPlayerIds = match.teams.flatMap { it.players.map { p -> p.playerId } }.toSet()
                val activePlayerIds = activeParties.flatMap { it.players.map { p -> p.id.toString() } }.toSet()
                actionQueue.pushAction(
                    ModeAction.FailUnexpectedMatch(
                        matchId = matchId,
                        requeuePlayerIds = activePlayerIds.toList(),
                        removePlayerIds = suggestionPlayerIds.subtract(activePlayerIds).toList(),
                    )
                )
                trackedMatches.remove(matchId)
            }

            if (hasParseError) {
                logger.debug("Some players have invalid party reference: mode = $mode, matchId = $matchId")
                failMatch()
                return@withLock
            }

            if (parsedPartyIds.size != activeParties.size) {
                logger.debug("Some parties are not found in suggested match: mode = $mode, matchId = $matchId")
                failMatch()
                return@withLock
            }

            val teams = match.teams.map { team ->
                val playerIds = team.players.map { player ->
                    player.playerId
                }
                MatchSuggestion.Team(playerIds)
            }

            // FIXME: Use the result provided by IDEM
            val acceptableServers = activeParties.flatMap {
                it.players.map { player -> player.servers.toSet() }
            }.reduce { acc, servers -> acc.intersect(servers) }
            val selectedServer = acceptableServers.firstOrNull() ?: run {
                logger.error("No acceptable server found: mode = $mode, matchId = $matchId")
                failMatch()
                return@withLock
            }
            trackedMatches[matchId] = TrackedMatch(matchId, selectedServer)
            matchSuggestions.add(
                MatchSuggestion(
                    matchId = match.uuid,
                    teams = teams,
                    parties = activeParties,
                    server = selectedServer
                )
            )
        }
    }

    suspend fun<R> execute(f: suspend (client: IdemClient) -> R): R {
        val wrapped: suspend (client: IdemClient) -> Any = { client -> f(client) as Any }
        val action = ModeAction.Execute(wrapped)
        actionQueue.pushAction(action)
        @Suppress("UNCHECKED_CAST")
        return action.deferred.await() as R
    }

    suspend fun handleModeAction(client: IdemClient, action: ModeAction) {
        when (action) {
            is ModeAction.Sync -> {
                try {
                    // Fail all suggested matches
                    val matches = client.getMatches(mode).matches
                    for (match in matches) {
                        logger.debug("Failing suggested match: mode = $mode, matchId = ${match.uuid}")
                        client.failMatch(
                            FailMatchActionPayload(
                                gameId = mode,
                                matchId = match.uuid,
                                remove = match.teams.flatMap { it.players.map { p -> p.playerId } },
                                requeue = emptyList()
                            )
                        )
                    }

                    // Remove all stale players
                    val players = client.getPlayers(mode).players
                    for (player in players) {
                        logger.debug("Removing stale player: mode = $mode, playerId = ${player.playerId}")
                        client.removePlayer(
                            RemovePlayerActionPayload(
                                gameId = mode,
                                playerId = player.playerId,
                                force = true
                            )
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error("Failed to sync mode: mode = $mode", e)
                } finally {
                    action.deferred.complete(Unit)
                }
            }

            is ModeAction.Execute -> {
                try {
                    val r = action.f(client)
                    action.deferred.complete(r)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    action.deferred.completeExceptionally(e)
                }
            }

            is ModeAction.AddParty -> {
                client.addPlayer(
                    AddPlayerActionPayload(
                        gameId = mode,
                        partyName = action.party.id.toString(),
                        players = action.party.players.map {
                            AddPlayerActionPayload.Player(
                                playerId = it.id.toString(),
                                servers = it.servers,
                                // Player reference is the party id
                                reference = action.party.id.toString(),
                            )
                        },
                    )
                )
            }

            is ModeAction.RemoveParty -> {
                for (player in action.party.players) {
                    client.removePlayer(
                        RemovePlayerActionPayload(
                            gameId = mode,
                            playerId = player.id.toString(),
                        )
                    )
                }
            }

            is ModeAction.ConfirmMatch -> {
                client.confirmMatch(
                    ConfirmMatchActionPayload(
                        gameId = mode,
                        matchId = action.matchId,
                    )
                )
            }

            is ModeAction.FailMatch -> {
                client.failMatch(
                    FailMatchActionPayload(
                        gameId = mode,
                        matchId = action.matchId,
                        remove = action.parties.flatMap { it.players.map { p -> p.id.toString() } },
                        requeue = emptyList()
                    )
                )
            }

            is ModeAction.FailUnexpectedMatch -> {
                client.failMatch(
                    FailMatchActionPayload(
                        gameId = mode,
                        matchId = action.matchId,
                        remove = action.removePlayerIds,
                        requeue = emptyList()
                    )
                )
            }

            is ModeAction.CompleteMatch -> {
                try {
                    val response = client.completeMatch(
                        CompleteMatchActionPayload(
                            gameId = mode,
                            matchId = action.matchId,
                            teams = action.teams,
                            gameLength = action.gameLength,
                            server = action.server,
                        )
                    )
                    action.deferred.complete(response)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    action.deferred.completeExceptionally(e)
                }
            }

            is ModeAction.MatchSuggestionDelivery -> {
                // TODO: Enable this when IDEM no longer errors on this
//                client.matchSuggestionDelivery(
//                    MatchSuggestionDeliveryActionPayload(
//                        gameId = mode,
//                        matchId = action.matchId,
//                    )
//                )
            }
        }
    }
}