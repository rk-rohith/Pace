@file:OptIn(ExperimentalLayoutApi::class)

package com.pace.tracker.ui.plan

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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.pace.tracker.domain.MealType
import com.pace.tracker.ui.components.EmptyState
import com.pace.tracker.ui.components.Pill
import com.pace.tracker.ui.components.ScreenScaffold
import com.pace.tracker.ui.components.SectionCard
import com.pace.tracker.ui.components.StatTile
import com.pace.tracker.ui.components.grouped
import com.pace.tracker.ui.components.paceViewModel
import com.pace.tracker.ui.theme.PaceColors

@Composable
fun RecipeScreen(recipeId: String, onBack: () -> Unit) {
    val vm = paceViewModel { MealPlanViewModel(it) }
    val recipe = vm.plan.recipes[recipeId]
    val uriHandler = LocalUriHandler.current
    var logType by rememberSaveable { mutableStateOf(MealType.LUNCH) }
    var loggedAs by rememberSaveable { mutableStateOf<MealType?>(null) }

    ScreenScaffold(recipe?.name ?: "Recipe", onBack = onBack) { padding ->
        if (recipe == null) {
            EmptyState("Recipe not found", "It may have been removed from the plan.", Modifier.padding(padding))
            return@ScreenScaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(recipe.source, PaceColors.Secondary)
                if (recipe.veg) Pill("Veg", PaceColors.Ahead)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Calories", recipe.kcal.grouped(), Modifier.weight(1f), sub = "kcal per serving")
                StatTile("Protein", "${recipe.protein} g", Modifier.weight(1f), sub = "C ${recipe.carbs} g · F ${recipe.fat} g")
            }
            SectionCard("Ingredients") {
                recipe.ingredients.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
            }
            SectionCard("Method") {
                recipe.steps.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodyLarge) }
            }
            if (recipe.tip.isNotBlank()) {
                SectionCard("Note") { Text(recipe.tip, style = MaterialTheme.typography.bodyMedium) }
            }
            if (recipe.reels.isNotEmpty()) {
                SectionCard("Original reel") {
                    recipe.reels.forEachIndexed { i, url ->
                        OutlinedButton(onClick = { runCatching { uriHandler.openUri(url) } }, modifier = Modifier.height(48.dp)) {
                            Text(if (recipe.reels.size > 1) "Watch reel ${i + 1}" else "Watch on Instagram")
                        }
                    }
                }
            }
            SectionCard("Log to today") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MealType.entries.forEach { t ->
                        FilterChip(selected = logType == t, onClick = { logType = t }, label = { Text(t.label) })
                    }
                }
                FilledTonalButton(
                    onClick = { vm.logRecipe(recipe, logType); loggedAs = logType },
                    modifier = Modifier.height(48.dp),
                ) { Text("Log ${recipe.kcal.grouped()} kcal as ${logType.label.lowercase()}") }
                loggedAs?.let { Text("Added to today's ${it.label.lowercase()}.", color = PaceColors.Ahead) }
            }
        }
    }
}
