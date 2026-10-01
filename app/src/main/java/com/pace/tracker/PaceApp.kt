package com.pace.tracker

import android.app.Application
import com.pace.tracker.data.MealPlanLoader
import com.pace.tracker.data.PaceRepository
import com.pace.tracker.data.db.PaceDatabase
import com.pace.tracker.domain.MealPlan
import com.pace.tracker.health.HealthConnectManager
import com.pace.tracker.health.StepSensorTracker
import com.pace.tracker.photo.PhotoStorage
import com.pace.tracker.reminders.Notifications
import com.pace.tracker.reminders.ReminderScheduler
import com.pace.tracker.widget.PaceWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency container – the app is small enough not to need a DI framework. */
class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: PaceDatabase = PaceDatabase.build(app)
    val photoStorage = PhotoStorage(app)
    val repository = PaceRepository(database, photoStorage)
    val healthConnect = HealthConnectManager(app, repository)
    val reminderScheduler = ReminderScheduler(app, repository)
    val stepTracker = StepSensorTracker(app, repository, appScope)
    val mealPlan: MealPlan by lazy { MealPlanLoader.load(app) }
}

class PaceApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        // Reminders are NOT rescheduled here: this also runs when WorkManager starts the process
        // to deliver a reminder, and re-enqueueing would cancel that very job. MainActivity and
        // BootReceiver handle rescheduling instead.
        container.appScope.launch {
            runCatching { container.repository.runPendingRecalibrations() }
        }
        // Keep home-screen widgets in sync with anything logged while the process is alive.
        PaceWidget.observe(this, container.repository, container.appScope)
    }
}
