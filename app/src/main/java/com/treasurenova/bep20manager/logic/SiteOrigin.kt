package com.treasurenova.bep20manager.logic

import java.net.URI

object SiteOrigin {
    const val DEFAULT_LOGIN_URL = "https://treasurenova.net/"
    private val allowedHosts = setOf("treasurenova.net", "www.treasurenova.net")

    fun isAllowed(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val url = raw.trim()
        if (url.any { it.isISOControl() || it.isWhitespace() }) return false
        if ('\\' in url || '@' in url) return false
        if (!url.startsWith("https://")) return false
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return false
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        if (!uri.userInfo.isNullOrEmpty()) return false
        val host = uri.host?.lowercase() ?: return false
        if (host !in allowedHosts) return false
        if (uri.port != -1 && uri.port != 443) return false
        val authorityHost = uri.rawAuthority
            ?.substringBefore('@')
            ?.substringBefore(':')
            ?.lowercase()
            ?: return false
        return authorityHost == host
    }

    /** Form actions may be empty or same-document relative. Absolute targets must be an allowed origin. */
    fun isCredentialActionAllowed(action: String?): Boolean {
        val value = action?.trim().orEmpty()
        if (value.isEmpty()) return true
        if (value.any { it.isISOControl() || it.isWhitespace() }) return false
        if ('\\' in value || '@' in value) return false
        if (value.startsWith("//")) return false
        val lower = value.lowercase()
        if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("blob:") || lower.startsWith("http:")) {
            return false
        }
        if (value.startsWith("/") || value.startsWith("?") || value.startsWith("#")) return true
        if ("://" !in value) return true
        return isAllowed(value)
    }

    fun maySubmitCredentials(currentUrl: String?): Boolean = isAllowed(currentUrl)
}
