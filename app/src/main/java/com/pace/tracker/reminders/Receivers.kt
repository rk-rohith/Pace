package com.pace.tracker.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pace.tracker.PaceApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** Exact-alarm path ("precise timing"): posts the reminder and arms the next one. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val type = ReminderType.fromKey(intent.getStringExtra(ReminderScheduler.KEY_TYPE)) ?: return
        val app = context.applicationContext as PaceApp
        runAsync {
            runCatching { ReminderFirer.fire(app, type) }
            runCatching { app.container.reminderScheduler.scheduleOne(type) }
        }
    }
}

/** Re-arms every reminder after reboot, app update, clock or timezone change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as PaceApp
        runAsync { runCatching { app.container.reminderScheduler.rescheduleAll() } }
    }
}
