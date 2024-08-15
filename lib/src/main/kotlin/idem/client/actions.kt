package idem.client

import idem.client.schemas.*

suspend fun IdemClient.addPlayerAction(payload: AddPlayerActionPayload) {
    sendAction("addPlayer", payload)
}

suspend fun IdemClient.removePlayerAction(payload: RemovePlayerActionPayload) {
    sendAction("removePlayer", payload)
}

suspend fun IdemClient.failMatchAction(payload: FailMatchActionPayload) {
    sendAction("updateMatchFailed", payload)
}

suspend fun IdemClient.confirmMatchAction(payload: ConfirmMatchActionPayload) {
    sendAction("updateMatchConfirmed", payload)
}

suspend fun IdemClient.completeMatchAction(payload: CompleteMatchActionPayload) {
    sendAction("updateMatchCompleted", payload)
}