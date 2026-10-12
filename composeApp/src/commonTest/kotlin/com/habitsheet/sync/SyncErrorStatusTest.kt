package com.habitsheet.sync

import com.habitsheet.presentation.UiText
import com.habitsheet.testing.English
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Every failure a person can meet maps to a short, catalog-backed status line (never raw technical text). */
class SyncErrorStatusTest {
    private val errors: List<SyncError> = listOf(
        SyncError.Offline(),
        SyncError.AuthExpired("technical detail that must not reach the screen"),
        SyncError.AccessDenied(),
        SyncError.NotFound(),
        SyncError.RateLimited(retryAfterSeconds = 30),
        SyncError.MalformedPlanTab(null, PlanTabProblem.MissingColumns),
        SyncError.MalformedPlanTab(7, PlanTabProblem.BadDate),
        SyncError.Unknown(status = 500),
        SyncError.Unknown(cause = IllegalStateException("boom")),
    )

    @Test
    fun everyErrorHasAReadableCatalogMessage() {
        errors.forEach { error ->
            val text = error.userMessage()
            assertFalse(text is UiText.Raw, "${error::class.simpleName} must use a catalog string")
            val rendered = English.render(text)
            assertTrue(rendered.isNotBlank())
            assertTrue(rendered.length <= 160, "keep the status line short: $rendered")
            assertFalse("technical detail" in rendered || "boom" in rendered || "Exception" in rendered)
        }
    }

    @Test
    fun onlyOfflineIsNotShownAsAnError() {
        assertEquals("Will sync when online.", English.render(SyncError.Offline().userMessage()))
    }
}
