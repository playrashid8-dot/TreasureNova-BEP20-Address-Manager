package com.treasurenova.bep20manager.logic

object AddressExtractor {
    fun decide(snapshot: PageSnapshot): AddressDecision {
        val blocked = snapshot.blockedMessage?.trim().orEmpty()
        if (blocked.isNotEmpty()) return AddressDecision.Blocked(blocked)
        val loginError = snapshot.loginError?.trim().orEmpty()
        if (loginError.isNotEmpty()) return AddressDecision.LoginFailed(loginError)
        if (snapshot.twoFactorVisible) return AddressDecision.NeedsTwoFactor
        if (snapshot.depositBlocks.isNotEmpty()) {
            return UsdtBep20Matcher.fromBlocks(snapshot.depositBlocks)
        }
        val single = snapshot.depositBlockText?.trim().orEmpty()
        if (single.isNotEmpty()) return UsdtBep20Matcher.classify(single)
        return AddressDecision.Missing("USDT BEP20 deposit address label was not visible")
    }

    fun isBep20Network(network: String): Boolean {
        val n = network.lowercase()
        if (n.contains("trc-20") || n.contains("trc20") || n.contains("tron")) return false
        return n.contains("bep-20") || n.contains("bep20") || n.contains("bnb smart chain")
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
