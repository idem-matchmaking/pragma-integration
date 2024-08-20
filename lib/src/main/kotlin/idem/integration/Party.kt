package idem.integration

import java.util.UUID

interface Party {
    val id: UUID
    val players: List<Player>
}

//e53ed63d-ebf9-49d5-867a-92ac3894162c