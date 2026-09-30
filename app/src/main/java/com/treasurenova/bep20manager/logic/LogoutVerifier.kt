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
        loggedOutUiVisible: Boolean,
    ): LogoutVerification {
        // A password field by itself is not proof of logout. Logged-out UI must be visible
        // and authenticated UI must be gone.
        val passwordFieldAlone = loginFormVisible && !loggedOutUiVisible
        val pageLoggedOut = logoutClicked &&
            loggedOutUiVisible &&
            !authenticatedUiVisible &&
            !passwordFieldAlone
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
