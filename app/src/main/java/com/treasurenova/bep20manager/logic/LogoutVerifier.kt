package com.treasurenova.bep20manager.logic

data class LogoutVerification(
    val pageLoggedOut: Boolean,
    val sessionCleared: Boolean,
) {
    /** Verified only when the page shows logged-out AND the cookie/session reset was confirmed. */
    val loggedOut: Boolean get() = pageLoggedOut && sessionCleared
    val confirmed: Boolean get() = loggedOut
}

object LogoutVerifier {
    fun verify(
        logoutClicked: Boolean,
        loginFormVisible: Boolean,
        authenticatedUiVisible: Boolean,
        cookiesCleared: Boolean,
    ): LogoutVerification {
        val pageLoggedOut = logoutClicked && loginFormVisible && !authenticatedUiVisible
        return LogoutVerification(
            pageLoggedOut = pageLoggedOut,
            sessionCleared = cookiesCleared,
        )
    }
}

object SessionIsolation {
    const val NOT_CONFIRMED = "Logout or session reset was not confirmed. Batch stopped."

    fun confirmed(loggedOut: Boolean, sessionCleared: Boolean): Boolean = loggedOut && sessionCleared

    fun apply(row: AddressRow, loggedOut: Boolean, sessionCleared: Boolean): AddressRow {
        if (confirmed(loggedOut, sessionCleared)) return row
        val error = if (row.error.isBlank()) NOT_CONFIRMED else "${row.error}; $NOT_CONFIRMED"
        val status = if (row.status == "Blocked" || row.status == "Stopped") row.status else "Failed"
        return row.copy(status = status, error = error, haltBatch = true)
    }
}
