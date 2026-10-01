package com.pace.tracker.ui.plan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pace.tracker.AppContainer
import com.pace.tracker.data.db.MealEntity
import com.pace.tracker.data.toEngine
import com.pace.tracker.data.today
import com.pace.tracker.domain.MealPlan
import com.pace.tracker.domain.MealPlanSchedule
import com.pace.tracker.domain.MealType
import com.pace.tracker.domain.PlanMeal
import com.pace.tracker.domain.PlanRecipe
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class MealPlanViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.repository
    val plan: MealPlan = container.mealPlan

    /** Week scheduled for today: A in odd programme weeks, B in even ones. */
    val currentWeekId: StateFlow<String> = repository.profile.map { p ->
        val week = p?.toEngine()?.weekOf(today())?.coerceAtLeast(1) ?: 1
        MealPlanSchedule.weekIdFor(week)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "A")

    /** Week being browsed; null means "follow the schedule". */
    var browsedWeekId by mutableStateOf<String?>(null)
    var selectedDay by mutableStateOf(MealPlanSchedule.dayIndex(LocalDate.now()))

    /** Descriptions of meals already logged today, to mark planned meals as done. */
    val loggedToday: StateFlow<Set<String>> = repository.observeMeals(today())
        .map { list -> list.map { it.description }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val groceryTicks: StateFlow<Map<String, Set<String>>> = repository.settings.map { s ->
        plan.groceries.keys.associateWith { id ->
            s[groceryKey(id)]?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun logMeal(meal: PlanMeal) = viewModelScope.launch {
        repository.saveMeal(
            MealEntity(
                epochDay = today(),
                type = meal.mealType,
                description = plan.title(meal),
                calories = plan.kcal(meal),
                protein = plan.protein(meal).toDouble(),
            ),
        )
    }

    fun logRecipe(recipe: PlanRecipe, type: MealType) = viewModelScope.launch {
        repository.saveMeal(MealEntity(epochDay = today(), type = type, description = recipe.name, calories = recipe.kcal, protein = recipe.protein.toDouble()))
    }

    fun toggleGrocery(weekId: String, itemKey: String, checked: Boolean) = viewModelScope.launch {
        val current = groceryTicks.value[weekId] ?: emptySet()
        val next = if (checked) current + itemKey else current - itemKey
        repository.setSetting(groceryKey(weekId), next.joinToString(","))
    }

    fun clearGroceries(weekId: String) = viewModelScope.launch {
        repository.setSetting(groceryKey(weekId), "")
    }

    private fun groceryKey(weekId: String) = "grocery_$weekId"
}
