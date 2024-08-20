package idem.integration

import idem.client.schemas.CompleteMatchActionPayload
import idem.client.schemas.Match
import org.slf4j.LoggerFactory
import java.lang.Exception
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class ModeTracker(
    private val mode: String,
    private val actionQueue: ModeActionQueue
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val partyTracker = PartyTracker()
    private val matchSuggestions = mutableListOf<MatchSuggestion>()
    private val addingPlayerIds = CounterMap()
    private val removingPlayers = CounterMap()
    private val trackedMatches = mutableMapOf<String, TrackedMatch>()
    private val lock = ReentrantLock()

    private data class TrackedMatch(
        val matchId: String,
        val server: String,
        var status: MatchStatus = MatchStatus.SUGGESTED,
    )

    fun refreshParties(parties: List<Party>) {
        lock.withLock {
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
            var nextSuggestion: MatchSuggestion? = null
            while (matchSuggestions.isNotEmpty()) {
                nextSuggestion = matchSuggestions.removeFirstOrNull() ?: break
                // Make sure all players are still in the queue
                if (!partyTracker.checkPartiesTracked(nextSuggestion.parties)) {
                    continue
                }

                val matchId = nextSuggestion.matchId
                partyTracker.removeParties(nextSuggestion.parties)
                trackedMatches[matchId]!!.status = MatchStatus.CONFIRMING
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
    ) {
        lock.withLock {
            val tracked = trackedMatches[matchId]
            if (tracked == null || tracked.status != MatchStatus.CONFIRMED) {
                logger.error("Unexpected completeMatch: mode = $mode, matchId = $matchId, status = ${tracked?.status}")
            } else {
                trackedMatches[matchId]!!.status = MatchStatus.COMPLETING
                actionQueue.pushAction(
                    ModeAction.CompleteMatch(
                        matchId,
                        teams,
                        gameLength,
                        tracked.server
                    )
                )
            }
        }
    }

    fun addMatchSuggestion(match: Match) {
        lock.withLock {
            val matchId = match.uuid
            val parties = mutableListOf<Party>()
            val failMatch = {
                trackedMatches[matchId]!!.status = MatchStatus.FAILING
                actionQueue.pushAction(
                    ModeAction.FailUnexpectedMatch(
                        matchId,
                        match.teams.flatMap { it.players.map { player -> player.playerId } }
                    )
                )
            }
            val teams = match.teams.mapIndexed { teamIndex, team ->
                val playerIds = team.players.mapIndexed { playerIndex, player ->
                    // Try parse the original partyId from the player reference
                    val partyId = try {
                        UUID.fromString(player.reference)
                    } catch (e: Exception) {
                        logger.error(
                            "Unable to get partyId from player reference: mode = $mode, matchId = $matchId, teamIndex = $teamIndex, playerIndex = $playerIndex",
                            e
                        )
                        failMatch()
                        return@withLock
                    }
                    val party = parties.firstOrNull { it.id == partyId } ?: partyTracker.getPartyById(partyId)
                    if (party == null) {
                        logger.error("Party not found: mode = $mode, matchId = $matchId, partyId = $partyId, teamIndex = $teamIndex, playerIndex = $playerIndex")
                        failMatch()
                        return@withLock
                    }
                    parties.add(party)
                    player.playerId
                }
                MatchSuggestion.Team(playerIds)
            }
            // FIXME: Use the result provided by IDEM
            val acceptableServers = parties.flatMap {
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
                    parties = parties,
                    server = selectedServer
                )
            )
        }
    }

    fun forceRemovePlayers(playerIds: Collection<String>) {
        lock.withLock {
            playerIds.forEach { playerId ->
                removingPlayers.increment(playerId)
                actionQueue.pushAction(ModeAction.ForceRemovePlayer(playerId))
            }
        }
    }

    fun addPlayerAck(playerIds: Collection<String>) {
        lock.withLock {
            playerIds.forEach { playerId ->
                addingPlayerIds.increment(playerId)
            }
        }
    }

    fun removePlayerAck(playerId: String) {
        lock.withLock {
            val newCounter = removingPlayers.decrementOrSkip(playerId)
            if (newCounter == null) {
                logger.error("Unexpected removePlayerAck: mode = $mode, playerId = $playerId")
            }
        }
    }

    fun failMatchAck(matchId: String) {
        lock.withLock {
            val tracked = trackedMatches[matchId]
            if (tracked == null || tracked.status != MatchStatus.FAILING) {
                logger.error("Unexpected failMatchAck: mode = $mode, matchId = $matchId, status = ${tracked?.status}")
            } else {
                trackedMatches.remove(matchId)
            }
        }
    }

    fun confirmMatchAck(matchId: String) {
        lock.withLock {
            val tracked = trackedMatches[matchId]
            if (tracked == null || tracked.status != MatchStatus.CONFIRMING) {
                logger.error("Unexpected confirmMatchAck: mode = $mode, matchId = $matchId, status = ${tracked?.status}")
            } else {
                trackedMatches[matchId]!!.status = MatchStatus.CONFIRMED
            }
        }
    }

    fun completeMatchAck(matchId: String) {
        lock.withLock {
            val tracked = trackedMatches[matchId]
            if (tracked == null || tracked.status != MatchStatus.CONFIRMED) {
                logger.error("Unexpected completeMatchAck: mode = $mode, matchId = $matchId, status = ${tracked?.status}")
            } else {
                trackedMatches.remove(matchId)
            }
        }
    }
}