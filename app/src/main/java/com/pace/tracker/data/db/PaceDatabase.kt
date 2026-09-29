package com.pace.tracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * Migration policy: the schema JSON for every version is exported to app/schemas/.
 * When changing an entity, bump [version] and append a [Migration] to [MIGRATIONS] –
 * the database is never destructively recreated, so logged data always survives upgrades.
 */
@Database(
    entities = [
        ProfileEntity::class,
        DailyLogEntity::class,
        MealEntity::class,
        WorkoutEntity::class,
        ProgressPhotoEntity::class,
        MeasurementEntity::class,
        RecalibrationEntity::class,
        WeeklyReflectionEntity::class,
        ReminderEntity::class,
        SettingEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class PaceDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun dailyDao(): DailyDao
    abstract fun bodyDao(): BodyDao
    abstract fun recalDao(): RecalDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        /** Add future migrations here, e.g. `object : Migration(1, 2) { ... }`. */
        val MIGRATIONS: Array<Migration> = emptyArray()

        fun build(context: Context): PaceDatabase =
            Room.databaseBuilder(context.applicationContext, PaceDatabase::class.java, "pace.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
