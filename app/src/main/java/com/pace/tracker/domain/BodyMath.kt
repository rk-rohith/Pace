package com.pace.tracker.domain

import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Pure body/energy formulas. No Android dependencies so they can be unit-tested on the JVM. */
object BodyMath {

    /** Mifflin–St Jeor basal metabolic rate (kcal/day). */
    fun bmr(weightKg: Double, heightCm: Double, age: Int, sex: Sex): Double {
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * age
        return if (sex == Sex.MALE) base + 5.0 else base - 161.0
    }

    fun tdee(weightKg: Double, heightCm: Double, age: Int, sex: Sex, activity: ActivityLevel): Double =
        bmr(weightKg, heightCm, age, sex) * activity.multiplier

    fun bmi(weightKg: Double, heightCm: Double): Double {
        val m = heightCm / 100.0
        return weightKg / (m * m)
    }

    fun weightForBmi(bmi: Double, heightCm: Double): Double {
        val m = heightCm / 100.0
        return bmi * m * m
    }

    /** Deurenberg BMI-based body-fat estimate (%). */
    fun bodyFatFromBmi(bmi: Double, age: Int, sex: Sex): Double {
        val s = if (sex == Sex.MALE) 1 else 0
        return 1.2 * bmi + 0.23 * age - 10.8 * s - 5.4
    }

    /** US Navy circumference body-fat estimate (%); null if inputs are missing/invalid. */
    fun bodyFatNavy(sex: Sex, heightCm: Double, waistCm: Double?, neckCm: Double?, hipsCm: Double?): Double? {
        if (waistCm == null || neckCm == null) return null
        return if (sex == Sex.MALE) {
            val d = waistCm - neckCm
            if (d <= 0) return null
            495.0 / (1.0324 - 0.19077 * log10(d) + 0.15456 * log10(heightCm)) - 450.0
        } else {
            if (hipsCm == null) return null
            val d = waistCm + hipsCm - neckCm
            if (d <= 0) return null
            495.0 / (1.29579 - 0.35004 * log10(d) + 0.22100 * log10(heightCm)) - 450.0
        }
    }

    fun calorieFloor(sex: Sex): Int =
        if (sex == Sex.MALE) AdaptiveConfig.MIN_KCAL_MALE else AdaptiveConfig.MIN_KCAL_FEMALE

    /** Planned weekly loss: the pace preference, capped at 1 % of current body weight. */
    fun plannedRateKg(pace: PacePreference, currentWeightKg: Double): Double =
        min(pace.kgPerWeek, currentWeightKg * AdaptiveConfig.MAX_PLANNED_LOSS_FRACTION)

    /** Daily deficit (kcal) for a weekly loss rate, capped at MAX_DEFICIT_FRACTION of TDEE. */
    fun dailyDeficit(rateKgPerWeek: Double, tdee: Double): Double =
        min(rateKgPerWeek * AdaptiveConfig.KCAL_PER_KG / 7.0, tdee * AdaptiveConfig.MAX_DEFICIT_FRACTION)

    fun roundKcal(kcal: Double): Int = ((kcal / 10.0).roundToInt()) * 10

    fun roundHalfKg(kg: Double): Double = (kg * 2.0).roundToInt() / 2.0

    fun clampTarget(kcal: Int, sex: Sex, tdee: Double): Int {
        val floor = calorieFloor(sex)
        val ceiling = max(floor, roundKcal(tdee))
        return kcal.coerceIn(floor, ceiling)
    }

    /** Estimated kcal burned by walking [steps] at [weightKg]. */
    fun stepKcal(steps: Int, weightKg: Double): Double =
        steps * AdaptiveConfig.KCAL_PER_STEP_70KG * (weightKg / 70.0)

    /** Daily protein target in grams, rounded to 5 g. */
    fun proteinTarget(goalWeightKg: Double): Int =
        (Math.round(goalWeightKg * AdaptiveConfig.PROTEIN_G_PER_KG_GOAL / 5.0) * 5).toInt()

    /** Days needed to lose [kg] at [ratePerWeek], rounded up to whole weeks. */
    fun daysNeeded(kg: Double, ratePerWeek: Double): Int {
        if (kg <= 0 || ratePerWeek <= 0) return 0
        return (ceil(kg / ratePerWeek) * 7).toInt()
    }
}

data class ProfileInputs(
    val heightCm: Double,
    val startWeightKg: Double,
    val targetWeightKg: Double,
    val age: Int,
    val sex: Sex,
    val activity: ActivityLevel,
    val pace: PacePreference,
    val durationDays: Int = AdaptiveConfig.DEFAULT_DURATION_DAYS,
)

data class InitialPlan(
    val bmr: Double,
    val tdee: Double,
    val plannedRateKg: Double,
    val dailyDeficit: Double,
    val targetKcal: Int,
    val requiredRateKg: Double,
    val projectedFinishKg: Double,
    val explanation: String,
)

object InitialPlanner {
    fun plan(p: ProfileInputs): InitialPlan {
        val bmr = BodyMath.bmr(p.startWeightKg, p.heightCm, p.age, p.sex)
        val tdee = bmr * p.activity.multiplier
        val rate = BodyMath.plannedRateKg(p.pace, p.startWeightKg)
        val deficit = BodyMath.dailyDeficit(rate, tdee)
        val target = BodyMath.clampTarget(BodyMath.roundKcal(tdee - deficit), p.sex, tdee)
        val effectiveDeficit = tdee - target
        val effectiveRate = effectiveDeficit * 7.0 / AdaptiveConfig.KCAL_PER_KG
        val weeks = p.durationDays / 7.0
        val toLose = (p.startWeightKg - p.targetWeightKg).coerceAtLeast(0.0)
        val required = if (weeks > 0) toLose / weeks else 0.0
        val projected = p.startWeightKg - effectiveRate * weeks
        val explanation = buildString {
            append("BMR ${fmtKcal(bmr)} × ${p.activity.multiplier} activity = TDEE ${fmtKcal(tdee)} kcal. ")
            append("${p.pace.label} pace aims for ${fmt1(rate)} kg/week, a ${fmtKcal(effectiveDeficit)} kcal/day deficit. ")
            append("Starting target: ${fmtKcal(target.toDouble())} kcal/day. ")
            append("Reaching ${fmt1(p.targetWeightKg)} kg in ${p.durationDays} days needs ${fmt2(required)} kg/week; ")
            append("at this pace you'd finish near ${fmt1(projected)} kg.")
        }
        return InitialPlan(bmr, tdee, rate, effectiveDeficit, target, required, projected, explanation)
    }
}

internal fun fmt1(v: Double): String = String.format(java.util.Locale.US, "%.1f", v)
internal fun fmt2(v: Double): String = String.format(java.util.Locale.US, "%.2f", v)
internal fun fmtKcal(v: Double): String = String.format(java.util.Locale.US, "%,d", v.roundToInt())
