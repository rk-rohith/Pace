package com.pace.tracker.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pace.tracker.domain.ActivityLevel
import com.pace.tracker.domain.MealType
import com.pace.tracker.domain.PacePreference
import com.pace.tracker.domain.PhotoPose
import com.pace.tracker.domain.RecalStatus
import com.pace.tracker.domain.Sex

/** Single-row table (id = 1). */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val heightCm: Double,
    val startWeightKg: Double,
    val targetWeightKg: Double,
    val originalTargetWeightKg: Double,
    val age: Int,
    val sex: Sex,
    val activity: ActivityLevel,
    val pace: PacePreference,
    val startEpochDay: Long,
    val durationDays: Int,
    val initialTdee: Double,
    val initialTargetKcal: Int,
    val currentTargetKcal: Int,
    val tdeeEstimate: Double,
    val stepGoal: Int,
    val waterGoalGlasses: Int,
    val workoutsPerWeek: Int,
    /** Week index whose "more cutdown" offer was dismissed (so it isn't shown again). */
    val dismissedSuggestionWeek: Int = 0,
    val createdAt: Long,
)

@Entity(tableName = "daily_log")
data class DailyLogEntity(
    @PrimaryKey val epochDay: Long,
    val weightKg: Double? = null,
    val bodyFatPct: Double? = null,
    val waterGlasses: Int = 0,
    val stepsManual: Int? = null,
    val stepsSensor: Int = 0,
    val stepsHealthConnect: Int? = null,
    val mood: Int? = null,
    val energy: Int? = null,
    val sleepHours: Double? = null,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Manual entry wins; otherwise the larger of Health Connect and the in-app sensor. */
val DailyLogEntity.effectiveSteps: Int?
    get() = stepsManual ?: when {
        stepsHealthConnect != null -> maxOf(stepsHealthConnect, stepsSensor)
        stepsSensor > 0 -> stepsSensor
        else -> null
    }

@Entity(tableName = "meal", indices = [Index("epochDay")])
data class MealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val type: MealType,
    val description: String,
    val calories: Int,
    val photoPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "workout", indices = [Index("epochDay")])
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val type: String,
    val durationMin: Int,
    val caloriesBurned: Int,
    val notes: String = "",
    val photoPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "progress_photo", indices = [Index("epochDay")])
data class ProgressPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val pose: PhotoPose,
    val path: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "measurement")
data class MeasurementEntity(
    @PrimaryKey val epochDay: Long,
    val chestCm: Double? = null,
    val waistCm: Double? = null,
    val hipsCm: Double? = null,
    val armsCm: Double? = null,
    val thighsCm: Double? = null,
    val neckCm: Double? = null,
)

/**
 * One row per calorie-target change. weekIndex > 0 for weekly recalibrations;
 * weekIndex = 0 for plan start, profile edits and accepted goal extensions.
 */
@Entity(tableName = "recalibration", indices = [Index("weekIndex")])
data class RecalibrationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekIndex: Int,
    val createdAt: Long,
    val effectiveEpochDay: Long,
    val status: RecalStatus,
    val avgWeightKg: Double?,
    val prevAvgWeightKg: Double?,
    val lossKg: Double?,
    val lossPct: Double?,
    val plannedRateKg: Double,
    val avgIntakeKcal: Double?,
    val avgSteps: Double?,
    val workoutCount: Int,
    val tdeeEstimate: Double,
    val oldTargetKcal: Int,
    val newTargetKcal: Int,
    val projectedFinishKg: Double?,
    val suggestedTargetKg: Double?,
    val suggestedDurationDays: Int?,
    val explanation: String,
    val seen: Boolean = false,
)

@Entity(tableName = "weekly_reflection")
data class WeeklyReflectionEntity(
    @PrimaryKey val weekIndex: Int,
    val note: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Reminder configuration. daysMask bit 0 = Monday … bit 6 = Sunday. */
@Entity(tableName = "reminder")
data class ReminderEntity(
    @PrimaryKey val key: String,
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    val daysMask: Int = 0x7F,
    val intervalMinutes: Int? = null,
    val endHour: Int? = null,
    val endMinute: Int? = null,
)

@Entity(tableName = "app_setting")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)
