package com.habitsheet.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Structure rules from P1-2 step 7: no oversized UI or data files, screens split into leaves that receive state and
 * lambdas (never a ViewModel), and a `@Preview` for the leaves of every split screen.
 */
class UiStructureTest {
    private val root = listOf(File("src/commonMain/kotlin/com/habitsheet"), File("composeApp/src/commonMain/kotlin/com/habitsheet"))
        .first { it.isDirectory }

    private fun kotlinFiles(dir: String) = File(root, dir).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun noUiOrDataFileIsLongerThan500Lines() {
        val tooLong = (kotlinFiles("ui") + kotlinFiles("data"))
            .map { it.relativeTo(root).invariantSeparatorsPath to it.readLines().size }
            .filter { it.second > 500 }
        assertEquals(emptyList(), tooLong)
    }

    /** Split screens: the entry file collects ViewModel state and hands the leaves plain values and lambdas. */
    private val splitScreens = mapOf("ui/month" to "MonthScreen.kt", "ui/manage" to "ManageHabitsScreen.kt")

    @Test
    fun onlyTheScreenEntryFileSeesTheViewModel() {
        splitScreens.forEach { (dir, entry) ->
            val files = kotlinFiles(dir)
            assertTrue(files.any { it.name == entry }, "$dir/$entry exists")
            assertTrue(files.size > 2, "$dir is split into leaf files")
            files.filter { it.name != entry }.forEach { leaf ->
                val code = leaf.readLines().filterNot { it.trimStart().startsWith("import ") || it.trimStart().startsWith("*") || it.trimStart().startsWith("//") }
                assertTrue(code.none { "ViewModel" in it }, "${leaf.relativeTo(root)} must not depend on a ViewModel")
            }
        }
    }

    @Test
    fun everyLeafFileHasAPreview() {
        splitScreens.forEach { (dir, entry) ->
            kotlinFiles(dir)
                .filter { it.name != entry && "@Composable" in it.readText() }
                .forEach { leaf -> assertTrue("@Preview" in leaf.readText(), "${leaf.relativeTo(root)} has a @Preview") }
        }
    }
}
