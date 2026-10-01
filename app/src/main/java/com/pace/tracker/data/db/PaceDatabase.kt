package com.pace.tracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        FoodItemEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class PaceDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun dailyDao(): DailyDao
    abstract fun bodyDao(): BodyDao
    abstract fun recalDao(): RecalDao
    abstract fun settingsDao(): SettingsDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val SQL_ADD_MEAL_PROTEIN = "ALTER TABLE `meal` ADD COLUMN `protein` REAL"
        const val SQL_CREATE_FOOD_ITEM =
            "CREATE TABLE IF NOT EXISTS `food_item` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `serving` TEXT NOT NULL, `kcal` INTEGER NOT NULL, " +
                "`protein` REAL NOT NULL, `createdAt` INTEGER NOT NULL)"

        /** v1 → v2: protein per meal + saved foods for quick-add. Checked against the exported schema by DatabaseMigrationTest. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(SQL_ADD_MEAL_PROTEIN)
                db.execSQL(SQL_CREATE_FOOD_ITEM)
            }
        }

        /** Append future migrations here; the database is never destructively recreated. */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        fun build(context: Context): PaceDatabase =
            Room.databaseBuilder(context.applicationContext, PaceDatabase::class.java, "pace.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
