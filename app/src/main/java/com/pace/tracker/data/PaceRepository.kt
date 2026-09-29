package com.pace.tracker.data

import com.pace.tracker.data.db.DailyLogEntity
import com.pace.tracker.data.db.MealEntity
import com.pace.tracker.data.db.MeasurementEntity
import com.pace.tracker.data.db.PaceDatabase
import com.pace.tracker.data.db.ProfileEntity
import com.pace.tracker.data.db.ProgressPhotoEntity
import com.pace.tracker.data.db.RecalibrationEntity
import com.pace.tracker.data.db.ReminderEntity
import com.pace.tracker.data.db.SettingEntity
import com.pace.tracker.data.db.WeeklyReflectionEntity
import com.pace.tracker.data.db.WorkoutEntity
import com.pace.tracker.domain.AdaptiveConfig
import com.pace.tracker.domain.AdaptiveEngine
import com.pace.tracker.domain.BodyMath
import com.pace.tracker.domain.EngineState
import com.pace.tracker.domain.InitialPlanner
import com.pace.tracker.domain.PhotoPose
import com.pace.tracker.domain.PriorWeek
import com.pace.tracker.domain.ProfileInputs
import com.pace.tracker.domain.RecalStatus
import com.pace.tracker.domain.WeekInput
import com.pace.tracker.photo.PhotoStorage
import com.pace.tracker.reminders.ReminderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

fun today(): Long = LocalDate.now().toEpochDay()

class PaceRepository(
    private val db: PaceDatabase,
    private val photos: PhotoStorage,
) {
    private val profileDao = db.profileDao()
    private val dailyDao = db.dailyDao()
    private val bodyDao = db.bodyDao()
    private val recalDao = db.recalDao()
    private val settingsDao = db.settingsDao()
    private val recalMutex = Mutex()
    private val logMutex = Mutex()

    val profile: Flow<ProfileEntity?> = profileDao.observe()

    /** Everything, combined. Screens derive their state from this. */
    val programData: Flow<ProgramData> = run {
        val a = combine(
            profileDao.observe(),
            dailyDao.observeAllLogs(),
            dailyDao.observeDailyCalories(),
            dailyDao.observeDailyWorkouts(),
        ) { p, logs, cals, wk ->
            ProgramData(
                profile = p,
                logs = logs.associateBy { it.epochDay },
                calories = cals.associate { it.epochDay to it.total },
                workouts = wk.associateBy { it.epochDay },
            )
        }
        combine(
            a,
            bodyDao.observePhotos(),
            bodyDao.observeMeasurements(),
            recalDao.observeAll(),
            recalDao.observeReflections(),
        ) { base, ph, ms, rc, rf ->
            base.copy(
                photos = ph, measurements = ms, recals = rc,
                reflections = rf.associate { it.weekIndex to it.note },
            )
        }
    }

    // ---------------------------------------------------------------- profile & plan

    suspend fun createProfile(inputs: ProfileInputs, stepGoal: Int, waterGoal: Int, startDay: Long = today()) {
        val plan = InitialPlanner.plan(inputs)
        val profile = ProfileEntity(
            heightCm = inputs.heightCm, startWeightKg = inputs.startWeightKg,
            targetWeightKg = inputs.targetWeightKg, originalTargetWeightKg = inputs.targetWeightKg,
            age = inputs.age, sex = inputs.sex, activity = inputs.activity, pace = inputs.pace,
            startEpochDay = startDay, durationDays = inputs.durationDays,
            initialTdee = plan.tdee, initialTargetKcal = plan.targetKcal,
            currentTargetKcal = plan.targetKcal, tdeeEstimate = plan.tdee,
            stepGoal = stepGoal, waterGoalGlasses = waterGoal,
            workoutsPerWeek = AdaptiveConfig.DEFAULT_WORKOUTS_PER_WEEK,
            createdAt = System.currentTimeMillis(),
        )
        profileDao.upsert(profile)
        recalDao.insert(
            RecalibrationEntity(
                weekIndex = 0, createdAt = System.currentTimeMillis(), effectiveEpochDay = startDay,
                status = RecalStatus.START, avgWeightKg = inputs.startWeightKg, prevAvgWeightKg = null,
                lossKg = null, lossPct = null, plannedRateKg = plan.plannedRateKg, avgIntakeKcal = null,
                avgSteps = null, workoutCount = 0, tdeeEstimate = plan.tdee, oldTargetKcal = plan.targetKcal,
                newTargetKcal = plan.targetKcal, projectedFinishKg = plan.projectedFinishKg,
                suggestedTargetKg = null, suggestedDurationDays = null, explanation = plan.explanation, seen = true,
            ),
        )
        // Seed the start weight as today's weigh-in so charts have a first point.
        updateLog(startDay) { it.copy(weightKg = it.weightKg ?: inputs.startWeightKg) }
        // The weekly recalibration reminder defaults to the weekday each programme week completes.
        val recalDayBit = 1 shl (LocalDate.ofEpochDay(startDay).dayOfWeek.value - 1)
        ReminderType.entries.forEach { type ->
            if (settingsDao.reminder(type.key) == null) {
                settingsDao.upsertReminder(type.defaultEntity(if (type == ReminderType.RECAL) recalDayBit else null))
            }
        }
    }

    /** Profile edits. If anything affecting energy needs changes, a new target is computed and logged. */
    suspend fun updateProfile(edited: ProfileEntity) {
        val old = profileDao.get() ?: return
        val energyChanged = old.heightCm != edited.heightCm || old.age != edited.age || old.sex != edited.sex ||
            old.activity != edited.activity || old.pace != edited.pace
        var p = edited
        if (energyChanged) {
            val weight = latestWeight() ?: edited.startWeightKg
            val plan = InitialPlanner.plan(
                ProfileInputs(edited.heightCm, weight, edited.targetWeightKg, edited.age, edited.sex,
                    edited.activity, edited.pace, edited.durationDays),
            )
            p = edited.copy(currentTargetKcal = plan.targetKcal, tdeeEstimate = plan.tdee)
            recalDao.insert(
                RecalibrationEntity(
                    weekIndex = 0, createdAt = System.currentTimeMillis(), effectiveEpochDay = today(),
                    status = RecalStatus.PROFILE_UPDATE, avgWeightKg = weight, prevAvgWeightKg = null, lossKg = null,
                    lossPct = null, plannedRateKg = plan.plannedRateKg, avgIntakeKcal = null, avgSteps = null,
                    workoutCount = 0, tdeeEstimate = plan.tdee, oldTargetKcal = old.currentTargetKcal,
                    newTargetKcal = plan.targetKcal, projectedFinishKg = null, suggestedTargetKg = null,
                    suggestedDurationDays = null,
                    explanation = "Profile updated (${edited.activity.label} activity, ${edited.pace.label} pace, " +
                        "${fmtKg(weight)} kg). " + plan.explanation,
                    seen = true,
                ),
            )
        }
        profileDao.upsert(p)
    }

    private suspend fun latestWeight(): Double? =
        dailyDao.allLogs().lastOrNull { it.weightKg != null }?.weightKg

    // ---------------------------------------------------------------- daily log

    fun observeLog(day: Long) = dailyDao.observeLog(day)
    fun observeMeals(day: Long) = dailyDao.observeMeals(day)
    fun observeWorkouts(day: Long) = dailyDao.observeWorkouts(day)

    /** Read-modify-write of a day's log, serialised so concurrent edits never clobber each other. */
    suspend fun updateLog(day: Long, transform: (DailyLogEntity) -> DailyLogEntity) = logMutex.withLock {
        val current = dailyDao.getLog(day) ?: DailyLogEntity(epochDay = day)
        val next = transform(current).copy(epochDay = day, updatedAt = System.currentTimeMillis())
        if (next != current) dailyDao.upsertLog(next)
    }

    suspend fun addSensorSteps(day: Long, steps: Int) {
        if (steps <= 0) return
        updateLog(day) { it.copy(stepsSensor = it.stepsSensor + steps) }
    }

    suspend fun setHealthConnectSteps(day: Long, steps: Int) {
        updateLog(day) { it.copy(stepsHealthConnect = steps) }
    }

    suspend fun saveMeal(meal: MealEntity) {
        dailyDao.upsertMeal(meal)
    }

    suspend fun deleteMeal(meal: MealEntity) {
        dailyDao.deleteMeal(meal)
        meal.photoPath?.let { photos.delete(it) }
    }

    suspend fun saveWorkout(w: WorkoutEntity) {
        dailyDao.upsertWorkout(w)
    }

    suspend fun deleteWorkout(w: WorkoutEntity) {
        dailyDao.deleteWorkout(w)
        w.photoPath?.let { photos.delete(it) }
    }

    // ---------------------------------------------------------------- body

    suspend fun addProgressPhoto(day: Long, pose: PhotoPose, path: String) {
        bodyDao.insertPhoto(ProgressPhotoEntity(epochDay = day, pose = pose, path = path))
    }

    suspend fun deleteProgressPhoto(photo: ProgressPhotoEntity) {
        bodyDao.deletePhoto(photo)
        photos.delete(photo.path)
    }

    suspend fun saveMeasurement(m: MeasurementEntity) = bodyDao.upsertMeasurement(m)
    suspend fun deleteMeasurement(m: MeasurementEntity) = bodyDao.deleteMeasurement(m)

    // ---------------------------------------------------------------- reflections & settings

    suspend fun saveReflection(week: Int, note: String) =
        recalDao.upsertReflection(WeeklyReflectionEntity(week, note))

    suspend fun markRecalsSeen() = recalDao.markAllSeen()

    val reminders = settingsDao.observeReminders()
    suspend fun reminders(): List<ReminderEntity> = settingsDao.reminders()
    suspend fun reminder(key: String): ReminderEntity? = settingsDao.reminder(key)
    suspend fun saveReminder(r: ReminderEntity) = settingsDao.upsertReminder(r)

    val settings: Flow<Map<String, String>> =
        settingsDao.observeSettings().map { list -> list.associate { it.key to it.value } }
    suspend fun setting(key: String): String? = settingsDao.setting(key)
    suspend fun setSetting(key: String, value: String) = settingsDao.upsertSetting(SettingEntity(key, value))

    suspend fun getLog(day: Long) = dailyDao.getLog(day)
    suspend fun mealsOn(day: Long) = dailyDao.mealsBetween(day, day)
    suspend fun workoutsOn(day: Long) = dailyDao.workoutsBetween(day, day)
    suspend fun getProfile() = profileDao.get()

    // ---------------------------------------------------------------- goal suggestion

    suspend fun acceptSuggestion(recal: RecalibrationEntity) {
        val p = profileDao.get() ?: return
        val target = recal.suggestedTargetKg ?: return
        val current = latestWeight() ?: recal.avgWeightKg ?: p.startWeightKg
        val newDuration = AdaptiveEngine.acceptSuggestion(p.toEngine(), current, today(), target)
        profileDao.upsert(p.copy(targetWeightKg = target, durationDays = newDuration, dismissedSuggestionWeek = recal.weekIndex))
        val goalDate = LocalDate.ofEpochDay(p.startEpochDay + newDuration).format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
        recalDao.insert(
            RecalibrationEntity(
                weekIndex = 0, createdAt = System.currentTimeMillis(), effectiveEpochDay = today(),
                status = RecalStatus.GOAL_EXTENDED, avgWeightKg = current, prevAvgWeightKg = null, lossKg = null,
                lossPct = null, plannedRateKg = BodyMath.plannedRateKg(p.pace, current), avgIntakeKcal = null,
                avgSteps = null, workoutCount = 0, tdeeEstimate = p.tdeeEstimate,
                oldTargetKcal = p.currentTargetKcal, newTargetKcal = p.currentTargetKcal,
                projectedFinishKg = target, suggestedTargetKg = null, suggestedDurationDays = null,
                explanation = "More cutdown accepted: goal moved from ${fmtKg(p.targetWeightKg)} kg to ${fmtKg(target)} kg. " +
                    (if (newDuration > p.durationDays) "Timeline extended to $newDuration days (until $goalDate). " else "Goal date unchanged ($goalDate). ") +
                    "Calorie target stays at ${p.currentTargetKcal} kcal — the weekly engine keeps adapting.",
                seen = true,
            ),
        )
    }

    suspend fun dismissSuggestion(recal: RecalibrationEntity) {
        val p = profileDao.get() ?: return
        profileDao.upsert(p.copy(dismissedSuggestionWeek = recal.weekIndex))
    }

    // ---------------------------------------------------------------- adaptive engine

    /**
     * Runs the weekly recalibration for every completed programme week that doesn't have one yet
     * (catches up if the app wasn't opened). Idempotent and safe to call from anywhere.
     */
    suspend fun runPendingRecalibrations(todayDay: Long = today()): List<RecalibrationEntity> = recalMutex.withLock {
        var profile = profileDao.get() ?: return@withLock emptyList()
        val completedWeeks = ((todayDay - profile.startEpochDay) / 7).toInt()
        if (completedWeeks < 1) return@withLock emptyList()
        val existing = recalDao.all().filter { it.weekIndex > 0 }.associateBy { it.weekIndex }.toMutableMap()
        val created = mutableListOf<RecalibrationEntity>()
        for (week in 1..completedWeeks) {
            if (existing.containsKey(week)) continue
            val ep = profile.toEngine()
            val start = ep.weekStart(week)
            val end = ep.weekEnd(week)
            val logs = dailyDao.logsBetween(start, end)
            val meals = dailyDao.mealsBetween(start, end)
            val workouts = dailyDao.workoutsBetween(start, end)
            val intake = meals.groupBy { it.epochDay }.values.map { list -> list.sumOf { it.calories } }.filter { it > 0 }
            val steps = logs.mapNotNull { it.effectiveSteps }
            val input = WeekInput(
                weekIndex = week,
                weights = logs.mapNotNull { it.weightKg },
                dailyIntakeKcal = intake,
                avgSteps = steps.takeIf { it.isNotEmpty() }?.average(),
                workoutCount = workouts.size,
                workoutKcal = workouts.sumOf { it.caloriesBurned },
            )
            val prior = existing.values.filter { it.weekIndex < week }.sortedBy { it.weekIndex }.map {
                PriorWeek(it.weekIndex, if (it.status == RecalStatus.INSUFFICIENT_DATA) null else it.avgWeightKg, it.lossKg)
            }
            val state = EngineState(profile.currentTargetKcal, profile.tdeeEstimate, prior)
            val r = AdaptiveEngine.recalibrate(ep, state, input)
            val entity = RecalibrationEntity(
                weekIndex = week, createdAt = System.currentTimeMillis(), effectiveEpochDay = end + 1,
                status = r.status, avgWeightKg = r.avgWeightKg, prevAvgWeightKg = r.prevAvgWeightKg,
                lossKg = r.lossKg, lossPct = r.lossPctOfBodyWeight, plannedRateKg = r.plannedRateKg,
                avgIntakeKcal = r.avgIntakeKcal, avgSteps = r.avgSteps, workoutCount = r.workoutCount,
                tdeeEstimate = r.tdeeEstimate, oldTargetKcal = r.oldTargetKcal, newTargetKcal = r.newTargetKcal,
                projectedFinishKg = r.projectedFinishKg, suggestedTargetKg = r.suggestedTargetKg,
                suggestedDurationDays = r.suggestedDurationDays, explanation = r.explanation,
            )
            val id = recalDao.insert(entity)
            val saved = entity.copy(id = id)
            existing[week] = saved
            created += saved
            profile = profile.copy(currentTargetKcal = r.newTargetKcal, tdeeEstimate = r.tdeeEstimate)
            profileDao.upsert(profile)
        }
        created
    }

    suspend fun allRecals(): List<RecalibrationEntity> = recalDao.all()
}

fun fmtKg(v: Double): String = String.format(Locale.US, "%.1f", v)
