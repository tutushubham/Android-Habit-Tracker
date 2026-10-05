package com.habitsheet.presentation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Audit guard. Screens may call a destructive view-model function only inside the confirmed flows listed
 * here; every listed file must also show [com.habitsheet.ui.DestructiveConfirmDialog]. A new call site fails
 * this test until it is reviewed, given a [DestructiveAction] text and added below.
 * Scans every Kotlin file under `ui/` (recursively); files are named by their path relative to `ui/`.
 */
class DestructiveCallSitesTest {
    private val uiDir = listOf(
        File("src/commonMain/kotlin/com/habitsheet/ui"),
        File("composeApp/src/commonMain/kotlin/com/habitsheet/ui"),
    ).first { it.isDirectory }

    private val destructive = Regex(
        """\b(viewModel|monthViewModel|manageHabitsViewModel|backupViewModel|settingsViewModel)\s*\.\s*""" +
            """(deleteDailyHabit|deleteWeeklyHabit|deleteCategory|deleteDayPlanById|deleteDayPlan|deleteWeeklyPlan|clearAllData|importBackup|disconnect)\b""",
    )
    private val bareReference = Regex("""::\s*(deleteDailyHabit|deleteWeeklyHabit|deleteCategory|deleteDayPlanById|deleteDayPlan|deleteWeeklyPlan|clearAllData|importBackup|disconnect)\b""")

    private val uiFiles: List<File> = uiDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun File.key(): String = relativeTo(uiDir).invariantSeparatorsPath

    /** file -> calls, each reviewed: it sits inside the `onConfirm` of a DestructiveConfirmDialog. */
    private val confirmed = mapOf(
        "DataBackupScreen.kt" to listOf("clearAllData", "importBackup"),
        "manage/ManageHabitsScreen.kt" to listOf("deleteDailyHabit", "deleteWeeklyHabit", "deleteCategory"),
        "PlanScreen.kt" to listOf("deleteWeeklyPlan", "deleteDayPlanById"),
        // Not data loss: unlinks the sheet and clears sync state; habits, plans and check-offs stay (see audit table).
        "SettingsScreen.kt" to listOf("disconnect"),
    )

    @Test
    fun everyDestructiveCallInTheUiIsReviewedAndConfirmed() {
        val found = mutableMapOf<String, MutableList<String>>()
        uiFiles.forEach { file ->
            val text = file.readText()
            val calls = destructive.findAll(text).map { it.groupValues[2] } + bareReference.findAll(text).map { it.groupValues[1] }
            calls.forEach { found.getOrPut(file.key()) { mutableListOf() }.add(it) }
        }
        val actual = found.mapValues { it.value.toSortedSet().toList() }
        assertEquals(confirmed.mapValues { it.value.sorted() }, actual.mapValues { it.value.sorted() })
    }

    @Test
    fun everyScreenWithDestructiveCallsUsesTheSharedConfirmationDialog() {
        confirmed.filterKeys { it != "SettingsScreen.kt" }.keys.forEach { name ->
            val text = File(uiDir, name).readText()
            assertTrue("DestructiveConfirmDialog(" in text, "$name must confirm through DestructiveConfirmDialog")
            assertTrue("DestructiveAction." in text, "$name must build a DestructiveAction")
        }
    }

    @Test
    fun noScreenBypassesTheDialogByCallingTheRepositoryDirectly() {
        uiFiles.forEach { file ->
            val text = file.readText()
            listOf("restoreFromSnapshot", "clearAllData()", "repository.delete").forEach { call ->
                // Data & Backup is the one screen allowed to call clearAllData(), inside its confirmation dialog.
                if (file.key() != "DataBackupScreen.kt" || call != "clearAllData()") {
                    assertTrue(call !in text, "${file.key()} calls $call")
                }
            }
        }
    }
}
