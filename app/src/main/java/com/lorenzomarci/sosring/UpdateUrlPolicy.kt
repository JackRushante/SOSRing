package com.lorenzomarci.sosring

import java.net.URI

object UpdateUrlPolicy {
    fun resolveVersionUrl(baseUrl: String): String? {
        return resolveRelativeUrl(baseUrl, "version.json")
    }

    fun resolveApkUrl(baseUrl: String, apkUrl: String): String? {
        if (apkUrl.isBlank()) return null
        if (apkUrl.contains("..")) return null

        val direct = parseUri(apkUrl) ?: return null
        if (direct.scheme == "http" || direct.scheme == "https") {
            return direct.toString()
        }
        if (direct.scheme != null) return null

        return resolveRelativeUrl(baseUrl, apkUrl)
    }

    private fun resolveRelativeUrl(baseUrl: String, relativePath: String): String? {
        if (baseUrl.isBlank() || relativePath.isBlank() || relativePath.contains("..")) return null
        val base = parseUri(baseUrl) ?: return null
        if (base.scheme != "http" && base.scheme != "https") return null

        val separator = if (baseUrl.endsWith("/")) "" else "/"
        return "$baseUrl$separator$relativePath"
    }

    private fun parseUri(value: String): URI? {
        return try {
            URI(value)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
