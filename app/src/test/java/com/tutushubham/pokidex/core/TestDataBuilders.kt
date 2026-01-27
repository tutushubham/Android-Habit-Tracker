package com.tutushubham.pokidex.core

import com.tutushubham.pokidex.core.data.AnchorEntity
import com.tutushubham.pokidex.core.data.CaptureEntity
import com.tutushubham.pokidex.core.data.IntentEntity
import com.tutushubham.pokidex.core.data.SessionEntity
import com.tutushubham.pokidex.core.domain.entity.Anchor
import com.tutushubham.pokidex.core.domain.entity.Capture
import com.tutushubham.pokidex.core.domain.entity.GoalIntent
import com.tutushubham.pokidex.core.domain.entity.Session
import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.SessionStatus
import com.tutushubham.pokidex.core.domain.model.SkipReason
import java.time.Instant
import java.time.LocalDate

// Test data builders for domain entities

fun goalIntent(
    id: String = "intent-1",
    domain: Domain = Domain.FITNESS,
    title: String = "Test Intent",
    targetCount: Int? = null,
    startDate: LocalDate = LocalDate.now(),
    endDate: LocalDate = LocalDate.now().plusDays(30),
    priority: Int = 1
): GoalIntent = GoalIntent(
    id = id,
    domain = domain,
    title = title,
    targetCount = targetCount,
    startDate = startDate,
    endDate = endDate,
    priority = priority
)

fun anchor(
    id: String = "anchor-1",
    block: DayBlock = DayBlock.MORNING,
    domain: Domain = Domain.FITNESS,
    defaultMinutes: Int = 60
): Anchor = Anchor(
    id = id,
    block = block,
    domain = domain,
    defaultMinutes = defaultMinutes
)

fun session(
    id: String = "session-1",
    intentId: String = "intent-1",
    domain: Domain = Domain.FITNESS,
    date: LocalDate = LocalDate.now(),
    block: DayBlock = DayBlock.MORNING,
    plannedMinutes: Int = 60,
    actualMinutes: Int? = null,
    status: SessionStatus = SessionStatus.PLANNED,
    skipReason: SkipReason? = null,
    startedAt: Instant? = null,
    endedAt: Instant? = null
): Session = Session(
    id = id,
    intentId = intentId,
    domain = domain,
    date = date,
    block = block,
    plannedMinutes = plannedMinutes,
    actualMinutes = actualMinutes,
    status = status,
    skipReason = skipReason,
    startedAt = startedAt,
    endedAt = endedAt
)

fun capture(
    id: String = "capture-1",
    content: String = "Test capture",
    createdAt: Instant = Instant.now(),
    resolved: Boolean = false,
    resolvedSessionId: String? = null
): Capture = Capture(
    id = id,
    content = content,
    createdAt = createdAt,
    resolved = resolved,
    resolvedSessionId = resolvedSessionId
)

// Test data builders for data entities

fun intentEntity(
    id: String = "intent-1",
    domain: Domain = Domain.FITNESS,
    title: String = "Test Intent",
    targetCount: Int? = null,
    startDate: LocalDate = LocalDate.now(),
    endDate: LocalDate = LocalDate.now().plusDays(30),
    priority: Int = 1
): IntentEntity = IntentEntity(
    id = id,
    domain = domain,
    title = title,
    targetCount = targetCount,
    startDate = startDate,
    endDate = endDate,
    priority = priority
)

fun sessionEntity(
    id: String = "session-1",
    intentId: String = "intent-1",
    domain: Domain = Domain.FITNESS,
    date: LocalDate = LocalDate.now(),
    block: DayBlock = DayBlock.MORNING,
    plannedMinutes: Int = 60,
    actualMinutes: Int? = null,
    status: SessionStatus = SessionStatus.PLANNED,
    skipReason: SkipReason? = null,
    startedAt: Instant? = null,
    endedAt: Instant? = null
): SessionEntity = SessionEntity(
    id = id,
    intentId = intentId,
    domain = domain,
    date = date,
    block = block,
    plannedMinutes = plannedMinutes,
    actualMinutes = actualMinutes,
    status = status,
    skipReason = skipReason,
    startedAt = startedAt,
    endedAt = endedAt
)

fun anchorEntity(
    id: String = "anchor-1",
    block: DayBlock = DayBlock.MORNING,
    domain: Domain = Domain.FITNESS,
    defaultMinutes: Int = 60
): AnchorEntity = AnchorEntity(
    id = id,
    block = block,
    domain = domain,
    defaultMinutes = defaultMinutes
)

fun captureEntity(
    id: String = "capture-1",
    content: String = "Test capture",
    createdAt: Instant = Instant.now(),
    resolved: Boolean = false,
    resolvedSessionId: String? = null
): CaptureEntity = CaptureEntity(
    id = id,
    content = content,
    createdAt = createdAt,
    resolved = resolved,
    resolvedSessionId = resolvedSessionId
)
