package com.tutushubham.pokidex.core.engine

import com.tutushubham.pokidex.core.domain.entity.Session
import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DomainProfileCalculatorTest {

    private val date = LocalDate.of(2024, 1, 15)

    private fun session(
        domain: Domain,
        actualMinutes: Int,
        status: SessionStatus = SessionStatus.COMPLETED
    ) = Session(
        id = "s-${domain.name}-$actualMinutes",
        intentId = "i1",
        domain = domain,
        date = date.minusDays(1),
        block = DayBlock.MORNING,
        plannedMinutes = 30,
        actualMinutes = if (status == SessionStatus.COMPLETED) actualMinutes else null,
        status = status,
        skipReason = null,
        startedAt = null,
        endedAt = null
    )

    @Test
    fun `preferred duration is median not mean`() {
        val sessions = mapOf(
            Domain.STUDIES to listOf(
                session(Domain.STUDIES, 10),
                session(Domain.STUDIES, 20),
                session(Domain.STUDIES, 100)
            )
        )
        val result = DomainProfileCalculator.compute(sessions, date)
        // Median of [10, 20, 100] = 20 (not mean of 43)
        assertEquals(20, result[Domain.STUDIES]!!.preferredSessionDuration)
    }

    @Test
    fun `empty domain with only skipped sessions is excluded`() {
        val sessions = mapOf(
            Domain.FITNESS to listOf(
                session(Domain.FITNESS, 30, SessionStatus.SKIPPED)
            )
        )
        val result = DomainProfileCalculator.compute(sessions, date)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `multiple domains computed independently`() {
        val sessions = mapOf(
            Domain.STUDIES to listOf(
                session(Domain.STUDIES, 25),
                session(Domain.STUDIES, 35)
            ),
            Domain.FITNESS to listOf(
                session(Domain.FITNESS, 40),
                session(Domain.FITNESS, 60)
            )
        )
        val result = DomainProfileCalculator.compute(sessions, date)
        assertEquals(2, result.size)
        // Median of [25, 35] = (25+35)/2 = 30
        assertEquals(30, result[Domain.STUDIES]!!.preferredSessionDuration)
        // Median of [40, 60] = (40+60)/2 = 50
        assertEquals(50, result[Domain.FITNESS]!!.preferredSessionDuration)
    }
}
