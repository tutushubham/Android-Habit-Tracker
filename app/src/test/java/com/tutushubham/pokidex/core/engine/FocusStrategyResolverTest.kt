package com.tutushubham.pokidex.core.engine

import com.tutushubham.pokidex.core.domain.entity.DomainFocusConfig
import com.tutushubham.pokidex.core.domain.entity.Focus
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.FocusStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FocusStrategyResolverTest {

    private val domain = Domain.STUDIES

    @Test
    fun `manual strategy returns manual override focus`() {
        val fA = Focus("a", domain, "A")
        val fB = Focus("b", domain, "B")
        val config = DomainFocusConfig(
            domain = domain,
            strategy = FocusStrategy.Manual,
            manualOverrideFocusId = "b",
            createdAt = LocalDate.of(2024, 1, 1)
        )
        val result = FocusStrategyResolver.resolve(config, listOf(fA, fB), LocalDate.of(2024, 6, 1))
        assertEquals("b", result?.id)
    }

    @Test
    fun `rotation strategy cycles through focuses by day`() {
        val fA = Focus("a", domain, "A")
        val fB = Focus("b", domain, "B")
        val created = LocalDate.of(2024, 1, 1)
        val config = DomainFocusConfig(
            domain = domain,
            strategy = FocusStrategy.Rotation(order = listOf("a", "b")),
            manualOverrideFocusId = null,
            createdAt = created
        )
        assertEquals("a", FocusStrategyResolver.resolve(config, listOf(fA, fB), created)?.id)
        assertEquals(
            "b",
            FocusStrategyResolver.resolve(config, listOf(fA, fB), created.plusDays(1))?.id
        )
        assertEquals(
            "a",
            FocusStrategyResolver.resolve(config, listOf(fA, fB), created.plusDays(2))?.id
        )
    }

    @Test
    fun `weighted strategy distributes by weight`() {
        val f1 = Focus("f1", domain, "One")
        val f2 = Focus("f2", domain, "Two")
        val created = LocalDate.of(2024, 1, 1)
        val config = DomainFocusConfig(
            domain = domain,
            strategy = FocusStrategy.Weighted(weights = mapOf("f1" to 1, "f2" to 2)),
            manualOverrideFocusId = null,
            createdAt = created
        )
        // Expanded order: f1, f2, f2
        assertEquals("f1", FocusStrategyResolver.resolve(config, listOf(f1, f2), created)?.id)
        assertEquals("f2", FocusStrategyResolver.resolve(config, listOf(f1, f2), created.plusDays(1))?.id)
        assertEquals("f2", FocusStrategyResolver.resolve(config, listOf(f1, f2), created.plusDays(2))?.id)
        assertEquals("f1", FocusStrategyResolver.resolve(config, listOf(f1, f2), created.plusDays(3))?.id)
    }

    @Test
    fun `deadline strategy returns closest deadline first`() {
        val base = LocalDate.of(2024, 6, 1)
        val far = Focus("far", domain, "Far", deadline = base.plusDays(30))
        val soon = Focus("soon", domain, "Soon", deadline = base.plusDays(5))
        val later = Focus("later", domain, "Later", deadline = base.plusDays(15))
        val config = DomainFocusConfig(
            domain = domain,
            strategy = FocusStrategy.DeadlineDriven,
            manualOverrideFocusId = null,
            createdAt = base
        )
        val result = FocusStrategyResolver.resolve(config, listOf(far, later, soon), base)
        assertEquals("soon", result?.id)
    }

    @Test
    fun `empty focus list returns null`() {
        val config = DomainFocusConfig(
            domain = domain,
            strategy = FocusStrategy.Manual,
            manualOverrideFocusId = null,
            createdAt = LocalDate.now()
        )
        assertNull(FocusStrategyResolver.resolve(config, emptyList(), LocalDate.now()))
    }
}
