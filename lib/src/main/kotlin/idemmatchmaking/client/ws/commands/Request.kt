package idemmatchmaking.client.ws.commands

import com.fasterxml.jackson.databind.JsonNode
import idemmatchmaking.client.schemas.*
import idemmatchmaking.client.utils.JsonUtils
import kotlinx.coroutines.CompletableDeferred

// Requests are actions that are sent to the server and expect a response.
internal sealed class Request : Command() {
    abstract val action: String
    abstract val actionPayload: Any

    abstract fun complete(responsePayload: JsonNode)
    abstract fun completeExceptionally(throwable: Throwable)

    data class GetPlayers(
        val gameId: String,
        val deferred: CompletableDeferred<GetPlayersResponsePayload> = CompletableDeferred()
    ): Request() {
        override val action = "getPlayers"
        override val actionPayload = mapOf("gameId" to gameId)

        override fun complete(responsePayload: JsonNode) {
            deferred.complete(JsonUtils.fromJsonNode(responsePayload))
        }

        override fun completeExceptionally(throwable: Throwable) {
            deferred.completeExceptionally(throwable)
        }
    }

    data class GetMatches(
        val gameId: String,
        val deferred: CompletableDeferred<GetMatchesResponsePayload> = CompletableDeferred()
    ): Request() {
        override val action = "getMatches"
        override val actionPayload = mapOf("gameId" to gameId)

        override fun complete(responsePayload: JsonNode) {
            deferred.complete(JsonUtils.fromJsonNode(responsePayload))
        }

        override fun completeExceptionally(throwable: Throwable) {
            deferred.completeExceptionally(throwable)
        }
    }

    data class AddPlayer(
        override val actionPayload: AddPlayerActionPayload,
        val deferred: CompletableDeferred<Unit> = CompletableDeferred()
    ): Request() {
        override val action = "addPlayer"

        override fun complete(responsePayload: JsonNode) {
            deferred.complete(Unit)
        }

        override fun completeExceptionally(throwable: Throwable) {
            deferred.completeExceptionally(throwable)
        }
    }

    data class RemovePlayer(
        override val actionPayload: RemovePlayerActionPayload,
        val deferred: CompletableDeferred<Unit> = CompletableDeferred()
    ): Request() {
        override val action = "removePlayer"

        override fun complete(responsePayload: JsonNode) {
            deferred.complete(Unit)
        }

        override fun completeExceptionally(throwable: Throwable) {
            deferred.completeExceptionally(throwable)
        }
    }

    data class FailMatch(
        override val actionPayload: FailMatchActionPayload,
        val deferred: CompletableDeferred<Unit> = CompletableDeferred()
    ): Request() {
        override val action = "updateMatchFailed"

        override fun complete(responsePayload: JsonNode) {
            deferred.complete(Unit)
        }

        override fun completeExceptionally(throwable: Throwable) {
            deferred.completeExceptionally(throwable)
        }
    }

    data class Subscribe(
        val payload: SubscribeActionPayload,
        val deferred: CompletableDeferred<Unit> = CompletableDeferred()
    ): Request() {
        override val action = "subscribe"
        override val actionPayload = payload

        override fun complete(responsePayload: JsonNode) {
            deferred.complete(Unit)
        }

        override fun completeExceptionally(throwable: Throwable) {
            deferred.completeExceptionally(throwable)
        }
    }

    data class ConfirmMatch(
        override val actionPayload: ConfirmMatchActionPayload,
        val deferred: CompletableDeferred<Unit> = CompletableDeferred()
    ): Request() {
        override val action = "updateMatchConfirmed"

        override fun complete(responsePayload: JsonNode) {
            deferred.complete(Unit)
        }

        override fun completeExceptionally(throwable: Throwable) {
            deferred.completeExceptionally(throwable)
        }
    }

    data class CompleteMatch(
        override val actionPayload: CompleteMatchActionPayload,
        val deferred: CompletableDeferred<CompleteMatchResponsePayload> = CompletableDeferred()
    ): Request() {
        override val action = "updateMatchCompleted"

        override fun complete(responsePayload: JsonNode) {
            deferred.complete(JsonUtils.fromJsonNode(responsePayload))
        }

        override fun completeExceptionally(throwable: Throwable) {
            deferred.completeExceptionally(throwable)
        }
    }

    data class MatchSuggestionDelivery(
        override val actionPayload: MatchSuggestionDeliveryActionPayload,
        val deferred: CompletableDeferred<Unit> = CompletableDeferred()
    ): Request() {
        override val action = "matchSuggestionDelivery"

        override fun complete(responsePayload: JsonNode) {
            deferred.complete(Unit)
        }

        override fun completeExceptionally(throwable: Throwable) {
            deferred.completeExceptionally(throwable)
        }
    }
}