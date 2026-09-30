package com.treasurenova.bep20manager.automation

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import com.treasurenova.bep20manager.logic.LoginControlCandidate
import com.treasurenova.bep20manager.logic.LoginControlSelector
import com.treasurenova.bep20manager.logic.LogoutVerifier
import com.treasurenova.bep20manager.logic.PageSnapshot
import com.treasurenova.bep20manager.logic.SiteOrigin
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

class WebViewBridge(
    private val webView: WebView,
    private val loginUrl: () -> String,
) : PageBridge {
    private var clientReady = false
    private var pageFinished: (() -> Unit)? = null
    private var originRejected = false
    private var sessionResetFailed = false

    @SuppressLint("SetJavaScriptEnabled")
    fun configure() {
        if (clientReady) return
        clientReady = true
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.settings.javaScriptCanOpenWindowsAutomatically = false
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url?.toString().orEmpty()
                if (!shouldCancelLoad(url, request.isForMainFrame)) return false
                if (shouldMarkOriginRejected(url)) {
                    originRejected = true
                    if (request.isForMainFrame) {
                        pageFinished?.invoke()
                        pageFinished = null
                    }
                }
                return true
            }

            @Deprecated("Required so older WebView paths cannot leave the allowlist")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return rejectUnlessAllowed(url)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                if (!url.isNullOrBlank()) rejectUnlessAllowed(url)
            }

            override fun onPageFinished(view: WebView?, finished: String?) {
                if (shouldMarkOriginRejected(finished)) {
                    originRejected = true
                }
                pageFinished?.invoke()
                pageFinished = null
            }
        }
    }

    override suspend fun loadLogin() {
        configure()
        originRejected = false
        sessionResetFailed = false
        val url = loginUrl().ifBlank { SiteOrigin.DEFAULT_LOGIN_URL }
        if (!SiteOrigin.isAllowed(url)) {
            originRejected = true
            return
        }
        if (!clearWebViewSession()) {
            sessionResetFailed = true
            return
        }
        suspendCancellableCoroutine { cont ->
            webView.post {
                pageFinished = { if (cont.isActive) cont.resume(Unit) }
                webView.loadUrl(url)
            }
        }
        if (originRejected || !SiteOrigin.isAllowed(currentUrl())) {
            originRejected = true
            return
        }
        eval(PageScripts.install())
    }

    override suspend fun fillAndSubmit(username: String, password: String): PageSnapshot {
        if (!credentialsAllowedSuspendCheck()) return refusedSnapshot()
        eval(PageScripts.install())
        if (!credentialsAllowedSuspendCheck()) return refusedSnapshot()
        val candidates = parseControls(eval(PageScripts.listLoginControls()))
        val pageUrl = currentUrl()
        if (!credentialsAllowed() || !SiteOrigin.isAllowed(pageUrl)) {
            if (!SiteOrigin.isAllowed(pageUrl)) originRejected = true
            return refusedSnapshot()
        }
        val chosen = LoginControlSelector.select(candidates, pageUrl)
        if (chosen == null) {
            return readSnapshot().copy(loginSubmitted = false)
        }
        if (!credentialsAllowedSuspendCheck()) return refusedSnapshot()
        val raw = eval(PageScripts.fillLogin(username, password, chosen.index))
        delay(1500)
        if (!credentialsAllowedSuspendCheck()) return refusedSnapshot()
        return parse(raw).merge(readSnapshot())
    }

    override suspend fun openWalletDeposit(): PageSnapshot {
        if (!credentialsAllowedSuspendCheck()) return refusedSnapshot()
        if (!clickIfAllowed("Wallet")) clickIfAllowed("Deposit").also { if (!it) clickIfAllowed("Recharge") }
        delay(1200)
        if (originRejected || !SiteOrigin.isAllowed(currentUrl())) {
            originRejected = true
            return refusedSnapshot()
        }
        return readSnapshot()
    }

    override suspend fun selectUsdt(): PageSnapshot {
        if (!credentialsAllowedSuspendCheck()) return refusedSnapshot()
        clickIfAllowed("USDT")
        delay(800)
        if (originRejected || !SiteOrigin.isAllowed(currentUrl())) {
            originRejected = true
            return refusedSnapshot()
        }
        return readSnapshot()
    }

    override suspend fun selectBep20(): PageSnapshot {
        if (!credentialsAllowedSuspendCheck()) return refusedSnapshot()
        if (!clickIfAllowed("BEP-20")) {
            if (!clickIfAllowed("BEP20")) clickIfAllowed("BNB Smart Chain")
        }
        delay(800)
        if (originRejected || !SiteOrigin.isAllowed(currentUrl())) {
            originRejected = true
            return refusedSnapshot()
        }
        return readSnapshot()
    }

    override suspend fun readSnapshot(): PageSnapshot {
        if (originRejected) return refusedSnapshot()
        eval(PageScripts.install())
        val snap = parse(eval(PageScripts.snapshot()))
        if (!SiteOrigin.isAllowed(currentUrl())) {
            originRejected = true
            return refusedSnapshot()
        }
        return snap
    }

    override suspend fun logout(): PageSnapshot {
        val allowedHere = !originRejected && SiteOrigin.isAllowed(currentUrl())
        val clicked = if (allowedHere) {
            when {
                clickIfAllowed("Log out") -> true
                clickIfAllowed("Logout") -> true
                clickIfAllowed("Sign out") -> true
                else -> false
            }
        } else {
            false
        }
        if (clicked) delay(600)
        val probe = if (allowedHere && !originRejected && SiteOrigin.isAllowed(currentUrl())) {
            eval(PageScripts.install())
            parse(eval(PageScripts.logoutProbe()))
        } else {
            PageSnapshot(authenticatedUiVisible = true)
        }
        val cleared = clearWebViewSession()
        val verification = LogoutVerifier.verify(
            logoutClicked = clicked,
            loginFormVisible = probe.loginFieldsFound,
            authenticatedUiVisible = probe.authenticatedUiVisible,
            cookiesCleared = cleared,
            loggedOutUiVisible = probe.loggedOutUiVisible,
        )
        return probe.copy(
            loggedOut = verification.loggedOut,
            sessionCleared = verification.sessionCleared,
            loginSubmitted = false,
        )
    }

    override fun wasSessionResetFailed(): Boolean = sessionResetFailed

    private fun credentialsAllowed(): Boolean {
        if (originRejected || sessionResetFailed) return false
        return true
    }

    private suspend fun credentialsAllowedSuspendCheck(): Boolean {
        val url = currentUrl()
        if (!credentialsAllowed()) return false
        if (!SiteOrigin.isAllowed(url)) {
            originRejected = true
            return false
        }
        return decideCredentials(originRejected, sessionResetFailed, url)
    }

    private fun refusedSnapshot(): PageSnapshot = PageSnapshot(
        loginSubmitted = false,
        blockedMessage = "Unauthorized origin or session reset failed. Credentials were not submitted.",
    )

    private fun rejectUnlessAllowed(url: String): Boolean {
        if (!shouldCancelLoad(url, isMainFrame = true)) return false
        if (!shouldMarkOriginRejected(url)) return true
        originRejected = true
        pageFinished?.invoke()
        pageFinished = null
        return true
    }

    private suspend fun clearWebViewSession(): Boolean = suspendCancellableCoroutine { cont ->
        webView.post {
            try {
                val cm = CookieManager.getInstance()
                cm.setAcceptCookie(true)
                val probeUrl = SiteOrigin.DEFAULT_LOGIN_URL
                cm.setCookie(probeUrl, "tn_probe=1; Path=/; Secure")
                cm.flush()
                val planted = cm.getCookie(probeUrl).orEmpty().contains("tn_probe")
                if (!planted) {
                    if (cont.isActive) cont.resume(false)
                } else cm.removeAllCookies {
                    webView.post {
                        cm.flush()
                        val storageCleared = try {
                            WebStorage.getInstance().deleteAllData()
                            true
                        } catch (_: Exception) {
                            false
                        }
                        val cacheCleared = try {
                            webView.clearCache(true)
                            webView.clearHistory()
                            webView.clearFormData()
                            true
                        } catch (_: Exception) {
                            false
                        }
                        val left = cm.getCookie(probeUrl).orEmpty()
                        val leftWww = cm.getCookie("https://www.treasurenova.net/").orEmpty()
                        val ok = storageCleared && cacheCleared && left.isBlank() && leftWww.isBlank()
                        if (cont.isActive) cont.resume(ok)
                    }
                }
            } catch (_: Exception) {
                if (cont.isActive) cont.resume(false)
            }
        }
    }

    private suspend fun currentUrl(): String = suspendCancellableCoroutine { cont ->
        webView.post {
            if (cont.isActive) cont.resume(webView.url.orEmpty())
        }
    }

    private suspend fun clickIfAllowed(label: String): Boolean {
        if (originRejected) return false
        return try {
            val raw = eval(PageScripts.clickLabel(label))
            JSONObject(unwrap(raw)).optBoolean("clicked")
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private suspend fun eval(script: String): String = suspendCancellableCoroutine { cont ->
        webView.post {
            webView.evaluateJavascript(script) { value ->
                if (cont.isActive) cont.resume(value ?: "null")
            }
        }
    }

    private fun parseControls(raw: String): List<LoginControlCandidate> {
        val text = unwrap(raw)
        if (text.isBlank() || text == "{}" || text == "null") return emptyList()
        val array = JSONArray(text)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    LoginControlCandidate(
                        index = obj.optInt("index", i),
                        text = obj.optString("text"),
                        tag = obj.optString("tag"),
                        type = obj.optString("type"),
                        inLoginForm = obj.optBoolean("inLoginForm"),
                        submitControl = obj.optBoolean("submitControl"),
                        formAction = obj.optString("formAction"),
                    )
                )
            }
        }
    }

    private fun parse(raw: String): PageSnapshot {
        val obj = JSONObject(unwrap(raw))
        val blocksJson = obj.optJSONArray("depositBlocks")
        val blocks = buildList {
            if (blocksJson != null) {
                for (i in 0 until blocksJson.length()) {
                    val item = blocksJson.optString(i).trim()
                    if (item.isNotEmpty()) add(item)
                }
            }
        }
        return PageSnapshot(
            loginFieldsFound = obj.optBoolean("loginFieldsFound"),
            loginSubmitted = obj.optBoolean("loginSubmitted"),
            loginError = obj.optString("loginError").ifBlank { null },
            twoFactorVisible = obj.optBoolean("twoFactorVisible"),
            blockedMessage = obj.optString("blockedMessage").ifBlank { null },
            bep20LabelPresent = obj.optBoolean("bep20LabelPresent"),
            networkText = obj.optString("networkText").ifBlank { null },
            bep20Value = obj.optString("bep20Value").ifBlank { null },
            loggedOut = false,
            sessionCleared = false,
            depositBlocks = blocks,
            authenticatedUiVisible = obj.optBoolean("authenticatedUiVisible"),
            accountIdentity = obj.optString("accountIdentity").ifBlank { null },
            loggedOutUiVisible = obj.optBoolean("loggedOutUiVisible"),
        )
    }

    private fun unwrap(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed == "null" || trimmed.isEmpty()) return "{}"
        if (trimmed.startsWith("\"")) {
            return JSONObject("{\"v\":$trimmed}").getString("v")
        }
        return trimmed
    }

    companion object {
        fun decideCredentials(originRejected: Boolean, sessionResetFailed: Boolean, currentUrl: String?): Boolean {
            if (originRejected || sessionResetFailed) return false
            return SiteOrigin.isAllowed(currentUrl)
        }

        fun shouldCancelLoad(url: String?, isMainFrame: Boolean): Boolean {
            if (SiteOrigin.isAllowed(url)) return false
            return if (isMainFrame) true else true
        }

        fun shouldMarkOriginRejected(url: String?): Boolean {
            if (url.isNullOrBlank()) return false
            val trimmed = url.trim()
            val lower = trimmed.lowercase()
            if (lower == "about:blank" || lower.startsWith("about:blank#") || lower.startsWith("about:blank?")) {
                return false
            }
            if (SiteOrigin.isAllowed(trimmed)) return false
            return true
        }
    }
}



private fun PageSnapshot.merge(later: PageSnapshot): PageSnapshot = PageSnapshot(
    loginFieldsFound = loginFieldsFound || later.loginFieldsFound,
    loginSubmitted = loginSubmitted || later.loginSubmitted,
    loginError = later.loginError ?: loginError,
    twoFactorVisible = twoFactorVisible || later.twoFactorVisible,
    blockedMessage = later.blockedMessage ?: blockedMessage,
    bep20LabelPresent = later.bep20LabelPresent,
    networkText = later.networkText ?: networkText,
    bep20Value = later.bep20Value ?: bep20Value,
    loggedOut = later.loggedOut,
    sessionCleared = later.sessionCleared,
    depositBlockText = later.depositBlockText ?: depositBlockText,
    depositBlocks = if (later.depositBlocks.isNotEmpty()) later.depositBlocks else depositBlocks,
    authenticatedUiVisible = later.authenticatedUiVisible,
    accountIdentity = later.accountIdentity ?: accountIdentity,
    loggedOutUiVisible = later.loggedOutUiVisible,
)
