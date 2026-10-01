@file:OptIn(ExperimentalLayoutApi::class)

package com.pace.tracker.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.remember
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pace.tracker.domain.MealPlan
import com.pace.tracker.domain.MealPlanSchedule
import com.pace.tracker.domain.PlanDay
import com.pace.tracker.domain.PlanMeal
import com.pace.tracker.domain.PlanRecipe
import com.pace.tracker.ui.components.EmptyState
import com.pace.tracker.ui.components.LabeledValue
import com.pace.tracker.ui.components.Pill
import com.pace.tracker.ui.components.ScreenScaffold
import com.pace.tracker.ui.components.SectionCard
import com.pace.tracker.ui.components.grouped
import com.pace.tracker.ui.components.paceViewModel
import com.pace.tracker.ui.theme.PaceColors
import java.time.LocalDate

private val tabs = listOf("Today", "Week", "Recipes", "Groceries")

@Composable
fun MealPlanScreen(onBack: () -> Unit, onOpenRecipe: (String) -> Unit) {
    val vm = paceViewModel { MealPlanViewModel(it) }
    val currentWeek by vm.currentWeekId.collectAsStateWithLifecycle()
    val logged by vm.loggedToday.collectAsStateWithLifecycle()
    val ticks by vm.groceryTicks.collectAsStateWithLifecycle()
    val plan by vm.effectivePlan.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var swapTarget by remember { mutableStateOf<SwapTarget?>(null) }
    val onSwap: (SwapTarget) -> Unit = { swapTarget = it }
    val weekId = vm.browsedWeekId ?: currentWeek

    ScreenScaffold("Meal plan", onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }, modifier = Modifier.height(52.dp))
                }
            }
            if (plan.weeks.isEmpty()) {
                EmptyState("Meal plan unavailable", "The bundled plan could not be read. Reinstall the app to restore it.")
                return@Column
            }
            when (tab) {
                0 -> TodayTab(plan, currentWeek, logged, vm::logMeal, onOpenRecipe, onSwap)
                1 -> WeekTab(plan, weekId, currentWeek, vm, onOpenRecipe, onSwap)
                2 -> RecipesTab(plan, onOpenRecipe)
                else -> GroceriesTab(plan, weekId, currentWeek, vm, ticks[weekId] ?: emptySet())
            }
        }
    }

    swapTarget?.let { t ->
        val day = plan.week(t.weekId).days.getOrNull(t.dayIndex)
        val meal = day?.meals?.getOrNull(t.mealIndex)
        if (day == null || meal == null) {
            swapTarget = null
        } else {
            SwapDialog(
                plan = plan,
                day = day,
                meal = meal,
                onPick = { id -> vm.swap(t.weekId, t.dayIndex, t.mealIndex, id); swapTarget = null },
                onReset = { vm.swap(t.weekId, t.dayIndex, t.mealIndex, null); swapTarget = null },
                onDismiss = { swapTarget = null },
            )
        }
    }
}

/** Which meal is being swapped. */
data class SwapTarget(val weekId: String, val dayIndex: Int, val mealIndex: Int)

@Composable
private fun SwapDialog(
    plan: MealPlan,
    day: PlanDay,
    meal: PlanMeal,
    onPick: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val current = plan.recipes[meal.recipeIds.first()]
    val original = plan.recipes[meal.swappedFrom ?: meal.recipeIds.first()]
    val options = plan.swapCandidates(day, meal)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Swap ${meal.label.lowercase()}") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    Text(
                        "Now: ${current?.name} (${current?.kcal?.grouped()} kcal). Options within about ${MealPlanSchedule.SWAP_KCAL} kcal" +
                            (if (day.veg) ", veg only" else "") + ". Sides stay as planned; the grocery list updates.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (options.isEmpty()) item { Text("No similar dishes found.") }
                items(options, key = { it.id }) { r ->
                    val diff = r.kcal - (original?.kcal ?: r.kcal)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(r.id) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${r.kcal.grouped()} kcal (" + (if (diff >= 0) "+" else "") + "$diff) · ${r.protein} g protein" +
                                    if (r.reels.isNotEmpty()) " · reel" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (r.veg) Pill("Veg", PaceColors.Ahead)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        dismissButton = {
            if (meal.swappedFrom != null) TextButton(onClick = onReset) { Text("Back to ${original?.name ?: "plan"}") }
        },
    )
}

@Composable
private fun WeekPicker(plan: MealPlan, selected: String, current: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        plan.weeks.forEach { w ->
            FilterChip(
                selected = selected == w.id,
                onClick = { onSelect(w.id) },
                label = { Text(w.title + if (w.id == current) " · this week" else "") },
                modifier = Modifier.height(44.dp),
            )
        }
    }
}

@Composable
private fun TodayTab(
    plan: MealPlan,
    weekId: String,
    logged: Set<String>,
    onLog: (PlanMeal) -> Unit,
    onOpenRecipe: (String) -> Unit,
    onSwap: (SwapTarget) -> Unit,
) {
    val week = plan.week(weekId)
    val dayIndex = MealPlanSchedule.dayIndex(LocalDate.now())
    val day = week.days.getOrNull(dayIndex) ?: return
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { DayHeader(plan, day, "${week.title} · ${week.subtitle}") }
        itemsIndexed(day.meals) { mi, meal ->
            val title = plan.title(meal)
            MealCard(
                plan, meal, done = title in logged, onLog = { onLog(meal) }, onOpenRecipe = onOpenRecipe,
                onSwap = if (MealPlanSchedule.kindOf(meal.label) != null) ({ onSwap(SwapTarget(week.id, dayIndex, mi)) }) else null,
            )
        }
        item {
            Text(
                "Tap Log to add a meal to today's log with its calories. Off-plan food? Log it from the Log tab as usual.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DayHeader(plan: MealPlan, day: PlanDay, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(day.day, style = MaterialTheme.typography.headlineMedium)
            Text(
                "$subtitle · ${plan.dayKcal(day).grouped()} kcal · ${plan.dayProtein(day)} g protein",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        when {
            day.veg -> Pill("Veg day", PaceColors.Ahead)
            day.kind.startsWith("Rest") -> Pill("Rest", PaceColors.OnPace)
            else -> Pill("Training", PaceColors.Secondary)
        }
    }
}

@Composable
private fun MealCard(
    plan: MealPlan,
    meal: PlanMeal,
    done: Boolean?,
    onLog: (() -> Unit)?,
    onOpenRecipe: (String) -> Unit,
    onSwap: (() -> Unit)? = null,
) {
    SectionCard(
        title = "${meal.time} · ${meal.label}" + if (meal.swappedFrom != null) " · swapped" else "",
        trailing = { Text("${plan.kcal(meal).grouped()} kcal · ${plan.protein(meal)} g P", fontWeight = FontWeight.SemiBold) },
    ) {
        plan.recipesFor(meal).forEach { r ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onOpenRecipe(r.id) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "${r.kcal.grouped()} kcal · ${r.protein} g protein" + if (r.reels.isNotEmpty()) " · from your reel" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (onSwap != null) {
            TextButton(onClick = onSwap) { Text(if (meal.swappedFrom != null) "Swap again" else "Swap dish") }
        }
        if (onLog != null) {
            if (done == true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = PaceColors.Ahead, modifier = Modifier.size(18.dp))
                    Text("  Logged today", color = PaceColors.Ahead)
                }
            } else {
                FilledTonalButton(onClick = onLog, modifier = Modifier.height(48.dp)) { Text("Log this meal") }
            }
        }
    }
}

@Composable
private fun WeekTab(
    plan: MealPlan,
    weekId: String,
    current: String,
    vm: MealPlanViewModel,
    onOpenRecipe: (String) -> Unit,
    onSwap: (SwapTarget) -> Unit,
) {
    val week = plan.week(weekId)
    val day = week.days.getOrNull(vm.selectedDay) ?: week.days.first()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { WeekPicker(plan, weekId, current) { vm.browsedWeekId = it } }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.days.forEachIndexed { i, d ->
                    FilterChip(
                        selected = vm.selectedDay == i,
                        onClick = { vm.selectedDay = i },
                        label = { Text(d.day.take(3)) },
                    )
                }
            }
        }
        item { DayHeader(plan, day, "${week.title} · ${week.subtitle}") }
        itemsIndexed(day.meals) { mi, meal ->
            MealCard(
                plan, meal, done = null, onLog = null, onOpenRecipe = onOpenRecipe,
                onSwap = if (MealPlanSchedule.kindOf(meal.label) != null) ({ onSwap(SwapTarget(week.id, vm.selectedDay, mi)) }) else null,
            )
        }
        if (plan.adjustments.isNotEmpty()) {
            item {
                SectionCard("When your target changes") {
                    plan.adjustments.forEach { LabeledValue(it.change, it.how) }
                }
            }
        }
    }
}

@Composable
private fun RecipesTab(plan: MealPlan, onOpenRecipe: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var vegOnly by rememberSaveable { mutableStateOf(false) }
    var reelsOnly by rememberSaveable { mutableStateOf(false) }
    val list = plan.recipes.values
        .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) || it.ingredients.any { i -> i.contains(query, true) } }
        .filter { !vegOnly || it.veg }
        .filter { !reelsOnly || it.reels.isNotEmpty() }
        .sortedWith(compareBy<PlanRecipe> { it.category }.thenBy { it.name })
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text("Search recipes or ingredients") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = vegOnly, onClick = { vegOnly = !vegOnly }, label = { Text("Veg") })
                FilterChip(selected = reelsOnly, onClick = { reelsOnly = !reelsOnly }, label = { Text("From reels") })
            }
        }
        if (list.isEmpty()) item { EmptyState("No recipes match", "Try a different word, or clear the filters.") }
        var lastCategory: String? = null
        list.forEach { r ->
            if (r.category != lastCategory) {
                lastCategory = r.category
                item(key = "cat-${r.category}") {
                    Text(r.category, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                }
            }
            item(key = r.id) {
                SectionCard(modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { onOpenRecipe(r.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${r.kcal.grouped()} kcal · ${r.protein} g protein · ${r.source}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (r.veg) Pill("Veg", PaceColors.Ahead)
                    }
                }
            }
        }
    }
}

@Composable
private fun GroceriesTab(plan: MealPlan, weekId: String, current: String, vm: MealPlanViewModel, ticked: Set<String>) {
    val sections = plan.groceryList(weekId)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { WeekPicker(plan, weekId, current) { vm.browsedWeekId = it } }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "For one person for this week, calculated from the planned meals including your swaps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { vm.clearGroceries(weekId) }) { Text("Clear ticks") }
            }
        }
        sections.forEachIndexed { si, section ->
            item(key = "$weekId-$si") {
                SectionCard(section.title) {
                    section.items.forEach { g ->
                        val key = g.name
                        val checked = key in ticked
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.toggleGrocery(weekId, key, !checked) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { vm.toggleGrocery(weekId, key, it) })
                            Column(Modifier.weight(1f)) {
                                Text(
                                    g.name,
                                    color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                )
                                if (g.note.isNotBlank()) {
                                    Text(g.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(g.qty, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        if (plan.prep.isNotEmpty()) {
            item {
                SectionCard("Sunday prep") {
                    plan.prep.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}
