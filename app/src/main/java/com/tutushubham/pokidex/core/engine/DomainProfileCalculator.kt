package com.tutushubham.pokidex.core.engine

import com.tutushubham.pokidex.core.domain.entity.DomainBehaviorProfile
import com.tutushubham.pokidex.core.domain.entity.Session
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.SessionStatus
import java.time.LocalDate

/**
 * Computes domain-level behavior profiles from recent sessions.
 *
 * Currently only calculates [DomainBehaviorProfile.preferredSessionDuration]
 * as the median actualMinutes of completed sessions per domain.
 */
object DomainProfileCalculator {

    fun compute(
        sessionsByDomain: Map<Domain, List<Session>>,
        date: LocalDate
    ): Map<Domain, DomainBehaviorProfile> {
        return sessionsByDomain.mapNotNull { (domain, sessions) ->
            val completedMinutes = sessions
                .filter { it.status == SessionStatus.COMPLETED && it.actualMinutes != null && it.actualMinutes > 0 }
                .map { it.actualMinutes!! }
                .sorted()

            if (completedMinutes.isEmpty()) return@mapNotNull null

            val median = if (completedMinutes.size % 2 == 0) {
                val mid = completedMinutes.size / 2
                (completedMinutes[mid - 1] + completedMinutes[mid]) / 2
            } else {
                completedMinutes[completedMinutes.size / 2]
            }

            domain to DomainBehaviorProfile(
                domain = domain,
                preferredSessionDuration = median,
                lastUpdated = date
            )
        }.toMap()
    }
}
