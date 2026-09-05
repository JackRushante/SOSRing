package com.lorenzomarci.sosring

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RepeatCallTrackerTest {
    private val a = "a".repeat(64)
    private val b = "b".repeat(64)
    private val window = 300_000L
    private fun ring(t: RepeatCallTracker, time: Long, key: String? = a,
        allowed: Boolean = true, mode: CallAlertMode = CallAlertMode.SECOND) =
        t.onRinging(key, allowed, time, window, mode, 25, true)

    @Test fun secondDistinctMissedCallRingsAndDuplicatesDoNotCount() {
        val t = RepeatCallTracker()
        assertFalse(ring(t, 0)!!.shouldRing)
        assertNull(ring(t, 1))
        assertNull(ring(t, 2, null))
        t.onIdle()
        t.onIdle()
        assertEquals(CallAlertDecision(true, 25, true), ring(t, 20_000))
        assertNull(ring(t, 20_001))
        t.onIdle()
        assertEquals(CallAlertDecision(true, 50, true), ring(t, 40_000))
        t.onIdle()
        assertEquals(CallAlertDecision(true, 100, true), ring(t, 60_000))
    }

    @Test fun numberlessEventCanBeFollowedByIdentifiedVipWithinSameRing() {
        val t = RepeatCallTracker()
        assertNull(ring(t, 0, null))
        assertTrue(t.busy)
        assertFalse(ring(t, 1)!!.shouldRing)
        t.onIdle()
        assertTrue(ring(t, 10_000)!!.shouldRing)
    }

    @Test fun windowIsAnchoredToFirstStartAndDoesNotSlide() {
        val t = RepeatCallTracker()
        ring(t, 0); t.onIdle()
        assertTrue(ring(t, 299_999)!!.shouldRing)
        t.onIdle()
        assertFalse(ring(t, 300_000)!!.shouldRing)
    }

    @Test fun answeringResetsOnlyThatCallersHistory() {
        val t = RepeatCallTracker()
        ring(t, 0, b); t.onIdle()
        ring(t, 10_000); t.onAnswered(); t.onAnswered(); t.onIdle()
        assertFalse(ring(t, 20_000)!!.shouldRing)
        t.onIdle()
        assertTrue(ring(t, 30_000, b)!!.shouldRing)
    }

    @Test fun differentNumbersNeverCombine() {
        val t = RepeatCallTracker()
        ring(t, 0); t.onIdle()
        assertFalse(ring(t, 100, b)!!.shouldRing)
        t.onIdle()
        assertTrue(ring(t, 200)!!.shouldRing)
    }

    @Test fun outgoingCallDoesNotCountAndCallWaitingCannotBecomeARepeat() {
        val t = RepeatCallTracker()
        t.onAnswered()
        assertNull(ring(t, 100))
        t.onIdle()
        assertFalse(ring(t, 200)!!.shouldRing)
        assertNull(ring(t, 201, b))
        t.onIdle()
        assertFalse(ring(t, 300, b)!!.shouldRing)
    }

    @Test fun pauseClearsHistoryAndCannotRearmCurrentCall() {
        val t = RepeatCallTracker()
        ring(t, 0); t.onIdle()
        assertNull(ring(t, 100, allowed = false))
        assertNull(ring(t, 200, allowed = true))
        t.onIdle()
        assertFalse(ring(t, 300)!!.shouldRing)
    }

    @Test fun settingsOrQuietTransitionWhileRingingDiscardsPendingAttempt() {
        val t = RepeatCallTracker()
        ring(t, 0)
        t.resetHistory()
        assertNull(ring(t, 1))
        t.onIdle()
        assertFalse(ring(t, 100)!!.shouldRing)
    }

    @Test fun completedMissedCallSurvivesProcessRestart() {
        val t = RepeatCallTracker()
        ring(t, 0); t.onIdle()
        val restored = RepeatCallTracker()
        restored.restore(JSONObject(t.snapshot().toString()), 10_000, window, false)
        assertEquals(CallAlertDecision(true, 25, true), ring(restored, 20_000))
    }

    @Test fun unknownOutcomeAfterProcessDeathIsNotTreatedAsMissed() {
        val t = RepeatCallTracker()
        ring(t, 0); t.onIdle(); ring(t, 10_000)
        val restored = RepeatCallTracker()
        restored.restore(t.snapshot(), 20_000, window, false)
        assertFalse(ring(restored, 30_000)!!.shouldRing)
    }

    @Test fun processRestartDuringRingingDoesNotCountReplayAsSecondCall() {
        val t = RepeatCallTracker()
        ring(t, 0)
        val restored = RepeatCallTracker()
        restored.restore(t.snapshot(), 100, window, true)
        assertNull(ring(restored, 200))
        restored.onIdle()
        assertFalse(ring(restored, 300)!!.shouldRing)
    }

    @Test fun expiredOrFutureHistoryIsDiscardedOnRestore() {
        val t = RepeatCallTracker()
        ring(t, 100); t.onIdle()
        for (now in listOf(50L, 300_100L)) {
            val restored = RepeatCallTracker()
            restored.restore(t.snapshot(), now, window, false)
            assertFalse(ring(restored, now)!!.shouldRing)
        }
    }

    @Test fun defaultModeStillRingsOnFirstAttempt() {
        assertEquals(CallAlertDecision(true, 25, true), ring(RepeatCallTracker(), 0, mode = CallAlertMode.FIRST))
    }

    @Test fun escalationUsesConfiguredBaseBelowFiftyAndCapsAtHundred() {
        assertEquals(listOf(25, 50, 100, 100), (1..4).map {
            RepeatCallPolicy.decide(CallAlertMode.FIRST, it, 25, true).volumePercent
        })
        assertEquals(listOf(45, 90, 100), (1..3).map {
            RepeatCallPolicy.decide(CallAlertMode.FIRST, it, 45, true).volumePercent
        })
        for (base in listOf(50, 75, 100)) {
            assertEquals(CallAlertDecision(true, base, false), RepeatCallPolicy.decide(CallAlertMode.FIRST, 4, base, true))
        }
        assertEquals(CallAlertDecision(true, 25, false), RepeatCallPolicy.decide(CallAlertMode.FIRST, 4, 25, false))
    }
}
