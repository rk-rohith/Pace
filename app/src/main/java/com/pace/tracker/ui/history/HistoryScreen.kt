package com.pace.tracker.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pace.tracker.data.PaceRepository
import com.pace.tracker.data.ProgramData
import com.pace.tracker.data.targetForDay
import com.pace.tracker.data.today
import com.pace.tracker.ui.charts.ChartSeries
import com.pace.tracker.ui.charts.PaceLineChart
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
import com.pace.tracker.ui.components.statusColor
import com.pace.tracker.ui.theme.PaceColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val repository: PaceRepository) : ViewModel() {
    val data: StateFlow<ProgramData> = repository.programData
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgramData(null))

    fun markSeen() = viewModelScope.launch { repository.markRecalsSeen() }
}

/** Target history: every calorie-target change with its explanation. */
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val vm = paceViewModel { HistoryViewModel(it.repository) }
    val data by vm.data.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.markSeen() }
    val p = data.profile
    ScreenScaffold("Target history", onBack = onBack) { padding ->
        if (p == null) return@ScreenScaffold
        val t = today()
        val targetPts = (p.startEpochDay..maxOf(t, p.startEpochDay + 1)).map { d ->
            (d - p.startEpochDay).toFloat() to data.targetForDay(d).toFloat()
        }
        val entries = data.recals.sortedWith(compareByDescending<com.pace.tracker.data.db.RecalibrationEntity> { it.effectiveEpochDay }.thenByDescending { it.id })
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard("Daily calorie target over time") {
                    PaceLineChart(
                        series = listOf(ChartSeries("Target kcal", PaceColors.Primary, targetPts)),
                        startDay = p.startEpochDay,
                        decimals = 0,
                        minY = (targetPts.minOf { it.second } - 200f).coerceAtLeast(0f),
                        maxY = targetPts.maxOf { it.second } + 200f,
                        height = 200.dp,
                    )
                    LabeledValue("Current target", "${p.currentTargetKcal.grouped()} kcal")
                    LabeledValue("Starting target", "${p.initialTargetKcal.grouped()} kcal")
                    LabeledValue("Estimated TDEE", "${p.tdeeEstimate.toInt().grouped()} kcal")
                }
            }
            if (entries.isEmpty()) item { EmptyState("No history yet", "Recalibrations appear here every 7 days.", icon = Icons.Filled.History) }
            items(entries, key = { it.id }) { r ->
                SectionCard(
                    title = if (r.weekIndex > 0) "Week ${r.weekIndex}" else r.status.label,
                    trailing = { Pill(r.status.label, statusColor(r.status)) },
                ) {
                    Text("Effective ${r.effectiveEpochDay.shortDate()}", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                    val delta = r.newTargetKcal - r.oldTargetKcal
                    Text(
                        if (delta == 0) "${r.newTargetKcal.grouped()} kcal (unchanged)"
                        else "${r.oldTargetKcal.grouped()} → ${r.newTargetKcal.grouped()} kcal (${if (delta > 0) "+" else ""}$delta)",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(r.explanation, style = MaterialTheme.typography.bodyMedium)
                    r.avgWeightKg?.let { LabeledValue("Average weight", it.kg()) }
                    r.lossKg?.let { LabeledValue("Change", "${if (it >= 0) "−" else "+"}${kotlin.math.abs(it).oneDecimal()} kg") }
                    r.projectedFinishKg?.let { LabeledValue("Projected finish", it.kg()) }
                    r.suggestedTargetKg?.let { LabeledValue("Suggested new goal", it.kg()) }
                }
            }
        }
    }
}
