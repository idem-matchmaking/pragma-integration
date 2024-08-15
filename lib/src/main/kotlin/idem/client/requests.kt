package idem.client

import idem.client.schemas.GetMatchesResponsePayload
import idem.client.schemas.GetPlayersResponsePayload
import idem.client.ws.commands.Request

suspend fun IdemClient.getPlayers(gameId: String): GetPlayersResponsePayload {
    val request = Request.GetPlayers(
        gameId = gameId
    )
    sendCommand(request)
    return request.deferred.await()
}

suspend fun IdemClient.getMatches(gameId: String): GetMatchesResponsePayload {
    val request = Request.GetMatches(
        gameId = gameId
    )
    sendCommand(request)
    return request.deferred.await()
}