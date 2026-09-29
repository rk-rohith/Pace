package com.pace.tracker.ui.streaks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pace.tracker.data.PaceRepository
import com.pace.tracker.data.ProgramData
import com.pace.tracker.data.streaks
import com.pace.tracker.data.today
import com.pace.tracker.data.weeklyRecals
import com.pace.tracker.data.weights
import com.pace.tracker.domain.BadgeCategory
import com.pace.tracker.domain.StreakCalculator
import com.pace.tracker.ui.components.ScreenScaffold
import com.pace.tracker.ui.components.SectionCard
import com.pace.tracker.ui.components.StatTile
import com.pace.tracker.ui.components.paceViewModel
import com.pace.tracker.ui.theme.PaceColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class StreaksViewModel(repository: PaceRepository) : ViewModel() {
    val data: StateFlow<ProgramData> = repository.programData
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgramData(null))
}

@Composable
fun StreaksScreen(onBack: () -> Unit) {
    val vm = paceViewModel { StreaksViewModel(it.repository) }
    val data by vm.data.collectAsStateWithLifecycle()
    ScreenScaffold("Streaks & badges", onBack = onBack) { padding ->
        val p = data.profile ?: return@ScreenScaffold
        val t = today()
        val s = data.streaks(t) ?: return@ScreenScaffold
        val daysIn = (t - p.startEpochDay + 1).toInt().coerceIn(0, p.durationDays)
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Daily logging", "${s.logging.current} days", Modifier.weight(1f), sub = "best ${s.logging.best}",
                    icon = Icons.Filled.EditNote, accent = PaceColors.Pink)
                StatTile("Step goal", "${s.steps.current} days", Modifier.weight(1f), sub = "best ${s.steps.best}",
                    icon = Icons.AutoMirrored.Filled.DirectionsWalk, accent = PaceColors.Tertiary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Calorie adherence", "${s.calories.current} days", Modifier.weight(1f), sub = "best ${s.calories.best}",
                    icon = Icons.Filled.Restaurant, accent = PaceColors.Secondary)
                StatTile("Workout weeks", "${s.workouts.current} weeks", Modifier.weight(1f),
                    sub = "≥${p.workoutsPerWeek}/week · best ${s.workouts.best}",
                    icon = Icons.Filled.FitnessCenter, accent = PaceColors.Primary)
            }
            Text(
                "A day counts as logged with a weigh-in, a meal or a workout. Calorie adherence = within " +
                    "+100 kcal of that day's target (and at least half of it logged). Today doesn't break a streak until it's over.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val wins = StreakCalculator.adaptiveWins(data.weeklyRecals.map { it.status }, data.weights, p.startWeightKg, p.targetWeightKg)
            SectionCard("Adaptive wins", icon = Icons.Filled.Celebration) {
                if (wins.isEmpty()) {
                    Text("Wins like “3 weeks on pace” and “new low weight” show up here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                wins.forEach { Text("🎉  $it", style = MaterialTheme.typography.bodyLarge) }
            }

            val badges = StreakCalculator.badges(s, daysIn)
            BadgeCategory.entries.forEach { cat ->
                SectionCard(cat.label, icon = Icons.Filled.EmojiEvents) {
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.padding(vertical = 4.dp)) {
                        badges.filter { it.category == cat }.forEach { b ->
                            BadgeView(b.days, b.achieved, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeView(days: Int, achieved: Boolean, modifier: Modifier = Modifier) {
    val color = if (achieved) PaceColors.Secondary else MaterialTheme.colorScheme.surfaceVariant
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).background(color.copy(alpha = if (achieved) 0.25f else 1f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.EmojiEvents, null, tint = if (achieved) PaceColors.Secondary else Color.Gray)
        }
        Text("$days days", textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium,
            color = if (achieved) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
