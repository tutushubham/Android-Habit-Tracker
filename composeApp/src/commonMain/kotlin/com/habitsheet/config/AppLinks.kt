package com.habitsheet.config

/**
 * Where the About screen sends people for legal and support matters.
 *
 * The real values are filled in during B3 (privacy policy hosting, support mailbox). A blank value is shown as
 * "not available yet" instead of a dead link, so an unfinished build is obviously unfinished.
 */
data class AppLinks(
    val privacyPolicyUrl: String = "",
    val supportEmail: String = "",
) {
    /** The privacy policy address, or null when it is not set or not an https link. */
    fun privacyPolicyOrNull(): String? = privacyPolicyUrl.trim().takeIf { it.startsWith(HTTPS) && it.length > HTTPS.length }

    /** A `mailto:` address for the support mailbox, or null when it is not set or does not look like an email. */
    fun supportMailtoOrNull(): String? {
        val address = supportEmail.trim()
        val at = address.indexOf('@')
        val valid = at > 0 && at < address.length - 1 && '.' in address.substring(at) && address.none { it.isWhitespace() }
        return if (valid) "mailto:$address" else null
    }

    companion object {
        private const val HTTPS = "https://"

        /** B3: replace these two values (see docs/production-plan/part-2-beyond-code/B3-legal-privacy-store-listing.md). */
        val Default = AppLinks(privacyPolicyUrl = "", supportEmail = "")
    }
}
