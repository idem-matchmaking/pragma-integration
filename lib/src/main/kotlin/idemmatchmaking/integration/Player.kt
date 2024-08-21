package idemmatchmaking.integration

import java.util.UUID

interface Player {
    val id: UUID
    val servers: List<String>
}