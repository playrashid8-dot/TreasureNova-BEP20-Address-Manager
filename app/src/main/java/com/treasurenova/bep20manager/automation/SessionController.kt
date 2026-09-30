package com.treasurenova.bep20manager.automation

import com.treasurenova.bep20manager.logic.AccountInput
import com.treasurenova.bep20manager.logic.AddressExtractor
import com.treasurenova.bep20manager.logic.AddressRow
import com.treasurenova.bep20manager.logic.BatchEngine
import com.treasurenova.bep20manager.logic.PageSnapshot

interface PageBridge {
    suspend fun loadLogin()
    suspend fun fillAndSubmit(username: String, password: String): PageSnapshot
    suspend fun openWalletDeposit(): PageSnapshot
    suspend fun selectUsdt(): PageSnapshot
    suspend fun selectBep20(): PageSnapshot
    suspend fun readSnapshot(): PageSnapshot
    suspend fun logout()
}

class SessionController(
    private val bridge: PageBridge,
    private val awaitTwoFactor: suspend (username: String) -> Unit,
    private val shouldStop: () -> Boolean,
    private val waitIfPaused: suspend () -> Unit = {},
    private val onStep: (username: String, step: String, detail: String) -> Unit = { _, _, _ -> },
) {
    suspend fun runBatch(accounts: List<AccountInput>, confirmed: Boolean): List<AddressRow> =
        BatchEngine.run(accounts, confirmed, shouldStop) { _, account ->
            runOne(account)
        }

    suspend fun runOne(account: AccountInput): AddressRow {
        waitIfPaused()
        if (shouldStop()) {
            return AddressRow(account.username, "", "Stopped", "Stopped by user")
        }
        onStep(account.username, "Login", "Opening login")
        bridge.loadLogin()
        val afterLogin = bridge.fillAndSubmit(account.username, account.password)
        val loginRow = gate(account.username, afterLogin, stage = "Login")
        if (loginRow != null) {
            bridge.logout()
            return loginRow
        }
        onStep(account.username, "Wallet", "Opening wallet")
        val wallet = bridge.openWalletDeposit()
        gate(account.username, wallet, stage = "Wallet")?.let {
            bridge.logout()
            return it
        }
        onStep(account.username, "USDT", "Selecting USDT")
        val usdt = bridge.selectUsdt()
        gate(account.username, usdt, stage = "USDT")?.let {
            bridge.logout()
            return it
        }
        onStep(account.username, "BEP20", "Selecting BEP20")
        val network = bridge.selectBep20()
        gate(account.username, network, stage = "BEP20")?.let {
            bridge.logout()
            return it
        }
        onStep(account.username, "Address", "Reading address")
        val snap = bridge.readSnapshot()
        val decided = continueAfterTwoFactor(account.username, snap)
        val row = AddressExtractor.toRow(account.username, AddressExtractor.decide(decided))
        onStep(account.username, "Logout", row.status)
        bridge.logout()
        return row
    }

    private suspend fun continueAfterTwoFactor(username: String, start: PageSnapshot): PageSnapshot {
        var snap = start
        if (AddressExtractor.decide(snap) == com.treasurenova.bep20manager.logic.AddressDecision.NeedsTwoFactor) {
            onStep(username, "Login", "Waiting for 2FA")
            awaitTwoFactor(username)
            snap = bridge.readSnapshot()
        }
        return snap
    }

    private suspend fun gate(username: String, snap: PageSnapshot, stage: String): AddressRow? {
        var current = snap
        val decision = AddressExtractor.decide(current)
        if (decision is com.treasurenova.bep20manager.logic.AddressDecision.NeedsTwoFactor) {
            onStep(username, stage, "Waiting for 2FA")
            awaitTwoFactor(username)
            current = bridge.readSnapshot()
            val after = AddressExtractor.decide(current)
            if (after is com.treasurenova.bep20manager.logic.AddressDecision.NeedsTwoFactor ||
                after is com.treasurenova.bep20manager.logic.AddressDecision.Blocked ||
                after is com.treasurenova.bep20manager.logic.AddressDecision.LoginFailed
            ) {
                return AddressExtractor.toRow(username, after)
            }
            return null
        }
        if (decision is com.treasurenova.bep20manager.logic.AddressDecision.Blocked ||
            decision is com.treasurenova.bep20manager.logic.AddressDecision.LoginFailed
        ) {
            return AddressExtractor.toRow(username, decision)
        }
        return null
    }
}
