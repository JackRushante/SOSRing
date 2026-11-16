package com.lorenzomarci.sosring

interface PushEngine {
    val liveContactNumber: String?
    fun requestLocation(contact: VipContact)
    fun runDiscovery()
    fun startLiveTracking(contact: VipContact, durationMinutes: Int = 15): Boolean
    fun stopLiveTracking()
    fun getLiveSessionId(): String?

    /** Why the outgoing live session gets no positions, when known; shown on the live map. */
    fun liveProblem(): String? = null

    /**
     * Enforcement opportunistico dello stato live su orologio a muro: i timer
     * uptime-based possono scattare in ritardo dopo il deep sleep, quindi ogni
     * poll della UI o evento di rete deve poter chiudere le sessioni scadute.
     */
    fun heartbeat(nowMs: Long) {}

    fun resubscribe()
    fun broadcastKeyRotated()
    fun stop()
}
