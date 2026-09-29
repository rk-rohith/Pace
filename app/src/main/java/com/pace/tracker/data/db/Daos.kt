package com.pace.tracker.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun get(): ProfileEntity?

    @Upsert
    suspend fun upsert(profile: ProfileEntity)
}

/** Row type for per-day calorie totals. */
data class DayCalories(val epochDay: Long, val total: Int)

/** Row type for per-day workout aggregates. */
data class DayWorkouts(val epochDay: Long, val count: Int, val kcal: Int, val minutes: Int)

@Dao
interface DailyDao {
    @Query("SELECT * FROM daily_log WHERE epochDay = :day")
    fun observeLog(day: Long): Flow<DailyLogEntity?>

    @Query("SELECT * FROM daily_log WHERE epochDay = :day")
    suspend fun getLog(day: Long): DailyLogEntity?

    @Query("SELECT * FROM daily_log ORDER BY epochDay")
    fun observeAllLogs(): Flow<List<DailyLogEntity>>

    @Query("SELECT * FROM daily_log WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay")
    suspend fun logsBetween(from: Long, to: Long): List<DailyLogEntity>

    @Query("SELECT * FROM daily_log ORDER BY epochDay")
    suspend fun allLogs(): List<DailyLogEntity>

    @Upsert
    suspend fun upsertLog(log: DailyLogEntity)

    // Meals
    @Query("SELECT * FROM meal WHERE epochDay = :day ORDER BY type, createdAt")
    fun observeMeals(day: Long): Flow<List<MealEntity>>

    @Query("SELECT * FROM meal WHERE epochDay BETWEEN :from AND :to")
    suspend fun mealsBetween(from: Long, to: Long): List<MealEntity>

    @Query("SELECT epochDay, SUM(calories) AS total FROM meal GROUP BY epochDay ORDER BY epochDay")
    fun observeDailyCalories(): Flow<List<DayCalories>>

    @Query("SELECT epochDay, SUM(calories) AS total FROM meal GROUP BY epochDay ORDER BY epochDay")
    suspend fun dailyCalories(): List<DayCalories>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeal(meal: MealEntity): Long

    @Delete
    suspend fun deleteMeal(meal: MealEntity)

    // Workouts
    @Query("SELECT * FROM workout WHERE epochDay = :day ORDER BY createdAt")
    fun observeWorkouts(day: Long): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workout WHERE epochDay BETWEEN :from AND :to")
    suspend fun workoutsBetween(from: Long, to: Long): List<WorkoutEntity>

    @Query(
        "SELECT epochDay, COUNT(*) AS count, SUM(caloriesBurned) AS kcal, SUM(durationMin) AS minutes " +
            "FROM workout GROUP BY epochDay ORDER BY epochDay",
    )
    fun observeDailyWorkouts(): Flow<List<DayWorkouts>>

    @Query(
        "SELECT epochDay, COUNT(*) AS count, SUM(caloriesBurned) AS kcal, SUM(durationMin) AS minutes " +
            "FROM workout GROUP BY epochDay ORDER BY epochDay",
    )
    suspend fun dailyWorkouts(): List<DayWorkouts>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkout(workout: WorkoutEntity): Long

    @Delete
    suspend fun deleteWorkout(workout: WorkoutEntity)
}

@Dao
interface BodyDao {
    @Query("SELECT * FROM progress_photo ORDER BY epochDay DESC, pose")
    fun observePhotos(): Flow<List<ProgressPhotoEntity>>

    @Query("SELECT * FROM progress_photo WHERE epochDay BETWEEN :from AND :to")
    suspend fun photosBetween(from: Long, to: Long): List<ProgressPhotoEntity>

    @Insert
    suspend fun insertPhoto(photo: ProgressPhotoEntity): Long

    @Delete
    suspend fun deletePhoto(photo: ProgressPhotoEntity)

    @Query("SELECT * FROM measurement ORDER BY epochDay")
    fun observeMeasurements(): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurement WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay")
    suspend fun measurementsBetween(from: Long, to: Long): List<MeasurementEntity>

    @Upsert
    suspend fun upsertMeasurement(m: MeasurementEntity)

    @Delete
    suspend fun deleteMeasurement(m: MeasurementEntity)
}

@Dao
interface RecalDao {
    @Query("SELECT * FROM recalibration ORDER BY effectiveEpochDay, id")
    fun observeAll(): Flow<List<RecalibrationEntity>>

    @Query("SELECT * FROM recalibration ORDER BY effectiveEpochDay, id")
    suspend fun all(): List<RecalibrationEntity>

    @Query("SELECT * FROM recalibration WHERE weekIndex = :week ORDER BY id DESC LIMIT 1")
    suspend fun forWeek(week: Int): RecalibrationEntity?

    @Insert
    suspend fun insert(r: RecalibrationEntity): Long

    @Query("UPDATE recalibration SET seen = 1 WHERE seen = 0")
    suspend fun markAllSeen()

    @Query("SELECT * FROM weekly_reflection")
    fun observeReflections(): Flow<List<WeeklyReflectionEntity>>

    @Upsert
    suspend fun upsertReflection(r: WeeklyReflectionEntity)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM reminder")
    fun observeReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminder")
    suspend fun reminders(): List<ReminderEntity>

    @Query("SELECT * FROM reminder WHERE `key` = :key")
    suspend fun reminder(key: String): ReminderEntity?

    @Upsert
    suspend fun upsertReminder(r: ReminderEntity)

    @Query("SELECT value FROM app_setting WHERE `key` = :key")
    suspend fun setting(key: String): String?

    @Query("SELECT * FROM app_setting")
    fun observeSettings(): Flow<List<SettingEntity>>

    @Upsert
    suspend fun upsertSetting(s: SettingEntity)
}
