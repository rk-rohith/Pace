@file:OptIn(ExperimentalLayoutApi::class)

package com.pace.tracker.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pace.tracker.AppContainer
import com.pace.tracker.data.UserRecipes
import com.pace.tracker.domain.MealPlan
import com.pace.tracker.domain.MealPlanSchedule
import com.pace.tracker.domain.PlanFood
import com.pace.tracker.domain.PlanRecipe
import com.pace.tracker.domain.RecipePart
import com.pace.tracker.ui.components.EmptyState
import com.pace.tracker.ui.components.NumberField
import com.pace.tracker.ui.components.ScreenScaffold
import com.pace.tracker.ui.components.SectionCard
import com.pace.tracker.ui.components.StatTile
import com.pace.tracker.ui.components.grouped
import com.pace.tracker.ui.components.paceViewModel
import com.pace.tracker.ui.components.toDecimalOrNull
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** One ingredient row while editing; [qty] is kept as typed. */
data class EditPart(val food: String, val qty: String, val note: String)

class RecipeEditorViewModel(private val container: AppContainer, private val recipeId: String?) : ViewModel() {
    private val repository = container.repository

    val plan: StateFlow<MealPlan> = repository.settings.map { MealPlanViewModel.effective(container.mealPlan, it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, container.mealPlan)

    var ready by mutableStateOf(false)
        private set
    var name by mutableStateOf("")
    var category by mutableStateOf(MealPlanSchedule.USER_CATEGORIES[1])
    var veg by mutableStateOf(false)
    val parts = mutableStateListOf<EditPart>()
    var extras by mutableStateOf("")
    var steps by mutableStateOf("")
    var tip by mutableStateOf("")

    /** True when editing a recipe that ships with the app. */
    var bundled by mutableStateOf(false)
        private set
    var editedBundled by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val p = MealPlanViewModel.effective(container.mealPlan, repository.settings.first())
            val r = recipeId?.let { p.recipes[it] }
            if (r != null) {
                name = r.name
                category = r.category
                veg = r.veg
                parts.addAll(r.parts.map { EditPart(it.food, RecipeTextNum(it.qty), it.note) })
                extras = r.extras.joinToString("\n")
                steps = r.steps.joinToString("\n")
                tip = r.tip
                bundled = recipeId in container.mealPlan.recipes
                editedBundled = r.edited
            }
            ready = true
        }
    }

    fun recipeParts(): List<RecipePart> = parts.mapNotNull { e ->
        e.qty.toDecimalOrNull()?.takeIf { it > 0 }?.let { RecipePart(e.food, it, e.note.trim()) }
    }

    val canSave: Boolean get() = name.isNotBlank() && recipeParts().isNotEmpty()

    private fun lines(text: String) = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

    /** Saves and returns the recipe id. Runs in the app scope so leaving the screen can't cancel it. */
    fun save(): String {
        val id = recipeId ?: ("my_" + System.currentTimeMillis().toString(36))
        val recipe = PlanRecipe(
            id = id, name = name.trim(), category = category, source = "Your recipe", veg = veg,
            kcal = 0, protein = 0, carbs = 0, fat = 0, ingredients = emptyList(),
            steps = lines(steps), tip = tip.trim(), reels = emptyList(),
            parts = recipeParts(), extras = lines(extras),
        )
        container.appScope.launch { repository.setSetting(UserRecipes.RECIPE_PREFIX + id, UserRecipes.encodeRecipe(recipe)) }
        return id
    }

    /** Deletes a user recipe, or resets a bundled one. */
    fun remove() {
        val id = recipeId ?: return
        container.appScope.launch { repository.setSetting(UserRecipes.RECIPE_PREFIX + id, "") }
    }

    /** Creates an ingredient from label values ([per] = 100 for per-100 g/ml, 1 for per piece) and adds it. */
    fun createFood(name: String, unit: String, per: Double, kcal: Double, protein: Double, carbs: Double, fat: Double, section: String, onList: Boolean) {
        val key = "my_" + System.currentTimeMillis().toString(36)
        val food = PlanFood(
            key = key, name = name.trim(), unit = unit,
            kcal = kcal / per, protein = protein / per, carbs = carbs / per, fat = fat / per,
            shop = if (onList) name.trim() else null, section = section, custom = true,
        )
        container.appScope.launch { repository.setSetting(UserRecipes.FOOD_PREFIX + key, UserRecipes.encodeFood(food)) }
        parts.add(EditPart(key, if (unit == "pc") "1" else "100", ""))
    }
}

private fun RecipeTextNum(q: Double) = com.pace.tracker.domain.RecipeText.num(q)

@Composable
fun RecipeEditorScreen(recipeId: String?, onBack: () -> Unit, onSaved: (String) -> Unit, onRemoved: () -> Unit) {
    val vm = paceViewModel(key = "recipe-edit-${recipeId ?: "new"}") { RecipeEditorViewModel(it, recipeId) }
    val plan by vm.plan.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    ScreenScaffold(
        if (recipeId == null) "New recipe" else "Edit recipe",
        onBack = onBack,
        actions = { TextButton(onClick = { onSaved(vm.save()) }, enabled = vm.canSave) { Text("Save") } },
    ) { padding ->
        if (!vm.ready) return@ScreenScaffold
        val parts = vm.recipeParts()
        val total = plan.macros(parts)
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Calories", total.kcal.roundToInt().grouped(), Modifier.weight(1f), sub = "kcal per serving")
                StatTile(
                    "Protein", "${total.protein.roundToInt()} g", Modifier.weight(1f),
                    sub = "C ${total.carbs.roundToInt()} g · F ${total.fat.roundToInt()} g",
                )
            }
            Text(
                "Calculated from the ingredients as you type. Enter raw weights for one serving.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = vm.name, onValueChange = { vm.name = it }, label = { Text("Recipe name") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (MealPlanSchedule.USER_CATEGORIES + listOf(vm.category).filter { it !in MealPlanSchedule.USER_CATEGORIES }).forEach { c ->
                    FilterChip(selected = vm.category == c, onClick = { vm.category = c }, label = { Text(c) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Vegetarian", style = MaterialTheme.typography.bodyLarge)
                    Text("Veg recipes can be swapped in on veg days.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = vm.veg, onCheckedChange = { vm.veg = it })
            }

            SectionCard("Ingredients") {
                if (vm.parts.isEmpty()) {
                    Text("Add each ingredient with its amount; the app works out the macros.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                vm.parts.forEachIndexed { i, e ->
                    PartRow(plan, e, onChange = { vm.parts[i] = it }, onRemove = { vm.parts.removeAt(i) })
                }
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.height(48.dp)) {
                    Icon(Icons.Filled.Add, null)
                    Text(" Add ingredient")
                }
            }
            OutlinedTextField(
                value = vm.extras, onValueChange = { vm.extras = it },
                label = { Text("Seasoning & extras (one per line, not counted)") },
                modifier = Modifier.fillMaxWidth(), minLines = 2,
            )
            OutlinedTextField(
                value = vm.steps, onValueChange = { vm.steps = it },
                label = { Text("Preparation (one step per line)") },
                modifier = Modifier.fillMaxWidth(), minLines = 4,
            )
            OutlinedTextField(
                value = vm.tip, onValueChange = { vm.tip = it }, label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(), minLines = 1,
            )
            FilledTonalButton(onClick = { onSaved(vm.save()) }, enabled = vm.canSave, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Save recipe")
            }
            if (!vm.canSave) {
                Text("Needs a name and at least one ingredient with an amount.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (recipeId != null && (!vm.bundled || vm.editedBundled)) {
                TextButton(onClick = { confirmRemove = true }) {
                    Text(if (vm.bundled) "Reset to the original recipe" else "Delete recipe", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (picking) {
        FoodPickerDialog(
            foods = plan.foods.values.toList(),
            onPick = { f -> vm.parts.add(EditPart(f.key, if (f.unit == "pc" || f.unit == "scoop") "1" else "100", "")); picking = false },
            onCreate = { picking = false; creating = true },
            onDismiss = { picking = false },
        )
    }
    if (creating) {
        NewFoodDialog(
            onCreate = { name, unit, per, k, p, c, f, section, onList ->
                vm.createFood(name, unit, per, k, p, c, f, section, onList); creating = false
            },
            onDismiss = { creating = false },
        )
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(if (vm.bundled) "Reset recipe?" else "Delete recipe?") },
            text = {
                Text(
                    if (vm.bundled) "Your changes are discarded and the original recipe comes back."
                    else "Meals swapped to this recipe go back to the planned dish.",
                )
            },
            confirmButton = { TextButton(onClick = { vm.remove(); confirmRemove = false; onRemoved() }) { Text(if (vm.bundled) "Reset" else "Delete") } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PartRow(plan: MealPlan, e: EditPart, onChange: (EditPart) -> Unit, onRemove: () -> Unit) {
    val food = plan.foods[e.food]
    val m = e.qty.toDecimalOrNull()?.let { q -> plan.macros(RecipePart(e.food, q)) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(food?.name ?: "Unknown ingredient", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    m?.let { "${it.kcal.roundToInt()} kcal · ${it.protein.oneDp()} g protein" } ?: "Enter an amount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            NumberField(
                value = e.qty, onValueChange = { onChange(e.copy(qty = it)) }, label = "Amount",
                suffix = food?.unit?.let { if (it == "pc") "pcs" else it }, modifier = Modifier.width(120.dp),
            )
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Remove") }
        }
        OutlinedTextField(
            value = e.note, onValueChange = { onChange(e.copy(note = it)) },
            placeholder = { Text("Note, e.g. chopped, for the marinade") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun Double.oneDp(): String = if (this >= 10) roundToInt().toString() else String.format(java.util.Locale.US, "%.1f", this)

/** Per-100 (or per-piece) summary used in the picker. */
private fun PlanFood.perLabel(): String {
    val per = if (unit == "pc" || unit == "scoop") 1.0 else 100.0
    val what = when (unit) { "pc" -> "each"; "scoop" -> "per scoop"; else -> "per 100 $unit" }
    return "${(kcal * per).roundToInt()} kcal · ${(protein * per).oneDp()} g protein $what"
}

@Composable
private fun FoodPickerDialog(foods: List<PlanFood>, onPick: (PlanFood) -> Unit, onCreate: () -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val list = foods.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) || (it.shop?.contains(query, true) == true) }
        .sortedWith(compareBy<PlanFood>({ !it.custom }, { it.name }))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ingredient") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, label = { Text("Search") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    if (list.isEmpty()) item { EmptyState("Not in the list", "Create it with the values from its label.") }
                    items(list, key = { it.key }) { f ->
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(f) }.padding(vertical = 8.dp),
                        ) {
                            Text(f.name + if (f.custom) " · yours" else "", style = MaterialTheme.typography.bodyLarge)
                            Text(f.perLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onCreate) { Text("New ingredient") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NewFoodDialog(
    onCreate: (name: String, unit: String, per: Double, kcal: Double, protein: Double, carbs: Double, fat: Double, section: String, onList: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf("g") }
    var kcal by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var carbs by rememberSaveable { mutableStateOf("") }
    var fat by rememberSaveable { mutableStateOf("") }
    var section by rememberSaveable { mutableStateOf(MealPlanSchedule.SECTION_ORDER.last()) }
    var onList by rememberSaveable { mutableStateOf(true) }
    val per = if (unit == "pc") 1.0 else 100.0
    val perText = if (unit == "pc") "per piece" else "per 100 $unit"
    val k = kcal.toDecimalOrNull()
    val valid = name.isNotBlank() && k != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New ingredient") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Measured in", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("g" to "grams", "ml" to "ml", "pc" to "pieces").forEach { (u, l) ->
                        FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(l) })
                    }
                }
                Text("Nutrition $perText (from the label)", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(kcal, { kcal = it }, "kcal", Modifier.weight(1f))
                    NumberField(protein, { protein = it }, "Protein g", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(carbs, { carbs = it }, "Carbs g", Modifier.weight(1f))
                    NumberField(fat, { fat = it }, "Fat g", Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = onList, onCheckedChange = { onList = it })
                    Text("Put it on the grocery list")
                }
                if (onList) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MealPlanSchedule.SECTION_ORDER.forEach { s ->
                            FilterChip(selected = section == s, onClick = { section = s }, label = { Text(s) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onCreate(
                        name, unit, per, k ?: 0.0, protein.toDecimalOrNull() ?: 0.0, carbs.toDecimalOrNull() ?: 0.0,
                        fat.toDecimalOrNull() ?: 0.0, section, onList,
                    )
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
