package idemmatchmaking.client.ws

import com.fasterxml.jackson.databind.JsonNode
import idemmatchmaking.client.schemas.*
import idemmatchmaking.client.utils.JsonUtils

sealed class IdemEvent {
    companion object {
        fun from(action: String, payload: JsonNode?): IdemEvent {
            return when (action) {
                "matchSuggestion" -> MatchSuggestion(JsonUtils.fromJsonNode(payload!!))
                "matchSuggestionDeliveryResponse" -> MatchSuggestionDeliveryAck(JsonUtils.fromJsonNode(payload!!))
                else -> UnexpectedResponse(action, payload)
            }
        }
    }

    data class MatchSuggestion(val payload: MatchSuggestionMessagePayload): IdemEvent()
    data class MatchSuggestionDeliveryAck(val payload: MatchSuggestionDeliveryResponsePayload): IdemEvent()
    data class UnexpectedResponse(
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