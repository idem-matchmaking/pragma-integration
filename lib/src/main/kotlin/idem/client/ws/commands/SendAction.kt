package idem.client.ws.commands

internal data class SendAction(
    val action: String,
    val payload: Any
): Command()