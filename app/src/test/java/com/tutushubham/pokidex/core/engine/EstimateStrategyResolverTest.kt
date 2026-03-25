package com.tutushubham.pokidex.core.engine

import com.tutushubham.pokidex.core.domain.entity.DomainBehaviorProfile
import com.tutushubham.pokidex.core.domain.entity.UserIntentStats
import com.tutushubham.pokidex.core.domain.model.Domain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class EstimateStrategyResolverTest {

    private val date = LocalDate.of(2024, 1, 15)

    @Test
    fun `high confidence fresh estimate is used directly`() {
        val fresh = LearnedEstimate(
            effectiveMinutesPerUnit = 28,
            learnedMinutesPerUnit = 30.0,
            confidence = 0.5,
            staticEstimate = 25
        )
        val result = EstimateStrategyResolver.resolve(
            staticMinutesPerUnit = 25,
            freshEstimate = fresh,
            persistedStats = null,
            domainProfile = null,
            daysSinceLastUpdate = 0
        )
        assertNotNull(result)
        assertEquals(fresh, result)
    }

    @Test
    fun `low confidence with domain profile blends using confidence weight`() {
        val fresh = LearnedEstimate(
            effectiveMinutesPerUnit = 25,
            learnedMinutesPerUnit = 26.0,
            confidence = 0.1,
            staticEstimate = 25
        )
        val domainProfile = DomainBehaviorProfile(Domain.STUDIES, 40, date)

        val result = EstimateStrategyResolver.resolve(
            staticMinutesPerUnit = 25,
            freshEstimate = fresh,
            persistedStats = null,
            domainProfile = domainProfile,
            daysSinceLastUpdate = 0
        )
        assertNotNull(result)
        // effective = 25 * 0.1 + 40 * 0.9 = 2.5 + 36 = 38.5 -> 39
        assertEquals(39, result!!.effectiveMinutesPerUnit)
        assertEquals(0.1, result.confidence, 0.001)
    }

    @Test
    fun `cold start with no fresh no persisted uses domain fallback`() {
        val domainProfile = DomainBehaviorProfile(Domain.STUDIES, 35, date)

        val result = EstimateStrategyResolver.resolve(
            staticMinutesPerUnit = 25,
            freshEstimate = null,
            persistedStats = null,
            domainProfile = domainProfile,
            daysSinceLastUpdate = 0
        )
        assertNotNull(result)
        assertEquals(35, result!!.effectiveMinutesPerUnit)
        assertEquals(0.0, result.confidence, 0.001)
    }

    @Test
    fun `null static with domain profile returns domain fallback`() {
        val domainProfile = DomainBehaviorProfile(Domain.FITNESS, 45, date)

        val result = EstimateStrategyResolver.resolve(
            staticMinutesPerUnit = null,
            freshEstimate = null,
            persistedStats = null,
            domainProfile = domainProfile,
            daysSinceLastUpdate = 0
        )
        assertNotNull(result)
        assertEquals(45, result!!.effectiveMinutesPerUnit)
    }

    @Test
    fun `persisted with fresh delegates to BehaviorMerger`() {
        val fresh = LearnedEstimate(
            effectiveMinutesPerUnit = 25,
            learnedMinutesPerUnit = null,
            confidence = 0.0,
            staticEstimate = 25
        )
        val persisted = UserIntentStats(
            intentId = "i1",
            learnedMinutesPerUnit = 30.0,
            confidence = 0.8,
            lastUpdated = date.minusDays(10)
        )

        val result = EstimateStrategyResolver.resolve(
            staticMinutesPerUnit = 25,
            freshEstimate = fresh,
            persistedStats = persisted,
            domainProfile = null,
            daysSinceLastUpdate = 10
        )
        assertNotNull(result)
        // BehaviorMerger handles the blend -- just verify we get a result back
        assertEquals(25, result!!.staticEstimate)
    }

    @Test
    fun `stale persisted over 30 days is ignored -- falls through to static`() {
        val fresh = LearnedEstimate(
            effectiveMinutesPerUnit = 25,
            learnedMinutesPerUnit = null,
            confidence = 0.0,
            staticEstimate = 25
        )
        val persisted = UserIntentStats(
            intentId = "i1",
            learnedMinutesPerUnit = 30.0,
            confidence = 0.8,
            lastUpdated = date.minusDays(35)
        )

        val result = EstimateStrategyResolver.resolve(
            staticMinutesPerUnit = 25,
            freshEstimate = fresh,
            persistedStats = persisted,
            domainProfile = null,
            daysSinceLastUpdate = 35
        )
        assertNotNull(result)
        assertEquals(25, result!!.effectiveMinutesPerUnit)
        assertNull(result.learnedMinutesPerUnit)
        assertEquals(0.0, result.confidence, 0.001)
    }
}
