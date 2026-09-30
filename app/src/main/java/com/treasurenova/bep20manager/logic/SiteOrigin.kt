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

    /**
     * Credential form actions are allowed only when they resolve onto the current page's
     * allowlisted origin. Relative actions (empty, "/", "?", "#", or a same-document path)
     * are resolved against [currentPageUrl] first. A value that is not that kind of relative
     * path (for example "intent:...") is rejected even when it has no "://".
     */
    fun isCredentialActionAllowed(action: String?, currentPageUrl: String?): Boolean {
        if (!isAllowed(currentPageUrl)) return false
        val value = action?.trim().orEmpty()
        if (value.any { it.isISOControl() || it.isWhitespace() }) return false
        if ('\\' in value || '@' in value) return false
        val lower = value.lowercase()
        if (lower.startsWith("javascript:") ||
            lower.startsWith("data:") ||
            lower.startsWith("blob:") ||
            lower.startsWith("intent:") ||
            lower.startsWith("file:") ||
            lower.startsWith("content:") ||
            lower.startsWith("http:")
        ) {
            return false
        }
        val sameDocument = value.isEmpty() ||
            value.startsWith("/") ||
            value.startsWith("./") ||
            value.startsWith("../") ||
            value.startsWith("?") ||
            value.startsWith("#") ||
            (':' !in value && !value.startsWith("//"))
        if (value.startsWith("//")) return false
        if (!sameDocument && "://" !in value) return false
        val resolved = try {
            val base = URI(currentPageUrl!!.trim())
            if (value.isEmpty()) base else base.resolve(value)
        } catch (_: Exception) {
            return false
        }
        return isAllowed(resolved.toString())
    }

    fun maySubmitCredentials(currentUrl: String?): Boolean = isAllowed(currentUrl)
}
