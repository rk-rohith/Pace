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
    /** Shopping quantities per serving, keyed by [PlanIngredient.key]. Derived from [parts] when there are any. */
    val items: Map<String, Double> = emptyMap(),
    /** Measured ingredients; the app calculates macros and groceries from these. */
    val parts: List<RecipePart> = emptyList(),
    /** Seasoning and serving notes that are not measured. */
    val extras: List<String> = emptyList(),
    /** Made by the user in the app. */
    val custom: Boolean = false,
    /** A bundled recipe the user changed. */
    val edited: Boolean = false,
) {
    val kind: String? get() = MealPlanSchedule.categoryKind(category)
}

data class PlanIngredient(val key: String, val name: String, val section: String, val unit: String)

/** One measured ingredient in a recipe: [qty] in the food's unit. */
data class RecipePart(val food: String, val qty: Double, val note: String = "")

/**
 * A food with nutrition per unit (per g, ml, piece or scoop). [shop] is its grocery-list name;
 * null keeps it off the list (spices).
 */
data class PlanFood(
    val key: String,
    val name: String,
    val unit: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val shop: String? = name,
    val section: String = "Pantry",
    val custom: Boolean = false,
)

data class Macros(val kcal: Double = 0.0, val protein: Double = 0.0, val carbs: Double = 0.0, val fat: Double = 0.0) {
    operator fun plus(o: Macros) = Macros(kcal + o.kcal, protein + o.protein, carbs + o.carbs, fat + o.fat)

    companion object {
        fun of(food: PlanFood, qty: Double) = Macros(food.kcal * qty, food.protein * qty, food.carbs * qty, food.fat * qty)
    }
}

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
    val foods: Map<String, PlanFood> = emptyMap(),
) {
    fun week(id: String): PlanWeek = weeks.firstOrNull { it.id == id } ?: weeks.first()

    fun recipesFor(meal: PlanMeal): List<PlanRecipe> = meal.recipeIds.mapNotNull { recipes[it] }

    fun kcal(meal: PlanMeal): Int = recipesFor(meal).sumOf { it.kcal }
    fun protein(meal: PlanMeal): Int = recipesFor(meal).sumOf { it.protein }
    fun title(meal: PlanMeal): String = recipesFor(meal).joinToString(" + ") { it.name }

    fun dayKcal(day: PlanDay): Int = day.meals.sumOf { kcal(it) }
    fun dayProtein(day: PlanDay): Int = day.meals.sumOf { protein(it) }

    // ---------------------------------------------------------------- macros & user recipes

    fun macros(part: RecipePart): Macros = foods[part.food]?.let { Macros.of(it, part.qty) } ?: Macros()
    fun macros(parts: List<RecipePart>): Macros = parts.fold(Macros()) { acc, p -> acc + macros(p) }

    /** Recipe with macros, shopping items and ingredient lines calculated from its parts. */
    fun calculated(r: PlanRecipe): PlanRecipe {
        if (r.parts.isEmpty() || foods.isEmpty()) return r
        val m = macros(r.parts)
        val items = linkedMapOf<String, Double>()
        r.parts.forEach { p -> if (foods[p.food]?.shop != null) items[p.food] = (items[p.food] ?: 0.0) + p.qty }
        return r.copy(
            kcal = kotlin.math.round(m.kcal).toInt(),
            protein = kotlin.math.round(m.protein).toInt(),
            carbs = kotlin.math.round(m.carbs).toInt(),
            fat = kotlin.math.round(m.fat).toInt(),
            items = items,
            ingredients = r.parts.map { p -> foods[p.food]?.let { RecipeText.line(p, it) } ?: p.note } + r.extras,
        )
    }

    /**
     * Adds the user's own foods and recipes. A recipe whose id matches a bundled one replaces it (an edit);
     * every recipe with parts gets its macros recalculated.
     */
    fun withUserContent(userFoods: Map<String, PlanFood>, userRecipes: Map<String, PlanRecipe>): MealPlan {
        val allFoods = if (userFoods.isEmpty()) foods else foods + userFoods
        val base = copy(foods = allFoods)
        val merged = LinkedHashMap(recipes)
        userRecipes.forEach { (id, r) ->
            val bundled = recipes[id]
            merged[id] = if (bundled != null) r.copy(source = bundled.source, reels = bundled.reels, custom = false, edited = true) else r.copy(custom = true)
        }
        return base.copy(recipes = merged.mapValues { base.calculated(it.value) })
    }

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
    fun swapCandidates(day: PlanDay, meal: PlanMeal, anyCalories: Boolean = false): List<PlanRecipe> {
        val original = recipes[meal.swappedFrom ?: meal.recipeIds.firstOrNull() ?: return emptyList()] ?: return emptyList()
        val kind = MealPlanSchedule.kindOf(meal.label) ?: return emptyList()
        val pool = recipes.values.filter { r ->
            r.kind == kind && r.id != meal.recipeIds.first() && (!day.veg || r.veg)
        }
        if (anyCalories) return pool.sortedBy { kotlin.math.abs(it.kcal - original.kcal) }
        fun within(limit: Int) = pool.filter { kotlin.math.abs(it.kcal - original.kcal) <= limit }
        val close = within(MealPlanSchedule.SWAP_KCAL).let { if (it.size >= 3) it else within(MealPlanSchedule.SWAP_KCAL * 2) }
        return close.sortedBy { kotlin.math.abs(it.kcal - original.kcal) }
    }

    // ---------------------------------------------------------------- groceries

    /** Shopping list for one week, calculated from the meals actually planned (after swaps). */
    fun groceryList(weekId: String): List<GrocerySection> {
        if (ingredients.isEmpty() && foods.isEmpty()) return groceries[weekId].orEmpty()
        val totals = linkedMapOf<String, Double>() // ingredient name -> amount
        val units = mutableMapOf<String, PlanIngredient>()
        week(weekId).days.forEach { d ->
            d.meals.forEach { m ->
                recipesFor(m).forEach { r ->
                    r.items.forEach { (key, qty) ->
                        val ing = foods[key]?.let { f -> f.shop?.let { PlanIngredient(key, it, f.section, f.unit) } }
                            ?: ingredients[key] ?: return@forEach
                        totals[ing.name] = (totals[ing.name] ?: 0.0) + qty
                        units[ing.name] = ing
                    }
                }
            }
        }
        val bySection = totals.entries.groupBy { units.getValue(it.key).section }
        val sections = (MealPlanSchedule.SECTION_ORDER + (bySection.keys - MealPlanSchedule.SECTION_ORDER.toSet())).mapNotNull { sec ->
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

/** Human-readable ingredient lines; matches tools/mealplan/export_json.py. */
object RecipeText {
    fun num(q: Double): String =
        if (q % 1.0 == 0.0) q.toLong().toString() else String.format(java.util.Locale.US, "%.1f", q).trimEnd('0').trimEnd('.')

    fun amount(qty: Double, unit: String, name: String): String = when (unit) {
        "pc" -> "${num(qty)} × $name"
        "scoop" -> "${num(qty)} scoop" + (if (qty == 1.0) "" else "s") + " $name"
        else -> "${num(qty)} $unit $name"
    }

    fun line(part: RecipePart, food: PlanFood): String =
        amount(part.qty, food.unit, food.name) + if (part.note.isNotBlank()) " — ${part.note}" else ""
}

object MealPlanSchedule {
    /** Weeks alternate A, B, A, B… from the programme start. */
    fun weekIdFor(programWeek: Int): String = if (programWeek % 2 == 1) "A" else "B"

    /** Index into a week's days (Monday = 0 … Sunday = 6). */
    fun dayIndex(date: LocalDate): Int = date.dayOfWeek.value - 1

    const val SWAP_KCAL = 120

    val SECTION_ORDER = listOf("Meat, fish & eggs", "Dairy", "Vegetables", "Fruit", "Grains, dals & snacks", "Pantry")

    /** Categories offered when the user writes a recipe. */
    val USER_CATEGORIES = listOf("Breakfast", "Lunch & dinner", "Snack")

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
