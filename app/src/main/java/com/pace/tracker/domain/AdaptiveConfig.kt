package com.pace.tracker.domain

/**
 * Every tunable constant used by the adaptive engine lives here.
 * See README.md → "Adaptive formula" for how each one is used.
 */
object AdaptiveConfig {
    /** Energy content of 1 kg of body fat (kcal). */
    const val KCAL_PER_KG = 7700.0

    /** Default programme length in days (soft – can be extended). */
    const val DEFAULT_DURATION_DAYS = 80

    /** Safety cap: planned loss never exceeds this fraction of body weight per week. */
    const val MAX_PLANNED_LOSS_FRACTION = 0.01

    /** Actual loss above this % of body weight per week => "too fast", ease the deficit. */
    const val TOO_FAST_PCT = 1.0

    /** Week 1 allowance: early loss is mostly water/glycogen, so the threshold is higher. */
    const val TOO_FAST_PCT_FIRST_WEEK = 1.5

    /** Weekly loss below this (kg) counts as a "stall" week. */
    const val STALL_KG = 0.3

    /** Number of consecutive stall weeks before the deficit is increased. */
    const val STALL_WEEKS = 2

    /** Loss below plannedRate × this ratio => behind pace. */
    const val BEHIND_RATIO = 0.7

    /** Loss above plannedRate × this ratio => ahead of pace. */
    const val AHEAD_RATIO = 1.1

    /**
     * Distance (kg) from the original plan line that counts as cumulatively ahead/behind.
     * "Behind" only tightens calories when cumulatively behind by more than this;
     * being ahead by more than this also counts as "ahead" even in a normal week.
     */
    const val PACE_TOLERANCE_KG = 0.5

    /** Calorie change when losing too fast (added). */
    const val EASE_STEP_KCAL = 150

    /** Calorie change after a confirmed stall (subtracted). */
    const val STALL_STEP_KCAL = 150

    /** Calorie change when behind pace (subtracted). */
    const val BEHIND_STEP_KCAL = 75

    /** Maximum deficit as a fraction of TDEE. */
    const val MAX_DEFICIT_FRACTION = 0.35

    /** Absolute calorie floors. */
    const val MIN_KCAL_MALE = 1500
    const val MIN_KCAL_FEMALE = 1200

    /** Minimum weigh-ins in a week for the week to count. */
    const val MIN_WEIGH_INS = 3

    /** Minimum days with food logged before intake is used to estimate real TDEE. */
    const val MIN_INTAKE_DAYS = 4

    /** Weight given to the newest observed TDEE (exponential smoothing). */
    const val TDEE_SMOOTHING = 0.5

    /** Observed TDEE is clamped to formula TDEE × [1 - x, 1 + x] to reject logging noise. */
    const val TDEE_CLAMP_FRACTION = 0.25

    /** How many recent weeks form the "trend" rate used for projections. */
    const val TREND_WEEKS = 3

    /** Suggest a lower goal only if projected finish beats the current goal by this margin (kg). */
    const val EXTEND_MARGIN_KG = 0.5

    /** Never suggest a target weight below this BMI. */
    const val MIN_HEALTHY_BMI = 20.0

    /** Days added when the goal is reached/extended close to (or after) the goal date. */
    const val EXTENSION_DAYS = 28

    /** Home-screen pace band: within ± this many kg of the trajectory counts as on pace. */
    const val PACE_BAND_KG = 0.3

    /** Calorie adherence: day counts if intake ≤ target + this tolerance… */
    const val ADHERENCE_TOLERANCE_KCAL = 100

    /** …and intake ≥ this fraction of target (filters out half-logged days). */
    const val ADHERENCE_MIN_FRACTION = 0.5

    /** Rough energy cost per step for a 70 kg person (scaled by body weight). */
    const val KCAL_PER_STEP_70KG = 0.04

    /** Default daily step goal. */
    const val DEFAULT_STEP_GOAL = 8000

    /** Workouts per week needed to extend the weekly workout streak. */
    const val DEFAULT_WORKOUTS_PER_WEEK = 3
}
