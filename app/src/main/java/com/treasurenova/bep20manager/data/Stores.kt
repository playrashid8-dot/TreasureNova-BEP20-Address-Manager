package com.treasurenova.bep20manager.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.treasurenova.bep20manager.logic.AccountInput
import com.treasurenova.bep20manager.logic.AddressRow
import com.treasurenova.bep20manager.logic.BatchSummary
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class AccountStore(context: Context) {
    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context,
            "tn_accounts",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun list(): List<AccountInput> {
        val raw = prefs.getString("accounts", "[]") ?: "[]"
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    AccountInput(
                        id = obj.getString("id"),
                        username = obj.getString("username"),
                        password = obj.getString("password"),
                    )
                )
            }
        }
    }

    fun save(accounts: List<AccountInput>) {
        val array = JSONArray()
        accounts.forEach { account ->
            array.put(
                JSONObject()
                    .put("id", account.id)
                    .put("username", account.username)
                    .put("password", account.password)
            )
        }
        prefs.edit().putString("accounts", array.toString()).apply()
    }

    fun upsert(username: String, password: String, id: String = UUID.randomUUID().toString()): AccountInput {
        val next = list().filterNot { it.id == id || it.username == username } +
            AccountInput(id, username, password)
        save(next)
        return next.last()
    }

    fun delete(id: String) {
        save(list().filterNot { it.id == id })
    }

    fun clear() {
        prefs.edit().remove("accounts").apply()
    }
}

class HistoryStore(context: Context) {
    private val dir = context.filesDir.resolve("history").apply { mkdirs() }

    fun save(summary: BatchSummary) {
        val array = JSONArray()
        summary.rows.forEach { row ->
            array.put(
                JSONObject()
                    .put("username", row.username)
                    .put("bep20Address", row.bep20Address)
                    .put("status", row.status)
                    .put("error", row.error)
            )
        }
        val body = JSONObject()
            .put("id", summary.id)
            .put("startedAtEpochMs", summary.startedAtEpochMs)
            .put("rows", array)
        dir.resolve("${summary.id}.json").writeText(body.toString())
    }

    fun list(): List<BatchSummary> =
        dir.listFiles { file -> file.extension == "json" }
            .orEmpty()
            .sortedByDescending { it.lastModified() }
            .mapNotNull { file ->
                runCatching { parse(file.readText()) }.getOrNull()
            }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun parse(text: String): BatchSummary {
        val obj = JSONObject(text)
        val rowsJson = obj.getJSONArray("rows")
        val rows = buildList {
            for (i in 0 until rowsJson.length()) {
                val row = rowsJson.getJSONObject(i)
                require(!row.has("password"))
                add(
                    AddressRow(
                        username = row.getString("username"),
                        bep20Address = row.optString("bep20Address"),
                        status = row.getString("status"),
                        error = row.optString("error"),
                    )
                )
            }
        }
        return BatchSummary(obj.getString("id"), obj.getLong("startedAtEpochMs"), rows)
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("tn_settings", Context.MODE_PRIVATE)

    fun loginUrl(): String = prefs.getString("login_url", "https://treasurenova.net/") ?: "https://treasurenova.net/"

    fun setLoginUrl(url: String) {
        prefs.edit().putString("login_url", url.trim()).apply()
    }
}
