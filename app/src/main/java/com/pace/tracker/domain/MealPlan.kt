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
    /** Shopping quantities per serving, keyed by [PlanIngredient.key]. */
    val items: Map<String, Double> = emptyMap(),
)

data class PlanIngredient(val key: String, val name: String, val section: String, val unit: String)

data class PlanMeal(
    val time: String,
    val label: String,
    val mealType: MealType,
    val recipeIds: List<String>,
    /** Original main dish when the user swapped it. */
    val swappedFrom: String? = null,
)

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
    val ingredients: Map<String, PlanIngredient> = emptyMap(),
    val staples: List<GroceryItem> = emptyList(),
) {
    fun week(id: String): PlanWeek = weeks.firstOrNull { it.id == id } ?: weeks.first()

    fun recipesFor(meal: PlanMeal): List<PlanRecipe> = meal.recipeIds.mapNotNull { recipes[it] }

    fun kcal(meal: PlanMeal): Int = recipesFor(meal).sumOf { it.kcal }
    fun protein(meal: PlanMeal): Int = recipesFor(meal).sumOf { it.protein }
    fun title(meal: PlanMeal): String = recipesFor(meal).joinToString(" + ") { it.name }

    fun dayKcal(day: PlanDay): Int = day.meals.sumOf { kcal(it) }
    fun dayProtein(day: PlanDay): Int = day.meals.sumOf { protein(it) }

    // ---------------------------------------------------------------- swaps

    /** Returns the plan with swapped main dishes. Keys come from [MealPlanSchedule.swapKey]. */
    fun withSwaps(swaps: Map<String, String>): MealPlan {
        if (swaps.isEmpty()) return this
        val newWeeks = weeks.map { w ->
            w.copy(days = w.days.mapIndexed { di, d ->
                d.copy(meals = d.meals.mapIndexed { mi, m ->
                    val swapId = swaps[MealPlanSchedule.swapKey(w.id, di, mi)]
                    if (swapId != null && swapId in recipes && m.recipeIds.isNotEmpty()) {
                        m.copy(recipeIds = listOf(swapId) + m.recipeIds.drop(1), swappedFrom = m.recipeIds.first())
                    } else m
                })
            })
        }
        return copy(weeks = newWeeks)
    }

    /**
     * Dishes that can replace a meal's main dish: same kind of meal, within ±[MealPlanSchedule.SWAP_KCAL]
     * kcal of it (widening once if that finds too few), veg-only on veg days. Closest calories first.
     */
    fun swapCandidates(day: PlanDay, meal: PlanMeal): List<PlanRecipe> {
        val original = recipes[meal.swappedFrom ?: meal.recipeIds.firstOrNull() ?: return emptyList()] ?: return emptyList()
        val kind = MealPlanSchedule.kindOf(meal.label) ?: return emptyList()
        val pool = recipes.values.filter { r ->
            MealPlanSchedule.categoryKind(r.category) == kind && r.id != meal.recipeIds.first() && (!day.veg || r.veg)
        }
        fun within(limit: Int) = pool.filter { kotlin.math.abs(it.kcal - original.kcal) <= limit }
        val close = within(MealPlanSchedule.SWAP_KCAL).let { if (it.size >= 3) it else within(MealPlanSchedule.SWAP_KCAL * 2) }
        return close.sortedBy { kotlin.math.abs(it.kcal - original.kcal) }
    }

    // ---------------------------------------------------------------- groceries

    /** Shopping list for one week, calculated from the meals actually planned (after swaps). */
    fun groceryList(weekId: String): List<GrocerySection> {
        if (ingredients.isEmpty()) return groceries[weekId].orEmpty()
        val totals = linkedMapOf<String, Double>() // ingredient name -> amount
        val units = mutableMapOf<String, PlanIngredient>()
        week(weekId).days.forEach { d ->
            d.meals.forEach { m ->
                recipesFor(m).forEach { r ->
                    r.items.forEach { (key, qty) ->
                        val ing = ingredients[key] ?: return@forEach
                        totals[ing.name] = (totals[ing.name] ?: 0.0) + qty
                        units[ing.name] = ing
                    }
                }
            }
        }
        val bySection = totals.entries.groupBy { units.getValue(it.key).section }
        val sections = MealPlanSchedule.SECTION_ORDER.mapNotNull { sec ->
            bySection[sec]?.let { entries ->
                GrocerySection(sec, entries.sortedBy { it.key }.map { (name, amount) ->
                    GroceryItem(name, MealPlanSchedule.formatQty(amount, units.getValue(name).unit), "")
                })
            }
        }
        return if (staples.isEmpty()) sections else sections + GrocerySection("Staples (check stock)", staples)
    }

    companion object {
        val EMPTY = MealPlan(emptyMap(), emptyList(), emptyMap(), emptyList(), emptyList())
    }
}

object MealPlanSchedule {
    /** Weeks alternate A, B, A, B… from the programme start. */
    fun weekIdFor(programWeek: Int): String = if (programWeek % 2 == 1) "A" else "B"

    /** Index into a week's days (Monday = 0 … Sunday = 6). */
    fun dayIndex(date: LocalDate): Int = date.dayOfWeek.value - 1

    const val SWAP_KCAL = 120

    val SECTION_ORDER = listOf("Meat, fish & eggs", "Dairy", "Vegetables", "Fruit", "Grains, dals & snacks", "Pantry")

    fun swapKey(weekId: String, dayIndex: Int, mealIndex: Int) = "$weekId|$dayIndex|$mealIndex"

    /** Which kind of dish fits a meal slot; null = not swappable (pre/post-workout basics). */
    fun kindOf(label: String): String? = when (label) {
        "Breakfast" -> "breakfast"
        "Lunch", "Dinner" -> "main"
        "Snack" -> "snack"
        else -> null
    }

    fun categoryKind(category: String): String? = when {
        category == "Breakfast" -> "breakfast"
        category.startsWith("Lunch & dinner") -> "main"
        category.startsWith("Snack") -> "snack"
        else -> null
    }

    /** Shopping-friendly amounts: grams/ml rounded up, kg/L above 1000, pieces rounded up. */
    fun formatQty(amount: Double, unit: String): String = when (unit) {
        "g", "ml" -> {
            val step = if (amount < 100) 10.0 else 50.0
            val rounded = kotlin.math.ceil(amount / step) * step
            val big = if (unit == "g") "kg" else "L"
            if (rounded >= 1000) {
                val v = kotlin.math.ceil(rounded / 50.0) * 50.0 / 1000.0
                (if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')) + " $big"
            } else "${rounded.toInt()} $unit"
        }
        "scoop" -> kotlin.math.ceil(amount).toInt().let { "$it scoop" + if (it == 1) "" else "s" }
        else -> kotlin.math.ceil(amount - 1e-9).toInt().toString()
    }
}
