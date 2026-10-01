package com.pace.tracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.pace.tracker.MainActivity
import com.pace.tracker.PaceApp
import com.pace.tracker.R
import com.pace.tracker.data.PaceRepository
import com.pace.tracker.data.ProgramData
import com.pace.tracker.data.latestWeight
import com.pace.tracker.data.paceIndicator
import com.pace.tracker.data.stepsFor
import com.pace.tracker.data.streaks
import com.pace.tracker.data.targetForDay
import com.pace.tracker.data.today
import com.pace.tracker.domain.PaceIndicator
import com.pace.tracker.reminders.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

/** What the widget shows; computed from the same data as the Home screen. */
data class WidgetSnapshot(
    val hasProfile: Boolean,
    val dayNumber: Int = 0,
    val durationDays: Int = 0,
    val daysLeft: Int = 0,
    val pace: PaceIndicator = PaceIndicator.NO_DATA,
    val eaten: Int = 0,
    val target: Int = 0,
    val steps: Int = 0,
    val stepGoal: Int = 0,
    val water: Int = 0,
    val waterGoal: Int = 0,
    val weight: Double? = null,
    val lostKg: Double? = null,
    val streak: Int = 0,
)

fun ProgramData.widgetSnapshot(day: Long = today()): WidgetSnapshot {
    val p = profile ?: return WidgetSnapshot(hasProfile = false)
    val weight = latestWeight()
    return WidgetSnapshot(
        hasProfile = true,
        dayNumber = (day - p.startEpochDay + 1).toInt().coerceAtLeast(1),
        durationDays = p.durationDays,
        daysLeft = (p.startEpochDay + p.durationDays - day).toInt().coerceAtLeast(0),
        pace = paceIndicator(day),
        eaten = calories[day] ?: 0,
        target = targetForDay(day),
        steps = stepsFor(day) ?: 0,
        stepGoal = p.stepGoal,
        water = logs[day]?.waterGlasses ?: 0,
        waterGoal = p.waterGoalGlasses,
        weight = weight,
        lostKg = weight?.let { p.startWeightKg - it },
        streak = streaks(day)?.logging?.current ?: 0,
    )
}

object PaceWidget {
    private const val ACTION_ADD_WATER = "com.pace.tracker.widget.ADD_WATER"

    private fun ids(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, PaceWidgetProvider::class.java))

    /** Pushes the latest numbers to every placed widget (no-op when none are on the home screen). */
    suspend fun refresh(context: Context, repository: PaceRepository) {
        val ids = ids(context)
        if (ids.isEmpty()) return
        val snapshot = repository.programData.first().widgetSnapshot()
        AppWidgetManager.getInstance(context).updateAppWidget(ids, render(context, snapshot))
    }

    /** Keeps widgets live while the app process runs: re-renders shortly after any logged change. */
    fun observe(context: Context, repository: PaceRepository, scope: CoroutineScope) {
        scope.launch {
            repository.programData.collectLatest { data ->
                delay(400) // coalesce bursts of writes (e.g. step sensor, typing in the log)
                val ids = ids(context)
                if (ids.isNotEmpty()) {
                    AppWidgetManager.getInstance(context).updateAppWidget(ids, render(context, data.widgetSnapshot()))
                }
            }
        }
    }

    fun render(context: Context, s: WidgetSnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_progress)
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context, null, 10))
        views.setOnClickPendingIntent(R.id.w_log, openApp(context, "log", 11))
        views.setOnClickPendingIntent(R.id.w_add_water, addWater(context))

        if (!s.hasProfile) {
            views.setTextViewText(R.id.w_title, "Pace")
            views.setTextViewText(R.id.w_subtitle, "Open Pace to set up your plan")
            views.setTextViewText(R.id.w_pace, "")
            listOf(R.id.w_kcal, R.id.w_steps, R.id.w_water).forEach { views.setTextViewText(it, "—") }
            listOf(R.id.w_kcal_bar, R.id.w_steps_bar, R.id.w_water_bar).forEach { views.setProgressBar(it, 100, 0, false) }
            views.setTextViewText(R.id.w_footer, "")
            return views
        }

        views.setTextViewText(R.id.w_title, "Day ${s.dayNumber} of ${s.durationDays}")
        views.setTextViewText(
            R.id.w_subtitle,
            if (s.daysLeft > 0) "${s.daysLeft} days left" else "Goal date reached",
        )
        views.setTextViewText(R.id.w_pace, if (s.pace == PaceIndicator.NO_DATA) "" else s.pace.label)
        views.setTextColor(R.id.w_pace, paceColor(s.pace))

        val over = s.eaten - s.target
        views.setTextViewText(
            R.id.w_kcal,
            "${fmt(s.eaten)} / ${fmt(s.target)}" + if (over > 0) "  (+${fmt(over)})" else "",
        )
        views.setTextColor(R.id.w_kcal, if (over > 0) Color.parseColor("#FFFF6B6B") else Color.parseColor("#FFE6EBEF"))
        bar(views, R.id.w_kcal_bar, s.eaten, s.target)

        views.setTextViewText(R.id.w_steps, "${fmt(s.steps)} / ${fmt(s.stepGoal)}")
        bar(views, R.id.w_steps_bar, s.steps, s.stepGoal)

        views.setTextViewText(R.id.w_water, "${s.water} / ${s.waterGoal} glasses")
        bar(views, R.id.w_water_bar, s.water, s.waterGoal)

        val weightText = s.weight?.let { w ->
            val lost = s.lostKg ?: 0.0
            String.format(Locale.US, "%.1f kg", w) +
                if (abs(lost) >= 0.05) String.format(Locale.US, " · %s%.1f", if (lost >= 0) "−" else "+", abs(lost)) else ""
        } ?: "No weigh-in yet"
        val streakText = if (s.streak > 0) "${s.streak}-day streak" else "Log today to start a streak"
        views.setTextViewText(R.id.w_footer, "$weightText\n$streakText")
        return views
    }

    private fun bar(views: RemoteViews, id: Int, value: Int, goal: Int) {
        val pct = if (goal > 0) ((value.toFloat() / goal) * 100).toInt().coerceIn(0, 100) else 0
        views.setProgressBar(id, 100, pct, false)
    }

    private fun fmt(n: Int) = String.format(Locale.US, "%,d", n)

    private fun paceColor(p: PaceIndicator): Int = Color.parseColor(
        when (p) {
            PaceIndicator.AHEAD -> "#FF4FD1A5"
            PaceIndicator.ON_PACE -> "#FF7AB8FF"
            PaceIndicator.BEHIND -> "#FFFFB84D"
            PaceIndicator.NO_DATA -> "#FF8B96A1"
        },
    )

    private fun openApp(context: Context, route: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (route != null) putExtra(Notifications.EXTRA_ROUTE, route)
        }
        return PendingIntent.getActivity(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun addWater(context: Context): PendingIntent {
        val intent = Intent(context, PaceWidgetProvider::class.java).setAction(ACTION_ADD_WATER)
        return PendingIntent.getBroadcast(
            context, 12, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    internal fun isAddWater(intent: Intent) = intent.action == ACTION_ADD_WATER
}

private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class PaceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val repository = (context.applicationContext as PaceApp).container.repository
        val pending = goAsync()
        widgetScope.launch {
            try {
                val snapshot = repository.programData.first().widgetSnapshot()
                appWidgetManager.updateAppWidget(appWidgetIds, PaceWidget.render(context, snapshot))
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!PaceWidget.isAddWater(intent)) {
            super.onReceive(context, intent)
            return
        }
        val repository = (context.applicationContext as PaceApp).container.repository
        val pending = goAsync()
        widgetScope.launch {
            try {
                if (repository.getProfile() != null) {
                    repository.updateLog(today()) { it.copy(waterGlasses = it.waterGlasses + 1) }
                }
                PaceWidget.refresh(context, repository)
            } finally {
                pending.finish()
            }
        }
    }
}
