package com.habitsheet.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipInputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatabaseFilesTest {
    private val directory: File = Files.createTempDirectory("habit-sheet-dbfiles").toFile()
    private val files = DatabaseFiles(directory, "habit-sheet.db")

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    private fun write(name: String, content: String) = File(directory, name).also { it.writeText(content) }

    @Test
    fun existingListsTheMainFileAndOnlyTheCompanionsThatArePresent() {
        assertTrue(files.existing().isEmpty())
        write("habit-sheet.db", "main")
        write("habit-sheet.db-wal", "wal")
        write("unrelated.db", "x")
        assertEquals(listOf("habit-sheet.db", "habit-sheet.db-wal"), files.existing().map { it.name })
    }

    @Test
    fun zipContainsEveryFileUnderItsOwnNameWithItsExactBytes() {
        File(directory, "habit-sheet.db").writeBytes(byteArrayOf(0, 1, 2, 3, -1, -2))
        write("habit-sheet.db-wal", "wal content")
        write("other.txt", "not part of it")

        val out = ByteArrayOutputStream()
        assertEquals(2, files.writeZip(out))

        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entries[it.name] = zip.readBytes() }
        }
        assertEquals(setOf("habit-sheet.db", "habit-sheet.db-wal"), entries.keys)
        assertContentEquals(byteArrayOf(0, 1, 2, 3, -1, -2), entries.getValue("habit-sheet.db"))
        assertEquals("wal content", entries.getValue("habit-sheet.db-wal").decodeToString())
    }

    @Test
    fun setAsideRenamesEverythingAndDeletesNothing() {
        val main = File(directory, "habit-sheet.db").apply { writeBytes(byteArrayOf(9, 8, 7)) }
        write("habit-sheet.db-journal", "journal")

        assertEquals(2, files.setAside(1_000))

        assertFalse(main.exists())
        assertTrue(files.existing().isEmpty())
        assertContentEquals(byteArrayOf(9, 8, 7), File(directory, "habit-sheet.db.damaged-1000").readBytes())
        assertEquals("journal", File(directory, "habit-sheet.db-journal.damaged-1000").readText())
        assertEquals(listOf(1_000L), files.asideSets())
    }

    @Test
    fun settingAsideWithNothingThereMovesNothing() {
        assertEquals(0, files.setAside(5))
        assertTrue(files.asideSets().isEmpty())
    }

    @Test
    fun onlyTheNewestThreeSetsAreKeptAndUnrelatedFilesSurvive() {
        val unrelated = write("notes.txt", "keep me")
        for (timestamp in listOf(100L, 200L, 300L, 400L)) {
            write("habit-sheet.db", "data $timestamp")
            files.setAside(timestamp)
        }
        assertEquals(listOf(400L, 300L, 200L), files.asideSets())
        assertFalse(File(directory, "habit-sheet.db.damaged-100").exists())
        assertEquals("data 200", File(directory, "habit-sheet.db.damaged-200").readText())
        assertTrue(unrelated.exists())
    }

    @Test
    fun aFailedRenameRestoresWhatWasAlreadyMovedAndReportsTheFailure() {
        val main = write("habit-sheet.db", "main")
        write("habit-sheet.db-wal", "wal")
        // The second file cannot be moved: its target name is taken.
        write("habit-sheet.db-wal.damaged-77", "in the way")

        assertFailsWith<java.io.IOException> { files.setAside(77) }

        assertTrue(main.exists(), "the main file must be back where it was")
        assertEquals("main", main.readText())
        assertEquals("wal", File(directory, "habit-sheet.db-wal").readText())
        assertFalse(File(directory, "habit-sheet.db.damaged-77").exists())
    }
}
