package com.pace.tracker.data

import com.pace.tracker.data.db.DailyLogEntity
import com.pace.tracker.data.db.DayWorkouts
import com.pace.tracker.data.db.effectiveSteps
import com.pace.tracker.data.db.MeasurementEntity
import com.pace.tracker.data.db.ProfileEntity
import com.pace.tracker.data.db.ProgressPhotoEntity
import com.pace.tracker.data.db.RecalibrationEntity
import com.pace.tracker.domain.AdaptiveConfig
import com.pace.tracker.domain.BodyMath
import com.pace.tracker.domain.DayFacts
import com.pace.tracker.domain.EngineProfile
import com.pace.tracker.domain.PaceIndicator
import com.pace.tracker.domain.PriorWeek
import com.pace.tracker.domain.RecalStatus
import com.pace.tracker.domain.StreakCalculator
import com.pace.tracker.domain.StreakSummary
import com.pace.tracker.domain.Trajectory
import com.pace.tracker.domain.TrajectoryAnchor
import kotlin.math.max
import kotlin.math.roundToLong

/** Snapshot of everything the app has logged. Small enough (≈ 100 days) to derive views in memory. */
data class ProgramData(
    val profile: ProfileEntity?,
    val logs: Map<Long, DailyLogEntity> = emptyMap(),
    val calories: Map<Long, Int> = emptyMap(),
    val workouts: Map<Long, DayWorkouts> = emptyMap(),
    val photos: List<ProgressPhotoEntity> = emptyList(),
    val measurements: List<MeasurementEntity> = emptyList(),
    val recals: List<RecalibrationEntity> = emptyList(),
    val reflections: Map<Int, String> = emptyMap(),
)

data class WeekSummary(
    val weekIndex: Int,
    val startDay: Long,
    val endDay: Long,
    val isComplete: Boolean,
    val firstWeight: Double?,
    val lastWeight: Double?,
    val avgWeight: Double?,
    val prevAvgWeight: Double?,
    val weighIns: Int,
    val avgCalories: Double?,
    val daysFoodLogged: Int,
    val avgTargetKcal: Int,
    val totalSteps: Int,
    val avgSteps: Int?,
    val workouts: Int,
    val workoutMinutes: Int,
    val photos: Int,
    val daysLogged: Int,
    val stepGoalDays: Int,
    val adherentDays: Int,
    val recal: RecalibrationEntity?,
    val reflection: String,
) {
    val weightChange: Double? get() = if (avgWeight != null && prevAvgWeight != null) avgWeight - prevAvgWeight else null
}

fun ProfileEntity.toEngine() = EngineProfile(
    heightCm = heightCm, age = age, sex = sex, activity = activity, pace = pace,
    startWeightKg = startWeightKg, targetWeightKg = targetWeightKg,
    startEpochDay = startEpochDay, durationDays = durationDays,
)

val ProgramData.weeklyRecals: List<RecalibrationEntity>
    get() = recals.filter { it.weekIndex > 0 }.sortedBy { it.weekIndex }

fun ProgramData.priorWeeks(): List<PriorWeek> = weeklyRecals.map {
    PriorWeek(
        weekIndex = it.weekIndex,
        avgWeightKg = if (it.status == RecalStatus.INSUFFICIENT_DATA) null else it.avgWeightKg,
        lossKg = it.lossKg,
    )
}

fun ProgramData.anchors(): List<TrajectoryAnchor> =
    profile?.let { Trajectory.anchors(it.toEngine(), priorWeeks()) } ?: emptyList()

/** Calorie target in effect on [day] (from the target history). */
fun ProgramData.targetForDay(day: Long): Int {
    val p = profile ?: return 0
    return recals.filter { it.effectiveEpochDay <= day }.maxByOrNull { it.effectiveEpochDay * 10_000 + it.id }
        ?.newTargetKcal ?: p.initialTargetKcal
}

val ProgramData.weights: List<Pair<Long, Double>>
    get() = logs.values.filter { it.weightKg != null }.sortedBy { it.epochDay }.map { it.epochDay to it.weightKg!! }

fun ProgramData.latestWeight(): Double? = weights.lastOrNull()?.second

/** Mean weight over the 7 days ending [endDay] and the mean day of those weigh-ins. */
fun ProgramData.sevenDayAverage(endDay: Long): Pair<Double, Long>? {
    val w = logs.values.filter { it.weightKg != null && it.epochDay in (endDay - 6)..endDay }
    if (w.isEmpty()) return null
    return w.map { it.weightKg!! }.average() to w.map { it.epochDay }.average().roundToLong()
}

fun ProgramData.paceIndicator(today: Long): PaceIndicator {
    val p = profile ?: return PaceIndicator.NO_DATA
    val avg = sevenDayAverage(today) ?: return PaceIndicator.NO_DATA
    return Trajectory.paceIndicator(p.toEngine(), anchors(), avg.first, avg.second)
}

fun ProgramData.stepsFor(day: Long): Int? = logs[day]?.effectiveSteps

/** Calories out: BMR × 1.2 (resting + daily living) + step energy + logged workouts; falls back to TDEE. */
fun ProgramData.caloriesOut(day: Long): Int? {
    val p = profile ?: return null
    val weight = weights.lastOrNull { it.first <= day }?.second ?: p.startWeightKg
    val workoutKcal = workouts[day]?.kcal ?: 0
    val steps = stepsFor(day)
    val bmr = BodyMath.bmr(weight, p.heightCm, p.age, p.sex)
    val base = if (steps != null) bmr * 1.2 + BodyMath.stepKcal(steps, weight) else bmr * p.activity.multiplier
    return (base + workoutKcal).toInt()
}

fun ProgramData.isLogged(day: Long): Boolean {
    val l = logs[day]
    return (l?.weightKg != null) || (calories[day] ?: 0) > 0 || (workouts[day]?.count ?: 0) > 0
}

fun ProgramData.dayFacts(): Map<Long, DayFacts> {
    val p = profile ?: return emptyMap()
    val days = (logs.keys + calories.keys + workouts.keys)
    return days.associateWith { d ->
        DayFacts(
            epochDay = d,
            logged = isLogged(d),
            steps = stepsFor(d),
            stepGoal = p.stepGoal,
            intakeKcal = calories[d],
            targetKcal = targetForDay(d),
            workoutCount = workouts[d]?.count ?: 0,
        )
    }
}

fun ProgramData.streaks(today: Long): StreakSummary? {
    val p = profile ?: return null
    return StreakCalculator.summarize(dayFacts(), p.startEpochDay, today, p.workoutsPerWeek)
}

fun ProgramData.currentWeekIndex(today: Long): Int = profile?.toEngine()?.weekOf(today)?.coerceAtLeast(1) ?: 1

fun ProgramData.weekSummary(week: Int, today: Long): WeekSummary? {
    val p = profile ?: return null
    val ep = p.toEngine()
    val start = ep.weekStart(week)
    val end = ep.weekEnd(week)
    val range = start..end
    val weekWeights = logs.values.filter { it.epochDay in range && it.weightKg != null }.sortedBy { it.epochDay }
    val cal = calories.filterKeys { it in range && calories[it]!! > 0 }
    val steps = (start..minOf(end, today)).mapNotNull { stepsFor(it) }
    val wk = workouts.filterKeys { it in range }.values
    val facts = dayFacts().filterKeys { it in range }.values
    val recal = weeklyRecals.lastOrNull { it.weekIndex == week }
    val prevAvg = if (week == 1) p.startWeightKg else weekWeightsAvg(week - 1) ?: recal?.prevAvgWeightKg
    val days = (start..minOf(end, today))
    return WeekSummary(
        weekIndex = week, startDay = start, endDay = end, isComplete = today > end,
        firstWeight = weekWeights.firstOrNull()?.weightKg, lastWeight = weekWeights.lastOrNull()?.weightKg,
        avgWeight = weekWeights.takeIf { it.isNotEmpty() }?.map { it.weightKg!! }?.average(),
        prevAvgWeight = prevAvg, weighIns = weekWeights.size,
        avgCalories = cal.values.takeIf { it.isNotEmpty() }?.average(), daysFoodLogged = cal.size,
        avgTargetKcal = if (days.isEmpty()) p.currentTargetKcal else days.map { targetForDay(it) }.average().toInt(),
        totalSteps = steps.sum(), avgSteps = steps.takeIf { it.isNotEmpty() }?.average()?.toInt(),
        workouts = wk.sumOf { it.count }, workoutMinutes = wk.sumOf { it.minutes },
        photos = photos.count { it.epochDay in range },
        daysLogged = facts.count { it.logged },
        stepGoalDays = facts.count { StreakCalculator.isStepGoalMet(it) },
        adherentDays = facts.count { StreakCalculator.isCalorieAdherent(it) },
        recal = recal, reflection = reflections[week] ?: "",
    )
}

private fun ProgramData.weekWeightsAvg(week: Int): Double? {
    val ep = profile?.toEngine() ?: return null
    val range = ep.weekStart(week)..ep.weekEnd(week)
    return logs.values.filter { it.epochDay in range && it.weightKg != null }.map { it.weightKg!! }
        .takeIf { it.isNotEmpty() }?.average()
}

fun ProgramData.allWeekSummaries(today: Long): List<WeekSummary> {
    val current = currentWeekIndex(today)
    return (1..max(1, current)).mapNotNull { weekSummary(it, today) }
}

/** The newest weekly recalibration offering a lower goal that hasn't been dismissed or applied. */
fun ProgramData.pendingSuggestion(): RecalibrationEntity? {
    val p = profile ?: return null
    val latest = weeklyRecals.lastOrNull() ?: return null
    val s = latest.suggestedTargetKg ?: return null
    if (latest.weekIndex <= p.dismissedSuggestionWeek) return null
    if (s >= p.targetWeightKg - 0.1) return null
    return latest
}

fun ProgramData.unseenRecal(): RecalibrationEntity? = recals.lastOrNull { !it.seen && it.weekIndex > 0 }

fun ProgramData.bmiFor(weight: Double): Double? = profile?.let { BodyMath.bmi(weight, it.heightCm) }

fun ProgramData.goalDay(): Long? = profile?.let { it.startEpochDay + it.durationDays }

fun ProgramData.whatIfRate(): Double {
    val p = profile ?: return 0.0
    val trend = weeklyRecals.mapNotNull { it.lossKg }.takeLast(AdaptiveConfig.TREND_WEEKS)
    val current = latestWeight() ?: p.startWeightKg
    val cap = current * AdaptiveConfig.MAX_PLANNED_LOSS_FRACTION
    val t = if (trend.isEmpty()) BodyMath.plannedRateKg(p.pace, current) else trend.average()
    // "More cutdown" scenario: the better of your trend and the plan, never above the 1 %/week cap.
    return minOf(cap, maxOf(t, BodyMath.plannedRateKg(p.pace, current)))
}
