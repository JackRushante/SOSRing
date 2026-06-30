package com.lorenzomarci.sosring

object LiveMapUriFactory {
    fun latestPointUri(points: List<LocationPoint>, label: String): String {
        val point = points.maxByOrNull { it.timestamp } ?: return ""
        return "geo:${point.lat},${point.lon}?q=${point.lat},${point.lon}($label)"
    }
}
