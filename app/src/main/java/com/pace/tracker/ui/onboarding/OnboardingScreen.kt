@file:OptIn(ExperimentalMaterial3Api::class)

package com.pace.tracker.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pace.tracker.data.PaceRepository
import com.pace.tracker.domain.ActivityLevel
import com.pace.tracker.domain.AdaptiveConfig
import com.pace.tracker.domain.InitialPlan
import com.pace.tracker.domain.InitialPlanner
import com.pace.tracker.domain.PacePreference
import com.pace.tracker.domain.ProfileInputs
import com.pace.tracker.domain.Sex
import com.pace.tracker.reminders.ReminderScheduler
import com.pace.tracker.ui.components.LabeledValue
import com.pace.tracker.ui.components.NumberField
import com.pace.tracker.ui.components.SectionCard
import com.pace.tracker.ui.components.kcal
import com.pace.tracker.ui.components.kg
import com.pace.tracker.ui.components.oneDecimal
import com.pace.tracker.ui.components.paceViewModel
import com.pace.tracker.ui.components.shortDate
import com.pace.tracker.ui.components.toDecimalOrNull
import com.pace.tracker.data.today
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val repository: PaceRepository,
    private val scheduler: ReminderScheduler,
) : ViewModel() {
    var saving by mutableStateOf(false)
        private set

    fun finish(inputs: ProfileInputs, stepGoal: Int, waterGoal: Int) {
        if (saving) return
        saving = true
        viewModelScope.launch {
            repository.createProfile(inputs, stepGoal, waterGoal)
            runCatching { scheduler.rescheduleAll() }
        }
    }
}

@Composable
fun OnboardingScreen() {
    val vm = paceViewModel { OnboardingViewModel(it.repository, it.reminderScheduler) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var sex by rememberSaveable { mutableStateOf(Sex.MALE) }
    var age by rememberSaveable { mutableStateOf("") }
    var height by rememberSaveable { mutableStateOf("") }
    var startWeight by rememberSaveable { mutableStateOf("") }
    var targetWeight by rememberSaveable { mutableStateOf("") }
    var activity by rememberSaveable { mutableStateOf(ActivityLevel.LIGHT) }
    var pace by rememberSaveable { mutableStateOf(PacePreference.MODERATE) }
    var stepGoal by rememberSaveable { mutableStateOf(AdaptiveConfig.DEFAULT_STEP_GOAL.toString()) }
    var waterGoal by rememberSaveable { mutableStateOf("8") }

    val ageV = age.toIntOrNull()
    val heightV = height.toDecimalOrNull()
    val startV = startWeight.toDecimalOrNull()
    val targetV = targetWeight.toDecimalOrNull()
    val bodyValid = ageV != null && ageV in 16..90 && heightV != null && heightV in 120.0..230.0 &&
        startV != null && startV in 35.0..300.0
    val goalValid = startV != null && targetV != null && targetV in 35.0..(startV - 0.5)

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Reminders degrade gracefully if denied; they can be enabled later from Settings.
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Pace", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text("80-day adaptive fat-loss tracker", style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(progress = { (step + 1) / 4f }, modifier = Modifier.fillMaxWidth())

        when (step) {
            0 -> SectionCard("About you") {
                Text("Sex (for the BMR formula)", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Sex.entries.forEach {
                        FilterChip(selected = sex == it, onClick = { sex = it }, label = { Text(it.label) },
                            modifier = Modifier.height(48.dp))
                    }
                }
                NumberField(age, { age = it }, "Age", Modifier.fillMaxWidth(), suffix = "years", decimal = false)
                NumberField(height, { height = it }, "Height", Modifier.fillMaxWidth(), suffix = "cm")
                NumberField(startWeight, { startWeight = it }, "Current weight", Modifier.fillMaxWidth(), suffix = "kg")
            }
            1 -> SectionCard("Your goal") {
                NumberField(
                    targetWeight, { targetWeight = it }, "Target weight", Modifier.fillMaxWidth(), suffix = "kg",
                    isError = targetWeight.isNotEmpty() && !goalValid,
                    supportingText = startV?.let { "Soft target — e.g. ${(it - 8.5).oneDecimal()} kg for −8.5 kg. The app adapts it weekly." },
                )
                Text("Pace preference", style = MaterialTheme.typography.labelLarge)
                PacePreference.entries.forEach { p ->
                    ChoiceRow(
                        selected = pace == p,
                        title = "${p.label} · ${p.kgPerWeek} kg/week",
                        subtitle = when (p) {
                            PacePreference.CONSERVATIVE -> "Easiest to sustain, most muscle-friendly"
                            PacePreference.MODERATE -> "Balanced — the default"
                            PacePreference.AGGRESSIVE -> "Top of the healthy band (capped at 1% body weight/week)"
                        },
                        onClick = { pace = p },
                    )
                }
                NumberField(stepGoal, { stepGoal = it }, "Daily step goal", Modifier.fillMaxWidth(), decimal = false)
                NumberField(waterGoal, { waterGoal = it }, "Daily water goal", Modifier.fillMaxWidth(), suffix = "glasses", decimal = false)
            }
            2 -> SectionCard("Activity level (outside logged workouts)") {
                ActivityLevel.entries.forEach { a ->
                    ChoiceRow(
                        selected = activity == a,
                        title = "${a.label} (×${a.multiplier})",
                        subtitle = a.description,
                        onClick = { activity = a },
                    )
                }
            }
            else -> {
                val inputs = ProfileInputs(heightV!!, startV!!, targetV!!, ageV!!, sex, activity, pace)
                PlanSummary(InitialPlanner.plan(inputs), inputs)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) {
                OutlinedButton(onClick = { step-- }, modifier = Modifier.weight(1f).height(56.dp)) { Text("Back") }
            }
            val canNext = when (step) {
                0 -> bodyValid
                1 -> goalValid && (stepGoal.toIntOrNull() ?: 0) > 0 && (waterGoal.toIntOrNull() ?: 0) > 0
                else -> true
            }
            Button(
                onClick = {
                    if (step < 3) {
                        step++
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        vm.finish(
                            ProfileInputs(heightV!!, startV!!, targetV!!, ageV!!, sex, activity, pace),
                            stepGoal.toInt(), waterGoal.toInt(),
                        )
                    }
                },
                enabled = canNext && !vm.saving,
                modifier = Modifier.weight(1f).height(56.dp),
            ) {
                Text(if (step < 3) "Next" else "Start my ${AdaptiveConfig.DEFAULT_DURATION_DAYS} days")
            }
        }
    }
}

@Composable
private fun ChoiceRow(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlanSummary(plan: InitialPlan, inputs: ProfileInputs) {
    val start = today()
    SectionCard("Your starting plan") {
        Text(
            plan.targetKcal.kcal() + " / day",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        LabeledValue("BMR (Mifflin–St Jeor)", plan.bmr.kcal())
        LabeledValue("TDEE (maintenance)", plan.tdee.kcal())
        LabeledValue("Daily deficit", plan.dailyDeficit.kcal())
        LabeledValue("Planned loss", "${plan.plannedRateKg.oneDecimal()} kg/week")
        LabeledValue("Needed for goal", "${String.format(java.util.Locale.US, "%.2f", plan.requiredRateKg)} kg/week")
        LabeledValue("Projected at day ${inputs.durationDays}", plan.projectedFinishKg.kg())
        LabeledValue("Goal date (soft)", (start + inputs.durationDays).shortDate())
        Text(plan.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Every 7 days the target is recalibrated from your real weight trend. If you're ahead, " +
                "the app will offer to extend the goal further.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
