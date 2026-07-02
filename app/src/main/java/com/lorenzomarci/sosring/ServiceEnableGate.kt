package com.lorenzomarci.sosring

object ServiceEnableGate {
    fun canEnable(phone: Boolean, callLog: Boolean, dnd: Boolean, notif: Boolean): Boolean {
        return phone && callLog && dnd && notif
    }
}
