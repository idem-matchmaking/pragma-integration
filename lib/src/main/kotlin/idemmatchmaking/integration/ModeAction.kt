package idemmatchmaking.integration

import idemmatchmaking.client.IdemClient
import idemmatchmaking.client.schemas.*
import kotlinx.coroutines.CompletableDeferred

internal sealed class ModeAction {
    data class Sync(val deferred: CompletableDeferred<Unit> = CompletableDeferred()) : ModeAction()

    data class Execute(
        val f: suspend (client: IdemClient) -> Any,
        val deferred: CompletableDeferred<Any> = CompletableDeferred()
    ) : ModeAction()

    data class AddParty(val party: Party) : ModeAction()
    data class RemoveParty(val party: Party) : ModeAction()

    data class ConfirmMatch(
        val matchId: String
    ) : ModeAction()

    data class FailMatch(
        val matchId: String,
        val parties: List<Party>,
    ) : ModeAction()

    data class FailUnexpectedMatch(
        val matchId: String,
        val requeuePlayerIds: List<String>,
        val removePlayerIds: List<String>,
    ) : ModeAction()

    data class CompleteMatch(
        val matchId: String,
        val teams: List<CompleteMatchActionPayload.Team>,
        val gameLength: Double,
        val deferred: CompletableDeferred<Unit>,
        val server: String? = null,
    ) : ModeAction()

    data class MatchSuggestionDelivery(
        val matchId: String,
    ) : ModeAction()
}