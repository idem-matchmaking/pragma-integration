package idemmatchmaking.integration

internal interface ModeActionQueue {
    fun pushAction(action: ModeAction)
}