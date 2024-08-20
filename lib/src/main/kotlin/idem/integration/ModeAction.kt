package idem.integration

import idem.client.*
import idem.client.schemas.*

internal sealed class ModeAction {
    data class AddParty(val party: Party) : ModeAction()
    data class RemoveParty(val party: Party) : ModeAction()

    data class ForceRemovePlayer(val playerId: String) : ModeAction()

    data class ConfirmMatch(
        val matchId: String
    ) : ModeAction()

    data class FailMatch(
        val matchId: String,
        val parties: List<Party>,
    ) : ModeAction()

    data class FailUnexpectedMatch(
        val matchId: String,
        val playerIds: List<String>,
    ) : ModeAction()

    data class CompleteMatch(
        val matchId: String,
        val teams: List<CompleteMatchActionPayload.Team>,
        val gameLength: Double,
        val server: String? = null,
    ) : ModeAction()
}

internal suspend fun IdemClient.sendModeAction(mode: String, action: ModeAction) {
    when (action) {
        is ModeAction.AddParty -> {
            addPlayerAction(
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
                removePlayerAction(
                    RemovePlayerActionPayload(
                        gameId = mode,
                        playerId = player.id.toString(),
                    )
                )
            }
        }

        is ModeAction.ForceRemovePlayer -> {
            removePlayerAction(
                RemovePlayerActionPayload(
                    gameId = mode,
                    playerId = action.playerId,
                    force = true
                )
            )
        }

        is ModeAction.ConfirmMatch -> {
            confirmMatchAction(
                ConfirmMatchActionPayload(
                    gameId = mode,
                    matchId = action.matchId,
                )
            )
        }

        is ModeAction.FailMatch -> {
            failMatchAction(
                FailMatchActionPayload(
                    gameId = mode,
                    matchId = action.matchId,
                    remove = action.parties.flatMap { it.players.map { p -> p.id.toString() } },
                    requeue = emptyList()
                )
            )
        }

        is ModeAction.FailUnexpectedMatch -> {
            failMatchAction(
                FailMatchActionPayload(
                    gameId = mode,
                    matchId = action.matchId,
                    remove = action.playerIds,
                    requeue = emptyList()
                )
            )
        }

        is ModeAction.CompleteMatch -> {
            completeMatchAction(
                CompleteMatchActionPayload(
                    gameId = mode,
                    matchId = action.matchId,
                    teams = action.teams,
                    gameLength = action.gameLength,
                    server = action.server,
                )
            )
        }
    }
}