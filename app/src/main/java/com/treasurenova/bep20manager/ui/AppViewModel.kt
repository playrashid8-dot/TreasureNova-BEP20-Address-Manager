package com.treasurenova.bep20manager.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.treasurenova.bep20manager.data.AccountStore
import com.treasurenova.bep20manager.data.HistoryStore
import com.treasurenova.bep20manager.data.SettingsStore
import com.treasurenova.bep20manager.logic.AccountInput
import com.treasurenova.bep20manager.logic.AddressRow
import com.treasurenova.bep20manager.logic.BatchSummary
import kotlinx.coroutines.CompletableDeferred
import java.util.UUID

class ProcessFlags {
    @Volatile var stopped: Boolean = false
    @Volatile var paused: Boolean = false
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val accountStore = AccountStore(app)
    private val historyStore = HistoryStore(app)
    private val settingsStore = SettingsStore(app)

    var accounts by mutableStateOf(accountStore.list())
        private set
    var results by mutableStateOf<List<AddressRow>>(emptyList())
        private set
    var history by mutableStateOf(historyStore.list())
        private set
    var loginUrl by mutableStateOf(settingsStore.loginUrl())
        private set
    var currentUsername by mutableStateOf("")
    var progress by mutableStateOf("0/0")
    var steps by mutableStateOf(
        linkedMapOf("Login" to "Waiting", "Wallet" to "Waiting", "USDT" to "Waiting", "BEP20" to "Waiting", "Address" to "Waiting")
    )
    var selected by mutableStateOf<AddressRow?>(null)
    val flags = ProcessFlags()
    var twoFactorGate: CompletableDeferred<Unit>? = null

    fun refresh() {
        accounts = accountStore.list()
        history = historyStore.list()
        loginUrl = settingsStore.loginUrl()
    }

    fun addAccount(username: String, password: String) {
        accountStore.upsert(username.trim(), password)
        refresh()
    }

    fun updateAccount(id: String, username: String, password: String) {
        accountStore.upsert(username.trim(), password, id)
        refresh()
    }

    fun deleteAccount(id: String) {
        accountStore.delete(id)
        refresh()
    }

    fun clearAccounts() {
        accountStore.clear()
        refresh()
    }

    fun setLoginUrl(url: String) {
        settingsStore.setLoginUrl(url)
        loginUrl = settingsStore.loginUrl()
    }

    fun clearHistory() {
        historyStore.clear()
        refresh()
    }

    fun accountById(id: String): AccountInput? = accounts.firstOrNull { it.id == id }

    fun begin(count: Int) {
        flags.stopped = false
        flags.paused = false
        results = emptyList()
        progress = "0/$count"
        currentUsername = ""
        steps = linkedMapOf("Login" to "Waiting", "Wallet" to "Waiting", "USDT" to "Waiting", "BEP20" to "Waiting", "Address" to "Waiting")
    }

    fun note(index: Int, total: Int, username: String, step: String, detail: String) {
        currentUsername = username
        progress = "${index + 1}/$total"
        val next = LinkedHashMap(steps)
        next[step] = detail
        steps = next
    }

    fun finish(rows: List<AddressRow>) {
        results = rows
        historyStore.save(BatchSummary(UUID.randomUUID().toString(), System.currentTimeMillis(), rows))
        history = historyStore.list()
    }

    fun openHistory(summary: BatchSummary) {
        results = summary.rows
    }
}
