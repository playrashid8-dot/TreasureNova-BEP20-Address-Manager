package com.treasurenova.bep20manager.logic

data class AccountInput(
    val id: String,
    val username: String,
    val password: String,
)

data class AddressRow(
    val username: String,
    val bep20Address: String,
    val status: String,
    val error: String = "",
)

data class BatchSummary(
    val id: String,
    val startedAtEpochMs: Long,
    val rows: List<AddressRow>,
) {
    val total: Int get() = rows.size
    val successful: Int get() = rows.count { it.status == "Success" }
    val failed: Int get() = rows.count { it.status != "Success" }
}

data class PageSnapshot(
    val loginFieldsFound: Boolean = false,
    val loginSubmitted: Boolean = false,
    val loginError: String? = null,
    val twoFactorVisible: Boolean = false,
    val blockedMessage: String? = null,
    val bep20LabelPresent: Boolean = false,
    val networkText: String? = null,
    val bep20Value: String? = null,
    val loggedOut: Boolean = false,
)

sealed class AddressDecision {
    data class Ok(val address: String) : AddressDecision()
    data class Missing(val reason: String) : AddressDecision()
    data class NetworkMismatch(val network: String) : AddressDecision()
    data object NeedsTwoFactor : AddressDecision()
    data class Blocked(val message: String) : AddressDecision()
    data class LoginFailed(val message: String) : AddressDecision()
}
