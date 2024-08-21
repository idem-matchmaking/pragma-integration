package idemmatchmaking.integration

import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class PartyTracker {
    data class PartyKey(
        val id: UUID,
        val players: List<Player>
    )

    private val lock = ReentrantLock()
    private val trackedParties: MutableMap<PartyKey, Party> = mutableMapOf()

    data class DiffResult(
        val newParties: List<Party>,
        val removedParties: List<Party>,
    )

    fun diffParties(parties: List<Party>): DiffResult {
        val map = parties.associateBy { it.getKey() }
        lock.withLock {
            val newKeys = map.keys - trackedParties.keys
            val removedKeys = trackedParties.keys - map.keys
            val removedParties = removedKeys.map { trackedParties.getValue(it) }
            trackedParties.clear()
            trackedParties.putAll(map)
            return DiffResult(
                newParties = newKeys.map { map.getValue(it) },
                removedParties = removedParties,
            )
        }
    }

    fun removeParties(parties: List<Party>) {
        lock.withLock {
            parties.forEach { trackedParties.remove(it.getKey()) }
        }
    }

    fun checkPartiesTracked(parties: List<Party>): Boolean {
        return lock.withLock {
            parties.all { trackedParties.containsKey(it.getKey()) }
        }
    }

    fun getPartyById(partyId: UUID): Party? {
        return lock.withLock {
            trackedParties.values.find { it.id == partyId }
        }
    }

    private fun Party.getKey(): PartyKey = PartyKey(
        id = id,
        players = players
    )
}