package com.lorenzomarci.sosring

import android.os.Handler
import android.os.Looper

/** Timing rules of a one-shot position request, kept apart for the unit tests. */
object P2pRequestPolicy {
    /**
     * The asked phone tries for a fix for [LocationHelper] 30 s; delivery both ways and a slow
     * push distributor get the rest.
     */
    const val ANSWER_TIMEOUT_MS = 75_000L

    /** The asked phone ignores a second request within its replay-guard interval. */
    const val MIN_RESEND_MS = P2pFreshness.MIN_REQUEST_INTERVAL_MS

    fun waitBeforeResendMs(lastSentAtMs: Long?, nowMs: Long): Long {
        if (lastSentAtMs == null) return 0L
        val elapsed = nowMs - lastSentAtMs
        return if (elapsed in 0 until MIN_RESEND_MS) MIN_RESEND_MS - elapsed else 0L
    }
}

/**
 * Position requests that have not been answered yet, so the requester is told when nothing
 * comes back instead of waiting forever. In memory only: the monitoring service keeps the
 * process alive, and after a restart there is nothing left to wait for.
 */
object P2pPendingRequests {

    private val handler = Handler(Looper.getMainLooper())
    private val sentAt = mutableMapOf<String, Long>()
    private val timeouts = mutableMapOf<String, Runnable>()

    @Synchronized
    fun waitBeforeResendMs(number: String, nowMs: Long = System.currentTimeMillis()): Long =
        P2pRequestPolicy.waitBeforeResendMs(sentAt[PhoneUtils.normalize(number)], nowMs)

    /** Starts the answer timer; [onTimeout] runs on the main thread if no answer arrives. */
    @Synchronized
    fun track(number: String, onTimeout: () -> Unit) {
        val key = PhoneUtils.normalize(number)
        timeouts.remove(key)?.let { handler.removeCallbacks(it) }
        sentAt[key] = System.currentTimeMillis()
        val runnable = Runnable {
            val stillPending = synchronized(this) { timeouts.remove(key) != null }
            if (stillPending) onTimeout()
        }
        timeouts[key] = runnable
        handler.postDelayed(runnable, P2pRequestPolicy.ANSWER_TIMEOUT_MS)
    }

    /** An answer, an error or a delivery failure arrived: stop waiting. */
    @Synchronized
    fun resolve(number: String) {
        val key = PhoneUtils.normalize(number)
        timeouts.remove(key)?.let { handler.removeCallbacks(it) }
    }

    /** Delivery failed: the asked phone never saw the request, so asking again is allowed. */
    @Synchronized
    fun forget(number: String) {
        val key = PhoneUtils.normalize(number)
        timeouts.remove(key)?.let { handler.removeCallbacks(it) }
        sentAt.remove(key)
    }
}
