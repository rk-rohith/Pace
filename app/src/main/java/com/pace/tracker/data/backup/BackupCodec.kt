package com.pace.tracker.data.backup

import com.pace.tracker.data.db.DailyLogEntity
import com.pace.tracker.data.db.FoodItemEntity
import com.pace.tracker.data.db.MealEntity
import com.pace.tracker.data.db.MeasurementEntity
import com.pace.tracker.data.db.ProfileEntity
import com.pace.tracker.data.db.ProgressPhotoEntity
import com.pace.tracker.data.db.RecalibrationEntity
import com.pace.tracker.data.db.ReminderEntity
import com.pace.tracker.data.db.SettingEntity
import com.pace.tracker.data.db.WeeklyReflectionEntity
import com.pace.tracker.data.db.WorkoutEntity
import com.pace.tracker.domain.ActivityLevel
import com.pace.tracker.domain.MealType
import com.pace.tracker.domain.PacePreference
import com.pace.tracker.domain.PhotoPose
import com.pace.tracker.domain.RecalStatus
import com.pace.tracker.domain.Sex
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Every table in the database. Photo paths are stored as bare file names in the backup. */
data class BackupSnapshot(
    val profiles: List<ProfileEntity> = emptyList(),
    val logs: List<DailyLogEntity> = emptyList(),
    val meals: List<MealEntity> = emptyList(),
    val workouts: List<WorkoutEntity> = emptyList(),
    val photos: List<ProgressPhotoEntity> = emptyList(),
    val measurements: List<MeasurementEntity> = emptyList(),
    val recals: List<RecalibrationEntity> = emptyList(),
    val reflections: List<WeeklyReflectionEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
    val settings: List<SettingEntity> = emptyList(),
    val foods: List<FoodItemEntity> = emptyList(),
) {
    val summary: String
        get() = "${logs.size} days logged, ${meals.size} meals, ${workouts.size} workouts, " +
            "${photos.size} progress photos, ${recals.size} recalibrations"
}

class BackupFormatException(message: String) : Exception(message)

/** JSON encoding of [BackupSnapshot]. Unknown fields are ignored and missing optional fields default, so old backups keep restoring. */
object BackupCodec {
    const val FORMAT = "pace-backup"
    const val VERSION = 1

    // ---- helpers
    private fun JSONObject.putN(k: String, v: Any?): JSONObject = put(k, v ?: JSONObject.NULL)
    private fun JSONObject.dbl(k: String): Double? = if (!has(k) || isNull(k)) null else getDouble(k)
    private fun JSONObject.int(k: String): Int? = if (!has(k) || isNull(k)) null else getInt(k)
    private fun JSONObject.str(k: String): String? = if (!has(k) || isNull(k)) null else getString(k)
    private fun fileName(path: String?): String? = path?.let { File(it).name }
    private fun <T> JSONObject.list(key: String, read: (JSONObject) -> T): List<T> {
        val arr = optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).map { read(arr.getJSONObject(it)) }
    }
    private fun <T> arr(items: List<T>, write: (T) -> JSONObject) = JSONArray().apply { items.forEach { put(write(it)) } }

    fun encode(s: BackupSnapshot, createdAt: Long, appVersion: String): JSONObject = JSONObject()
        .put("format", FORMAT).put("version", VERSION).put("createdAt", createdAt).put("appVersion", appVersion)
        .put("profile", arr(s.profiles) { p ->
            JSONObject().put("id", p.id).put("heightCm", p.heightCm).put("startWeightKg", p.startWeightKg)
                .put("targetWeightKg", p.targetWeightKg).put("originalTargetWeightKg", p.originalTargetWeightKg)
                .put("age", p.age).put("sex", p.sex.name).put("activity", p.activity.name).put("pace", p.pace.name)
                .put("startEpochDay", p.startEpochDay).put("durationDays", p.durationDays).put("initialTdee", p.initialTdee)
                .put("initialTargetKcal", p.initialTargetKcal).put("currentTargetKcal", p.currentTargetKcal)
                .put("tdeeEstimate", p.tdeeEstimate).put("stepGoal", p.stepGoal).put("waterGoalGlasses", p.waterGoalGlasses)
                .put("workoutsPerWeek", p.workoutsPerWeek).put("dismissedSuggestionWeek", p.dismissedSuggestionWeek)
                .put("createdAt", p.createdAt)
        })
        .put("dailyLog", arr(s.logs) { l ->
            JSONObject().put("epochDay", l.epochDay).putN("weightKg", l.weightKg).putN("bodyFatPct", l.bodyFatPct)
                .put("waterGlasses", l.waterGlasses).putN("stepsManual", l.stepsManual).put("stepsSensor", l.stepsSensor)
                .putN("stepsHealthConnect", l.stepsHealthConnect).putN("mood", l.mood).putN("energy", l.energy)
                .putN("sleepHours", l.sleepHours).put("notes", l.notes).put("updatedAt", l.updatedAt)
        })
        .put("meal", arr(s.meals) { m ->
            JSONObject().put("id", m.id).put("epochDay", m.epochDay).put("type", m.type.name).put("description", m.description)
                .put("calories", m.calories).putN("protein", m.protein).putN("photo", fileName(m.photoPath)).put("createdAt", m.createdAt)
        })
        .put("workout", arr(s.workouts) { w ->
            JSONObject().put("id", w.id).put("epochDay", w.epochDay).put("type", w.type).put("durationMin", w.durationMin)
                .put("caloriesBurned", w.caloriesBurned).put("notes", w.notes).putN("photo", fileName(w.photoPath)).put("createdAt", w.createdAt)
        })
        .put("progressPhoto", arr(s.photos) { p ->
            JSONObject().put("id", p.id).put("epochDay", p.epochDay).put("pose", p.pose.name).put("photo", fileName(p.path)).put("createdAt", p.createdAt)
        })
        .put("measurement", arr(s.measurements) { m ->
            JSONObject().put("epochDay", m.epochDay).putN("chestCm", m.chestCm).putN("waistCm", m.waistCm).putN("hipsCm", m.hipsCm)
                .putN("armsCm", m.armsCm).putN("thighsCm", m.thighsCm).putN("neckCm", m.neckCm)
        })
        .put("recalibration", arr(s.recals) { r ->
            JSONObject().put("id", r.id).put("weekIndex", r.weekIndex).put("createdAt", r.createdAt).put("effectiveEpochDay", r.effectiveEpochDay)
                .put("status", r.status.name).putN("avgWeightKg", r.avgWeightKg).putN("prevAvgWeightKg", r.prevAvgWeightKg)
                .putN("lossKg", r.lossKg).putN("lossPct", r.lossPct).put("plannedRateKg", r.plannedRateKg)
                .putN("avgIntakeKcal", r.avgIntakeKcal).putN("avgSteps", r.avgSteps).put("workoutCount", r.workoutCount)
                .put("tdeeEstimate", r.tdeeEstimate).put("oldTargetKcal", r.oldTargetKcal).put("newTargetKcal", r.newTargetKcal)
                .putN("projectedFinishKg", r.projectedFinishKg).putN("suggestedTargetKg", r.suggestedTargetKg)
                .putN("suggestedDurationDays", r.suggestedDurationDays).put("explanation", r.explanation).put("seen", r.seen)
        })
        .put("weeklyReflection", arr(s.reflections) { r ->
            JSONObject().put("weekIndex", r.weekIndex).put("note", r.note).put("updatedAt", r.updatedAt)
        })
        .put("reminder", arr(s.reminders) { r ->
            JSONObject().put("key", r.key).put("enabled", r.enabled).put("hour", r.hour).put("minute", r.minute)
                .put("daysMask", r.daysMask).putN("intervalMinutes", r.intervalMinutes).putN("endHour", r.endHour).putN("endMinute", r.endMinute)
        })
        .put("setting", arr(s.settings) { JSONObject().put("key", it.key).put("value", it.value) })
        .put("foodItem", arr(s.foods) { f ->
            JSONObject().put("id", f.id).put("name", f.name).put("serving", f.serving).put("kcal", f.kcal)
                .put("protein", f.protein).put("createdAt", f.createdAt)
        })

    /** [photoPath] maps a stored photo file name to its absolute path on this device. */
    fun decode(root: JSONObject, photoPath: (String) -> String): BackupSnapshot {
        if (root.optString("format") != FORMAT) throw BackupFormatException("This file isn't a Pace backup.")
        if (root.optInt("version", 0) > VERSION) throw BackupFormatException("This backup was made by a newer version of Pace. Update the app first.")
        fun photo(o: JSONObject): String? = o.str("photo")?.let(photoPath)
        return BackupSnapshot(
            profiles = root.list("profile") { o ->
                ProfileEntity(
                    id = o.optInt("id", 1), heightCm = o.getDouble("heightCm"), startWeightKg = o.getDouble("startWeightKg"),
                    targetWeightKg = o.getDouble("targetWeightKg"), originalTargetWeightKg = o.optDouble("originalTargetWeightKg", o.getDouble("targetWeightKg")),
                    age = o.getInt("age"), sex = Sex.valueOf(o.getString("sex")), activity = ActivityLevel.valueOf(o.getString("activity")),
                    pace = PacePreference.valueOf(o.getString("pace")), startEpochDay = o.getLong("startEpochDay"),
                    durationDays = o.getInt("durationDays"), initialTdee = o.getDouble("initialTdee"),
                    initialTargetKcal = o.getInt("initialTargetKcal"), currentTargetKcal = o.getInt("currentTargetKcal"),
                    tdeeEstimate = o.getDouble("tdeeEstimate"), stepGoal = o.getInt("stepGoal"),
                    waterGoalGlasses = o.getInt("waterGoalGlasses"), workoutsPerWeek = o.optInt("workoutsPerWeek", 3),
                    dismissedSuggestionWeek = o.optInt("dismissedSuggestionWeek", 0), createdAt = o.optLong("createdAt", 0),
                )
            },
            logs = root.list("dailyLog") { o ->
                DailyLogEntity(
                    epochDay = o.getLong("epochDay"), weightKg = o.dbl("weightKg"), bodyFatPct = o.dbl("bodyFatPct"),
                    waterGlasses = o.optInt("waterGlasses", 0), stepsManual = o.int("stepsManual"), stepsSensor = o.optInt("stepsSensor", 0),
                    stepsHealthConnect = o.int("stepsHealthConnect"), mood = o.int("mood"), energy = o.int("energy"),
                    sleepHours = o.dbl("sleepHours"), notes = o.optString("notes", ""), updatedAt = o.optLong("updatedAt", 0),
                )
            },
            meals = root.list("meal") { o ->
                MealEntity(
                    id = o.getLong("id"), epochDay = o.getLong("epochDay"), type = MealType.valueOf(o.getString("type")),
                    description = o.optString("description", ""), calories = o.getInt("calories"), protein = o.dbl("protein"),
                    photoPath = photo(o), createdAt = o.optLong("createdAt", 0),
                )
            },
            workouts = root.list("workout") { o ->
                WorkoutEntity(
                    id = o.getLong("id"), epochDay = o.getLong("epochDay"), type = o.getString("type"), durationMin = o.getInt("durationMin"),
                    caloriesBurned = o.getInt("caloriesBurned"), notes = o.optString("notes", ""), photoPath = photo(o),
                    createdAt = o.optLong("createdAt", 0),
                )
            },
            photos = root.list("progressPhoto") { o ->
                ProgressPhotoEntity(
                    id = o.getLong("id"), epochDay = o.getLong("epochDay"), pose = PhotoPose.valueOf(o.getString("pose")),
                    path = photo(o) ?: "", createdAt = o.optLong("createdAt", 0),
                )
            }.filter { it.path.isNotEmpty() },
            measurements = root.list("measurement") { o ->
                MeasurementEntity(
                    epochDay = o.getLong("epochDay"), chestCm = o.dbl("chestCm"), waistCm = o.dbl("waistCm"), hipsCm = o.dbl("hipsCm"),
                    armsCm = o.dbl("armsCm"), thighsCm = o.dbl("thighsCm"), neckCm = o.dbl("neckCm"),
                )
            },
            recals = root.list("recalibration") { o ->
                RecalibrationEntity(
                    id = o.getLong("id"), weekIndex = o.getInt("weekIndex"), createdAt = o.optLong("createdAt", 0),
                    effectiveEpochDay = o.getLong("effectiveEpochDay"), status = RecalStatus.valueOf(o.getString("status")),
                    avgWeightKg = o.dbl("avgWeightKg"), prevAvgWeightKg = o.dbl("prevAvgWeightKg"), lossKg = o.dbl("lossKg"),
                    lossPct = o.dbl("lossPct"), plannedRateKg = o.getDouble("plannedRateKg"), avgIntakeKcal = o.dbl("avgIntakeKcal"),
                    avgSteps = o.dbl("avgSteps"), workoutCount = o.optInt("workoutCount", 0), tdeeEstimate = o.getDouble("tdeeEstimate"),
                    oldTargetKcal = o.getInt("oldTargetKcal"), newTargetKcal = o.getInt("newTargetKcal"),
                    projectedFinishKg = o.dbl("projectedFinishKg"), suggestedTargetKg = o.dbl("suggestedTargetKg"),
                    suggestedDurationDays = o.int("suggestedDurationDays"), explanation = o.optString("explanation", ""),
                    seen = o.optBoolean("seen", true),
                )
            },
            reflections = root.list("weeklyReflection") { o ->
                WeeklyReflectionEntity(o.getInt("weekIndex"), o.optString("note", ""), o.optLong("updatedAt", 0))
            },
            reminders = root.list("reminder") { o ->
                ReminderEntity(
                    key = o.getString("key"), enabled = o.getBoolean("enabled"), hour = o.getInt("hour"), minute = o.getInt("minute"),
                    daysMask = o.optInt("daysMask", 0x7F), intervalMinutes = o.int("intervalMinutes"), endHour = o.int("endHour"),
                    endMinute = o.int("endMinute"),
                )
            },
            settings = root.list("setting") { o -> SettingEntity(o.getString("key"), o.optString("value", "")) },
            foods = root.list("foodItem") { o ->
                FoodItemEntity(
                    id = o.getLong("id"), name = o.getString("name"), serving = o.optString("serving", "1 serving"),
                    kcal = o.getInt("kcal"), protein = o.optDouble("protein", 0.0), createdAt = o.optLong("createdAt", 0),
                )
            },
        )
    }
}
