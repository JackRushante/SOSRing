package com.lorenzomarci.sosring

object AlertGatePolicy {
    fun allowsCall(
        monitoringEnabled: Boolean,
        paused: Boolean,
        quietHours: Boolean
    ): Boolean = monitoringEnabled && !paused && !quietHours

    fun allowsMessage(
        monitoringEnabled: Boolean,
        paused: Boolean,
        quietHours: Boolean,
        messageSoundEnabled: Boolean
    ): Boolean = allowsCall(monitoringEnabled, paused, quietHours) && messageSoundEnabled
}
