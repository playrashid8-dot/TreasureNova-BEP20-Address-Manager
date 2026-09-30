package com.treasurenova.bep20manager.automation

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import com.treasurenova.bep20manager.logic.PageSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

class WebViewBridge(
    private val webView: WebView,
    private val loginUrl: () -> String,
) : PageBridge {
    @SuppressLint("SetJavaScriptEnabled")
    fun configure() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.javaScriptCanOpenWindowsAutomatically = false
    }

    override suspend fun loadLogin() {
        configure()
        val url = loginUrl().ifBlank { "https://treasurenova.net/" }
        suspendCancellableCoroutine { cont ->
            webView.post {
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, finished: String?) {
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
                if (webView.url.isNullOrBlank()) webView.loadUrl(url) else webView.loadUrl(url)
            }
        }
        eval(PageScripts.install())
        val snap = readSnapshot()
        if (!snap.loginFieldsFound) {
            clickIfAllowed("Login")
            delay(800)
            eval(PageScripts.install())
        }
    }

    override suspend fun fillAndSubmit(username: String, password: String): PageSnapshot {
        eval(PageScripts.install())
        val raw = eval(PageScripts.fillLogin(username, password))
        delay(1500)
        return parse(raw).merge(readSnapshot())
    }

    override suspend fun openWalletDeposit(): PageSnapshot {
        if (!clickIfAllowed("Wallet")) clickIfAllowed("Deposit").also { if (!it) clickIfAllowed("Recharge") }
        delay(1200)
        return readSnapshot()
    }

    override suspend fun selectUsdt(): PageSnapshot {
        clickIfAllowed("USDT")
        delay(800)
        return readSnapshot()
    }

    override suspend fun selectBep20(): PageSnapshot {
        if (!clickIfAllowed("BEP-20")) {
            if (!clickIfAllowed("BEP20")) clickIfAllowed("BNB Smart Chain")
        }
        delay(800)
        return readSnapshot()
    }

    override suspend fun readSnapshot(): PageSnapshot {
        eval(PageScripts.install())
        return parse(eval(PageScripts.snapshot()))
    }

    override suspend fun logout() {
        if (!clickIfAllowed("Log out")) {
            if (!clickIfAllowed("Logout")) clickIfAllowed("Sign out")
        }
        delay(600)
    }

    private suspend fun clickIfAllowed(label: String): Boolean {
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

    private fun parse(raw: String): PageSnapshot {
        val obj = JSONObject(unwrap(raw))
        return PageSnapshot(
            loginFieldsFound = obj.optBoolean("loginFieldsFound"),
            loginSubmitted = obj.optBoolean("loginSubmitted"),
            loginError = obj.optString("loginError").ifBlank { null },
            twoFactorVisible = obj.optBoolean("twoFactorVisible"),
            blockedMessage = obj.optString("blockedMessage").ifBlank { null },
            bep20LabelPresent = obj.optBoolean("bep20LabelPresent"),
            networkText = obj.optString("networkText").ifBlank { null },
            bep20Value = obj.optString("bep20Value").ifBlank { null },
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
)
