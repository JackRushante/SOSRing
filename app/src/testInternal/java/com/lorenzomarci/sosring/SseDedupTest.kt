package com.lorenzomarci.sosring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SseDedupTest {

    @Test
    fun firstTimeIdIsAccepted() {
        val dedup = SseDedup(capacity = 4)
        assertTrue(dedup.markSeen("a"))
    }

    @Test
    fun duplicateIdIsRejected() {
        val dedup = SseDedup(capacity = 4)
        dedup.markSeen("a")
        assertFalse(dedup.markSeen("a"))
    }

    @Test
    fun blankIdsAreNeverDeduplicated() {
        val dedup = SseDedup(capacity = 4)
        assertTrue(dedup.markSeen(""))
        assertTrue(dedup.markSeen(""))
    }

    @Test
    fun oldestIdIsEvictedAtCapacity() {
        val dedup = SseDedup(capacity = 2)
        dedup.markSeen("a")
        dedup.markSeen("b")
        dedup.markSeen("c") // sfratta "a"
        assertTrue(dedup.markSeen("a"))
        assertFalse(dedup.markSeen("c"))
    }
}
