package com.habitsheet.domain.backup

import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.domain.model.CompletionKey
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.HabitKind
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.SheetSyncChanges
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupFormatTest {
    private val aug5 = LocalDate(2026, 8, 5)

    // ---- SHA-256 (published test vectors) ----------------------------------------------------------

    @Test
    fun sha256MatchesKnownVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha256.hex(""))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.hex("abc"))
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Sha256.hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
        )
        // 1,000 'a' crosses several 64-byte blocks and the length-padding boundary.
        assertEquals("41edece42d63e8d9bf515a9ba6932e1c20cbc9f5a5d134645adb5db1b9737ea3", Sha256.hex("a".repeat(1000)))
    }

    // ---- every version restores --------------------------------------------------------------------

    @Test
    fun v1BackupStillRestoresWithDefaultsForFieldsItNeverHad() {
        val backup = BackupSerializer.parse(BackupFixtures.V1)
        assertEquals(1, backup.version)
        assertNull(backup.settings)
        val snapshot = backup.snapshot
        assertEquals("Fitness", snapshot.categories.single().name)
        val run = snapshot.dailyHabits.single()
        assertEquals(HabitKind.ACTION, run.kind)
        assertFalse(run.datedOnly)
        assertEquals("run|2026-08-05", snapshot.dailyCompletions.single().planId)
        assertEquals("Gym", snapshot.weeklyHabits.single().name)
        assertTrue(snapshot.weeklyPlans.isEmpty() && snapshot.dayPlans.isEmpty())
    }

    @Test
    fun v2BackupKeepsItsWeeklyPlans() {
        val snapshot = BackupSerializer.parse(BackupFixtures.V2).snapshot
        assertEquals("Intervals", snapshot.weeklyPlans.single().detail)
        assertTrue(snapshot.dayPlans.isEmpty())
    }

    @Test
    fun v3BackupKeepsDayPlansAndExplicitPlanIds() {
        val backup = BackupSerializer.parse(BackupFixtures.V3)
        assertEquals(3, backup.version)
        assertNull(backup.settings)
        val snapshot = backup.snapshot
        assertEquals("Easy 6 km", snapshot.dayPlans.single().detail)
        assertTrue(snapshot.dailyHabits.single().datedOnly)
        assertEquals("run|2026-08-05", snapshot.dailyCompletions.single().planId)
    }

    @Test
    fun v4BackupVerifiesItsChecksumAndCarriesSettings() {
        val backup = BackupSerializer.parse(BackupFixtures.V4)
        assertEquals(4, backup.version)
        assertEquals(BackupSerializer.parse(BackupFixtures.V3).snapshot, backup.snapshot)
        val settings = assertNotNull(backup.settings)
        assertEquals(2, settings.themeMode)
        assertEquals(true, settings.onboardingCompleted)
        assertEquals("https://docs.google.com/spreadsheets/d/FIXTURESHEETID/edit", settings.sheetUrl)
    }

    @Test
    fun v1AndV3DescribeTheSameDataWhereTheyOverlap() {
        val v1 = BackupSerializer.deserialize(BackupFixtures.V1)
        val v3 = BackupSerializer.deserialize(BackupFixtures.V3)
        assertEquals(v1.categories, v3.categories)
        assertEquals(v1.dailyCompletions, v3.dailyCompletions)
        assertEquals(v1.weeklyHabits, v3.weeklyHabits)
        assertEquals(v1.weeklyCompletions, v3.weeklyCompletions)
    }

    // ---- writing v4 --------------------------------------------------------------------------------

    @Test
    fun serializeWritesVersion4RoundTripsAndNeverWritesSyncState() {
        val snapshot = BackupSerializer.deserialize(BackupFixtures.V3).copy(
            sheetManagedHabitIds = setOf("run"),
            pendingCompletions = setOf(CompletionKey("run|2026-08-05", aug5)),
        )
        val json = BackupSerializer.serialize(snapshot, 77, BackupSettings(themeMode = 1, onboardingCompleted = false))

        val parsed = BackupSerializer.parse(json)
        assertEquals(4, parsed.version)
        assertEquals(77, parsed.timestamp)
        assertEquals(BackupSettings(themeMode = 1, onboardingCompleted = false), parsed.settings)
        assertEquals(snapshot.copy(sheetManagedHabitIds = emptySet(), pendingCompletions = emptySet()), parsed.snapshot)
        listOf("pending", "managed", "syncedKeys", "lastSync", "sheetUrl").forEach {
            assertFalse(it in json, "backup must not contain '$it'")
        }
    }

    @Test
    fun sheetLinkIsWrittenOnlyWhenGivenInSettings() {
        val without = BackupSerializer.serialize(HabitSnapshot(), 1, BackupSettings(themeMode = 0))
        assertFalse("docs.google.com" in without)
        val with = BackupSerializer.serialize(HabitSnapshot(), 1, BackupSettings(sheetUrl = "https://docs.google.com/spreadsheets/d/abc/edit"))
        assertEquals("https://docs.google.com/spreadsheets/d/abc/edit", BackupSerializer.parse(with).settings?.sheetUrl)
    }

    // ---- tampering and bad versions ----------------------------------------------------------------

    @Test
    fun changingAnyDataValueIsDetected() {
        val tampered = BackupFixtures.V4.replace("\"Run\"", "\"Rum\"")
        assertTrue(tampered != BackupFixtures.V4)
        val error = assertFailsWith<BackupValidationException> { BackupSerializer.parse(tampered) }
        assertTrue("changed or damaged" in error.message.orEmpty())
    }

    @Test
    fun changingSettingsIsDetectedToo() {
        val tampered = BackupFixtures.V4.replace("FIXTURESHEETID", "SOMEONEELSESSHEET")
        assertFailsWith<BackupValidationException> { BackupSerializer.parse(tampered) }
    }

    @Test
    fun dataSplicedUnderAnotherBackupsChecksumIsDetected() {
        val empty = BackupSerializer.serialize(HabitSnapshot(), 5)
        val full = BackupSerializer.serialize(BackupSerializer.deserialize(BackupFixtures.V3), 5)
        val checksumField = Regex("\"checksum\": \"[^\"]+\"")
        val spliced = empty.replace(checksumField, checksumField.find(full)!!.value)
        assertTrue(spliced != empty)
        assertFailsWith<BackupValidationException> { BackupSerializer.parse(spliced) }
    }

    @Test
    fun v4WithoutAChecksumIsRejected() {
        val stripped = BackupFixtures.V4.replace(Regex("\"checksum\": \"[^\"]+\",\\s*"), "")
        assertFalse("checksum" in stripped)
        val error = assertFailsWith<BackupValidationException> { BackupSerializer.parse(stripped) }
        assertTrue("checksum" in error.message.orEmpty())
    }

    @Test
    fun unknownVersionsAreRejectedBeforeAnythingElseIsRead() {
        listOf(0, 5, 99, -1).forEach { version ->
            val error = assertFailsWith<BackupValidationException>("version $version") {
                BackupSerializer.parse("""{"version": $version, "timestamp": 0, "data": {}}""")
            }
            assertTrue("Unsupported backup version: $version" in error.message.orEmpty())
        }
        assertFailsWith<BackupValidationException> { BackupSerializer.parse("""{"timestamp": 0, "data": {}}""") }
    }

    @Test
    fun invalidSettingsAreRejectedEvenWithAValidChecksum() {
        val badTheme = BackupSerializer.serialize(HabitSnapshot(), 1, BackupSettings(themeMode = 9))
        assertFailsWith<BackupValidationException> { BackupSerializer.parse(badTheme) }
        val badLink = BackupSerializer.serialize(HabitSnapshot(), 1, BackupSettings(sheetUrl = "https://evil.example/x"))
        assertFailsWith<BackupValidationException> { BackupSerializer.parse(badLink) }
    }

    // ---- restore clears sync state -----------------------------------------------------------------

    private suspend fun syncedRepository(): InMemoryHabitRepository {
        val repo = InMemoryHabitRepository(BackupSerializer.deserialize(BackupFixtures.V3))
        repo.setSheetUrl("https://docs.google.com/spreadsheets/d/current/edit")
        repo.applySheetSync(SheetSyncChanges(managedHabitIds = setOf("run")), setOf("run-1"), 999)
        repo.setDailyCompletion(DailyHabitCompletion("run", LocalDate(2026, 8, 6), true, 5))
        assertTrue(repo.snapshot.value.pendingCompletions.isNotEmpty())
        return repo
    }

    @Test
    fun restoreClearsSyncStateButKeepsTheCurrentSheetLink() = runTest {
        val repo = syncedRepository()

        val backup = BackupSerializer.parse(BackupFixtures.V3)
        repo.restoreFromSnapshot(backup.snapshot, backup.settings)

        assertEquals(emptySet(), repo.getSheetSyncedKeys())
        assertEquals(0L, repo.getSheetLastSync())
        assertTrue(repo.snapshot.value.sheetManagedHabitIds.isEmpty())
        assertTrue(repo.snapshot.value.pendingCompletions.isEmpty())
        assertEquals("https://docs.google.com/spreadsheets/d/current/edit", repo.getSheetUrl())
        assertEquals(backup.snapshot.dailyHabits, repo.snapshot.value.dailyHabits)
    }

    @Test
    fun v4RestoreAppliesThemeAndOnboardingButTheSheetLinkOnlyOnRequest() = runTest {
        val backup = BackupSerializer.parse(BackupFixtures.V4)

        val kept = syncedRepository()
        kept.restoreFromSnapshot(backup.snapshot, backup.settings)
        assertEquals(2, kept.getThemeMode())
        assertTrue(kept.isOnboardingCompleted())
        assertEquals("https://docs.google.com/spreadsheets/d/current/edit", kept.getSheetUrl())

        val replaced = syncedRepository()
        replaced.restoreFromSnapshot(backup.snapshot, backup.settings, restoreSheetLink = true)
        assertEquals("https://docs.google.com/spreadsheets/d/FIXTURESHEETID/edit", replaced.getSheetUrl())
        assertEquals(emptySet(), replaced.getSheetSyncedKeys())
    }

    @Test
    fun oldBackupsLeaveThemeAndOnboardingUntouched() = runTest {
        val repo = syncedRepository()
        repo.setThemeMode(1)
        repo.setOnboardingCompleted(true)

        repo.restoreFromSnapshot(BackupSerializer.deserialize(BackupFixtures.V2))

        assertEquals(1, repo.getThemeMode())
        assertTrue(repo.isOnboardingCompleted())
    }
}
