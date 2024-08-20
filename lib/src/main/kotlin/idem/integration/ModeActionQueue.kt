package idem.integration

internal interface ModeActionQueue {
    fun pushAction(action: ModeAction)
}