package com.tutushubham.pokidex.core.domain.entity

import com.tutushubham.pokidex.core.domain.model.Domain
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FocusTest {

    @Test
    fun `isDeadlineActive returns true when deadline is today`() {
        // Given
        val today = LocalDate.of(2024, 6, 15)
        val focus = Focus(
            id = "focus-1",
            domain = Domain.FITNESS,
            name = "Running",
            weight = 1,
            deadline = today
        )

        // When
        val isActive = focus.isDeadlineActive(today)

        // Then
        assertTrue(isActive)
    }

    @Test
    fun `isDeadlineActive returns true when deadline is in future`() {
        // Given
        val today = LocalDate.of(2024, 6, 15)
        val future = LocalDate.of(2024, 12, 31)
        val focus = Focus(
            id = "focus-1",
            domain = Domain.FITNESS,
            name = "Running",
            weight = 1,
            deadline = future
        )

        // When
        val isActive = focus.isDeadlineActive(today)

        // Then
        assertTrue(isActive)
    }

    @Test
    fun `isDeadlineActive returns false when deadline is in past`() {
        // Given
        val today = LocalDate.of(2024, 6, 15)
        val past = LocalDate.of(2024, 1, 1)
        val focus = Focus(
            id = "focus-1",
            domain = Domain.FITNESS,
            name = "Running",
            weight = 1,
            deadline = past
        )

        // When
        val isActive = focus.isDeadlineActive(today)

        // Then
        assertFalse(isActive)
    }

    @Test
    fun `isDeadlineActive returns false when deadline is null`() {
        // Given
        val today = LocalDate.of(2024, 6, 15)
        val focus = Focus(
            id = "focus-1",
            domain = Domain.FITNESS,
            name = "Running",
            weight = 1,
            deadline = null
        )

        // When
        val isActive = focus.isDeadlineActive(today)

        // Then
        assertFalse(isActive)
    }
}
