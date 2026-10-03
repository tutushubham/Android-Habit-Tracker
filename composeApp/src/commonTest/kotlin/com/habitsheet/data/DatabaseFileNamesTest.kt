package com.habitsheet.data

import kotlin.test.Test
import kotlin.test.assertEquals

class DatabaseFileNamesTest {
    private val db = "habit-sheet.db"

    @Test
    fun aDatabaseIsTheMainFilePlusItsCompanions() {
        assertEquals(listOf(db, "$db-wal", "$db-shm", "$db-journal"), DatabaseFileNames.filesOf(db))
    }

    @Test
    fun asideNamesKeepTheOriginalNameAndAddTheTimestamp() {
        assertEquals("$db-wal.damaged-1700", DatabaseFileNames.asideName("$db-wal", 1700))
    }

    @Test
    fun onlyTheNewestSetsAreKeptAndOnlyAsideFilesAreEverListedForDeletion() {
        val files = listOf(
            db, "$db-wal", "unrelated.txt", "other.db.damaged-5", // never touched
            "$db.damaged-100", "$db-wal.damaged-100",
            "$db.damaged-200",
            "$db.damaged-300", "$db-journal.damaged-300",
            "$db.damaged-400",
        )
        assertEquals(listOf(400L, 300L, 200L, 100L), DatabaseFileNames.asideSets(files, db))
        assertEquals(listOf("$db.damaged-100", "$db-wal.damaged-100"), DatabaseFileNames.staleAsideNames(files, db, keep = 3))
        assertEquals(emptyList(), DatabaseFileNames.staleAsideNames(files, db, keep = 4))
    }

    @Test
    fun malformedSuffixesAreIgnored() {
        val files = listOf("$db.damaged-", "$db.damaged-abc", "$db.damaged-12x", "$db.damaged-7")
        assertEquals(listOf(7L), DatabaseFileNames.asideSets(files, db))
        assertEquals(emptyList(), DatabaseFileNames.staleAsideNames(files, db, keep = 1))
    }
}
