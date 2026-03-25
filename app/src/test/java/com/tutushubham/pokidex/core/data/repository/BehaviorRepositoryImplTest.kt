package com.tutushubham.pokidex.core.data.repository

import com.tutushubham.pokidex.core.data.DomainBehaviorProfileEntity
import com.tutushubham.pokidex.core.data.UserIntentStatsEntity
import com.tutushubham.pokidex.core.data.local.db.dao.DomainBehaviorProfileDao
import com.tutushubham.pokidex.core.data.local.db.dao.UserIntentStatsDao
import com.tutushubham.pokidex.core.domain.entity.DomainBehaviorProfile
import com.tutushubham.pokidex.core.domain.entity.UserIntentStats
import com.tutushubham.pokidex.core.domain.model.Domain
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class BehaviorRepositoryImplTest {

    private val date = LocalDate.of(2024, 1, 15)

    @Test
    fun `save and reload UserIntentStats`() = runTest {
        val fakeStatsDao = FakeUserIntentStatsDao()
        val fakeProfileDao = FakeDomainBehaviorProfileDao()
        val repo = BehaviorRepositoryImpl(fakeStatsDao, fakeProfileDao)

        val stats = UserIntentStats("i1", 28.0, 0.7, date)
        repo.saveIntentStats(stats)

        val loaded = repo.getIntentStats("i1")
        assertNotNull(loaded)
        assertEquals("i1", loaded!!.intentId)
        assertEquals(28.0, loaded.learnedMinutesPerUnit!!, 0.001)
        assertEquals(0.7, loaded.confidence, 0.001)
        assertEquals(date, loaded.lastUpdated)
    }

    @Test
    fun `save and reload DomainBehaviorProfile`() = runTest {
        val fakeStatsDao = FakeUserIntentStatsDao()
        val fakeProfileDao = FakeDomainBehaviorProfileDao()
        val repo = BehaviorRepositoryImpl(fakeStatsDao, fakeProfileDao)

        val profile = DomainBehaviorProfile(Domain.STUDIES, 35, date)
        repo.saveDomainProfile(profile)

        val loaded = repo.getDomainProfile(Domain.STUDIES)
        assertNotNull(loaded)
        assertEquals(Domain.STUDIES, loaded!!.domain)
        assertEquals(35, loaded.preferredSessionDuration)
    }

    @Test
    fun `missing data returns null`() = runTest {
        val fakeStatsDao = FakeUserIntentStatsDao()
        val fakeProfileDao = FakeDomainBehaviorProfileDao()
        val repo = BehaviorRepositoryImpl(fakeStatsDao, fakeProfileDao)

        assertNull(repo.getIntentStats("nonexistent"))
        assertNull(repo.getDomainProfile(Domain.FITNESS))
    }
}

class FakeUserIntentStatsDao : UserIntentStatsDao {
    private val store = mutableMapOf<String, UserIntentStatsEntity>()

    override suspend fun getStats(intentId: String) = store[intentId]
    override suspend fun getAll() = store.values.toList()
    override suspend fun upsert(stats: UserIntentStatsEntity) {
        store[stats.intentId] = stats
    }
}

class FakeDomainBehaviorProfileDao : DomainBehaviorProfileDao {
    private val store = mutableMapOf<Domain, DomainBehaviorProfileEntity>()

    override suspend fun getProfile(domain: Domain) = store[domain]
    override suspend fun getAll() = store.values.toList()
    override suspend fun upsert(profile: DomainBehaviorProfileEntity) {
        store[profile.domain] = profile
    }
}
