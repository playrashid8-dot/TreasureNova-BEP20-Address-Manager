package com.treasurenova.bep20manager.logic

object Safety {
    val forbiddenClickLabels = listOf(
        "withdraw",
        "withdrawal",
        "transfer",
        "trade",
        "swap",
        "sell",
        "pay 30",
        "pay ",
        "complete verification",
        "complete",
    )

    val allowedProcessingActions = listOf(
        "Login",
        "Wallet",
        "USDT",
        "BEP20",
        "Address",
        "Logout",
    )

    fun allowClick(label: String): Boolean {
        val value = label.trim().lowercase()
        if (value.isEmpty()) return false
        if (value == "confirm" || value == "login" || value == "log in" || value == "sign in" ||
            value == "usdt" || value == "wallet" || value == "deposit" ||
            value == "recharge" || value == "bep20" || value == "bep-20" ||
            value == "bnb smart chain" || value == "bsc" || value == "log out" ||
            value == "logout" || value == "sign out"
        ) {
            return forbiddenClickLabels.none { value.contains(it) }
        }
        return false
    }

    fun jsString(raw: String): String {
        val sb = StringBuilder("\"")
        for (ch in raw) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '<' -> sb.append("\\u003c")
                else -> sb.append(ch)
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
