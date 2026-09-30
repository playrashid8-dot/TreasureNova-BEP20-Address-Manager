package com.treasurenova.bep20manager

import com.treasurenova.bep20manager.automation.PageBridge
import com.treasurenova.bep20manager.automation.SessionController
import com.treasurenova.bep20manager.logic.AccountImport
import com.treasurenova.bep20manager.logic.AccountInput
import com.treasurenova.bep20manager.logic.AddressExtractor
import com.treasurenova.bep20manager.logic.AddressRow
import com.treasurenova.bep20manager.logic.BatchEngine
import com.treasurenova.bep20manager.logic.ExportFormatter
import com.treasurenova.bep20manager.logic.PageSnapshot
import com.treasurenova.bep20manager.logic.Safety
import com.treasurenova.bep20manager.ui.Bep20Warning
import com.treasurenova.bep20manager.ui.UiCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowTest {
    private val good = "0x" + "ab".repeat(20)
    private val other = "0x" + "cd".repeat(20)

    @Test
    fun extractsOnlyUsdtBep20Address() {
        val ok = AddressExtractor.decide(
            PageSnapshot(bep20LabelPresent = true, networkText = "USDT Deposit Address(BEP-20)", bep20Value = good)
        )
        assertTrue(ok is com.treasurenova.bep20manager.logic.AddressDecision.Ok)
        val trc = AddressExtractor.decide(
            PageSnapshot(bep20LabelPresent = true, networkText = "TRC-20", bep20Value = good)
        )
        assertTrue(trc is com.treasurenova.bep20manager.logic.AddressDecision.NetworkMismatch)
        val junk = AddressExtractor.decide(
            PageSnapshot(bep20LabelPresent = true, networkText = "BEP20", bep20Value = "not-an-address")
        )
        assertTrue(junk is com.treasurenova.bep20manager.logic.AddressDecision.Missing)
        assertFalse(AddressExtractor.isBep20Network("TRC20"))
        assertTrue(AddressExtractor.isBep20Network("BNB Smart Chain"))
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
        assertTrue(Safety.allowClick("Confirm"))
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
    }

    @Test
    fun multiAccountIsSequentialAndStopsOnBlock() = runBlocking {
        val bridge = FakeBridge(good)
        bridge.blockOnSecond = true
        var stopped = false
        val rows = SessionController(bridge, awaitTwoFactor = {}, shouldStop = { stopped })
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
        val unconfirmed = runCatching {
            BatchEngine.run(emptyList(), confirmed = false, shouldStop = { false }) { _, _ ->
                error("should not run")
            }
        }
        assertTrue(unconfirmed.isFailure)
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
}

private class FakeBridge(private val address: String) : PageBridge {
    val calls = mutableListOf<String>()
    var blockOnSecond = false
    var twoFactorOnce = false
    private var fills = 0

    override suspend fun loadLogin() { calls += "load" }
    override suspend fun fillAndSubmit(username: String, password: String): PageSnapshot {
        calls += "fill:$username"
        fills += 1
        check(!password.isBlank())
        return if (twoFactorOnce && fills == 1) PageSnapshot(twoFactorVisible = true, loginFieldsFound = true, loginSubmitted = true)
        else PageSnapshot(loginFieldsFound = true, loginSubmitted = true)
    }
    override suspend fun openWalletDeposit(): PageSnapshot {
        calls += "wallet"
        return if (blockOnSecond && fills == 2) PageSnapshot(blockedMessage = "Service is not available in your region")
        else PageSnapshot()
    }
    override suspend fun selectUsdt(): PageSnapshot { calls += "usdt"; return PageSnapshot() }
    override suspend fun selectBep20(): PageSnapshot { calls += "bep20"; return PageSnapshot(bep20LabelPresent = true, networkText = "BEP-20") }
    override suspend fun readSnapshot(): PageSnapshot {
        calls += "read"
        return PageSnapshot(bep20LabelPresent = true, networkText = "USDT Deposit Address(BEP-20)", bep20Value = address)
    }
    override suspend fun logout() { calls += "logout" }
}
