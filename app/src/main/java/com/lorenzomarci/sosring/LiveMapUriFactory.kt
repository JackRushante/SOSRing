package com.lorenzomarci.sosring

import java.net.URLEncoder

object LiveMapUriFactory {
    fun latestPointUri(points: List<LocationPoint>, label: String): String {
        val point = points.maxByOrNull { it.timestamp } ?: return ""
        val encodedLabel = URLEncoder.encode(label, "UTF-8").replace("+", "%20")
        return "geo:${point.lat},${point.lon}?q=${point.lat},${point.lon}($encodedLabel)"
    }
}
