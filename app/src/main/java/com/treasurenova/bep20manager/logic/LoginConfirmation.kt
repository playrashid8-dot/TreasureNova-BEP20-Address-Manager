package com.treasurenova.bep20manager.logic

object LoginConfirmation {
    /** Login is confirmed only when the submit was accepted and authenticated UI is actually showing. */
    fun confirmed(snapshot: PageSnapshot): Boolean {
        if (!snapshot.loginSubmitted) return false
        if (!snapshot.authenticatedUiVisible) return false
        if (snapshot.twoFactorVisible) return false
        if (!snapshot.loginError.isNullOrBlank()) return false
        if (!snapshot.blockedMessage.isNullOrBlank()) return false
        return true
    }

    /**
     * Identity must be a non-empty visible account label that matches [username].
     * An empty or missing label is not a match.
     */
    fun identityMatches(username: String, identity: String?): Boolean {
        val want = username.trim()
        if (want.isEmpty()) return false
        val got = identity?.trim().orEmpty()
        if (got.isEmpty()) return false
        if (got.equals(want, ignoreCase = true)) return true
        if (got.length > 80) return false
        val pattern = Regex("(?<![A-Za-z0-9])${Regex.escape(want)}(?![A-Za-z0-9])", RegexOption.IGNORE_CASE)
        return pattern.containsMatchIn(got)
    }
}
