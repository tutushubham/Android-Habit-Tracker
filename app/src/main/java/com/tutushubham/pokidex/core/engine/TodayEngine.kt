package com.tutushubham.pokidex.core.engine

import com.tutushubham.pokidex.core.domain.entity.Session
import com.tutushubham.pokidex.core.domain.repository.AnchorRepository
import com.tutushubham.pokidex.core.domain.repository.IntentRepository
import com.tutushubham.pokidex.core.domain.repository.SessionRepository
import java.time.LocalDate

open class TodayEngine(
    private val intentRepository: IntentRepository,
    private val sessionRepository: SessionRepository,
    private val anchorRepository: AnchorRepository,
    private val focusResolver: FocusResolver
) {

    open suspend fun generate(date: LocalDate): TodayPlan {
        val anchors = anchorRepository.getAllAnchors()
        val existingSessions = sessionRepository.getSessionsForDate(date)
        val intents = intentRepository.getIntentsForDateRange(date, date)

        val plannedSessions = mutableListOf<Session>()

        for (anchor in anchors) {

            // 1. Skip if session already exists for this block
            val alreadyPlanned = existingSessions.any {
                it.block == anchor.block && it.domain == anchor.domain
            }
            if (alreadyPlanned) continue

            // 2. Resolve focus for this domain
            val focus = focusResolver.resolve(anchor.domain, date)

            // 3. Filter intents by domain (+ focus if present)
            val candidateIntents = intents
                .filter { it.domain == anchor.domain }
                .filter { intent ->
                    focus == null || intent.title.contains(
                        focus.name,
                        ignoreCase = true
                    )
                }

            // 4. Pick highest-priority intent
            val intent = candidateIntents.minByOrNull { it.priority }
                ?: continue

            // 5. Create planned session
            plannedSessions.add(
                Session.planned(
                    intent = intent,
                    date = date,
                    block = anchor.block,
                    minutes = anchor.defaultMinutes
                )
            )
        }

        return TodayPlan(plannedSessions)
    }
}

