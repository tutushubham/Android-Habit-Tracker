package com.habitsheet.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SheetLinkTest {
    @Test fun acceptsSharedGoogleSheetsLink() {
        assertEquals(
            "https://docs.google.com/spreadsheets/d/1kgELxuHMHkjJQPNwB58Eb1RQF4Uyt9N7CwM13aFsYP0/edit",
            SheetLink.canonicalize("https://docs.google.com/spreadsheets/d/1kgELxuHMHkjJQPNwB58Eb1RQF4Uyt9N7CwM13aFsYP0/edit?usp=sharing"),
        )
    }

    @Test fun rejectsOtherHosts() {
        assertNull(SheetLink.canonicalize("https://example.com/spreadsheets/d/abc/edit"))
    }
}
