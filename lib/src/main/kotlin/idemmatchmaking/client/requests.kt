package idemmatchmaking.client

import idemmatchmaking.client.schemas.*
import idemmatchmaking.client.ws.commands.Request

suspend fun IdemClient.getPlayers(gameId: String): GetPlayersResponsePayload {
    val request = Request.GetPlayers(
        gameId = gameId
    )
    request(request)
    return request.deferred.await()
}

suspend fun IdemClient.getMatches(gameId: String): GetMatchesResponsePayload {
    val request = Request.GetMatches(
        gameId = gameId
    )
    request(request)
    return request.deferred.await()
}

suspend fun IdemClient.confirmMatch(payload: ConfirmMatchActionPayload) {
    val request = Request.ConfirmMatch(payload)
    request(request)
    request.deferred.await()
}

suspend fun IdemClient.failMatch(payload: FailMatchActionPayload) {
    val request = Request.FailMatch(payload)
    request(request)
    request.deferred.await()
}

suspend fun IdemClient.completeMatch(payload: CompleteMatchActionPayload): CompleteMatchResponsePayload {
    val request = Request.CompleteMatch(payload)
    request(request)
    return request.deferred.await()
}

suspend fun IdemClient.matchSuggestionDelivery(payload: MatchSuggestionDeliveryActionPayload) {
    val request = Request.MatchSuggestionDelivery(payload)
    request(request)
    request.deferred.await()
}
suspend fun IdemClient.addPlayer(payload: AddPlayerActionPayload) {
    val request = Request.AddPlayer(payload)
    request(request)
    request.deferred.await()
}

suspend fun IdemClient.removePlayer(payload: RemovePlayerActionPayload) {
    val request = Request.RemovePlayer(payload)
    request(request)
    request.deferred.await()
}