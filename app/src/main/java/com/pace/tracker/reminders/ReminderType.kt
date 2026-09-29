package com.pace.tracker.reminders

import com.pace.tracker.data.db.ReminderEntity

enum class ReminderKind { DAILY, DAYS_OF_WEEK, INTERVAL }

/** bit 0 = Monday … bit 6 = Sunday (java.time DayOfWeek.value - 1). */
const val ALL_DAYS = 0x7F
private const val MON = 1
private const val WED = 1 shl 2
private const val FRI = 1 shl 4
private const val SUN = 1 shl 6

enum class ReminderType(
    val key: String,
    val title: String,
    val message: String,
    val kind: ReminderKind,
    val defaultHour: Int,
    val defaultMinute: Int,
    val defaultEnabled: Boolean,
    val defaultDaysMask: Int = ALL_DAYS,
    val defaultIntervalMinutes: Int? = null,
    val defaultEndHour: Int? = null,
    /** Navigation route opened when the notification is tapped. */
    val route: String,
) {
    WEIGHT("weight", "Morning weigh-in", "Step on the scale before breakfast and log it — takes 10 seconds.",
        ReminderKind.DAILY, 7, 30, true, route = "log"),
    BREAKFAST("meal_breakfast", "Log breakfast", "What did you have for breakfast?",
        ReminderKind.DAILY, 9, 0, false, route = "log"),
    LUNCH("meal_lunch", "Log lunch", "Quick — log lunch while you remember it.",
        ReminderKind.DAILY, 13, 30, false, route = "log"),
    DINNER("meal_dinner", "Log dinner", "Log dinner and check your remaining calories.",
        ReminderKind.DAILY, 20, 0, false, route = "log"),
    WORKOUT("workout", "Workout time", "Scheduled workout — log it when you're done.",
        ReminderKind.DAYS_OF_WEEK, 18, 0, false, defaultDaysMask = MON or WED or FRI, route = "log"),
    WATER("water", "Drink water", "Time for a glass of water.",
        ReminderKind.INTERVAL, 9, 0, false, defaultIntervalMinutes = 90, defaultEndHour = 21, route = "log"),
    PHOTOS("weekly_photos", "Progress photos & measurements", "Weekly check-in: front/side/back photos and tape measurements.",
        ReminderKind.DAYS_OF_WEEK, 9, 0, true, defaultDaysMask = SUN, route = "photos"),
    RECAL("recal_summary", "Weekly recalibration", "Your calorie target has been recalibrated.",
        ReminderKind.DAYS_OF_WEEK, 20, 0, true, defaultDaysMask = SUN, route = "history"),
    ;

    fun defaultEntity(daysMaskOverride: Int? = null) = ReminderEntity(
        key = key,
        enabled = defaultEnabled,
        hour = defaultHour,
        minute = defaultMinute,
        daysMask = daysMaskOverride ?: defaultDaysMask,
        intervalMinutes = defaultIntervalMinutes,
        endHour = defaultEndHour,
        endMinute = if (defaultEndHour != null) 0 else null,
    )

    companion object {
        fun fromKey(key: String?): ReminderType? = entries.firstOrNull { it.key == key }
    }
}
