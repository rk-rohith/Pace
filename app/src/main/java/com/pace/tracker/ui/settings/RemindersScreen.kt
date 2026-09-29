@file:OptIn(ExperimentalLayoutApi::class)

package com.pace.tracker.ui.settings

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pace.tracker.AppContainer
import com.pace.tracker.data.db.ReminderEntity
import com.pace.tracker.reminders.Notifications
import com.pace.tracker.reminders.ReminderKind
import com.pace.tracker.reminders.ReminderScheduler
import com.pace.tracker.reminders.ReminderType
import com.pace.tracker.ui.components.ScreenScaffold
import com.pace.tracker.ui.components.SectionCard
import com.pace.tracker.ui.theme.PaceColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class RemindersState(
    val configs: Map<String, ReminderEntity> = emptyMap(),
    val settings: Map<String, String> = emptyMap(),
)

class RemindersViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository
    val state: StateFlow<RemindersState> = combine(repo.reminders, repo.settings) { r, s ->
        RemindersState(r.associateBy { it.key }, s)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemindersState())

    fun update(type: ReminderType, transform: (ReminderEntity) -> ReminderEntity) = viewModelScope.launch {
        val current = repo.reminder(type.key) ?: type.defaultEntity()
        repo.saveReminder(transform(current))
        container.reminderScheduler.scheduleOne(type)
    }

    fun setPrecise(on: Boolean) = viewModelScope.launch {
        repo.setSetting(ReminderScheduler.KEY_PRECISE, on.toString())
        container.reminderScheduler.rescheduleAll()
    }

    fun rescheduleAll() = viewModelScope.launch { container.reminderScheduler.rescheduleAll() }

    fun canScheduleExact() = container.reminderScheduler.canScheduleExact()
}

@Composable
fun RemindersScreen(onBack: () -> Unit) {
    val vm = com.pace.tracker.ui.components.paceViewModel { RemindersViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var canPost by remember { mutableStateOf(Notifications.canPost(context)) }
    var canExact by remember { mutableStateOf(vm.canScheduleExact()) }
    var refresh by remember { mutableIntStateOf(0) }

    // Re-check permissions when returning from system settings.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val nowExact = vm.canScheduleExact()
                if (nowExact != canExact) vm.rescheduleAll()
                canPost = Notifications.canPost(context)
                canExact = nowExact
                refresh++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canPost = Notifications.canPost(context)
        if (!canPost) {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
    }
    val precise = state.settings[ReminderScheduler.KEY_PRECISE] == "true"

    ScreenScaffold("Reminders", onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!canPost) {
                SectionCard("Notifications are off", icon = Icons.Filled.Warning) {
                    Text("Reminders are saved and scheduled, but Android won't show them until notifications are allowed.")
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        }
                    }, modifier = Modifier.height(48.dp)) { Text("Allow notifications") }
                }
            }

            SectionCard("Precise timing", trailing = {
                Switch(checked = precise, onCheckedChange = { vm.setPrecise(it) })
            }) {
                Text(
                    "Off: WorkManager (battery-friendly; Android may delay a reminder by a few minutes in Doze). " +
                        "On: exact alarms, re-armed automatically after reboot.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (precise && !canExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Text("Exact alarms need permission — falling back to WorkManager until granted.", color = PaceColors.Behind)
                    OutlinedButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                        )
                    }, modifier = Modifier.height(48.dp)) { Text("Grant exact alarms") }
                }
            }

            ReminderType.entries.forEach { type ->
                val cfg = state.configs[type.key] ?: type.defaultEntity()
                ReminderCard(type, cfg, state.settings[ReminderScheduler.nextKey(type)], refresh) { transform -> vm.update(type, transform) }
            }
            Text(
                "Tip: if reminders arrive late, exclude Pace from battery optimisation in system settings.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatTime(context: android.content.Context, hour: Int, minute: Int): String {
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    return java.time.LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

@Composable
private fun ReminderCard(
    type: ReminderType,
    cfg: ReminderEntity,
    nextAt: String?,
    @Suppress("UNUSED_PARAMETER") refresh: Int,
    onChange: ((ReminderEntity) -> ReminderEntity) -> Unit,
) {
    val context = LocalContext.current
    fun pickTime(h: Int, m: Int, set: (Int, Int) -> Unit) {
        TimePickerDialog(context, { _, hh, mm -> set(hh, mm) }, h, m, DateFormat.is24HourFormat(context)).show()
    }
    SectionCard(type.title, icon = Icons.Filled.Notifications, trailing = {
        Switch(checked = cfg.enabled, onCheckedChange = { on -> onChange { it.copy(enabled = on) } })
    }) {
        Text(type.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickTime(cfg.hour, cfg.minute) { h, m -> onChange { it.copy(hour = h, minute = m) } } },
                modifier = Modifier.height(48.dp)) {
                Text((if (type.kind == ReminderKind.INTERVAL) "From " else "") + formatTime(context, cfg.hour, cfg.minute))
            }
            if (type.kind == ReminderKind.INTERVAL) {
                val endH = cfg.endHour ?: 21
                val endM = cfg.endMinute ?: 0
                OutlinedButton(onClick = { pickTime(endH, endM) { h, m -> onChange { it.copy(endHour = h, endMinute = m) } } },
                    modifier = Modifier.height(48.dp)) {
                    Text("Until " + formatTime(context, endH, endM))
                }
            }
        }
        if (type.kind == ReminderKind.INTERVAL) {
            Text("Every", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(30, 60, 90, 120, 180).forEach { mins ->
                    FilterChip(
                        selected = cfg.intervalMinutes == mins,
                        onClick = { onChange { it.copy(intervalMinutes = mins) } },
                        label = { Text(if (mins < 60) "$mins min" else "${mins / 60.0}".removeSuffix(".0") + " h") },
                    )
                }
            }
        }
        if (type.kind != ReminderKind.DAILY) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DayOfWeek.values().forEach { dow ->
                    val bit = 1 shl (dow.value - 1)
                    FilterChip(
                        selected = cfg.daysMask and bit != 0,
                        onClick = { onChange { it.copy(daysMask = it.daysMask xor bit) } },
                        label = { Text(dow.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                    )
                }
            }
        }
        val next = nextAt?.toLongOrNull()
        if (cfg.enabled && next != null) {
            val dt = Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault())
            Text(
                "Next: " + dt.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())) + " " +
                    formatTime(context, dt.hour, dt.minute),
                style = MaterialTheme.typography.bodySmall, color = PaceColors.Ahead,
            )
        }
        if (type == ReminderType.RECAL) {
            Text("Runs the weekly recalibration (if due) and notifies you with the explanation.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
