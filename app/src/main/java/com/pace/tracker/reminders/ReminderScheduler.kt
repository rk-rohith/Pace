package com.pace.tracker.reminders

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.pace.tracker.PaceApp
import com.pace.tracker.data.PaceRepository
import com.pace.tracker.data.today
import com.pace.tracker.domain.ReminderTiming
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Schedules every reminder as a one-shot job for its next occurrence; when it fires, the next
 * occurrence is scheduled. WorkManager persists jobs across reboots by itself. If "precise timing"
 * is on (and the exact-alarm permission is granted) AlarmManager is used instead, and
 * [BootReceiver] re-arms alarms after a reboot, time change or app update.
 */
class ReminderScheduler(private val context: Context, private val repository: PaceRepository) {

    private val workManager get() = WorkManager.getInstance(context)
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    suspend fun preciseEnabled(): Boolean = repository.setting(KEY_PRECISE) == "true"

    suspend fun rescheduleAll() {
        if (repository.getProfile() == null) return
        ReminderType.entries.forEach { scheduleOne(it) }
    }

    /** [fromWorker] = called by the worker that is currently running for this reminder. */
    @SuppressLint("ScheduleExactAlarm") // guarded by canScheduleExact()
    suspend fun scheduleOne(type: ReminderType, fromWorker: Boolean = false) {
        val cfg = repository.reminder(type.key) ?: type.defaultEntity()
        alarmManager.cancel(alarmIntent(type))
        if (!fromWorker) workManager.cancelUniqueWork(workName(type))
        if (!cfg.enabled) {
            repository.setSetting(nextKey(type), "")
            return
        }
        val interval = if (type.kind == ReminderKind.INTERVAL) cfg.intervalMinutes else null
        val mask = if (type.kind == ReminderKind.DAILY) ALL_DAYS else cfg.daysMask
        val next = ReminderTiming.next(LocalDateTime.now(), cfg.hour, cfg.minute, mask, interval, cfg.endHour, cfg.endMinute)
        if (next == null) {
            repository.setSetting(nextKey(type), "")
            return
        }
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (preciseEnabled() && canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent(type))
        } else {
            val delay = (at - System.currentTimeMillis()).coerceAtLeast(0)
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_TYPE to type.key))
                .addTag(TAG)
                .build()
            // From inside the running worker we must not REPLACE (that would cancel ourselves).
            val policy = if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
            workManager.enqueueUniqueWork(workName(type), policy, request)
        }
        repository.setSetting(nextKey(type), at.toString())
    }

    private fun alarmIntent(type: ReminderType): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(KEY_TYPE, type.key)
        }
        return PendingIntent.getBroadcast(
            context, type.ordinal, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val KEY_TYPE = "reminder_type"
        const val KEY_PRECISE = "precise_reminders"
        const val TAG = "pace_reminder"
        const val ACTION_FIRE = "com.pace.tracker.REMINDER_FIRE"
        fun workName(type: ReminderType) = "reminder_${type.key}"
        fun nextKey(type: ReminderType) = "next_${type.key}"
    }
}

/** Decides whether a reminder is still useful right now and posts it. */
object ReminderFirer {
    private const val KEY_LAST_NOTIFIED_RECAL = "recal_notified_id"

    suspend fun fire(context: Context, type: ReminderType) {
        val container = (context.applicationContext as PaceApp).container
        val repo = container.repository
        val profile = repo.getProfile() ?: return
        val day = today()
        val log = repo.getLog(day)
        var title = type.title
        var text = type.message
        var channel = Notifications.CHANNEL_REMINDERS
        when (type) {
            ReminderType.WEIGHT -> if (log?.weightKg != null) return
            ReminderType.BREAKFAST, ReminderType.LUNCH, ReminderType.DINNER -> {
                val mealType = when (type) {
                    ReminderType.BREAKFAST -> com.pace.tracker.domain.MealType.BREAKFAST
                    ReminderType.LUNCH -> com.pace.tracker.domain.MealType.LUNCH
                    else -> com.pace.tracker.domain.MealType.DINNER
                }
                if (repo.mealsOn(day).any { it.type == mealType }) return
            }
            ReminderType.WORKOUT -> if (repo.workoutsOn(day).isNotEmpty()) return
            ReminderType.WATER -> {
                val glasses = log?.waterGlasses ?: 0
                if (glasses >= profile.waterGoalGlasses) return
                text = "$glasses of ${profile.waterGoalGlasses} glasses so far. Time for another!"
            }
            ReminderType.PHOTOS -> Unit
            ReminderType.RECAL -> {
                repo.runPendingRecalibrations()
                val last = repo.setting(KEY_LAST_NOTIFIED_RECAL)?.toLongOrNull() ?: 0L
                val newest = repo.allRecals().filter { it.weekIndex > 0 }.maxByOrNull { it.id } ?: return
                if (newest.id <= last) return
                repo.setSetting(KEY_LAST_NOTIFIED_RECAL, newest.id.toString())
                title = "Week ${newest.weekIndex}: ${newest.status.label} · ${newest.newTargetKcal} kcal"
                text = newest.explanation
                channel = Notifications.CHANNEL_SUMMARY
            }
        }
        Notifications.show(context, 1000 + type.ordinal, channel, title, text, type.route)
    }
}

class ReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val type = ReminderType.fromKey(inputData.getString(ReminderScheduler.KEY_TYPE)) ?: return Result.success()
        runCatching { ReminderFirer.fire(applicationContext, type) }
        val scheduler = (applicationContext as PaceApp).container.reminderScheduler
        runCatching { scheduler.scheduleOne(type, fromWorker = true) }
        return Result.success()
    }
}
