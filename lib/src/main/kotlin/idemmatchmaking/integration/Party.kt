package idemmatchmaking.integration

import java.util.UUID

interface Party {
    val id: UUID
    val players: List<Player>
}