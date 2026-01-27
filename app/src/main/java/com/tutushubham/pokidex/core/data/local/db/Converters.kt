package com.tutushubham.pokidex.core.data.local.db

import androidx.room.TypeConverter
import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.SessionStatus
import com.tutushubham.pokidex.core.domain.model.SkipReason
import java.time.Instant
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun fromLocalDate(value: LocalDate?): Long? {
        return value?.toEpochDay()
    }

    @TypeConverter
    fun toLocalDate(value: Long?): LocalDate? {
        return value?.let { LocalDate.ofEpochDay(it) }
    }

    @TypeConverter
    fun fromInstant(value: Instant?): Long? {
        return value?.toEpochMilli()
    }

    @TypeConverter
    fun toInstant(value: Long?): Instant? {
        return value?.let { Instant.ofEpochMilli(it) }
    }

    @TypeConverter
    fun fromDomain(value: Domain?): Int? {
        return value?.ordinal
    }

    @TypeConverter
    fun toDomain(value: Int?): Domain? {
        return value?.let { Domain.entries[it] }
    }

    @TypeConverter
    fun fromDayBlock(value: DayBlock?): Int? {
        return value?.ordinal
    }

    @TypeConverter
    fun toDayBlock(value: Int?): DayBlock? {
        return value?.let { DayBlock.entries[it] }
    }

    @TypeConverter
    fun fromSessionStatus(value: SessionStatus?): Int? {
        return value?.ordinal
    }

    @TypeConverter
    fun toSessionStatus(value: Int?): SessionStatus? {
        return value?.let { SessionStatus.entries[it] }
    }

    @TypeConverter
    fun fromSkipReason(value: SkipReason?): Int? {
        return value?.ordinal
    }

    @TypeConverter
    fun toSkipReason(value: Int?): SkipReason? {
        return value?.let { SkipReason.entries[it] }
    }
}
