package com.pace.tracker.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pace.tracker.data.PaceRepository
import com.pace.tracker.data.ProgramData
import com.pace.tracker.data.db.RecalibrationEntity
import com.pace.tracker.data.latestWeight
import com.pace.tracker.data.paceIndicator
import com.pace.tracker.data.pendingSuggestion
import com.pace.tracker.data.stepsFor
import com.pace.tracker.data.streaks
import com.pace.tracker.data.targetForDay
import com.pace.tracker.data.toEngine
import com.pace.tracker.data.today
import com.pace.tracker.data.unseenRecal
import com.pace.tracker.data.weeklyRecals
import com.pace.tracker.data.weights
import com.pace.tracker.domain.BodyMath
import com.pace.tracker.domain.MealPlan
import com.pace.tracker.domain.MealPlanSchedule
import com.pace.tracker.domain.PaceIndicator
import com.pace.tracker.domain.StreakCalculator
import com.pace.tracker.ui.components.Pill
import com.pace.tracker.ui.components.ProgressRing
import com.pace.tracker.ui.components.SectionCard
import com.pace.tracker.ui.components.StatTile
import com.pace.tracker.ui.components.grouped
import com.pace.tracker.ui.components.kg
import com.pace.tracker.ui.components.oneDecimal
import com.pace.tracker.ui.components.paceColor
import com.pace.tracker.ui.components.paceViewModel
import com.pace.tracker.ui.components.shortDate
import com.pace.tracker.ui.components.statusColor
import com.pace.tracker.ui.nav.Routes
import com.pace.tracker.ui.plan.MealPlanViewModel
import com.pace.tracker.ui.theme.PaceColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HomeState(
    val loaded: Boolean = false,
    val dayNumber: Int = 1,
    val durationDays: Int = 80,
    val daysLeft: Int = 80,
    val goalDay: Long = 0,
    val startWeight: Double = 0.0,
    val currentWeight: Double? = null,
    val targetWeight: Double = 0.0,
    val progress: Float = 0f,
    val pace: PaceIndicator = PaceIndicator.NO_DATA,
    val targetKcal: Int = 0,
    val eatenKcal: Int = 0,
    val protein: Int = 0,
    val proteinGoal: Int = 0,
    val steps: Int = 0,
    val stepGoal: Int = 8000,
    val water: Int = 0,
    val waterGoal: Int = 8,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val wins: List<String> = emptyList(),
    val unseenRecal: RecalibrationEntity? = null,
    val suggestion: RecalibrationEntity? = null,
    val projectedFinish: Double? = null,
    val menuWeek: String = "",
    val menu: List<Pair<String, String>> = emptyList(),
    val menuKcal: Int = 0,
    val menuVeg: Boolean = false,
)

class HomeViewModel(private val repository: PaceRepository, private val mealPlan: MealPlan) : ViewModel() {
    val state: StateFlow<HomeState> = combine(repository.programData, repository.settings) { data, settings ->
        data.toHome(mealPlan.withSwaps(MealPlanViewModel.swapsFrom(settings)))
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    private fun ProgramData.toHome(plan: MealPlan): HomeState {
        val p = profile ?: return HomeState()
        val t = today()
        val dayNumber = (t - p.startEpochDay + 1).toInt().coerceAtLeast(1)
        val current = latestWeight()
        val toLose = (p.startWeightKg - p.targetWeightKg).coerceAtLeast(0.1)
        val lost = current?.let { p.startWeightKg - it } ?: 0.0
        val s = streaks(t)
        val week = plan.weeks.takeIf { it.isNotEmpty() }
            ?.let { plan.week(MealPlanSchedule.weekIdFor(p.toEngine().weekOf(t).coerceAtLeast(1))) }
        val planDay = week?.days?.getOrNull(MealPlanSchedule.dayIndex(LocalDate.ofEpochDay(t)))
        return HomeState(
            loaded = true,
            dayNumber = dayNumber,
            durationDays = p.durationDays,
            daysLeft = (p.startEpochDay + p.durationDays - t).toInt().coerceAtLeast(0),
            goalDay = p.startEpochDay + p.durationDays,
            startWeight = p.startWeightKg,
            currentWeight = current,
            targetWeight = p.targetWeightKg,
            progress = (lost / toLose).toFloat().coerceIn(0f, 1f),
            pace = paceIndicator(t),
            targetKcal = targetForDay(t),
            eatenKcal = calories[t] ?: 0,
            protein = (protein[t] ?: 0.0).toInt(),
            proteinGoal = BodyMath.proteinTarget(p.targetWeightKg),
            steps = stepsFor(t) ?: 0,
            stepGoal = p.stepGoal,
            water = logs[t]?.waterGlasses ?: 0,
            waterGoal = p.waterGoalGlasses,
            streak = s?.logging?.current ?: 0,
            bestStreak = s?.logging?.best ?: 0,
            wins = StreakCalculator.adaptiveWins(weeklyRecals.map { it.status }, weights, p.startWeightKg, p.targetWeightKg),
            unseenRecal = unseenRecal(),
            suggestion = pendingSuggestion(),
            projectedFinish = weeklyRecals.lastOrNull()?.projectedFinishKg,
            menuWeek = week?.title ?: "",
            menu = planDay?.meals?.map { "${it.time}  ${it.label}" to plan.title(it) } ?: emptyList(),
            menuKcal = planDay?.let { plan.dayKcal(it) } ?: 0,
            menuVeg = planDay?.veg == true,
        )
    }

    fun addWater() = viewModelScope.launch {
        repository.updateLog(today()) { it.copy(waterGlasses = it.waterGlasses + 1) }
    }

    fun markSeen() = viewModelScope.launch { repository.markRecalsSeen() }
    fun accept(r: RecalibrationEntity) = viewModelScope.launch { repository.acceptSuggestion(r) }
    fun dismiss(r: RecalibrationEntity) = viewModelScope.launch { repository.dismissSuggestion(r) }
}

@Composable
fun HomeScreen(onLogToday: () -> Unit, onOpen: (String) -> Unit) {
    val vm = paceViewModel { HomeViewModel(it.repository, it.mealPlan) }
    val s by vm.state.collectAsStateWithLifecycle()
    if (!s.loaded) return

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
                .padding(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Countdown header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Day ${s.dayNumber} of ${s.durationDays}", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        if (s.daysLeft > 0) "${s.daysLeft} days left · goal ${s.goalDay.shortDate()}" else "Goal date reached — keep going or extend",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Pill(s.pace.label, paceColor(s.pace))
            }

            // Progress ring
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ProgressRing(progress = s.progress, size = 150.dp, stroke = 14.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${(s.progress * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium)
                            Text("of goal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Now", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(s.currentWeight?.kg() ?: "—", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Start ${s.startWeight.oneDecimal()} → Goal ${s.targetWeight.oneDecimal()}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        s.currentWeight?.let {
                            val lost = s.startWeight - it
                            Text(
                                if (lost >= 0) "−${lost.oneDecimal()} kg so far" else "+${(-lost).oneDecimal()} kg so far",
                                color = if (lost >= 0) PaceColors.Ahead else PaceColors.Behind,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        s.projectedFinish?.let {
                            Text("Projected: ${it.kg()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Adaptive calorie target
            SectionCard("Today's adaptive target", icon = Icons.Filled.Tune, trailing = {
                TextButton(onClick = { onOpen(Routes.HISTORY) }) { Text("History") }
            }) {
                val remaining = s.targetKcal - s.eatenKcal
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(s.targetKcal.grouped(), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                    Text(" kcal", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
                }
                LinearProgressIndicator(
                    progress = { if (s.targetKcal > 0) (s.eatenKcal.toFloat() / s.targetKcal).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    color = if (remaining >= 0) MaterialTheme.colorScheme.primary else PaceColors.Error,
                )
                Text(
                    "${s.eatenKcal.grouped()} eaten · " +
                        if (remaining >= 0) "${remaining.grouped()} left" else "${(-remaining).grouped()} over",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            s.unseenRecal?.let { r ->
                SectionCard("Week ${r.weekIndex} recalibration", icon = Icons.AutoMirrored.Filled.TrendingDown, trailing = {
                    Pill(r.status.label, statusColor(r.status))
                }) {
                    Text(r.explanation, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { vm.markSeen() }, modifier = Modifier.height(48.dp)) { Text("Got it") }
                        TextButton(onClick = { onOpen(Routes.review(r.weekIndex)) }, modifier = Modifier.height(48.dp)) { Text("Weekly review") }
                    }
                }
            }

            s.suggestion?.let { r ->
                SectionCard("Push further?", icon = Icons.Filled.Celebration) {
                    Text(
                        "You're trending ahead. Based on your pace you could reach " +
                            "${r.suggestedTargetKg?.kg()} instead of ${s.targetWeight.kg()}" +
                            (r.suggestedDurationDays?.takeIf { it > s.durationDays }?.let { " (timeline extends to $it days)" } ?: "") + ".",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.accept(r) }, modifier = Modifier.height(48.dp)) { Text("Extend goal") }
                        OutlinedButton(onClick = { vm.dismiss(r) }, modifier = Modifier.height(48.dp)) { Text("Keep current") }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    "Steps today", s.steps.grouped(), Modifier.weight(1f),
                    sub = "goal ${s.stepGoal.grouped()}", icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                    accent = if (s.steps >= s.stepGoal) PaceColors.Ahead else PaceColors.Tertiary,
                )
                StatTile(
                    "Calories", s.eatenKcal.grouped(), Modifier.weight(1f),
                    sub = "of ${s.targetKcal.grouped()} kcal", icon = Icons.Filled.Restaurant, accent = PaceColors.Secondary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    "Streak", "${s.streak} days", Modifier.weight(1f),
                    sub = "best ${s.bestStreak}", icon = Icons.Filled.LocalFireDepartment, accent = PaceColors.Pink,
                )
                StatTile(
                    "Protein", "${s.protein} g", Modifier.weight(1f),
                    sub = "of ${s.proteinGoal} g", icon = Icons.Filled.FitnessCenter,
                    accent = if (s.protein >= s.proteinGoal && s.proteinGoal > 0) PaceColors.Ahead else PaceColors.Purple,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    "Water", "${s.water}/${s.waterGoal}", Modifier.weight(1f),
                    sub = "tap + below", icon = Icons.Filled.LocalDrink, accent = PaceColors.Tertiary,
                )
            }
            FilledTonalButton(onClick = { vm.addWater() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Filled.LocalDrink, null)
                Spacer(Modifier.size(8.dp))
                Text("+1 glass of water")
            }

            if (s.menu.isNotEmpty()) {
                SectionCard(
                    "Today's menu · ${s.menuWeek}" + if (s.menuVeg) " · veg" else "",
                    icon = Icons.Filled.Restaurant,
                    trailing = { TextButton(onClick = { onOpen(Routes.PLAN) }) { Text("Open") } },
                ) {
                    s.menu.forEach { (slot, dish) ->
                        Row {
                            Text(slot, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(120.dp))
                            Text(dish, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        }
                    }
                    Text("Plan total ${s.menuKcal.grouped()} kcal. Tap Open to log meals with one tap.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (s.wins.isNotEmpty()) {
                SectionCard("Wins", icon = Icons.Filled.Celebration, trailing = {
                    TextButton(onClick = { onOpen(Routes.STREAKS) }) { Text("Badges") }
                }) {
                    s.wins.forEach { Text("🎉  $it", style = MaterialTheme.typography.bodyLarge) }
                }
            }
        }

        Button(
            onClick = onLogToday,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp).height(64.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Icon(Icons.Filled.EditNote, null)
            Spacer(Modifier.size(10.dp))
            Text("Log Today", style = MaterialTheme.typography.titleLarge)
        }
    }
}
