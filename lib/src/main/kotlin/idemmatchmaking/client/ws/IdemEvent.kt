package idemmatchmaking.client.ws

import com.fasterxml.jackson.databind.JsonNode
import idemmatchmaking.client.schemas.*
import idemmatchmaking.client.utils.JsonUtils

sealed class IdemEvent {
    companion object {
        fun from(action: String, payload: JsonNode?): IdemEvent {
            return when (action) {
                "addPlayerResponse" -> AddPlayerAck(JsonUtils.fromJsonNode(payload!!))
                "removePlayerResponse" -> RemovedPlayerAck(JsonUtils.fromJsonNode(payload!!))
                "updateMatchFailedResponse" -> FailMatchAck(JsonUtils.fromJsonNode(payload!!))
                "updateMatchConfirmedResponse" -> ConfirmMatchAck(JsonUtils.fromJsonNode(payload!!))
                "updateMatchCompletedResponse" -> CompleteMatchAck(JsonUtils.fromJsonNode(payload!!))
                "matchSuggestion" -> MatchSuggestion(JsonUtils.fromJsonNode(payload!!))
                "matchSuggestionDeliveryResponse" -> MatchSuggestionDeliveryAck(JsonUtils.fromJsonNode(payload!!))
                else -> UnknownResponse(action, payload)
            }
        }
    }

    data class AddPlayerAck(val payload: AddPlayerResponsePayload): IdemEvent()
    data class RemovedPlayerAck(val payload: RemovePlayerResponsePayload): IdemEvent()
    data class FailMatchAck(val payload: FailMatchResponsePayload): IdemEvent()
    data class ConfirmMatchAck(val payload: ConfirmMatchResponsePayload): IdemEvent()
    data class CompleteMatchAck(val payload: CompleteMatchResponsePayload): IdemEvent()
    data class MatchSuggestion(val payload: MatchSuggestionMessagePayload): IdemEvent()
    data class MatchSuggestionDeliveryAck(val payload: MatchSuggestionDeliveryResponsePayload): IdemEvent()
    data class UnknownResponse(
        val action: String,
        val payload: JsonNode?
    ): IdemEvent()
    data class UnknownErrorResponse(
        val action: String,
        val error: Error
    ): IdemEvent()
    data class Disconnected(val reason: Reason): IdemEvent() {
        enum class Reason {
            ERROR,
            CLOSED
        }
    }
}