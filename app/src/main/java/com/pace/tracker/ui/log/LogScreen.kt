@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.pace.tracker.ui.log

import androidx.compose.foundation.clickable
import com.pace.tracker.domain.BodyMath
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pace.tracker.data.db.MealEntity
import com.pace.tracker.data.db.WorkoutEntity
import com.pace.tracker.data.db.effectiveSteps
import com.pace.tracker.data.today
import com.pace.tracker.domain.MealType
import com.pace.tracker.photo.PhotoInputButtons
import com.pace.tracker.ui.components.NumberField
import com.pace.tracker.ui.components.ScreenScaffold
import com.pace.tracker.ui.components.SectionCard
import com.pace.tracker.ui.components.dayLabel
import com.pace.tracker.ui.components.grouped
import com.pace.tracker.ui.components.paceViewModel
import com.pace.tracker.ui.theme.PaceColors
import java.io.File
import kotlin.math.roundToInt

@Composable
fun LogScreen(initialDay: Long) {
    val vm = paceViewModel(key = "log") { LogViewModel(it, initialDay) }
    LaunchedEffect(initialDay) { if (initialDay > 0) vm.setDay(initialDay) }
    val day by vm.day.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    val meals by vm.meals.collectAsStateWithLifecycle()
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val target by vm.targetKcal.collectAsStateWithLifecycle()
    val form = vm.form
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }

    var mealDialog by remember { mutableStateOf<MealEntity?>(null) }
    var pickerType by remember { mutableStateOf<MealType?>(null) }
    val yesterday by vm.yesterdayMeals.collectAsStateWithLifecycle()
    val recent by vm.recentFoods.collectAsStateWithLifecycle()
    val myFoods by vm.myFoods.collectAsStateWithLifecycle()
    var workoutDialog by remember { mutableStateOf<WorkoutEntity?>(null) }

    ScreenScaffold(title = if (day == today()) "Today" else day.dayLabel(), snackbarHost = { SnackbarHost(snackbar) }, actions = {
        IconButton(onClick = { vm.setDay(day - 1) }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.ChevronLeft, "Previous day")
        }
        IconButton(onClick = { vm.setDay(day + 1) }, enabled = day < today(), modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.ChevronRight, "Next day")
        }
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (day != today()) {
                TextButton(onClick = { vm.setDay(today()) }) { Text("Jump to today") }
            }

            // ---- Weight
            SectionCard("Weight", icon = Icons.Filled.MonitorWeight, trailing = { SavedBadge(vm.savedAt != null) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(form.weight, { v -> vm.update { it.copy(weight = v) } }, "Weight",
                        Modifier.weight(1.3f), suffix = "kg")
                    NumberField(form.bodyFat, { v -> vm.update { it.copy(bodyFat = v) } }, "Body fat",
                        Modifier.weight(1f), suffix = "%")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(-0.1, 0.1).forEach { delta ->
                        FilledTonalButton(
                            onClick = {
                                val base = form.weight.replace(',', '.').toDoubleOrNull() ?: profile?.startWeightKg ?: return@FilledTonalButton
                                vm.update { it.copy(weight = String.format(java.util.Locale.US, "%.1f", base + delta)) }
                            },
                            enabled = vm.formLoaded,
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) { Text(if (delta < 0) "−0.1" else "+0.1") }
                    }
                }
            }

            // ---- Meals
            val total = meals.sumOf { it.calories }
            SectionCard("Meals", icon = Icons.Filled.Restaurant, trailing = {
                Text("${total.grouped()} / ${target.grouped()} kcal",
                    color = if (total <= target) MaterialTheme.colorScheme.primary else PaceColors.Error,
                    fontWeight = FontWeight.SemiBold)
            }) {
                val proteinTotal = meals.sumOf { it.protein ?: 0.0 }
                val proteinGoal = profile?.let { BodyMath.proteinTarget(it.targetWeightKg) } ?: 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Protein", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${proteinTotal.roundToInt()} / $proteinGoal g", fontWeight = FontWeight.SemiBold,
                        color = if (proteinTotal >= proteinGoal && proteinGoal > 0) PaceColors.Ahead else MaterialTheme.colorScheme.onSurface)
                }
                LinearProgressIndicator(
                    progress = { if (proteinGoal > 0) (proteinTotal / proteinGoal).toFloat().coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = PaceColors.Purple,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { pickerType = defaultMealType() }, modifier = Modifier.weight(1f).height(48.dp)) {
                        Icon(Icons.Filled.Bolt, null)
                        Text(" Quick add")
                    }
                    if (meals.isEmpty() && yesterday.isNotEmpty()) {
                        OutlinedButton(onClick = { vm.repeatYesterday(null) }, modifier = Modifier.weight(1f).height(48.dp)) {
                            Text("Copy yesterday")
                        }
                    }
                }
                MealType.entries.forEach { type ->
                    val items = meals.filter { it.type == type }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(type.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (items.isNotEmpty()) Text("${items.sumOf { it.calories }.grouped()} kcal",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (items.isEmpty() && yesterday.any { it.type == type }) {
                            IconButton(onClick = { vm.repeatYesterday(type) }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Filled.History, "Repeat yesterday's ${type.label.lowercase()}")
                            }
                        }
                        IconButton(onClick = { pickerType = type }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.Bolt, "Quick add ${type.label.lowercase()}")
                        }
                        IconButton(
                            onClick = { mealDialog = MealEntity(epochDay = day, type = type, description = "", calories = 0) },
                            modifier = Modifier.size(48.dp),
                        ) { Icon(Icons.Filled.Add, "Custom ${type.label.lowercase()}") }
                    }
                    items.forEach { meal ->
                        EntryRow(
                            title = meal.description.ifBlank { type.label },
                            subtitle = "${meal.calories.grouped()} kcal" + (meal.protein?.let { " · ${it.roundToInt()} g protein" } ?: ""),
                            photo = meal.photoPath,
                            onClick = { mealDialog = meal },
                        )
                    }
                }
            }

            // ---- Water
            SectionCard("Water", icon = Icons.Filled.LocalDrink) {
                val water = log?.waterGlasses ?: 0
                val goal = profile?.waterGoalGlasses ?: 8
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FilledIconButton(onClick = { vm.changeWater(-1) }, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.Filled.Remove, "Less water")
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$water", style = MaterialTheme.typography.displaySmall)
                        Text("of $goal glasses", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledIconButton(onClick = { vm.changeWater(1) }, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.Filled.Add, "More water")
                    }
                }
            }

            // ---- Workouts
            SectionCard("Workout", icon = Icons.Filled.FitnessCenter, trailing = {
                IconButton(
                    onClick = { workoutDialog = WorkoutEntity(epochDay = day, type = "", durationMin = 0, caloriesBurned = 0) },
                    modifier = Modifier.size(48.dp),
                ) { Icon(Icons.Filled.Add, "Add workout") }
            }) {
                if (workouts.isEmpty()) {
                    Text("No workout logged. Tap + to add one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                workouts.forEach { w ->
                    EntryRow(
                        title = w.type,
                        subtitle = "${w.durationMin} min · ${w.caloriesBurned.grouped()} kcal" + if (w.notes.isNotBlank()) " · ${w.notes}" else "",
                        photo = w.photoPath,
                        onClick = { workoutDialog = w },
                    )
                }
            }

            // ---- Steps
            SectionCard("Steps", icon = Icons.AutoMirrored.Filled.DirectionsWalk, trailing = {
                IconButton(onClick = { vm.syncHealthConnect() }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Sync, "Sync Health Connect")
                }
            }) {
                val effective = log?.effectiveSteps ?: 0
                val goal = profile?.stepGoal ?: 8000
                Text("${effective.grouped()} / ${goal.grouped()}", style = MaterialTheme.typography.headlineMedium,
                    color = if (effective >= goal) PaceColors.Ahead else MaterialTheme.colorScheme.onSurface)
                Text(
                    "Health Connect: ${log?.stepsHealthConnect?.grouped() ?: "—"} · In-app sensor: ${(log?.stepsSensor ?: 0).grouped()}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NumberField(
                    form.stepsManual, { v -> vm.update { it.copy(stepsManual = v) } }, "Manual steps (overrides)",
                    Modifier.fillMaxWidth(), decimal = false,
                )
            }

            // ---- Wellbeing
            SectionCard("Mood, energy & sleep", icon = Icons.Filled.Bedtime) {
                SliderRow("Mood", form.mood, form.moodSet, 1f..5f, 3, moodLabel(form.mood)) { v ->
                    vm.update { it.copy(mood = v, moodSet = true) }
                }
                SliderRow("Energy", form.energy, form.energySet, 1f..5f, 3, "${form.energy.roundToInt()}/5") { v ->
                    vm.update { it.copy(energy = v, energySet = true) }
                }
                SliderRow("Sleep", form.sleep, form.sleepSet, 0f..12f, 23, "${String.format(java.util.Locale.US, "%.1f", form.sleep)} h") { v ->
                    vm.update { it.copy(sleep = (v * 2).roundToInt() / 2f, sleepSet = true) }
                }
            }

            // ---- Notes
            SectionCard("Notes", icon = Icons.Filled.EditNote) {
                OutlinedTextField(
                    value = form.notes,
                    onValueChange = { v -> vm.update { it.copy(notes = v) } },
                    placeholder = { Text("How did today go?") },
                    enabled = vm.formLoaded,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                "Everything saves automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }

    mealDialog?.let { meal ->
        MealDialog(
            initial = meal,
            onSave = { vm.saveMeal(it); mealDialog = null },
            onSaveFood = { name, kcal, protein -> vm.saveFood(name, kcal, protein) },
            onDelete = if (meal.id != 0L) ({ vm.deleteMeal(meal); mealDialog = null }) else null,
            onDismiss = { mealDialog = null },
        )
    }
    pickerType?.let { t ->
        FoodPicker(
            initialType = t,
            recent = recent,
            myFoods = myFoods,
            onAdd = { type, desc, kcal, protein -> vm.quickAdd(type, desc, kcal, protein) },
            onDeleteFood = { vm.deleteFood(it) },
            onCustom = { type ->
                pickerType = null
                mealDialog = MealEntity(epochDay = day, type = type, description = "", calories = 0)
            },
            onDismiss = { pickerType = null },
        )
    }
    workoutDialog?.let { w ->
        WorkoutDialog(
            initial = w,
            weightKg = form.weight.replace(',', '.').toDoubleOrNull() ?: profile?.startWeightKg ?: 75.0,
            onSave = { vm.saveWorkout(it); workoutDialog = null },
            onDelete = if (w.id != 0L) ({ vm.deleteWorkout(w); workoutDialog = null }) else null,
            onDismiss = { workoutDialog = null },
        )
    }
}

/** Breakfast before 11:00, lunch before 16:00, snack before 19:00, then dinner. */
private fun defaultMealType(): MealType {
    val h = java.time.LocalTime.now().hour
    return when {
        h < 11 -> MealType.BREAKFAST
        h < 16 -> MealType.LUNCH
        h < 19 -> MealType.SNACK
        else -> MealType.DINNER
    }
}

private fun moodLabel(v: Float) = when (v.roundToInt()) {
    1 -> "😞 1/5"; 2 -> "🙁 2/5"; 3 -> "😐 3/5"; 4 -> "🙂 4/5"; else -> "😄 5/5"
}

@Composable
private fun SavedBadge(saved: Boolean) {
    if (saved) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, null, tint = PaceColors.Ahead, modifier = Modifier.size(16.dp))
            Text(" Saved", style = MaterialTheme.typography.labelMedium, color = PaceColors.Ahead)
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    set: Boolean,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: String,
    onChange: (Float) -> Unit,
) {
    Column {
        Row {
            Text(label, modifier = Modifier.weight(1f))
            Text(if (set) valueLabel else "not set", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun EntryRow(title: String, subtitle: String, photo: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (photo != null) {
            AsyncImage(
                model = File(photo), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PhotoPreview(path: String?, onRemove: () -> Unit) {
    if (path == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = File(path), contentDescription = "Photo", contentScale = ContentScale.Crop,
            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)),
        )
        TextButton(onClick = onRemove) { Text("Remove photo") }
    }
}

@Composable
private fun MealDialog(
    initial: MealEntity,
    onSave: (MealEntity) -> Unit,
    onSaveFood: (name: String, kcal: Int, protein: Double) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var protein by remember { mutableStateOf(initial.protein?.let { String.format(java.util.Locale.US, "%.0f", it) } ?: "") }
    var saveFood by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf(initial.type) }
    var description by remember { mutableStateOf(initial.description) }
    var calories by remember { mutableStateOf(if (initial.calories > 0) initial.calories.toString() else "") }
    var photo by remember { mutableStateOf(initial.photoPath) }
    val kcal = calories.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Add meal" else "Edit meal") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MealType.entries.forEach {
                        FilterChip(selected = type == it, onClick = { type = it }, label = { Text(it.label) })
                    }
                }
                OutlinedTextField(description, { description = it }, label = { Text("What did you eat?") }, modifier = Modifier.fillMaxWidth())
                NumberField(calories, { calories = it }, "Calories", Modifier.fillMaxWidth(), suffix = "kcal", decimal = false)
                NumberField(protein, { protein = it }, "Protein (optional)", Modifier.fillMaxWidth(), suffix = "g")
                Row(
                    Modifier.fillMaxWidth().clickable { saveFood = !saveFood },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = saveFood, onCheckedChange = { saveFood = it })
                    Text("Save to My foods for quick add")
                }
                PhotoPreview(photo) { photo = null }
                PhotoInputButtons(onPhoto = { photo = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val p = protein.replace(',', '.').toDoubleOrNull()
                    if (saveFood && description.isNotBlank()) onSaveFood(description.trim(), kcal ?: 0, p ?: 0.0)
                    onSave(initial.copy(type = type, description = description.trim(), calories = kcal ?: 0, protein = p, photoPath = photo))
                },
                enabled = kcal != null,
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = PaceColors.Error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** MET values used for the "Estimate" button (kcal = MET × kg × hours). */
private val workoutTypes = linkedMapOf(
    "Walk" to 3.5, "Run" to 9.8, "Cycling" to 7.5, "Strength" to 5.0, "HIIT" to 8.0,
    "Yoga" to 2.5, "Swim" to 7.0, "Sports" to 7.0, "Other" to 5.0,
)

@Composable
private fun WorkoutDialog(
    initial: WorkoutEntity,
    weightKg: Double,
    onSave: (WorkoutEntity) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var type by remember { mutableStateOf(initial.type.ifBlank { "Walk" }) }
    var duration by remember { mutableStateOf(if (initial.durationMin > 0) initial.durationMin.toString() else "") }
    var kcal by remember { mutableStateOf(if (initial.caloriesBurned > 0) initial.caloriesBurned.toString() else "") }
    var notes by remember { mutableStateOf(initial.notes) }
    var photo by remember { mutableStateOf(initial.photoPath) }
    val minutes = duration.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Add workout" else "Edit workout") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    workoutTypes.keys.forEach {
                        FilterChip(selected = type == it, onClick = { type = it }, label = { Text(it) })
                    }
                }
                OutlinedTextField(type, { type = it }, label = { Text("Type") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                NumberField(duration, { duration = it }, "Duration", Modifier.fillMaxWidth(), suffix = "min", decimal = false)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumberField(kcal, { kcal = it }, "Calories burned", Modifier.weight(1f), suffix = "kcal", decimal = false)
                    TextButton(
                        onClick = {
                            val met = workoutTypes[type] ?: 5.0
                            kcal = ((met * weightKg * (minutes ?: 0) / 60.0).roundToInt()).toString()
                        },
                        enabled = (minutes ?: 0) > 0,
                    ) { Text("Estimate") }
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                PhotoPreview(photo) { photo = null }
                PhotoInputButtons(onPhoto = { photo = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(initial.copy(type = type.trim().ifBlank { "Workout" }, durationMin = minutes ?: 0,
                        caloriesBurned = kcal.toIntOrNull() ?: 0, notes = notes.trim(), photoPath = photo))
                },
                enabled = minutes != null && minutes > 0,
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = PaceColors.Error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
