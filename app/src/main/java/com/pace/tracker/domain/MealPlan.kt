package com.pace.tracker.domain

import java.time.LocalDate

/** The bundled two-week meal plan (assets/meal_plan.json). Pure data, no Android types. */
data class PlanRecipe(
    val id: String,
    val name: String,
    val category: String,
    val source: String,
    val veg: Boolean,
    val kcal: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val ingredients: List<String>,
    val steps: List<String>,
    val tip: String,
    val reels: List<String>,
)

data class PlanMeal(val time: String, val label: String, val mealType: MealType, val recipeIds: List<String>)

data class PlanDay(val day: String, val kind: String, val veg: Boolean, val meals: List<PlanMeal>)

data class PlanWeek(val id: String, val title: String, val subtitle: String, val days: List<PlanDay>)

data class GroceryItem(val name: String, val qty: String, val note: String)

data class GrocerySection(val title: String, val items: List<GroceryItem>)

data class CalorieAdjustment(val change: String, val how: String)

data class MealPlan(
    val recipes: Map<String, PlanRecipe>,
    val weeks: List<PlanWeek>,
    val groceries: Map<String, List<GrocerySection>>,
    val prep: List<String>,
    val adjustments: List<CalorieAdjustment>,
) {
    fun week(id: String): PlanWeek = weeks.firstOrNull { it.id == id } ?: weeks.first()

    fun recipesFor(meal: PlanMeal): List<PlanRecipe> = meal.recipeIds.mapNotNull { recipes[it] }

    fun kcal(meal: PlanMeal): Int = recipesFor(meal).sumOf { it.kcal }
    fun protein(meal: PlanMeal): Int = recipesFor(meal).sumOf { it.protein }
    fun title(meal: PlanMeal): String = recipesFor(meal).joinToString(" + ") { it.name }

    fun dayKcal(day: PlanDay): Int = day.meals.sumOf { kcal(it) }
    fun dayProtein(day: PlanDay): Int = day.meals.sumOf { protein(it) }

    companion object {
        val EMPTY = MealPlan(emptyMap(), emptyList(), emptyMap(), emptyList(), emptyList())
    }
}

object MealPlanSchedule {
    /** Weeks alternate A, B, A, B… from the programme start. */
    fun weekIdFor(programWeek: Int): String = if (programWeek % 2 == 1) "A" else "B"

    /** Index into a week's days (Monday = 0 … Sunday = 6). */
    fun dayIndex(date: LocalDate): Int = date.dayOfWeek.value - 1
}
