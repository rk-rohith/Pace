@file:OptIn(ExperimentalLayoutApi::class)

package com.pace.tracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pace.tracker.data.PaceRepository
import com.pace.tracker.data.db.ProfileEntity
import com.pace.tracker.domain.ActivityLevel
import com.pace.tracker.domain.PacePreference
import com.pace.tracker.domain.Sex
import com.pace.tracker.ui.components.LabeledValue
import com.pace.tracker.ui.components.NumberField
import com.pace.tracker.ui.components.ScreenScaffold
import com.pace.tracker.ui.components.SectionCard
import com.pace.tracker.ui.components.grouped
import com.pace.tracker.ui.components.kg
import com.pace.tracker.ui.components.oneDecimal
import com.pace.tracker.ui.components.paceViewModel
import com.pace.tracker.ui.components.shortDate
import com.pace.tracker.ui.components.toDecimalOrNull
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(private val repository: PaceRepository) : ViewModel() {
    val profile: StateFlow<ProfileEntity?> = repository.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    var message by mutableStateOf<String?>(null)

    fun save(p: ProfileEntity) = viewModelScope.launch {
        repository.updateProfile(p)
        message = "Saved. Any change to activity, pace or body stats recalculates your target (see Target history)."
    }
}

@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val vm = paceViewModel { ProfileViewModel(it.repository) }
    val profile by vm.profile.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snackbar.showSnackbar(it); vm.message = null } }
    ScreenScaffold("Profile & goal", onBack = onBack, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        val p = profile ?: return@ScreenScaffold
        var sex by remember(p) { mutableStateOf(p.sex) }
        var age by remember(p) { mutableStateOf(p.age.toString()) }
        var height by remember(p) { mutableStateOf(p.heightCm.oneDecimal()) }
        var target by remember(p) { mutableStateOf(p.targetWeightKg.oneDecimal()) }
        var duration by remember(p) { mutableStateOf(p.durationDays.toString()) }
        var activity by remember(p) { mutableStateOf(p.activity) }
        var pace by remember(p) { mutableStateOf(p.pace) }
        var steps by remember(p) { mutableStateOf(p.stepGoal.toString()) }
        var water by remember(p) { mutableStateOf(p.waterGoalGlasses.toString()) }
        var workouts by remember(p) { mutableStateOf(p.workoutsPerWeek.toString()) }

        val valid = age.toIntOrNull()?.let { it in 16..90 } == true &&
            height.toDecimalOrNull()?.let { it in 120.0..230.0 } == true &&
            target.toDecimalOrNull()?.let { it in 35.0..300.0 } == true &&
            duration.toIntOrNull()?.let { it in 7..365 } == true &&
            (steps.toIntOrNull() ?: 0) > 0 && (water.toIntOrNull() ?: 0) > 0 && (workouts.toIntOrNull() ?: -1) in 0..14

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard("Programme") {
                LabeledValue("Started", "${p.startEpochDay.shortDate()} at ${p.startWeightKg.kg()}")
                LabeledValue("Original goal", p.originalTargetWeightKg.kg())
                LabeledValue("Current target", "${p.currentTargetKcal.grouped()} kcal/day")
                LabeledValue("Goal date", (p.startEpochDay + p.durationDays).shortDate())
            }
            SectionCard("Body") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Sex.entries.forEach { FilterChip(selected = sex == it, onClick = { sex = it }, label = { Text(it.label) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(age, { age = it }, "Age", Modifier.weight(1f), decimal = false)
                    NumberField(height, { height = it }, "Height", Modifier.weight(1f), suffix = "cm")
                }
            }
            SectionCard("Goal") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(target, { target = it }, "Target weight", Modifier.weight(1f), suffix = "kg")
                    NumberField(duration, { duration = it }, "Duration", Modifier.weight(1f), suffix = "days", decimal = false)
                }
                Text("Pace", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PacePreference.entries.forEach {
                        FilterChip(selected = pace == it, onClick = { pace = it }, label = { Text("${it.label} ${it.kgPerWeek}") })
                    }
                }
                Text("Activity level", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActivityLevel.entries.forEach {
                        FilterChip(selected = activity == it, onClick = { activity = it }, label = { Text(it.label) })
                    }
                }
            }
            SectionCard("Daily goals") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(steps, { steps = it }, "Steps", Modifier.weight(1f), decimal = false)
                    NumberField(water, { water = it }, "Water", Modifier.weight(1f), suffix = "glasses", decimal = false)
                }
                NumberField(workouts, { workouts = it }, "Workouts per week (streak goal)", Modifier.fillMaxWidth(), decimal = false)
            }
            Button(
                onClick = {
                    vm.save(
                        p.copy(
                            sex = sex, age = age.toInt(), heightCm = height.toDecimalOrNull()!!,
                            targetWeightKg = target.toDecimalOrNull()!!, durationDays = duration.toInt(),
                            activity = activity, pace = pace, stepGoal = steps.toInt(),
                            waterGoalGlasses = water.toInt(), workoutsPerWeek = workouts.toInt(),
                        ),
                    )
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Save") }
        }
    }
}
