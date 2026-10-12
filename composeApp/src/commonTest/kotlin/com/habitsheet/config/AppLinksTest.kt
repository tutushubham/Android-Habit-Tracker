package com.habitsheet.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppLinksTest {
    @Test
    fun blankValuesAreUnavailable() {
        val links = AppLinks()
        assertNull(links.privacyPolicyOrNull())
        assertNull(links.supportMailtoOrNull())
    }

    @Test
    fun onlyHttpsPolicyLinksAreOffered() {
        assertNull(AppLinks(privacyPolicyUrl = "http://example.com/privacy").privacyPolicyOrNull())
        assertNull(AppLinks(privacyPolicyUrl = "https://").privacyPolicyOrNull())
        assertEquals("https://example.com/privacy", AppLinks(privacyPolicyUrl = " https://example.com/privacy ").privacyPolicyOrNull())
    }

    @Test
    fun supportEmailBecomesAMailtoLinkOnlyWhenItLooksLikeAnAddress() {
        assertEquals("mailto:help@example.com", AppLinks(supportEmail = "help@example.com").supportMailtoOrNull())
        assertNull(AppLinks(supportEmail = "help@example").supportMailtoOrNull())
        assertNull(AppLinks(supportEmail = "not an email@example.com").supportMailtoOrNull())
        assertNull(AppLinks(supportEmail = "@example.com").supportMailtoOrNull())
    }

    @Test
    fun noticesAreCompleteAndLinkToHttps() {
        assertTrue(openSourceNotices.isNotEmpty())
        assertEquals(openSourceNotices.size, openSourceNotices.map { it.name }.toSet().size)
        openSourceNotices.forEach {
            assertTrue(it.license.isNotBlank() && it.url.startsWith("https://"), it.name)
        }
    }
}
