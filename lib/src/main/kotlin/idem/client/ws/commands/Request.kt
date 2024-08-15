package idem.client.ws.commands

import com.fasterxml.jackson.databind.JsonNode
import idem.client.schemas.GetMatchesResponsePayload
import idem.client.schemas.GetPlayersResponsePayload
import idem.client.utils.JsonUtils
import kotlinx.coroutines.CompletableDeferred

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
}