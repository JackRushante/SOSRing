package com.lorenzomarci.sosring

object PermissionHealth {
    fun criticalMissing(callLogOk: Boolean, phoneStateOk: Boolean, dndOk: Boolean): List<String> {
        val missing = mutableListOf<String>()
        if (!callLogOk) missing.add("READ_CALL_LOG")
        if (!phoneStateOk) missing.add("READ_PHONE_STATE")
        if (!dndOk) missing.add("DND")
        return missing
    }
}
