package com.habitsheet.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Compose Multiplatform resources are not Android resources: the generated resource keeps a backslash that Android's
 * aapt would have consumed, so `Couldn\'t` is shown on screen with its backslash. Apostrophes and quotes need no
 * escaping in this XML. (Found by the Compose UI tests; the rendered text is asserted in `UiScenariosMore`.)
 */
class StringCatalogEscapesTest {
    private val strings = listOf(
        File("src/commonMain/composeResources/values/strings.xml"),
        File("composeApp/src/commonMain/composeResources/values/strings.xml"),
    ).first { it.isFile }

    @Test
    fun noCatalogStringContainsABackslashEscapeThatWouldBeShownLiterally() {
        val offending = strings.readLines().withIndex().filter { (_, line) ->
            // \n and \t are converted by the resource plugin; everything else would reach the screen as typed.
            Regex("""\\[^nt]""").containsMatchIn(line)
        }.map { (index, line) -> "line ${index + 1}: ${line.trim().take(90)}" }

        assertTrue(offending.isEmpty(), "Remove the backslashes (${offending.size} lines):\n" + offending.take(10).joinToString("\n"))
    }
}
