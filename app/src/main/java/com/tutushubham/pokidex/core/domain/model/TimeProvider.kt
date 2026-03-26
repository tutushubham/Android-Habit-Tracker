package com.tutushubham.pokidex.core.domain.model

import java.time.Instant
import java.time.LocalDate

interface TimeProvider {
    fun today(): LocalDate
    fun now(): Instant
}

class SystemTimeProvider : TimeProvider {
    override fun today(): LocalDate = LocalDate.now()
    override fun now(): Instant = Instant.now()
}
