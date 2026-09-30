package com.treasurenova.bep20manager

import com.treasurenova.bep20manager.automation.PageBridge
import com.treasurenova.bep20manager.automation.SessionController
import com.treasurenova.bep20manager.logic.AccountImport
import com.treasurenova.bep20manager.logic.AccountInput
import com.treasurenova.bep20manager.logic.AddressDecision
import com.treasurenova.bep20manager.logic.AddressExtractor
import com.treasurenova.bep20manager.logic.AddressRow
import com.treasurenova.bep20manager.logic.BatchEngine
import com.treasurenova.bep20manager.logic.ExportFormatter
import com.treasurenova.bep20manager.logic.LoginControlCandidate
import com.treasurenova.bep20manager.automation.PageScripts
import com.treasurenova.bep20manager.automation.WebViewBridge
import com.treasurenova.bep20manager.logic.LoginConfirmation
import com.treasurenova.bep20manager.logic.LoginControlSelector
import com.treasurenova.bep20manager.logic.LogoutVerifier
import com.treasurenova.bep20manager.logic.PageSnapshot
import com.treasurenova.bep20manager.logic.Safety
import com.treasurenova.bep20manager.logic.SessionIsolation
import com.treasurenova.bep20manager.logic.SiteOrigin
import com.treasurenova.bep20manager.logic.UsdtBep20Matcher
import com.treasurenova.bep20manager.ui.Bep20Warning
import com.treasurenova.bep20manager.ui.UiCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FlowTest {
    private val good = "0x" + "ab".repeat(20)
    private val other = "0x" + "cd".repeat(20)

    @Test
    fun extractsOnlyUsdtBep20Address() {
        val ok = AddressExtractor.decide(
            PageSnapshot(depositBlocks = listOf("USDT Deposit Address (BEP-20) $good"))
        )
        assertTrue(ok is AddressDecision.Ok)
        assertEquals(good, (ok as AddressDecision.Ok).address)
        val trc = AddressExtractor.decide(
            PageSnapshot(depositBlocks = listOf("USDT Deposit Address (TRC-20) $good"))
        )
        assertTrue(trc is AddressDecision.NetworkMismatch)
        val junk = AddressExtractor.decide(
            PageSnapshot(depositBlocks = listOf("USDT BEP20 0x1234"))
        )
        assertTrue(junk is AddressDecision.Missing)
        assertFalse(AddressExtractor.isBep20Network("TRC20"))
        assertFalse(AddressExtractor.isBep20Network("BNB"))
        assertTrue(AddressExtractor.isBep20Network("BNB Smart Chain"))
    }

    @Test
    fun usdtBep20RequiresSameBlockAndRejectsOtherNetworks() {
        val valid = UsdtBep20Matcher.classify("USDT Deposit Address (BEP-20) $good")
        assertTrue(valid is AddressDecision.Ok)
        assertEquals(good, (valid as AddressDecision.Ok).address)

        val bep20Phrase = UsdtBep20Matcher.classify("Send USDT on BEP20 $good")
        assertEquals(good, (bep20Phrase as AddressDecision.Ok).address)
        val smartChain = UsdtBep20Matcher.classify("USDT on BNB Smart Chain $good")
        assertEquals(good, (smartChain as AddressDecision.Ok).address)

        val trc = UsdtBep20Matcher.classify("USDT Deposit Address (TRC-20) TXYZop $good")
        assertTrue(trc is AddressDecision.NetworkMismatch)
        val tron = UsdtBep20Matcher.classify("USDT Tron deposit $good")
        assertTrue(tron is AddressDecision.NetworkMismatch)

        val genericBnb = UsdtBep20Matcher.classify("BNB deposit $good")
        assertFalse(genericBnb is AddressDecision.Ok)
        val genericBepWithoutUsdt = UsdtBep20Matcher.classify("BEP20 wallet $good")
        assertFalse(genericBepWithoutUsdt is AddressDecision.Ok)

        val invalid = UsdtBep20Matcher.classify("USDT BEP-20 0x1234")
        assertTrue(invalid is AddressDecision.Missing)
        assertTrue((invalid as AddressDecision.Missing).reason.contains("not a BEP20"))
        val tooLong = UsdtBep20Matcher.classify("USDT BEP20 ${good}ff")
        assertFalse(tooLong is AddressDecision.Ok)

        val nearby = UsdtBep20Matcher.fromBlocks(
            listOf(
                "USDT Deposit Address (BEP-20)",
                "Other wallet BNB $other",
            )
        )
        assertFalse(nearby is AddressDecision.Ok)
        val splitLabels = UsdtBep20Matcher.fromBlocks(
            listOf(
                "USDT Deposit",
                "BEP-20 network",
                other,
            )
        )
        assertFalse(splitLabels is AddressDecision.Ok)
        val trcBesideAddress = UsdtBep20Matcher.fromBlocks(
            listOf(
                "USDT Deposit Address (TRC-20)",
                "BEP20 $good",
            )
        )
        assertFalse(trcBesideAddress is AddressDecision.Ok)
    }

    @Test
    fun loginControlPrefersSubmitAndRejectsUnrelatedButtons() {
        val randomLogin = control(0, "Login", submit = false, inForm = false)
        val signInSubmit = control(1, "Sign In", submit = true, inForm = true)
        val pay = control(2, "Pay", submit = true, inForm = true)
        val withdraw = control(3, "Withdraw", submit = false, inForm = false)
        val register = control(4, "Register", submit = true, inForm = true)
        val nearbyConfirm = control(5, "Confirm", submit = false, inForm = true)
        val confirmSubmit = control(6, "Confirm", submit = true, inForm = true)
        val logIn = control(7, "Log In", submit = false, inForm = true)
        val loginSubmit = control(8, "Login", submit = true, inForm = true, type = "submit", tag = "input")

        val page = "https://treasurenova.net/login"
        assertEquals(signInSubmit, LoginControlSelector.select(listOf(randomLogin, signInSubmit, pay, nearbyConfirm), page))
        assertEquals(loginSubmit, LoginControlSelector.select(listOf(randomLogin, nearbyConfirm, loginSubmit, register), page))
        assertEquals(logIn, LoginControlSelector.select(listOf(pay, withdraw, register, nearbyConfirm, confirmSubmit, logIn), page))
        assertNull(LoginControlSelector.select(listOf(pay, withdraw, register, nearbyConfirm, confirmSubmit, randomLogin), page))
        assertTrue(LoginControlSelector.isSafeLabel("Login"))
        assertTrue(LoginControlSelector.isSafeLabel("Log In"))
        assertTrue(LoginControlSelector.isSafeLabel("Sign In"))
        assertFalse(LoginControlSelector.isSafeLabel("Confirm"))
        assertFalse(Safety.allowClick("Confirm"))
        assertFalse(Safety.allowClick("Pay"))
        assertFalse(Safety.allowClick("Withdraw"))
        assertFalse(Safety.allowClick("Register"))
        assertTrue(Safety.allowClick("Login"))
        assertTrue(Safety.allowClick("Log In"))
        assertTrue(Safety.allowClick("Sign In"))

        val foreignPost = control(9, "Login", submit = true, inForm = true, action = "https://treasurenova.com/login")
        assertNull(LoginControlSelector.select(listOf(foreignPost), page))
        val sameSite = control(10, "Login", submit = true, inForm = true, action = "https://treasurenova.net/login")
        assertEquals(sameSite, LoginControlSelector.select(listOf(foreignPost, sameSite), page))
    }

    @Test
    fun logoutIsVerifiedAndSessionResetFailureStopsTheBatch() {
        val ok = LogoutVerifier.verify(
            logoutClicked = true,
            loginFormVisible = true,
            authenticatedUiVisible = false,
            cookiesCleared = true,
            loggedOutUiVisible = true,
        )
        assertTrue(ok.loggedOut)
        assertTrue(ok.sessionCleared)
        assertTrue(ok.confirmed)

        val cookiesLeft = LogoutVerifier.verify(true, true, false, cookiesCleared = false, loggedOutUiVisible = true)
        assertTrue(cookiesLeft.pageLoggedOut)
        assertFalse(cookiesLeft.loggedOut)
        assertFalse(cookiesLeft.confirmed)

        val stillAuthenticated = LogoutVerifier.verify(true, loginFormVisible = false, authenticatedUiVisible = true, cookiesCleared = true, loggedOutUiVisible = false)
        assertFalse(stillAuthenticated.pageLoggedOut)
        assertFalse(stillAuthenticated.loggedOut)

        val clickMissed = LogoutVerifier.verify(false, true, false, true, loggedOutUiVisible = true)
        assertFalse(clickMissed.loggedOut)

        val kept = SessionIsolation.apply(AddressRow("alice", good, "Success", ""), loggedOut = true, sessionCleared = true)
        assertEquals("Success", kept.status)
        assertFalse(kept.haltBatch)

        val stopped = SessionIsolation.apply(AddressRow("alice", good, "Success", ""), loggedOut = false, sessionCleared = true)
        assertEquals("Failed", stopped.status)
        assertTrue(stopped.haltBatch)
        assertTrue(stopped.error.contains("Logout or session reset"))

        val clearedOnly = SessionIsolation.apply(AddressRow("alice", good, "Success", ""), loggedOut = true, sessionCleared = false)
        assertEquals("Failed", clearedOnly.status)
        assertTrue(clearedOnly.haltBatch)
    }

    @Test
    fun loginOriginAllowlistRejectsOtherHosts() {
        assertTrue(SiteOrigin.isAllowed("https://treasurenova.net/"))
        assertTrue(SiteOrigin.isAllowed("https://treasurenova.net/login"))
        assertTrue(SiteOrigin.isAllowed("https://www.treasurenova.net/account"))
        val here = "https://treasurenova.net/login"
        assertTrue(SiteOrigin.isCredentialActionAllowed("", here))
        assertTrue(SiteOrigin.isCredentialActionAllowed("/login", here))
        assertTrue(SiteOrigin.isCredentialActionAllowed("?", here))
        assertTrue(SiteOrigin.isCredentialActionAllowed("#session", here))
        assertTrue(SiteOrigin.isCredentialActionAllowed("https://www.treasurenova.net/session", here))
        assertFalse(SiteOrigin.isAllowed("https://treasurenova.com/"))
        assertFalse(SiteOrigin.isAllowed("http://treasurenova.net/"))
        assertFalse(SiteOrigin.isAllowed("http://www.treasurenova.net/"))
        assertFalse(SiteOrigin.isAllowed("https://evil.example/"))
        assertFalse(SiteOrigin.isAllowed("https://treasurenova.net.evil.com/"))
        assertFalse(SiteOrigin.isAllowed("https://nottreasurenova.net/"))
        assertFalse(SiteOrigin.isAllowed("https://login.treasurenova.net/"))
        assertFalse(SiteOrigin.isAllowed("https://treasurenova.net:8443/"))
        assertFalse(SiteOrigin.isAllowed("https://user:pass@treasurenova.net/"))
        assertFalse(SiteOrigin.isAllowed("javascript:alert(1)"))
        assertFalse(SiteOrigin.isCredentialActionAllowed("https://treasurenova.com/login", here))
        assertFalse(SiteOrigin.isCredentialActionAllowed("http://treasurenova.net/login", here))
        assertFalse(SiteOrigin.isCredentialActionAllowed("//evil.example/login", here))
        assertFalse(SiteOrigin.isCredentialActionAllowed("", "https://evil.example/"))
        assertFalse(SiteOrigin.isCredentialActionAllowed("/login", "https://treasurenova.com/"))
        assertFalse(SiteOrigin.isCredentialActionAllowed("intent:scan", here))
        assertFalse(SiteOrigin.isCredentialActionAllowed("javascript:alert(1)", here))
        assertFalse(SiteOrigin.isCredentialActionAllowed("data:text/html,1", here))
        assertFalse(SiteOrigin.maySubmitCredentials("https://treasurenova.com/"))
    }

    @Test
    fun exportNeverIncludesPasswordColumnOrSecret() {
        val secret = "SuperSecret123"
        val rows = listOf(AddressRow("alice", good, "Success", ""))
        val csv = ExportFormatter.csv(rows)
        val txt = ExportFormatter.txt(rows)
        val xlsx = AccountImport.parseXlsx(ExportFormatter.xlsx(rows))
        assertFalse(csv.contains("Password", ignoreCase = true))
        assertFalse(txt.contains(secret))
        assertEquals(listOf("Username", "BEP20 Address", "Status", "Error"), xlsx.first())
        assertEquals(good, xlsx[1][1])
        assertFalse(AddressRow::class.java.declaredFields.any { it.name.contains("password", true) })
        val imported = AccountImport.parse("accounts.csv", "Username,Password\nalice,$secret\n".toByteArray())
        assertEquals(listOf("alice"), AccountImport.previewUsernames(imported))
        assertFalse(AccountImport.previewUsernames(imported).joinToString().contains(secret))
    }

    @Test
    fun refusesWithdrawalAndPaymentClicks() {
        assertFalse(Safety.allowClick("Withdraw"))
        assertFalse(Safety.allowClick("Transfer"))
        assertFalse(Safety.allowClick("Trade"))
        assertFalse(Safety.allowClick("Pay 30"))
        assertFalse(Safety.allowClick("Complete Verification"))
        assertFalse(Safety.allowClick("Confirm"))
        assertTrue(Safety.allowClick("USDT"))
        assertTrue(Safety.allowClick("BEP-20"))
        assertFalse(Safety.allowedProcessingActions.any { it.contains("withdraw", true) || it.contains("trade", true) })
    }

    @Test
    fun singleAccountFlowReturnsAddressAndLogsOut() = runBlocking {
        val bridge = FakeBridge(good)
        val rows = SessionController(bridge, awaitTwoFactor = {}, shouldStop = { false })
            .runBatch(listOf(AccountInput("1", "alice", "SuperSecret123")), confirmed = true)
        assertEquals(1, rows.size)
        assertEquals("Success", rows[0].status)
        assertEquals(good, rows[0].bep20Address)
        assertFalse(rows[0].toString().contains("SuperSecret123"))
        assertEquals(listOf("load", "fill:alice", "wallet", "usdt", "bep20", "read", "logout"), bridge.calls)
        assertFalse(bridge.reusedAuthenticatedSession)
    }

    @Test
    fun multiAccountIsSequentialAndStopsOnBlock() = runBlocking {
        val bridge = FakeBridge(good)
        bridge.blockOnSecond = true
        val rows = SessionController(bridge, awaitTwoFactor = {}, shouldStop = { false })
            .runBatch(
                listOf(
                    AccountInput("1", "alice", "pw-a"),
                    AccountInput("2", "bob", "pw-b"),
                    AccountInput("3", "cara", "pw-c"),
                ),
                confirmed = true,
            )
        assertEquals(listOf("alice", "bob"), rows.map { it.username })
        assertEquals("Success", rows[0].status)
        assertEquals("Blocked", rows[1].status)
        assertTrue(bridge.calls.indexOf("logout") < bridge.calls.indexOf("fill:bob"))
        assertFalse(rows.joinToString().contains("pw-"))
        assertEquals(listOf(0, 1), bridge.sessionsAtFill)
        assertFalse(bridge.reusedAuthenticatedSession)
        val unconfirmed = runCatching {
            BatchEngine.run(emptyList(), confirmed = false, shouldStop = { false }) { _, _ ->
                error("should not run")
            }
        }
        assertTrue(unconfirmed.isFailure)
    }

    @Test
    fun nextAccountDoesNotReusePreviousSession() = runBlocking {
        val bridge = FakeBridge(good)
        val rows = SessionController(bridge, awaitTwoFactor = {}, shouldStop = { false })
            .runBatch(
                listOf(
                    AccountInput("1", "alice", "pw-a"),
                    AccountInput("2", "bob", "pw-b"),
                ),
                confirmed = true,
            )
        assertEquals(listOf("Success", "Success"), rows.map { it.status })
        assertEquals(listOf(0, 1), bridge.sessionsAtFill)
        assertFalse(bridge.reusedAuthenticatedSession)
        assertTrue(bridge.calls.indexOf("logout") < bridge.calls.indexOf("fill:bob"))
    }

    @Test
    fun logoutNotConfirmedFailsAccountAndStopsBatch() = runBlocking {
        val bridge = FakeBridge(good)
        bridge.cookiesCleared = false
        val rows = SessionController(bridge, awaitTwoFactor = {}, shouldStop = { false })
            .runBatch(
                listOf(
                    AccountInput("1", "alice", "pw-a"),
                    AccountInput("2", "bob", "pw-b"),
                ),
                confirmed = true,
            )
        assertEquals(listOf("alice"), rows.map { it.username })
        assertEquals("Failed", rows[0].status)
        assertTrue(rows[0].haltBatch)
        assertTrue(rows[0].error.contains("Logout or session reset"))
        assertFalse(bridge.calls.contains("fill:bob"))
        assertFalse(rows.joinToString().contains("pw-"))
    }

    @Test
    fun pageStillAuthenticatedStopsBatch() = runBlocking {
        val bridge = FakeBridge(good)
        bridge.authenticatedUiAfterLogout = true
        bridge.loginFormAfterLogout = false
        val rows = SessionController(bridge, awaitTwoFactor = {}, shouldStop = { false })
            .runBatch(
                listOf(
                    AccountInput("1", "alice", "pw-a"),
                    AccountInput("2", "bob", "pw-b"),
                ),
                confirmed = true,
            )
        assertEquals(1, rows.size)
        assertEquals("Failed", rows[0].status)
        assertFalse(bridge.calls.contains("fill:bob"))
    }

    @Test
    fun twoFactorPausesUntilContinued() = runBlocking {
        val bridge = FakeBridge(good)
        bridge.twoFactorOnce = true
        var continued = false
        val rows = SessionController(
            bridge,
            awaitTwoFactor = { continued = true },
            shouldStop = { false },
        ).runBatch(listOf(AccountInput("1", "alice", "pw")), confirmed = true)
        assertTrue(continued)
        assertEquals("Success", rows.single().status)
    }

    @Test
    fun homeListsRequiredScreensAndWarning() {
        assertTrue(UiCatalog.homeActions.containsAll(listOf("Single Account", "Multi Account", "Import Accounts", "Results", "Settings")))
        assertEquals(listOf("Login", "Wallet", "USDT", "BEP20", "Address"), UiCatalog.stepNames)
        assertTrue(Bep20Warning.contains("BEP20"))
        assertTrue(Bep20Warning.contains("BNB Smart Chain"))
    }

    @Test
    fun jsStringDoesNotBreakOut() {
        val encoded = Safety.jsString("a\"<script>")
        assertFalse(encoded.contains("<script>"))
        assertTrue(encoded.startsWith("\""))
    }


    @Test
    fun credentialsAllowedSuspendCheckDoesNotRecurseAndUsesOriginGate() {
        val source = readSource("src/main/java/com/treasurenova/bep20manager/automation/WebViewBridge.kt")
        val body = functionBody(source, "credentialsAllowedSuspendCheck")
        assertFalse(body.contains("credentialsAllowedSuspendCheck"))
        assertTrue(body.contains("credentialsAllowed()"))
        assertTrue(body.contains("SiteOrigin.isAllowed"))
        assertTrue(body.contains("decideCredentials("))
        assertTrue(body.contains("return "))
        val fill = functionBody(source, "fillAndSubmit")
        val checkAt = fill.indexOf("credentialsAllowedSuspendCheck()")
        val fillAt = fill.indexOf("fillLogin")
        assertTrue(checkAt >= 0 && fillAt > checkAt)
        val allowedFn = functionBody(source, "credentialsAllowed")
        assertTrue(allowedFn.contains("sessionResetFailed"))
        assertTrue(allowedFn.contains("originRejected"))
    }

    @Test
    fun webViewBridgeDecideCredentialsBlocksDisallowedOriginAndSessionReset() {
        assertFalse(WebViewBridge.decideCredentials(originRejected = false, sessionResetFailed = false, currentUrl = "https://evil.example/"))
        assertFalse(WebViewBridge.decideCredentials(originRejected = false, sessionResetFailed = false, currentUrl = "http://treasurenova.net/"))
        assertFalse(WebViewBridge.decideCredentials(originRejected = false, sessionResetFailed = false, currentUrl = "javascript:alert(1)"))
        assertFalse(WebViewBridge.decideCredentials(originRejected = false, sessionResetFailed = false, currentUrl = "intent://scan/#Intent;end"))
        assertFalse(WebViewBridge.decideCredentials(originRejected = false, sessionResetFailed = false, currentUrl = "data:text/html,hi"))
        assertFalse(WebViewBridge.decideCredentials(originRejected = true, sessionResetFailed = false, currentUrl = "https://treasurenova.net/"))
        assertFalse(WebViewBridge.decideCredentials(originRejected = false, sessionResetFailed = true, currentUrl = "https://www.treasurenova.net/login"))
        assertFalse(WebViewBridge.decideCredentials(originRejected = false, sessionResetFailed = false, currentUrl = null))
        assertFalse(WebViewBridge.decideCredentials(originRejected = false, sessionResetFailed = false, currentUrl = ""))
        assertTrue(WebViewBridge.decideCredentials(originRejected = false, sessionResetFailed = false, currentUrl = "https://treasurenova.net/login"))
        assertTrue(WebViewBridge.decideCredentials(originRejected = false, sessionResetFailed = false, currentUrl = "https://www.treasurenova.net/account"))
        assertFalse(LoginConfirmation.confirmed(PageSnapshot(loginSubmitted = true, authenticatedUiVisible = false)))
        assertTrue(
            LoginConfirmation.confirmed(
                PageSnapshot(loginSubmitted = true, authenticatedUiVisible = true, accountIdentity = "alice"),
            ),
        )
    }

    @Test
    fun navigationGuardCancelsUnauthorizedIframesAndDangerousSchemes() {
        assertFalse(WebViewBridge.shouldCancelLoad("https://treasurenova.net/login", isMainFrame = true))
        assertFalse(WebViewBridge.shouldCancelLoad("https://www.treasurenova.net/", isMainFrame = false))
        assertTrue(WebViewBridge.shouldCancelLoad("https://evil.example/frame", isMainFrame = false))
        assertTrue(WebViewBridge.shouldCancelLoad("javascript:alert(1)", isMainFrame = true))
        assertTrue(WebViewBridge.shouldCancelLoad("intent:scan", isMainFrame = true))
        assertTrue(WebViewBridge.shouldCancelLoad("data:text/html,hi", isMainFrame = false))
        assertTrue(WebViewBridge.shouldCancelLoad("http://treasurenova.net/", isMainFrame = true))
        assertTrue(WebViewBridge.shouldMarkOriginRejected("javascript:alert(1)"))
        assertTrue(WebViewBridge.shouldMarkOriginRejected("intent://evil.example/"))
        assertTrue(WebViewBridge.shouldMarkOriginRejected("data:text/html,1"))
        assertTrue(WebViewBridge.shouldMarkOriginRejected("https://treasurenova.com/"))
        assertFalse(WebViewBridge.shouldMarkOriginRejected("https://treasurenova.net/"))
        assertFalse(WebViewBridge.shouldMarkOriginRejected("about:blank"))
        val source = readSource("src/main/java/com/treasurenova/bep20manager/automation/WebViewBridge.kt")
        assertFalse(source.contains("if (!request.isForMainFrame) return false"))
        assertFalse(source.contains("if (!isHttpUrl(url)) return false"))
        val nav = functionBody(source, "shouldOverrideUrlLoading")
        assertTrue(nav.contains("shouldCancelLoad"))
        assertTrue(nav.contains("isForMainFrame"))
        assertTrue(nav.contains("originRejected = true"))
    }

    @Test
    fun relativeFormActionResolvesAgainstCurrentOrigin() {
        val here = "https://treasurenova.net/login"
        assertTrue(SiteOrigin.isCredentialActionAllowed("", here))
        assertTrue(SiteOrigin.isCredentialActionAllowed("/", here))
        assertTrue(SiteOrigin.isCredentialActionAllowed("/login", here))
        assertTrue(SiteOrigin.isCredentialActionAllowed("?", "https://www.treasurenova.net/account"))
        assertTrue(SiteOrigin.isCredentialActionAllowed("#token", here))
        assertTrue(SiteOrigin.isCredentialActionAllowed("next", here))
        assertFalse(SiteOrigin.isCredentialActionAllowed("", null))
        assertFalse(SiteOrigin.isCredentialActionAllowed("/login", "https://evil.example/a"))
        assertFalse(SiteOrigin.isCredentialActionAllowed("?x=1", "http://treasurenova.net/login"))
        assertFalse(SiteOrigin.isCredentialActionAllowed("", "https://treasurenova.net.evil.com/"))
    }

    @Test
    fun credentialActionRejectsIntentJavascriptAndData() {
        val here = "https://treasurenova.net/"
        assertFalse(SiteOrigin.isCredentialActionAllowed("intent:scan", here))
        assertFalse(SiteOrigin.isCredentialActionAllowed("intent://evil.example/a", here))
        assertFalse(SiteOrigin.isCredentialActionAllowed("javascript:alert(1)", here))
        assertFalse(SiteOrigin.isCredentialActionAllowed("data:text/html,1", here))
        assertFalse(SiteOrigin.isCredentialActionAllowed("foo:bar", here))
        val intent = control(1, "Login", submit = true, inForm = true, action = "intent:scan")
        assertNull(LoginControlSelector.select(listOf(intent), here))
        val relative = control(2, "Login", submit = true, inForm = true, action = "/session")
        assertEquals(relative, LoginControlSelector.select(listOf(intent, relative), here))
        assertNull(LoginControlSelector.select(listOf(relative), "https://evil.example/"))
    }

    @Test
    fun loadLoginDoesNotClickGenericLoginNode() {
        val source = readSource("src/main/java/com/treasurenova/bep20manager/automation/WebViewBridge.kt")
        val body = functionBody(source, "loadLogin")
        assertFalse(body.contains("clickIfAllowed(\"Login\")"))
        assertFalse(body.contains("clickIfAllowed(\"Log In\")"))
        assertFalse(body.contains("clickIfAllowed(\"Sign In\")"))
        assertTrue(body.contains("loadUrl"))
    }

    @Test
    fun fillLoginAndLoginRootStayInsideTheSameForm() {
        val js = PageScripts.library
        val fill = jsFunction(js, "fillLogin: function")
        assertTrue(fill.contains("usernameWithin"))
        assertTrue(fill.contains("root.contains"))
        assertFalse(fill.contains("document.querySelector"))
        val root = jsFunction(js, "function loginRoot")
        assertTrue(root.contains("usernameWithin(root)"))
        assertFalse(root.contains("document.querySelector('input[placeholder"))
        assertFalse(js.contains("button,a,li,span,div"))
        assertFalse(js.contains("span,div"))
        val click = jsFunction(js, "function clickExact")
        assertTrue(click.contains("button, a, input[type=\"submit\"], input[type=\"button\"]"))
        assertFalse(click.contains("span"))
        assertFalse(click.contains("div"))
    }

    @Test
    fun loginNotConfirmedStopsBatchBeforeWallet() = runBlocking {
        val bridge = FakeBridge(good)
        bridge.loginSucceeds = false
        val rows = SessionController(bridge, awaitTwoFactor = {}, shouldStop = { false })
            .runBatch(
                listOf(
                    AccountInput("1", "alice", "pw-a"),
                    AccountInput("2", "bob", "pw-b"),
                ),
                confirmed = true,
            )
        assertEquals(listOf("alice"), rows.map { it.username })
        assertEquals("Failed", rows[0].status)
        assertTrue(rows[0].haltBatch)
        assertEquals("", rows[0].bep20Address)
        assertTrue(rows[0].error.contains("Login was not confirmed"))
        assertFalse(bridge.calls.contains("wallet"))
        assertFalse(bridge.calls.contains("fill:bob"))
        assertFalse(rows.joinToString().contains("pw-"))
    }

    @Test
    fun identityNotConfirmedDoesNotSaveAddressAndStopsBatch() = runBlocking {
        val bridge = FakeBridge(good)
        bridge.identityOnRead = "someone-else"
        val rows = SessionController(bridge, awaitTwoFactor = {}, shouldStop = { false })
            .runBatch(
                listOf(
                    AccountInput("1", "alice", "pw-a"),
                    AccountInput("2", "bob", "pw-b"),
                ),
                confirmed = true,
            )
        assertEquals(1, rows.size)
        assertEquals("Failed", rows[0].status)
        assertEquals("", rows[0].bep20Address)
        assertTrue(rows[0].haltBatch)
        assertTrue(rows[0].error.contains("identity"))
        assertFalse(rows[0].bep20Address.contains(good))
        assertFalse(bridge.calls.contains("fill:bob"))
        assertFalse(LoginConfirmation.identityMatches("alice", null))
        assertFalse(LoginConfirmation.identityMatches("alice", ""))
        assertFalse(LoginConfirmation.identityMatches("alice", "bob"))
        assertTrue(LoginConfirmation.identityMatches("alice", "alice"))
    }

    @Test
    fun parentUnder800GluingUsdtAndBep20OntoUnrelatedAddressIsRejected() {
        val unrelated = other
        val usdtChild = "USDT"
        val bepChild = "BEP20"
        val parent = "$usdtChild $bepChild $unrelated"
        assertTrue(parent.length < 800)
        val glued = UsdtBep20Matcher.fromBlocks(listOf(parent, usdtChild, bepChild, unrelated))
        assertFalse(glued is AddressDecision.Ok)
        val distant = "USDT BEP20" + " ".repeat(260) + unrelated
        assertTrue(distant.length < 800)
        assertFalse(UsdtBep20Matcher.classify(distant) is AddressDecision.Ok)
        assertTrue(UsdtBep20Matcher.isUnrelatedGluedParent(parent, listOf(parent, usdtChild, bepChild, unrelated)))
    }

    @Test
    fun childOkDoesNotWinWhenParentHasMultipleAddressesOrOtherNetwork() {
        val child = "USDT Deposit Address (BEP-20) $good"
        assertTrue(UsdtBep20Matcher.classify(child) is AddressDecision.Ok)
        val multi = "USDT BEP-20 $good $other"
        assertTrue(multi.length < 800)
        val fromMulti = UsdtBep20Matcher.fromBlocks(listOf(multi, child))
        assertFalse(fromMulti is AddressDecision.Ok)
        val otherNetwork = "USDT TRC-20 $other"
        val parent = "$otherNetwork $child"
        assertTrue(parent.length < 800)
        val fromNetwork = UsdtBep20Matcher.fromBlocks(listOf(parent, child, otherNetwork))
        assertFalse(fromNetwork is AddressDecision.Ok)
        assertTrue(fromNetwork is AddressDecision.NetworkMismatch || fromNetwork is AddressDecision.Missing)
    }

    @Test
    fun bep20TokenDoesNotMatchInsideUnrelatedTokens() {
        assertFalse(UsdtBep20Matcher.classify("USDT xbep20 $good") is AddressDecision.Ok)
        assertFalse(UsdtBep20Matcher.classify("USDT bep200 $good") is AddressDecision.Ok)
        assertFalse(UsdtBep20Matcher.classify("USDT mybep-20coin $good") is AddressDecision.Ok)
        assertFalse(UsdtBep20Matcher.classify("USDT BEP20USDT $good") is AddressDecision.Ok)
        val hyphen = UsdtBep20Matcher.classify("USDT Deposit Address (BEP-20) $good")
        assertEquals(good, (hyphen as AddressDecision.Ok).address)
        val plain = UsdtBep20Matcher.classify("Send USDT on BEP20 $good")
        assertEquals(good, (plain as AddressDecision.Ok).address)
    }

    @Test
    fun logoutProbeDoesNotTreatPasswordFieldAsLoggedOut() {
        val js = PageScripts.library
        val probe = jsFunction(js, "logoutProbe: function")
        assertFalse(probe.contains("if (!loginFields)"))
        assertFalse(probe.contains("authenticatedUiVisible = false"))
        assertFalse(probe.contains("authenticatedUiVisible = !loginFields"))
        assertTrue(probe.contains("authenticatedUi()"))
        assertTrue(probe.contains("loggedOutUi()"))
        val loggedOut = jsFunction(js, "function loggedOutUi")
        assertFalse(loggedOut.contains("input[type=\"password\"]"))
        assertTrue(js.contains("isUnrelatedParent"))
        val blocks = jsFunction(js, "function depositBlocks")
        assertTrue(blocks.contains("isUnrelatedParent"))
    }

    @Test
    fun passwordFieldAloneDoesNotConfirmLogout() {
        val passwordOnly = LogoutVerifier.verify(
            logoutClicked = true,
            loginFormVisible = true,
            authenticatedUiVisible = false,
            cookiesCleared = true,
            loggedOutUiVisible = false,
        )
        assertFalse(passwordOnly.pageLoggedOut)
        assertFalse(passwordOnly.loggedOut)
        assertFalse(passwordOnly.confirmed)
        val real = LogoutVerifier.verify(
            logoutClicked = true,
            loginFormVisible = true,
            authenticatedUiVisible = false,
            cookiesCleared = true,
            loggedOutUiVisible = true,
        )
        assertTrue(real.loggedOut)
    }

    @Test
    fun sessionResetFailedIsReadAndHaltsBatch() = runBlocking {
        val bridge = FakeBridge(good)
        bridge.resetFailed = true
        val rows = SessionController(bridge, awaitTwoFactor = {}, shouldStop = { false })
            .runBatch(
                listOf(
                    AccountInput("1", "alice", "pw-a"),
                    AccountInput("2", "bob", "pw-b"),
                ),
                confirmed = true,
            )
        assertEquals(listOf("alice"), rows.map { it.username })
        assertEquals("Failed", rows[0].status)
        assertTrue(rows[0].haltBatch)
        assertTrue(rows[0].error.contains("Session reset"))
        assertFalse(bridge.calls.contains("fill:alice"))
        assertFalse(bridge.calls.contains("fill:bob"))
        assertTrue(bridge.wasSessionResetFailed())
    }

    @Test
    fun deleteAllDataResultIsRequiredForSessionReset() {
        val source = readSource("src/main/java/com/treasurenova/bep20manager/automation/WebViewBridge.kt")
        val body = functionBody(source, "clearWebViewSession")
        assertTrue(body.contains("deleteAllData()"))
        assertTrue(body.contains("storageCleared"))
        assertTrue(body.contains("storageCleared &&"))
        assertFalse(body.contains("runCatching"))
        val controller = readSource("src/main/java/com/treasurenova/bep20manager/automation/SessionController.kt")
        assertTrue(controller.contains("wasSessionResetFailed()"))
        assertTrue(controller.contains("haltBatch = true"))
    }


    private fun readSource(relative: String): String {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        repeat(6) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate.readText()
            val appCandidate = File(dir, "app/$relative")
            if (appCandidate.isFile) return appCandidate.readText()
            dir = dir.parentFile ?: return@repeat
        }
        error("Cannot find $relative from " + File(".").absolutePath)
    }

    private fun functionBody(source: String, name: String): String {
        val marker = "fun $name"
        val start = source.indexOf(marker)
        if (start < 0) error("missing $name")
        return braceBody(source, source.indexOf('{', start))
    }

    private fun jsFunction(source: String, name: String): String {
        val start = source.indexOf(name)
        if (start < 0) error("missing $name")
        return braceBody(source, source.indexOf('{', start))
    }

    private fun braceBody(source: String, brace: Int): String {
        var depth = 0
        for (i in brace until source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(brace + 1, i)
                }
            }
        }
        error("unclosed body")
    }


    private fun control(
        index: Int,
        text: String,
        submit: Boolean,
        inForm: Boolean,
        tag: String = "button",
        type: String = if (submit) "submit" else "button",
        action: String = "",
    ) = LoginControlCandidate(
        index = index,
        text = text,
        tag = tag,
        type = type,
        inLoginForm = inForm,
        submitControl = submit,
        formAction = action,
    )
}

private class FakeBridge(private val address: String) : PageBridge {
    val calls = mutableListOf<String>()
    var blockOnSecond = false
    var twoFactorOnce = false
    var logoutClicked = true
    var loginFormAfterLogout = true
    var authenticatedUiAfterLogout = false
    var cookiesCleared = true
    var loggedOutUiAfterLogout = true
    var loginSucceeds = true
    var resetFailed = false
    var identityOnRead: String? = null
    private var activeUser = ""
    val sessionsAtFill = mutableListOf<Int>()
    var reusedAuthenticatedSession = false
    private var fills = 0
    private var authenticated = false
    private var sessionGeneration = 0

    override fun wasSessionResetFailed(): Boolean = resetFailed

    override suspend fun loadLogin() { calls += "load" }

    override suspend fun fillAndSubmit(username: String, password: String): PageSnapshot {
        if (authenticated) reusedAuthenticatedSession = true
        calls += "fill:$username"
        sessionsAtFill += sessionGeneration
        fills += 1
        authenticated = true
        check(!password.isBlank())
        activeUser = username
        return if (twoFactorOnce && fills == 1) {
            PageSnapshot(twoFactorVisible = true, loginFieldsFound = true, loginSubmitted = true)
        } else if (!loginSucceeds) {
            PageSnapshot(loginFieldsFound = true, loginSubmitted = false, authenticatedUiVisible = false)
        } else {
            PageSnapshot(
                loginFieldsFound = true,
                loginSubmitted = true,
                authenticatedUiVisible = true,
                accountIdentity = username,
            )
        }
    }

    override suspend fun openWalletDeposit(): PageSnapshot {
        calls += "wallet"
        return if (blockOnSecond && fills == 2) PageSnapshot(blockedMessage = "Service is not available in your region")
        else PageSnapshot()
    }

    override suspend fun selectUsdt(): PageSnapshot { calls += "usdt"; return PageSnapshot() }

    override suspend fun selectBep20(): PageSnapshot {
        calls += "bep20"
        return PageSnapshot(bep20LabelPresent = true, networkText = "BEP-20")
    }

    override suspend fun readSnapshot(): PageSnapshot {
        calls += "read"
        val identity = identityOnRead ?: activeUser
        return PageSnapshot(
            depositBlocks = listOf("USDT Deposit Address (BEP-20) $address"),
            loginSubmitted = true,
            authenticatedUiVisible = true,
            accountIdentity = identity,
        )
    }

    override suspend fun logout(): PageSnapshot {
        calls += "logout"
        val verification = LogoutVerifier.verify(
            logoutClicked = logoutClicked,
            loginFormVisible = loginFormAfterLogout,
            authenticatedUiVisible = authenticatedUiAfterLogout,
            cookiesCleared = cookiesCleared,
            loggedOutUiVisible = loggedOutUiAfterLogout,
        )
        if (verification.confirmed) {
            authenticated = false
            sessionGeneration += 1
        }
        return PageSnapshot(
            loggedOut = verification.loggedOut,
            sessionCleared = verification.sessionCleared,
            loginFieldsFound = loginFormAfterLogout,
            authenticatedUiVisible = authenticatedUiAfterLogout,
        )
    }
}
