package com.lorenzomarci.sosring

/**
 * Deduplica i messaggi SSE per id: riconnettersi con since= può riconsegnare
 * eventi già processati.
 */
class SseDedup(private val capacity: Int = 200) {
    private val order = ArrayDeque<String>()
    private val seen = HashSet<String>()

    /** @return true se l'id è nuovo (da processare), false se già visto. */
    @Synchronized
    fun markSeen(id: String): Boolean {
        if (id.isBlank()) return true
        if (!seen.add(id)) return false
        order.addLast(id)
        if (order.size > capacity) {
            seen.remove(order.removeFirst())
        }
        return true
    }
}
