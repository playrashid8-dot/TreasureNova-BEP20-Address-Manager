package com.treasurenova.bep20manager.logic

object AddressExtractor {
    private val evm = Regex("^0x[a-fA-F0-9]{40}$")

    fun decide(snapshot: PageSnapshot): AddressDecision {
        val blocked = snapshot.blockedMessage?.trim().orEmpty()
        if (blocked.isNotEmpty()) return AddressDecision.Blocked(blocked)
        val loginError = snapshot.loginError?.trim().orEmpty()
        if (loginError.isNotEmpty()) return AddressDecision.LoginFailed(loginError)
        if (snapshot.twoFactorVisible) return AddressDecision.NeedsTwoFactor
        if (!snapshot.bep20LabelPresent) {
            return AddressDecision.Missing("USDT BEP20 deposit address label was not visible")
        }
        val network = snapshot.networkText?.trim().orEmpty()
        if (!isBep20Network(network)) {
            return AddressDecision.NetworkMismatch(network.ifEmpty { "unknown" })
        }
        val raw = snapshot.bep20Value?.trim().orEmpty()
        if (raw.isEmpty() || raw == "-" || raw == "null") {
            return AddressDecision.Missing("USDT BEP20 deposit address was empty")
        }
        if (!evm.matches(raw)) {
            return AddressDecision.Missing("Visible value is not a BEP20 address")
        }
        return AddressDecision.Ok(raw)
    }

    fun isBep20Network(network: String): Boolean {
        val n = network.lowercase()
        val bep = n.contains("bep-20") || n.contains("bep20") || n.contains("bnb") ||
            n.contains("smart chain") || n.contains("bsc")
        val trcOnly = (n.contains("trc-20") || n.contains("trc20") || n.contains("tron")) && !bep
        return bep && !trcOnly
    }

    fun toRow(username: String, decision: AddressDecision): AddressRow = when (decision) {
        is AddressDecision.Ok -> AddressRow(username, decision.address, "Success", "")
        is AddressDecision.Missing -> AddressRow(username, "", "Failed", decision.reason)
        is AddressDecision.NetworkMismatch ->
            AddressRow(username, "", "Failed", "Network mismatch: ${decision.network}")
        AddressDecision.NeedsTwoFactor ->
            AddressRow(username, "", "Paused", "2FA is showing. Finish it in the page, then continue.")
        is AddressDecision.Blocked ->
            AddressRow(username, "", "Blocked", decision.message)
        is AddressDecision.LoginFailed ->
            AddressRow(username, "", "Failed", decision.message)
    }
}
