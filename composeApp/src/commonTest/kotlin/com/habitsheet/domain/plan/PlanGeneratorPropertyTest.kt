package com.habitsheet.domain.plan

import com.habitsheet.testing.PROPERTY_RUNS
import com.habitsheet.testing.plusDays
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/** Every template keeps `PlanInvariants` for every valid answer, and is deterministic. */
class PlanGeneratorPropertyTest {
    private val templates = PlanTemplateId.entries - PlanTemplateId.SCRATCH

    private fun randomQuestions(random: Random, template: PlanTemplateId, today: LocalDate): PlanQuestions {
        val start = today.plusDays(random.nextInt(0, 14))
        val minDays = if (template == PlanTemplateId.INTERVIEW) 7 else 28
        val maxEvent = today.plusDays(PLAN_HORIZON_DAYS)
        val eventSpan = (maxEvent.toEpochDays() - start.toEpochDays()).toInt() - minDays
        val event = when {
            template == PlanTemplateId.INTERVIEW && random.nextBoolean() -> null
            template == PlanTemplateId.GENERAL_FITNESS -> null
            else -> start.plusDays(minDays + random.nextInt(0, eventSpan + 1))
        }
        return PlanQuestions(
            startDate = start,
            eventDate = event,
            level = PlanLevel.entries.random(random),
            daysPerWeek = random.nextInt(PlanQuestions.MIN_DAYS_PER_WEEK, PlanQuestions.MAX_DAYS_PER_WEEK + 1),
            restDay = if (random.nextInt(4) == 0) null else DayOfWeek.entries.random(random),
        )
    }

    @Test
    fun everyTemplateKeepsTheInvariantsForEveryValidAnswer() {
        repeat(PROPERTY_RUNS) { seed ->
            val random = Random(70_000 + seed)
            val today = LocalDate(2026, 1, 1).plusDays(random.nextInt(0, 365))
            for (template in templates) {
                val questions = randomQuestions(random, template, today)
                val result = TemplatePlanSource.generate(template, questions, today)
                val draft = (result as? PlanResult.Ok)?.draft ?: fail("seed $seed $template $questions: $result")
                assertEquals(emptyList(), PlanInvariants.violations(draft, questions, today), "seed $seed $template $questions")
                assertTrue(draft.sessions.isNotEmpty(), "seed $seed $template has sessions")
                assertEquals(result, TemplatePlanSource.generate(template, questions, today), "seed $seed $template is deterministic")
            }
        }
    }

    @Test
    fun raceAndInterviewPlansEndWithTheEventItself() {
        repeat(PROPERTY_RUNS) { seed ->
            val random = Random(80_000 + seed)
            val today = LocalDate(2026, 10, 12)
            for (template in listOf(PlanTemplateId.MARATHON, PlanTemplateId.HYROX, PlanTemplateId.INTERVIEW)) {
                val questions = randomQuestions(random, template, today)
                val event = questions.eventDate ?: continue
                val draft = assertIs<PlanResult.Ok>(TemplatePlanSource.generate(template, questions, today)).draft
                assertEquals(event, draft.sessions.maxOf { it.date }, "seed $seed $template")
                assertEquals(1, draft.sessions.count { it.date == event }, "seed $seed $template")
            }
        }
    }
}
