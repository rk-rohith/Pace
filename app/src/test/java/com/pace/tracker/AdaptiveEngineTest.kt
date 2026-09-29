package com.pace.tracker

import com.pace.tracker.domain.ActivityLevel
import com.pace.tracker.domain.AdaptiveEngine
import com.pace.tracker.domain.BodyMath
import com.pace.tracker.domain.DayFacts
import com.pace.tracker.domain.EngineProfile
import com.pace.tracker.domain.EngineState
import com.pace.tracker.domain.InitialPlanner
import com.pace.tracker.domain.PacePreference
import com.pace.tracker.domain.PaceIndicator
import com.pace.tracker.domain.PriorWeek
import com.pace.tracker.domain.ProfileInputs
import com.pace.tracker.domain.RecalStatus
import com.pace.tracker.domain.Sex
import com.pace.tracker.domain.StreakCalculator
import com.pace.tracker.domain.Trajectory
import com.pace.tracker.domain.WeekInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveEngineTest {

    private val profile = EngineProfile(
        heightCm = 178.0, age = 30, sex = Sex.MALE, activity = ActivityLevel.LIGHT,
        pace = PacePreference.MODERATE, startWeightKg = 90.0, targetWeightKg = 81.5,
        startEpochDay = 20_000, durationDays = 80,
    )
    private val state = EngineState(currentTargetKcal = 1900, tdeeEstimate = 2700.0, priorWeeks = emptyList())

    private fun week(i: Int, avg: Double, intake: Int? = 1900) = WeekInput(
        weekIndex = i,
        weights = listOf(avg + 0.2, avg, avg - 0.2, avg),
        dailyIntakeKcal = if (intake == null) emptyList() else List(6) { intake },
        avgSteps = 7000.0, workoutCount = 3, workoutKcal = 900,
    )

    @Test
    fun bmrMatchesMifflinStJeor() {
        assertEquals(1867.5, BodyMath.bmr(90.0, 178.0, 30, Sex.MALE), 0.01)
        assertEquals(1701.5, BodyMath.bmr(90.0, 178.0, 30, Sex.FEMALE), 0.01)
    }

    @Test
    fun initialPlanUsesPaceAndFloors() {
        val plan = InitialPlanner.plan(
            ProfileInputs(178.0, 90.0, 81.5, 30, Sex.MALE, ActivityLevel.LIGHT, PacePreference.MODERATE),
        )
        assertEquals(0.8, plan.plannedRateKg, 1e-9)
        // TDEE 2568 - 880 deficit ≈ 1688 → rounded to 10
        assertEquals(1690, plan.targetKcal)
        assertTrue(plan.explanation.contains("TDEE"))
    }

    @Test
    fun onPaceKeepsTarget() {
        val r = AdaptiveEngine.recalibrate(profile, state, week(1, 89.2))
        assertEquals(RecalStatus.ON_PACE, r.status)
        assertEquals(1900, r.newTargetKcal)
        assertTrue(r.explanation.contains("Target stays at 1,900 kcal"))
    }

    @Test
    fun tooFastEasesDeficit() {
        val prior = state.copy(priorWeeks = listOf(PriorWeek(1, 89.0, 1.0)))
        val r = AdaptiveEngine.recalibrate(profile, prior, week(2, 87.8))
        assertEquals(RecalStatus.TOO_FAST, r.status)
        assertEquals(2050, r.newTargetKcal)
    }

    @Test
    fun firstWeekWaterWeightAllowance() {
        // 1.2 kg = 1.33 % in week 1 → under the 1.5 % first-week allowance → not "too fast".
        val r = AdaptiveEngine.recalibrate(profile, state, week(1, 88.8))
        assertTrue(r.status != RecalStatus.TOO_FAST)
    }

    @Test
    fun twoStallWeeksTightens() {
        val prior = state.copy(priorWeeks = listOf(PriorWeek(1, 89.2, 0.8), PriorWeek(2, 89.0, 0.2)))
        val r = AdaptiveEngine.recalibrate(profile, prior, week(3, 88.9))
        assertEquals(RecalStatus.STALLED, r.status)
        assertEquals(1750, r.newTargetKcal)
    }

    @Test
    fun singleSlowWeekIsNotStall() {
        val prior = state.copy(priorWeeks = listOf(PriorWeek(1, 89.2, 0.8)))
        val r = AdaptiveEngine.recalibrate(profile, prior, week(2, 89.1))
        assertEquals(RecalStatus.BEHIND, r.status)
    }

    @Test
    fun stallAtFloorSuggestsActivity() {
        val lowState = EngineState(1500, 2500.0, listOf(PriorWeek(1, 89.2, 0.8), PriorWeek(2, 89.1, 0.1)))
        val r = AdaptiveEngine.recalibrate(profile, lowState, week(3, 89.0))
        assertEquals(1500, r.newTargetKcal)
        assertTrue(r.explanation.contains("safety floor"))
    }

    @Test
    fun aheadOffersMoreCutdown() {
        val prior = state.copy(priorWeeks = listOf(PriorWeek(1, 89.0, 1.0), PriorWeek(2, 88.1, 0.9)))
        val r = AdaptiveEngine.recalibrate(profile, prior, week(3, 87.3))
        assertEquals(RecalStatus.AHEAD, r.status)
        assertNotNull(r.suggestedTargetKg)
        assertTrue(r.suggestedTargetKg!! < profile.targetWeightKg)
        assertTrue(r.suggestedTargetKg!! >= BodyMath.weightForBmi(20.0, 178.0) - 0.5)
    }

    @Test
    fun insufficientDataHolds() {
        val w = WeekInput(1, listOf(89.0), emptyList(), null, 0, 0)
        val r = AdaptiveEngine.recalibrate(profile, state, w)
        assertEquals(RecalStatus.INSUFFICIENT_DATA, r.status)
        assertEquals(1900, r.newTargetKcal)
        assertNull(r.lossKg)
    }

    @Test
    fun goalReachedSuggestsExtension() {
        val r = AdaptiveEngine.recalibrate(profile, state.copy(priorWeeks = listOf(PriorWeek(10, 82.0, 0.8))), week(11, 81.4))
        assertEquals(RecalStatus.GOAL_REACHED, r.status)
        assertNotNull(r.suggestedDurationDays)
    }

    @Test
    fun trajectoryReanchorsAndIndicatesPace() {
        val anchors = Trajectory.anchors(profile, listOf(PriorWeek(1, 89.0, 1.0)))
        val mid = profile.weekMid(2)
        val expected = Trajectory.expected(profile, anchors, mid)
        assertEquals(89.0 - 0.8, expected, 1e-6)
        assertEquals(PaceIndicator.AHEAD, Trajectory.paceIndicator(profile, anchors, 87.5, mid))
        assertEquals(PaceIndicator.ON_PACE, Trajectory.paceIndicator(profile, anchors, 88.3, mid))
        assertEquals(PaceIndicator.BEHIND, Trajectory.paceIndicator(profile, anchors, 88.9, mid))
    }

    @Test
    fun streaksCountBackFromYesterdayWhenTodayPending() {
        val start = 100L
        val days = (100L..110L).associateWith {
            DayFacts(it, logged = it != 104L && it != 110L, steps = 9000, stepGoal = 8000,
                intakeKcal = 1800, targetKcal = 1900, workoutCount = if (it % 2 == 0L) 1 else 0)
        }
        val s = StreakCalculator.summarize(days, start, 110L, 3)
        assertEquals(5, s.logging.current) // 105..109, today (110) pending
        assertEquals(5, s.logging.best)
        assertEquals(11, s.steps.current)
        assertEquals(11, s.calories.best)
        assertEquals(1, s.workouts.current) // week 0 had 4 workouts; week 1 in progress with 2
    }
}

class ReminderTimingTest {
    private val monday0800 = java.time.LocalDateTime.of(2026, 9, 28, 8, 0) // a Monday

    @Test
    fun dailyLaterToday() {
        val n = com.pace.tracker.domain.ReminderTiming.next(monday0800, 9, 30, 0x7F)
        assertEquals(java.time.LocalDateTime.of(2026, 9, 28, 9, 30), n)
    }

    @Test
    fun dailyPassedGoesTomorrow() {
        val n = com.pace.tracker.domain.ReminderTiming.next(monday0800, 7, 0, 0x7F)
        assertEquals(java.time.LocalDateTime.of(2026, 9, 29, 7, 0), n)
    }

    @Test
    fun weeklySunday() {
        val n = com.pace.tracker.domain.ReminderTiming.next(monday0800, 9, 0, 1 shl 6)
        assertEquals(java.time.LocalDateTime.of(2026, 10, 4, 9, 0), n)
    }

    @Test
    fun intervalWithinWindow() {
        val n = com.pace.tracker.domain.ReminderTiming.next(monday0800.withHour(10).withMinute(5), 9, 0, 0x7F, 90, 21, 0)
        assertEquals(java.time.LocalDateTime.of(2026, 9, 28, 10, 30), n)
        val late = com.pace.tracker.domain.ReminderTiming.next(monday0800.withHour(21).withMinute(1), 9, 0, 0x7F, 90, 21, 0)
        assertEquals(java.time.LocalDateTime.of(2026, 9, 29, 9, 0), late)
    }

    @Test
    fun noDaysMeansNever() {
        assertNull(com.pace.tracker.domain.ReminderTiming.next(monday0800, 9, 0, 0))
    }
}
