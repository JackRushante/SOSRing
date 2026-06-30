package com.lorenzomarci.sosring

interface PushEngine {
    val liveContactNumber: String?
    fun requestLocation(contact: VipContact)
    fun runDiscovery()
    fun startLiveTracking(contact: VipContact, durationMinutes: Int = 15): Boolean
    fun stopLiveTracking()
    fun getLiveSessionId(): String?
    fun resubscribe()
    fun broadcastKeyRotated()
    fun stop()
}
