package com.habitsheet.presentation.onboarding

import com.habitsheet.domain.plan.PlanLabel
import com.habitsheet.domain.plan.PlanText
import com.habitsheet.testing.English
import com.habitsheet.testing.EnglishCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlanTextResourcesTest {
    @Test
    fun everyPlanTextHasWordingWithExactlyItsNumberOfArguments() {
        for (text in PlanText.entries) {
            val pattern = EnglishCatalog.strings.getValue(text.resource().key)
            val placeholders = Regex("%(\\d)\\\$" + "d").findAll(pattern).map { it.groupValues[1].toInt() }.toSet()
            assertEquals((1..text.argCount).toSet(), placeholders, "$text: $pattern")
            assertTrue(Regex("%\\d\\\$" + "s").find(pattern) == null, "$text takes numbers only: $pattern")
        }
    }

    @Test
    fun aLabelRendersWithItsNumbers() {
        assertEquals("Long run 18 km", English.render(PlanLabel(PlanText.RUN_LONG_KM, 18).toUiText()))
        assertEquals("Race day: marathon", English.render(PlanLabel(PlanText.RACE_MARATHON).toUiText()))
    }
}
