package com.habitsheet.domain.model

/**
 * The `updated_at` to store for a row that is written again. The device clock can move backwards (manual change,
 * time-zone-less NTP corrections, travel mistakes), but a row's `updated_at` must never go back.
 *
 * With [strict] the result is always greater than [previous] when there is one. Completions use it: the upload
 * acknowledgement compares `updated_at` with the value the sync saw, so a later change must never reuse it.
 */
fun monotonicUpdatedAt(candidate: Long, previous: Long?, strict: Boolean = false): Long = when {
    previous == null -> candidate
    strict -> maxOf(candidate, previous + 1)
    else -> maxOf(candidate, previous)
}
