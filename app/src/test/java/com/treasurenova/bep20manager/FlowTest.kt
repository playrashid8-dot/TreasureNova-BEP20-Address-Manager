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

        assertEquals(signInSubmit, LoginControlSelector.select(listOf(randomLogin, signInSubmit, pay, nearbyConfirm)))
        assertEquals(loginSubmit, LoginControlSelector.select(listOf(randomLogin, nearbyConfirm, loginSubmit, register)))
        assertEquals(logIn, LoginControlSelector.select(listOf(pay, withdraw, register, nearbyConfirm, confirmSubmit, logIn)))
        assertNull(LoginControlSelector.select(listOf(pay, withdraw, register, nearbyConfirm, confirmSubmit, randomLogin)))
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
        assertNull(LoginControlSelector.select(listOf(foreignPost)))
        val sameSite = control(10, "Login", submit = true, inForm = true, action = "https://treasurenova.net/login")
        assertEquals(sameSite, LoginControlSelector.select(listOf(foreignPost, sameSite)))
    }

    @Test
    fun logoutIsVerifiedAndSessionResetFailureStopsTheBatch() {
        val ok = LogoutVerifier.verify(
            logoutClicked = true,
            loginFormVisible = true,
            authenticatedUiVisible = false,
            cookiesCleared = true,
        )
        assertTrue(ok.loggedOut)
        assertTrue(ok.sessionCleared)
        assertTrue(ok.confirmed)

        val cookiesLeft = LogoutVerifier.verify(true, true, false, cookiesCleared = false)
        assertTrue(cookiesLeft.pageLoggedOut)
        assertFalse(cookiesLeft.loggedOut)
        assertFalse(cookiesLeft.confirmed)

        val stillAuthenticated = LogoutVerifier.verify(true, loginFormVisible = false, authenticatedUiVisible = true, cookiesCleared = true)
        assertFalse(stillAuthenticated.pageLoggedOut)
        assertFalse(stillAuthenticated.loggedOut)

        val clickMissed = LogoutVerifier.verify(false, true, false, true)
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
        assertTrue(SiteOrigin.isCredentialActionAllowed(""))
        assertTrue(SiteOrigin.isCredentialActionAllowed("/login"))
        assertTrue(SiteOrigin.isCredentialActionAllowed("https://www.treasurenova.net/session"))
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
        assertFalse(SiteOrigin.isCredentialActionAllowed("https://treasurenova.com/login"))
        assertFalse(SiteOrigin.isCredentialActionAllowed("http://treasurenova.net/login"))
        assertFalse(SiteOrigin.isCredentialActionAllowed("//evil.example/login"))
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
    val sessionsAtFill = mutableListOf<Int>()
    var reusedAuthenticatedSession = false
    private var fills = 0
    private var authenticated = false
    private var sessionGeneration = 0

    override suspend fun loadLogin() { calls += "load" }

    override suspend fun fillAndSubmit(username: String, password: String): PageSnapshot {
        if (authenticated) reusedAuthenticatedSession = true
        calls += "fill:$username"
        sessionsAtFill += sessionGeneration
        fills += 1
        authenticated = true
        check(!password.isBlank())
        return if (twoFactorOnce && fills == 1) {
            PageSnapshot(twoFactorVisible = true, loginFieldsFound = true, loginSubmitted = true)
        } else {
            PageSnapshot(loginFieldsFound = true, loginSubmitted = true)
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
        return PageSnapshot(depositBlocks = listOf("USDT Deposit Address (BEP-20) $address"))
    }

    override suspend fun logout(): PageSnapshot {
        calls += "logout"
        val verification = LogoutVerifier.verify(
            logoutClicked = logoutClicked,
            loginFormVisible = loginFormAfterLogout,
            authenticatedUiVisible = authenticatedUiAfterLogout,
            cookiesCleared = cookiesCleared,
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
