package com.pace.tracker.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class EngineProfile(
    val heightCm: Double,
    val age: Int,
    val sex: Sex,
    val activity: ActivityLevel,
    val pace: PacePreference,
    val startWeightKg: Double,
    val targetWeightKg: Double,
    val startEpochDay: Long,
    val durationDays: Int,
) {
    val goalEpochDay: Long get() = startEpochDay + durationDays

    /** Week k (1-based) covers [startEpochDay + 7(k-1), startEpochDay + 7k - 1]. */
    fun weekStart(week: Int): Long = startEpochDay + 7L * (week - 1)
    fun weekEnd(week: Int): Long = weekStart(week) + 6
    fun weekMid(week: Int): Long = weekStart(week) + 3
    fun weekOf(epochDay: Long): Int = ((epochDay - startEpochDay) / 7).toInt() + 1
}

/** Aggregated raw data for one completed week. */
data class WeekInput(
    val weekIndex: Int,
    val weights: List<Double>,
    val dailyIntakeKcal: List<Int>,
    val avgSteps: Double?,
    val workoutCount: Int,
    val workoutKcal: Int,
)

/** What the engine needs to remember from earlier recalibrations. */
data class PriorWeek(val weekIndex: Int, val avgWeightKg: Double?, val lossKg: Double?)

data class EngineState(
    val currentTargetKcal: Int,
    val tdeeEstimate: Double,
    val priorWeeks: List<PriorWeek>,
)

data class RecalResult(
    val weekIndex: Int,
    val avgWeightKg: Double?,
    val prevAvgWeightKg: Double,
    val lossKg: Double?,
    val lossPctOfBodyWeight: Double?,
    val plannedRateKg: Double,
    val avgIntakeKcal: Double?,
    val avgSteps: Double?,
    val workoutCount: Int,
    val observedTdee: Double?,
    val tdeeEstimate: Double,
    val oldTargetKcal: Int,
    val newTargetKcal: Int,
    val status: RecalStatus,
    val trendRateKg: Double,
    val projectedFinishKg: Double,
    val suggestedTargetKg: Double?,
    val suggestedDurationDays: Int?,
    val explanation: String,
)

/**
 * The adaptive weekly engine. Pure function of (profile, state, week data) → result.
 * The rules are documented in README.md and every constant lives in [AdaptiveConfig].
 */
object AdaptiveEngine {

    fun recalibrate(profile: EngineProfile, state: EngineState, week: WeekInput): RecalResult {
        val c = AdaptiveConfig
        val prevAvg = state.priorWeeks.lastOrNull { it.avgWeightKg != null }?.avgWeightKg
            ?: profile.startWeightKg
        val plannedRate = BodyMath.plannedRateKg(profile.pace, prevAvg)
        val oldTarget = state.currentTargetKcal
        val avgIntake = if (week.dailyIntakeKcal.size >= c.MIN_INTAKE_DAYS) week.dailyIntakeKcal.average() else null
        val weekEnd = profile.weekEnd(week.weekIndex)
        val weeksRemaining = max(0.0, (profile.goalEpochDay - (weekEnd + 1)) / 7.0)

        // ---- Not enough weigh-ins: hold everything --------------------------------------
        if (week.weights.size < c.MIN_WEIGH_INS) {
            val trend = trendRate(state.priorWeeks.mapNotNull { it.lossKg }, plannedRate)
            val projected = prevAvg - trend * weeksRemaining
            val text = "Week ${week.weekIndex}: only ${week.weights.size} weigh-in(s) logged " +
                "(need ${c.MIN_WEIGH_INS}), so the trend can't be trusted. " +
                "Target stays at ${fmtKcal(oldTarget.toDouble())} kcal. Weigh in at least ${c.MIN_WEIGH_INS} mornings next week."
            return RecalResult(
                weekIndex = week.weekIndex, avgWeightKg = week.weights.takeIf { it.isNotEmpty() }?.average(),
                prevAvgWeightKg = prevAvg, lossKg = null, lossPctOfBodyWeight = null, plannedRateKg = plannedRate,
                avgIntakeKcal = avgIntake, avgSteps = week.avgSteps, workoutCount = week.workoutCount,
                observedTdee = null, tdeeEstimate = state.tdeeEstimate, oldTargetKcal = oldTarget,
                newTargetKcal = oldTarget, status = RecalStatus.INSUFFICIENT_DATA, trendRateKg = trend,
                projectedFinishKg = projected, suggestedTargetKg = null, suggestedDurationDays = null,
                explanation = text,
            )
        }

        val avg = week.weights.average()
        val loss = prevAvg - avg
        val lossPct = loss / prevAvg * 100.0

        // ---- TDEE estimate (energy balance, smoothed) -----------------------------------
        val formulaTdee = BodyMath.tdee(avg, profile.heightCm, profile.age, profile.sex, profile.activity)
        val observed = avgIntake?.let {
            val raw = it + loss * c.KCAL_PER_KG / 7.0
            raw.coerceIn(formulaTdee * (1 - c.TDEE_CLAMP_FRACTION), formulaTdee * (1 + c.TDEE_CLAMP_FRACTION))
        }
        val tdee = if (observed != null) {
            c.TDEE_SMOOTHING * observed + (1 - c.TDEE_SMOOTHING) * state.tdeeEstimate
        } else {
            0.5 * state.tdeeEstimate + 0.5 * formulaTdee
        }

        // ---- Classify the week -----------------------------------------------------------
        val tooFastPct = if (week.weekIndex == 1) c.TOO_FAST_PCT_FIRST_WEEK else c.TOO_FAST_PCT
        val stallStreak = if (loss < c.STALL_KG) {
            1 + state.priorWeeks.reversed().takeWhile { it.lossKg != null && it.lossKg < c.STALL_KG }.size
        } else 0
        val baselineExpected = baselineExpected(profile, profile.weekMid(week.weekIndex))
        val cumulativeBehind = avg - baselineExpected

        val status = when {
            avg <= profile.targetWeightKg -> RecalStatus.GOAL_REACHED
            lossPct > tooFastPct -> RecalStatus.TOO_FAST
            stallStreak >= c.STALL_WEEKS -> RecalStatus.STALLED
            loss < plannedRate * c.BEHIND_RATIO -> RecalStatus.BEHIND
            loss > plannedRate * c.AHEAD_RATIO || cumulativeBehind < -c.PACE_TOLERANCE_KG -> RecalStatus.AHEAD
            else -> RecalStatus.ON_PACE
        }

        val floor = BodyMath.calorieFloor(profile.sex)
        var delta = when (status) {
            RecalStatus.TOO_FAST -> c.EASE_STEP_KCAL
            RecalStatus.STALLED -> -c.STALL_STEP_KCAL
            RecalStatus.BEHIND -> if (cumulativeBehind > c.PACE_TOLERANCE_KG) -c.BEHIND_STEP_KCAL else 0
            else -> 0
        }
        var newTarget = BodyMath.clampTarget(oldTarget + delta, profile.sex, max(tdee, formulaTdee))
        delta = newTarget - oldTarget
        val atFloor = newTarget <= floor && (status == RecalStatus.STALLED || status == RecalStatus.BEHIND)

        // ---- Projection & "more cutdown" suggestion ----------------------------------------
        val losses = state.priorWeeks.mapNotNull { it.lossKg } + loss
        val trend = trendRate(losses, plannedRate)
        val projected = avg - trend * weeksRemaining
        val minHealthy = BodyMath.weightForBmi(c.MIN_HEALTHY_BMI, profile.heightCm)
        var suggested: Double? = null
        var suggestedDuration: Int? = null
        if (status == RecalStatus.GOAL_REACHED) {
            val horizonWeeks = max(weeksRemaining, c.EXTENSION_DAYS / 7.0)
            val s = BodyMath.roundHalfKg(max(minHealthy, avg - plannedRate * horizonWeeks))
            if (s < avg - c.EXTEND_MARGIN_KG) {
                suggested = s
                suggestedDuration = durationFor(profile, weekEnd, avg, s, plannedRate)
            }
        } else if (status == RecalStatus.AHEAD || status == RecalStatus.ON_PACE) {
            val safeTrend = min(trend, avg * c.MAX_PLANNED_LOSS_FRACTION)
            val s = BodyMath.roundHalfKg(max(minHealthy, avg - safeTrend * weeksRemaining))
            if (s < profile.targetWeightKg - c.EXTEND_MARGIN_KG) {
                suggested = s
                suggestedDuration = durationFor(profile, weekEnd, avg, s, safeTrend)
            }
        }

        // ---- Plain-English explanation -------------------------------------------------------
        val text = buildString {
            append("Week ${week.weekIndex}: ")
            if (loss >= 0) append("you lost ${fmt1(loss)} kg") else append("you gained ${fmt1(-loss)} kg")
            append(" (avg ${fmt1(prevAvg)} → ${fmt1(avg)} kg, ${fmt1(abs(lossPct))}% of body weight). ")
            append("Plan: ${fmt1(plannedRate)} kg/week. ")
            when (status) {
                RecalStatus.GOAL_REACHED -> append("You've reached your ${fmt1(profile.targetWeightKg)} kg goal! ")
                RecalStatus.TOO_FAST -> append("That's faster than ${fmt1(tooFastPct)}%/week, so the deficit is eased to protect muscle. ")
                RecalStatus.STALLED -> append("That's under ${fmt1(c.STALL_KG)} kg for $stallStreak weeks in a row — a stall. ")
                RecalStatus.BEHIND -> if (delta < 0) append("Behind pace and ${fmt1(cumulativeBehind)} kg behind plan overall, so a small tightening. ")
                else append("A bit behind pace, but still within ${fmt1(c.PACE_TOLERANCE_KG)} kg of plan overall — holding steady. ")
                RecalStatus.AHEAD -> append("Ahead of pace. ")
                else -> append("On pace. ")
            }
            when {
                delta > 0 -> append("Target rises to ${fmtKcal(newTarget.toDouble())} kcal (+$delta).")
                delta < 0 -> append("Target drops to ${fmtKcal(newTarget.toDouble())} kcal ($delta).")
                else -> append("Target stays at ${fmtKcal(newTarget.toDouble())} kcal.")
            }
            if (atFloor) {
                append(" You're at the ${floor} kcal safety floor, so add ~2,000 steps/day or one extra workout instead of eating less.")
            } else if (status == RecalStatus.STALLED) {
                val steps = week.avgSteps?.roundToInt()
                if (steps != null) append(" Steps averaged ${fmtKcal(steps.toDouble())}/day — adding 2,000 more burns ≈${fmtKcal(BodyMath.stepKcal(2000, avg))} kcal/day.")
            }
            if (avgIntake != null) append(" Avg intake ${fmtKcal(avgIntake)} kcal; estimated real TDEE ${fmtKcal(tdee)} kcal.")
            else append(" Log food on ${c.MIN_INTAKE_DAYS}+ days/week to calibrate your real TDEE.")
            append(" Projected finish: ${fmt1(projected)} kg.")
            if (suggested != null) append(" You could push further — suggested new goal ${fmt1(suggested)} kg.")
        }

        return RecalResult(
            weekIndex = week.weekIndex, avgWeightKg = avg, prevAvgWeightKg = prevAvg, lossKg = loss,
            lossPctOfBodyWeight = lossPct, plannedRateKg = plannedRate, avgIntakeKcal = avgIntake,
            avgSteps = week.avgSteps, workoutCount = week.workoutCount, observedTdee = observed,
            tdeeEstimate = tdee, oldTargetKcal = oldTarget, newTargetKcal = newTarget, status = status,
            trendRateKg = trend, projectedFinishKg = projected, suggestedTargetKg = suggested,
            suggestedDurationDays = suggestedDuration, explanation = text,
        )
    }

    /** Mean of the last TREND_WEEKS weekly losses; falls back to the planned rate. */
    fun trendRate(losses: List<Double>, fallback: Double): Double {
        val recent = losses.takeLast(AdaptiveConfig.TREND_WEEKS)
        return if (recent.isEmpty()) fallback else max(0.0, recent.average())
    }

    /** The original (non-adaptive) plan line, used for "cumulatively behind" checks. */
    fun baselineExpected(profile: EngineProfile, epochDay: Long): Double {
        val rate = BodyMath.plannedRateKg(profile.pace, profile.startWeightKg)
        val w = profile.startWeightKg - rate * (epochDay - profile.startEpochDay) / 7.0
        return max(profile.targetWeightKg, w)
    }

    private fun durationFor(profile: EngineProfile, weekEnd: Long, fromKg: Double, toKg: Double, rate: Double): Int {
        val r = if (rate > 0.05) rate else BodyMath.plannedRateKg(profile.pace, fromKg)
        val needEnd = weekEnd + 1 + BodyMath.daysNeeded(fromKg - toKg, r)
        return max(profile.durationDays, (needEnd - profile.startEpochDay).toInt())
    }

    /** Applying a "more cutdown" offer: the new goal and (if needed) a longer timeline. */
    fun acceptSuggestion(profile: EngineProfile, currentWeightKg: Double, todayEpochDay: Long, newTargetKg: Double): Int {
        val rate = BodyMath.plannedRateKg(profile.pace, currentWeightKg)
        val needEnd = todayEpochDay + BodyMath.daysNeeded(currentWeightKg - newTargetKg, rate)
        return max(profile.durationDays, (needEnd - profile.startEpochDay).toInt())
    }
}

/** Anchor for the adaptive target trajectory: at [epochDay] the expected weight is [weightKg]. */
data class TrajectoryAnchor(val epochDay: Long, val weightKg: Double)

object Trajectory {
    /**
     * Adaptive target trajectory: starts at the start weight and is re-anchored to the real
     * weekly average after every recalibration, then descends at the planned rate toward the goal.
     */
    fun anchors(profile: EngineProfile, weeks: List<PriorWeek>): List<TrajectoryAnchor> {
        val list = mutableListOf(TrajectoryAnchor(profile.startEpochDay, profile.startWeightKg))
        weeks.filter { it.avgWeightKg != null }.sortedBy { it.weekIndex }.forEach {
            list += TrajectoryAnchor(profile.weekMid(it.weekIndex), it.avgWeightKg!!)
        }
        return list
    }

    fun expected(profile: EngineProfile, anchors: List<TrajectoryAnchor>, epochDay: Long): Double {
        val anchor = anchors.lastOrNull { it.epochDay <= epochDay } ?: anchors.first()
        if (anchor.weightKg <= profile.targetWeightKg) return anchor.weightKg
        val rate = BodyMath.plannedRateKg(profile.pace, anchor.weightKg)
        val w = anchor.weightKg - rate * (epochDay - anchor.epochDay) / 7.0
        return max(profile.targetWeightKg, w)
    }

    /**
     * Compare a 7-day weight average (centred on [avgMidDay]) against the trajectory.
     * Lower than expected by more than the band = ahead.
     */
    fun paceIndicator(profile: EngineProfile, anchors: List<TrajectoryAnchor>, avgWeight: Double?, avgMidDay: Long): PaceIndicator {
        if (avgWeight == null) return PaceIndicator.NO_DATA
        val expected = expected(profile, anchors, avgMidDay)
        val diff = avgWeight - expected
        return when {
            diff < -AdaptiveConfig.PACE_BAND_KG -> PaceIndicator.AHEAD
            diff > AdaptiveConfig.PACE_BAND_KG -> PaceIndicator.BEHIND
            else -> PaceIndicator.ON_PACE
        }
    }
}
