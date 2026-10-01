package com.pace.tracker

import com.pace.tracker.data.backup.BackupCodec
import com.pace.tracker.data.backup.BackupFormatException
import com.pace.tracker.data.backup.BackupSnapshot
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
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupCodecTest {
    private val old = "/data/user/0/com.pace.tracker/files/photos"
    private val new = "/data/user/0/com.pace.tracker/files/photos-new"

    private val snapshot = BackupSnapshot(
        profiles = listOf(ProfileEntity(1, 178.0, 90.0, 81.5, 81.5, 30, Sex.MALE, ActivityLevel.LIGHT, PacePreference.MODERATE,
            20_000, 80, 2568.0, 1690, 1750, 2600.0, 8000, 8, 3, 2, 123L)),
        logs = listOf(
            DailyLogEntity(20_000, 90.0, 22.5, 6, null, 1200, 7400, 4, 3, 7.5, "good day", 99L),
            DailyLogEntity(20_001),
        ),
        meals = listOf(
            MealEntity(5, 20_000, MealType.LUNCH, "Chicken rice bowl", 575, "$old/a.jpg", 10L, 55.0),
            MealEntity(6, 20_000, MealType.SNACK, "Apple", 95, null, 11L, null),
        ),
        workouts = listOf(WorkoutEntity(1, 20_000, "Strength", 50, 300, "legs", "$old/w.jpg", 12L)),
        photos = listOf(ProgressPhotoEntity(3, 20_000, PhotoPose.SIDE, "$old/p.jpg", 13L)),
        measurements = listOf(MeasurementEntity(20_000, 100.0, 92.5, null, 35.0, null, 39.0)),
        recals = listOf(RecalibrationEntity(7, 1, 1L, 20_007, RecalStatus.ON_PACE, 89.2, 90.0, 0.8, 0.9, 0.8, 1720.0,
            7300.0, 3, 2590.0, 1690, 1690, 80.6, null, null, "Week 1: on pace", true)),
        reflections = listOf(WeeklyReflectionEntity(1, "Felt strong", 5L)),
        reminders = listOf(ReminderEntity("water", true, 9, 0, 0x7F, 90, 21, 0)),
        settings = listOf(SettingEntity("grocery_A", "0-1,2-3")),
        foods = listOf(FoodItemEntity(4, "Protein dosa", "1 serving", 320, 24.5, 14L)),
    )

    @Test
    fun roundTripKeepsEverythingAndRemapsPhotos() {
        val json = BackupCodec.encode(snapshot, 1L, "test").toString()
        val back = BackupCodec.decode(JSONObject(json)) { "$new/$it" }
        val expected = snapshot.copy(
            meals = snapshot.meals.map { m -> m.copy(photoPath = m.photoPath?.replace(old, new)) },
            workouts = snapshot.workouts.map { w -> w.copy(photoPath = w.photoPath?.replace(old, new)) },
            photos = snapshot.photos.map { p -> p.copy(path = p.path.replace(old, new)) },
        )
        assertEquals(expected, back)
    }

    @Test(expected = BackupFormatException::class)
    fun rejectsOtherFiles() {
        BackupCodec.decode(JSONObject("""{"hello":"world"}""")) { it }
    }
}
