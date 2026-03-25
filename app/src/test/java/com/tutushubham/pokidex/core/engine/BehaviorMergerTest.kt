package com.tutushubham.pokidex.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BehaviorMergerTest {

    private val staticEstimate = 25

    private fun freshEstimate(
        learned: Double? = 28.0,
        confidence: Double = 0.5
    ) = LearnedEstimate(
        effectiveMinutesPerUnit = 27,
        learnedMinutesPerUnit = learned,
        confidence = confidence,
        staticEstimate = staticEstimate
    )

    @Test
    fun `fresh and persisted blends to hybrid result`() {
        val result = BehaviorMerger.merge(
            freshEstimate = freshEstimate(),
            persistedLearned = 30.0,
            persistedConfidence = 0.8,
            daysSinceLastUpdate = 5,
            staticMinutesPerUnit = staticEstimate
        )
        assertNotNull(result)
        assertNotNull(result.learnedMinutesPerUnit)
        // persistedWeight = 1.0 - 5/30.0 = 0.833
        // blendedLearned = 30 * 0.833 * 0.5 + 28 * 0.5 = 12.5 + 14 = 26.5
        // clamped to [12.5, 62.5] -> 26.5
        assertEquals(26.5, result.learnedMinutesPerUnit!!, 0.5)
    }

    @Test
    fun `null persisted learned returns fresh as-is`() {
        val fresh = freshEstimate()
        val result = BehaviorMerger.merge(
            freshEstimate = fresh,
            persistedLearned = null,
            persistedConfidence = 0.8,
            daysSinceLastUpdate = 5,
            staticMinutesPerUnit = staticEstimate
        )
        assertEquals(fresh, result)
    }

    @Test
    fun `persisted over 30 days has zero weight -- returns fresh`() {
        val fresh = freshEstimate()
        val result = BehaviorMerger.merge(
            freshEstimate = fresh,
            persistedLearned = 30.0,
            persistedConfidence = 0.8,
            daysSinceLastUpdate = 31,
            staticMinutesPerUnit = staticEstimate
        )
        assertEquals(fresh, result)
    }

    @Test
    fun `persisted at 15 days has 50 percent weight`() {
        val result = BehaviorMerger.merge(
            freshEstimate = freshEstimate(learned = 28.0, confidence = 0.5),
            persistedLearned = 32.0,
            persistedConfidence = 0.7,
            daysSinceLastUpdate = 15,
            staticMinutesPerUnit = staticEstimate
        )
        assertNotNull(result)
        // persistedWeight = 1.0 - 15/30 = 0.5
        // blendedLearned = 32 * 0.5 * 0.5 + 28 * 0.5 = 8 + 14 = 22
        // clamped to [12.5, 62.5] -> 22
        assertEquals(22.0, result.learnedMinutesPerUnit!!, 0.5)
    }
}
