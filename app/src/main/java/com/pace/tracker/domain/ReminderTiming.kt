package com.pace.tracker.domain

import java.time.LocalDateTime

/** Computes the next fire time for a reminder. daysMask bit 0 = Monday … bit 6 = Sunday. */
object ReminderTiming {
    fun next(
        now: LocalDateTime,
        hour: Int,
        minute: Int,
        daysMask: Int,
        intervalMinutes: Int? = null,
        endHour: Int? = null,
        endMinute: Int? = null,
    ): LocalDateTime? {
        if (daysMask and 0x7F == 0) return null
        for (offset in 0L..7L) {
            val date = now.toLocalDate().plusDays(offset)
            if (daysMask and (1 shl (date.dayOfWeek.value - 1)) == 0) continue
            val start = date.atTime(hour, minute)
            val times: Sequence<LocalDateTime> = if (intervalMinutes != null && intervalMinutes > 0) {
                val end = date.atTime(endHour ?: 21, endMinute ?: 0)
                if (end.isBefore(start)) sequenceOf(start)
                else generateSequence(start) { it.plusMinutes(intervalMinutes.toLong()) }.takeWhile { !it.isAfter(end) }
            } else {
                sequenceOf(start)
            }
            times.firstOrNull { it.isAfter(now) }?.let { return it }
        }
        return null
    }
}
