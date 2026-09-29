package com.pace.tracker.domain

import kotlin.math.max

/** Facts about a single day used for streaks. */
data class DayFacts(
    val epochDay: Long,
    val logged: Boolean,
    val steps: Int?,
    val stepGoal: Int,
    val intakeKcal: Int?,
    val targetKcal: Int,
    val workoutCount: Int,
)

data class Streak(val current: Int, val best: Int, val unit: String)

data class StreakSummary(
    val logging: Streak,
    val steps: Streak,
    val calories: Streak,
    val workouts: Streak,
)

enum class BadgeCategory(val label: String) {
    LOGGING("Daily logging"), STEPS("Step goal"), CALORIES("Calorie adherence"), JOURNEY("Days in programme"),
}

data class Badge(val category: BadgeCategory, val days: Int, val achieved: Boolean) {
    val title: String get() = "${days}-day ${category.label.lowercase()}"
}

object StreakCalculator {
    val BADGE_DAYS = listOf(7, 30, 50, 80)

    fun isStepGoalMet(d: DayFacts): Boolean = (d.steps ?: 0) >= d.stepGoal && d.stepGoal > 0

    fun isCalorieAdherent(d: DayFacts): Boolean {
        val intake = d.intakeKcal ?: return false
        return intake <= d.targetKcal + AdaptiveConfig.ADHERENCE_TOLERANCE_KCAL &&
            intake >= d.targetKcal * AdaptiveConfig.ADHERENCE_MIN_FRACTION
    }

    /**
     * Current streak counts back from today; if today isn't satisfied yet it counts back from
     * yesterday so an unfinished day doesn't break the streak until it's over.
     */
    fun dailyStreak(days: Map<Long, DayFacts>, fromDay: Long, today: Long, ok: (DayFacts) -> Boolean): Streak {
        fun sat(day: Long) = days[day]?.let(ok) == true
        var cur = 0
        var d = if (sat(today)) today else today - 1
        while (d >= fromDay && sat(d)) { cur++; d-- }
        var best = 0
        var run = 0
        for (day in fromDay..today) {
            if (sat(day)) { run++; best = max(best, run) } else run = 0
        }
        return Streak(cur, max(best, cur), "days")
    }

    /** Consecutive programme weeks with at least [perWeek] workouts (current week counts once met). */
    fun weeklyWorkoutStreak(days: Map<Long, DayFacts>, startDay: Long, today: Long, perWeek: Int): Streak {
        if (today < startDay) return Streak(0, 0, "weeks")
        val currentWeek = ((today - startDay) / 7).toInt()
        fun count(week: Int): Int = (0..6).sumOf { days[startDay + week * 7L + it]?.workoutCount ?: 0 }
        val met = (0..currentWeek).map { count(it) >= perWeek }
        var cur = 0
        var w = if (met[currentWeek]) currentWeek else currentWeek - 1
        while (w >= 0 && met[w]) { cur++; w-- }
        var best = 0
        var run = 0
        met.forEach { if (it) { run++; best = max(best, run) } else run = 0 }
        return Streak(cur, max(best, cur), "weeks")
    }

    fun summarize(days: Map<Long, DayFacts>, startDay: Long, today: Long, workoutsPerWeek: Int): StreakSummary {
        val from = minOf(startDay, days.keys.minOrNull() ?: startDay)
        return StreakSummary(
            logging = dailyStreak(days, from, today) { it.logged },
            steps = dailyStreak(days, from, today, ::isStepGoalMet),
            calories = dailyStreak(days, from, today, ::isCalorieAdherent),
            workouts = weeklyWorkoutStreak(days, startDay, today, workoutsPerWeek),
        )
    }

    fun badges(summary: StreakSummary, daysInProgramme: Int): List<Badge> =
        BadgeCategory.entries.flatMap { cat ->
            val value = when (cat) {
                BadgeCategory.LOGGING -> summary.logging.best
                BadgeCategory.STEPS -> summary.steps.best
                BadgeCategory.CALORIES -> summary.calories.best
                BadgeCategory.JOURNEY -> daysInProgramme
            }
            BADGE_DAYS.map { Badge(cat, it, value >= it) }
        }

    /**
     * Celebrations of adaptive wins. [weeklyStatuses] are recalibration statuses oldest→newest,
     * [weights] are (epochDay, kg) logs oldest→newest.
     */
    fun adaptiveWins(
        weeklyStatuses: List<RecalStatus>,
        weights: List<Pair<Long, Double>>,
        startWeightKg: Double,
        targetWeightKg: Double,
    ): List<String> {
        val wins = mutableListOf<String>()
        val onPaceRun = weeklyStatuses
            .filter { it != RecalStatus.PROFILE_UPDATE && it != RecalStatus.GOAL_EXTENDED && it != RecalStatus.START }
            .reversed()
            .takeWhile { it == RecalStatus.ON_PACE || it == RecalStatus.AHEAD || it == RecalStatus.TOO_FAST }
            .size
        if (onPaceRun >= 2) wins += "$onPaceRun weeks on pace in a row"
        if (weights.isNotEmpty()) {
            val latest = weights.last().second
            val priorMin = weights.dropLast(1).minOfOrNull { it.second }
            if (priorMin != null && latest < priorMin && latest < startWeightKg) wins += "New low weight: ${fmt1(latest)} kg"
            val lost = startWeightKg - latest
            listOf(10, 5, 3, 1).firstOrNull { lost >= it }?.let { wins += "$it kg down since the start" }
            if (latest <= targetWeightKg) wins += "Goal weight reached"
        }
        return wins
    }
}
