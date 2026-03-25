package com.tutushubham.pokidex.core.engine

import com.tutushubham.pokidex.core.domain.entity.Session
import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EstimateLearnerTest {

    @Test
    fun `zero completed units returns static estimate with null learned`() {
        val result = EstimateLearner.computeEstimate(
            staticMinutesPerUnit = 25,
            totalActualMinutes = 0,
            completedUnits = 0
        )
        assertEquals(25, result.effectiveMinutesPerUnit)
        assertNull(result.learnedMinutesPerUnit)
        assertEquals(0.0, result.confidence, 0.001)
        assertEquals(25, result.staticEstimate)
    }

    @Test
    fun `1 completed unit gives low confidence -- blends towards static`() {
        // 1 unit completed, 30 actual minutes, static = 25
        // confidence = 0.1 * 1 / 5.0 = 0.02
        // learned = 30.0 (clamped to [12.5, 62.5] -> 30.0)
        // blended = 25 * 0.98 + 30 * 0.02 = 24.5 + 0.6 = 25.1 -> rounds to 25
        val result = EstimateLearner.computeEstimate(
            staticMinutesPerUnit = 25,
            totalActualMinutes = 30,
            completedUnits = 1
        )
        assertEquals(0.02, result.confidence, 0.001)
        assertEquals(25, result.effectiveMinutesPerUnit)
        assertEquals(30.0, result.learnedMinutesPerUnit!!, 0.01)
    }

    @Test
    fun `5 completed units gives moderate confidence`() {
        // confidence = min(1.0, 5.0 / 20) = 0.25
        // learned = 150 / 5 = 30.0 (within [12.5, 62.5])
        // blended = 25 * 0.75 + 30 * 0.25 = 18.75 + 7.5 = 26.25 -> rounds to 26
        val result = EstimateLearner.computeEstimate(
            staticMinutesPerUnit = 25,
            totalActualMinutes = 150,
            completedUnits = 5
        )
        assertEquals(0.25, result.confidence, 0.001)
        assertEquals(26, result.effectiveMinutesPerUnit)
        assertEquals(30.0, result.learnedMinutesPerUnit!!, 0.01)
    }

    @Test
    fun `20 completed units gives full confidence -- returns learned`() {
        // confidence = min(1.0, 20.0 / 20) = 1.0
        // learned = 600 / 20 = 30.0
        // blended = 25 * 0.0 + 30 * 1.0 = 30.0
        val result = EstimateLearner.computeEstimate(
            staticMinutesPerUnit = 25,
            totalActualMinutes = 600,
            completedUnits = 20
        )
        assertEquals(1.0, result.confidence, 0.001)
        assertEquals(30, result.effectiveMinutesPerUnit)
        assertEquals(30.0, result.learnedMinutesPerUnit!!, 0.01)
    }

    @Test
    fun `learned lower than static -- effective decreases`() {
        // static = 25, learned = 400 / 20 = 20.0 (within [12.5, 62.5])
        // confidence = 1.0
        // blended = 25 * 0 + 20 * 1.0 = 20
        val result = EstimateLearner.computeEstimate(
            staticMinutesPerUnit = 25,
            totalActualMinutes = 400,
            completedUnits = 20
        )
        assertEquals(20, result.effectiveMinutesPerUnit)
        assertTrue(result.effectiveMinutesPerUnit < result.staticEstimate)
    }

    @Test
    fun `learned higher than static -- effective increases`() {
        // static = 25, learned = 800 / 20 = 40.0 (within [12.5, 62.5])
        // confidence = 1.0
        // blended = 25 * 0 + 40 * 1.0 = 40
        val result = EstimateLearner.computeEstimate(
            staticMinutesPerUnit = 25,
            totalActualMinutes = 800,
            completedUnits = 20
        )
        assertEquals(40, result.effectiveMinutesPerUnit)
        assertTrue(result.effectiveMinutesPerUnit > result.staticEstimate)
    }

    @Test
    fun `effective never goes below 1`() {
        val result = EstimateLearner.computeEstimate(
            staticMinutesPerUnit = 1,
            totalActualMinutes = 0,
            completedUnits = 0
        )
        assertTrue(result.effectiveMinutesPerUnit >= 1)
    }

    @Test
    fun `zero actual minutes returns static estimate`() {
        val result = EstimateLearner.computeEstimate(
            staticMinutesPerUnit = 30,
            totalActualMinutes = 0,
            completedUnits = 5
        )
        assertEquals(30, result.effectiveMinutesPerUnit)
        assertNull(result.learnedMinutesPerUnit)
        assertEquals(0.0, result.confidence, 0.001)
    }

    @Test
    fun `extreme outlier clamped to 2_5x static`() {
        // static = 25, raw learned = 300 / 1 = 300.0
        // clamped to 25 * 2.5 = 62.5
        // confidence = 0.1 * 1 / 5.0 = 0.02
        // blended = 25 * 0.98 + 62.5 * 0.02 = 24.5 + 1.25 = 25.75 -> rounds to 26
        val result = EstimateLearner.computeEstimate(
            staticMinutesPerUnit = 25,
            totalActualMinutes = 300,
            completedUnits = 1
        )
        assertEquals(62.5, result.learnedMinutesPerUnit!!, 0.01)
        assertEquals(26, result.effectiveMinutesPerUnit)
    }

    @Test
    fun `extremely fast session clamped to 0_5x static`() {
        // static = 25, raw learned = 5 / 1 = 5.0
        // clamped to 25 * 0.5 = 12.5
        // confidence = 0.1 * 1 / 5.0 = 0.02
        // blended = 25 * 0.98 + 12.5 * 0.02 = 24.5 + 0.25 = 24.75 -> rounds to 25
        val result = EstimateLearner.computeEstimate(
            staticMinutesPerUnit = 25,
            totalActualMinutes = 5,
            completedUnits = 1
        )
        assertEquals(12.5, result.learnedMinutesPerUnit!!, 0.01)
        assertEquals(25, result.effectiveMinutesPerUnit)
    }

    // --- computeRecentEstimate tests ---

    private val baseDate = LocalDate.of(2024, 1, 15)

    private fun completedSession(
        daysAgo: Int,
        actualMinutes: Int
    ) = Session(
        id = "s-$daysAgo",
        intentId = "i1",
        domain = Domain.STUDIES,
        date = baseDate.minusDays(daysAgo.toLong()),
        block = DayBlock.MORNING,
        plannedMinutes = 30,
        actualMinutes = actualMinutes,
        status = SessionStatus.COMPLETED,
        skipReason = null,
        startedAt = null,
        endedAt = null
    )

    @Test
    fun `computeRecentEstimate returns null for null static`() {
        val result = EstimateLearner.computeRecentEstimate(null, emptyList())
        assertNull(result)
    }

    @Test
    fun `computeRecentEstimate returns null for zero static`() {
        val result = EstimateLearner.computeRecentEstimate(0, emptyList())
        assertNull(result)
    }

    @Test
    fun `computeRecentEstimate with no completed sessions returns static fallback`() {
        val result = EstimateLearner.computeRecentEstimate(25, emptyList())
        assertNotNull(result)
        assertEquals(25, result!!.effectiveMinutesPerUnit)
        assertNull(result.learnedMinutesPerUnit)
        assertEquals(0.0, result.confidence, 0.001)
    }

    @Test
    fun `computeRecentEstimate with recent sessions applies harmonic weighting`() {
        val sessions = listOf(
            completedSession(1, 40),
            completedSession(2, 20),
            completedSession(3, 20)
        )
        val result = EstimateLearner.computeRecentEstimate(25, sessions)
        assertNotNull(result)
        assertNotNull(result!!.learnedMinutesPerUnit)
        // weight0 = 1.0 (40 min), weight1 = 0.5 (20 min), weight2 = 0.333 (20 min)
        // weighted = (40*1.0 + 20*0.5 + 20*0.333) / (1.0 + 0.5 + 0.333) = 56.66 / 1.833 = 30.91
        // clamped to [12.5, 62.5] -> 30.91
        assertTrue(result.learnedMinutesPerUnit!! > 25.0)
    }

    @Test
    fun `computeRecentEstimate clamps extreme learned value`() {
        val sessions = listOf(completedSession(1, 300))
        val result = EstimateLearner.computeRecentEstimate(25, sessions)
        assertNotNull(result)
        assertEquals(62.5, result!!.learnedMinutesPerUnit!!, 0.01)
    }
}
