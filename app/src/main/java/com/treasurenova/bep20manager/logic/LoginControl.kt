package com.treasurenova.bep20manager.logic

data class LoginControlCandidate(
    val index: Int,
    val text: String,
    val tag: String,
    val type: String,
    val inLoginForm: Boolean,
    val submitControl: Boolean,
    val formAction: String = "",
)

object LoginControlSelector {
    val safeLabels = setOf("login", "log in", "sign in")

    fun isSafeLabel(text: String): Boolean = text.trim().lowercase() in safeLabels

    /**
     * Picks the login submit control. A type=submit control inside the login form wins when its
     * label is exactly Login, Log In, or Sign In. Other buttons are ignored.
     */
    fun select(candidates: List<LoginControlCandidate>): LoginControlCandidate? {
        val eligible = candidates.filter { candidate ->
            val tag = candidate.tag.trim().lowercase()
            val tagOk = tag == "button" || tag == "input"
            isSafeLabel(candidate.text) &&
                tagOk &&
                candidate.inLoginForm &&
                SiteOrigin.isCredentialActionAllowed(candidate.formAction)
        }
        return eligible.firstOrNull { it.submitControl } ?: eligible.firstOrNull()
    }
}
