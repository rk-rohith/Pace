package com.pace.tracker.ui.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pace.tracker.data.PaceRepository
import com.pace.tracker.data.ProgramData
import com.pace.tracker.data.WeekSummary
import com.pace.tracker.data.allWeekSummaries
import com.pace.tracker.data.streaks
import com.pace.tracker.data.today
import com.pace.tracker.ui.components.EmptyState
import com.pace.tracker.ui.components.LabeledValue
import com.pace.tracker.ui.components.Pill
import com.pace.tracker.ui.components.ScreenScaffold
import com.pace.tracker.ui.components.SectionCard
import com.pace.tracker.ui.components.grouped
import com.pace.tracker.ui.components.kg
import com.pace.tracker.ui.components.oneDecimal
import com.pace.tracker.ui.components.paceViewModel
import com.pace.tracker.ui.components.shortDate
import com.pace.tracker.ui.components.signedKg
import com.pace.tracker.ui.components.statusColor
import com.pace.tracker.ui.theme.PaceColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReviewViewModel(private val repository: PaceRepository) : ViewModel() {
    val data: StateFlow<ProgramData> = repository.programData
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgramData(null))
    var savedNote by mutableStateOf(false)

    fun saveReflection(week: Int, note: String) = viewModelScope.launch {
        repository.saveReflection(week, note)
        savedNote = true
    }
}

@Composable
fun ReviewListScreen(onBack: () -> Unit, onOpenWeek: (Int) -> Unit) {
    val vm = paceViewModel { ReviewViewModel(it.repository) }
    val data by vm.data.collectAsStateWithLifecycle()
    val weeks = data.allWeekSummaries(today()).reversed()
    ScreenScaffold("Weekly review", onBack = onBack) { padding ->
        if (data.profile == null) return@ScreenScaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (weeks.isEmpty()) item { EmptyState("No weeks yet", "Your first weekly review appears after day 1.") }
            items(weeks, key = { it.weekIndex }) { w -> WeekCard(w, Modifier.clickable { onOpenWeek(w.weekIndex) }) }
        }
    }
}

@Composable
private fun WeekCard(w: WeekSummary, modifier: Modifier = Modifier) {
    SectionCard(
        "Week ${w.weekIndex}" + if (!w.isComplete) " (in progress)" else "",
        modifier = modifier,
        icon = Icons.Filled.CalendarMonth,
        trailing = { w.recal?.let { Pill(it.status.label, statusColor(it.status)) } },
    ) {
        Text("${w.startDay.shortDate()} – ${w.endDay.shortDate()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(w.weightChange?.signedKg() ?: "— kg", fontWeight = FontWeight.Bold,
                color = when {
                    w.weightChange == null -> MaterialTheme.colorScheme.onSurface
                    w.weightChange!! <= 0 -> PaceColors.Ahead
                    else -> PaceColors.Behind
                })
            Text("${w.avgCalories?.toInt()?.grouped() ?: "—"} kcal/day")
            Text("${w.workouts} workouts")
        }
        w.recal?.let { Text("Target → ${it.newTargetKcal.grouped()} kcal", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
fun ReviewDetailScreen(week: Int, onBack: () -> Unit) {
    val vm = paceViewModel { ReviewViewModel(it.repository) }
    val data by vm.data.collectAsStateWithLifecycle()
    val t = today()
    val all = data.allWeekSummaries(t)
    val w = all.firstOrNull { it.weekIndex == week }
    ScreenScaffold("Week $week review", onBack = onBack) { padding ->
        if (data.profile == null) return@ScreenScaffold
        if (w == null) {
            EmptyState("Week not started", "This week hasn't begun yet.", Modifier.padding(padding))
            return@ScreenScaffold
        }
        var note by remember(w.reflection) { mutableStateOf(w.reflection) }
        LaunchedEffect(note) { vm.savedNote = false }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("${w.startDay.shortDate()} – ${w.endDay.shortDate()}" + if (!w.isComplete) " · in progress" else "",
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            SectionCard("Summary") {
                LabeledValue("Weight change (weekly avg)", w.weightChange?.signedKg() ?: "—")
                LabeledValue("Average weight", w.avgWeight?.kg() ?: "—")
                LabeledValue("First → last weigh-in",
                    if (w.firstWeight != null && w.lastWeight != null) "${w.firstWeight.oneDecimal()} → ${w.lastWeight.oneDecimal()} kg" else "—")
                LabeledValue("Weigh-ins", "${w.weighIns}")
                LabeledValue("Avg calories", w.avgCalories?.let { "${it.toInt().grouped()} kcal (target ${w.avgTargetKcal.grouped()})" } ?: "—")
                LabeledValue("Days with food logged", "${w.daysFoodLogged}/7")
                LabeledValue("Total steps", w.totalSteps.grouped() + (w.avgSteps?.let { " (avg ${it.grouped()})" } ?: ""))
                LabeledValue("Workouts", "${w.workouts} · ${w.workoutMinutes} min")
                LabeledValue("Progress photos", "${w.photos}")
            }

            SectionCard("Streak status") {
                LabeledValue("Days logged", "${w.daysLogged}/7")
                LabeledValue("Step goal met", "${w.stepGoalDays}/7")
                LabeledValue("Calories on target", "${w.adherentDays}/7")
                data.streaks(t)?.let { LabeledValue("Current logging streak", "${it.logging.current} days") }
            }

            SectionCard("Recalibration", trailing = { w.recal?.let { Pill(it.status.label, statusColor(it.status)) } }) {
                val r = w.recal
                if (r == null) {
                    Text(
                        if (w.isComplete) "Recalibration will run next time the app opens." else
                            "Runs automatically on ${(w.endDay + 1).shortDate()} when the week completes.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("${r.oldTargetKcal.grouped()} → ${r.newTargetKcal.grouped()} kcal", style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary)
                    Text(r.explanation)
                }
            }

            SectionCard("Reflection") {
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    placeholder = { Text("What worked? What will you change next week?") },
                    minLines = 4, modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { vm.saveReflection(week, note) }, modifier = Modifier.height(48.dp)) { Text("Save reflection") }
                    if (vm.savedNote) Text("  Saved ✓", color = PaceColors.Ahead)
                }
            }

            SectionCard("Compared to other weeks") {
                CompareTable(all, week)
            }
        }
    }
}

@Composable
private fun CompareTable(weeks: List<WeekSummary>, highlight: Int) {
    val headers = listOf("Week", "Avg kg", "Change", "kcal", "Steps", "Wkts", "Target")
    val widths = listOf(52, 64, 64, 60, 64, 44, 60)
    Column(Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            headers.forEachIndexed { i, h ->
                Text(h, Modifier.width(widths[i].dp), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        weeks.forEach { w ->
            val cells = listOf(
                "W${w.weekIndex}",
                w.avgWeight?.oneDecimal() ?: "—",
                w.weightChange?.let { String.format(java.util.Locale.US, "%+.1f", it) } ?: "—",
                w.avgCalories?.toInt()?.grouped() ?: "—",
                w.avgSteps?.grouped() ?: "—",
                "${w.workouts}",
                w.recal?.newTargetKcal?.grouped() ?: "—",
            )
            Row {
                cells.forEachIndexed { i, c ->
                    Text(
                        c, Modifier.width(widths[i].dp),
                        fontWeight = if (w.weekIndex == highlight) FontWeight.Bold else FontWeight.Normal,
                        color = if (w.weekIndex == highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
