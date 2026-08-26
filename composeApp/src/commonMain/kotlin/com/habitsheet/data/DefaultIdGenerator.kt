package com.habitsheet.data

import com.habitsheet.domain.repository.IdGenerator
import kotlin.random.Random
import kotlin.time.Clock

class DefaultIdGenerator : IdGenerator {
    override fun newId(): String = buildString {
        append(Clock.System.now().toEpochMilliseconds().toString(36))
        append('-')
        append(Random.nextLong().toULong().toString(36))
    }
}
