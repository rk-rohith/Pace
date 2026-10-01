package com.pace.tracker

import com.pace.tracker.data.MealPlanLoader
import com.pace.tracker.data.UserRecipes
import com.pace.tracker.domain.MealPlanSchedule
import com.pace.tracker.domain.PlanFood
import com.pace.tracker.domain.PlanRecipe
import com.pace.tracker.domain.RecipePart
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class MealPlanTest {

    private val plan by lazy {
        MealPlanLoader.parse(JSONObject(File("src/main/assets/meal_plan.json").readText()))
    }

    @Test
    fun bundledPlanIsComplete() {
        assertEquals(listOf("A", "B"), plan.weeks.map { it.id })
        plan.weeks.forEach { w ->
            assertEquals(7, w.days.size)
            w.days.forEach { d ->
                d.meals.forEach { m -> m.recipeIds.forEach { id -> assertTrue("missing $id", id in plan.recipes) } }
                val kcal = plan.dayKcal(d)
                assertTrue("${w.id} ${d.day} kcal $kcal", kcal in 1600..1950)
                assertTrue("${w.id} ${d.day} protein", plan.dayProtein(d) >= 135)
            }
        }
        assertTrue(plan.groceries.keys.containsAll(listOf("A", "B")))
    }

    @Test
    fun vegDaysHaveNoMeatOrEggs() {
        plan.weeks.flatMap { it.days }.filter { it.veg }.forEach { d ->
            d.meals.flatMap { plan.recipesFor(it) }.forEach { r -> assertTrue("${d.day}: ${r.name}", r.veg) }
        }
    }

    @Test
    fun weeksAlternate() {
        assertEquals("A", MealPlanSchedule.weekIdFor(1))
        assertEquals("B", MealPlanSchedule.weekIdFor(2))
        assertEquals("A", MealPlanSchedule.weekIdFor(3))
        assertEquals(0, MealPlanSchedule.dayIndex(LocalDate.of(2026, 9, 28))) // Monday
        assertEquals(6, MealPlanSchedule.dayIndex(LocalDate.of(2026, 10, 4))) // Sunday
    }

    @Test
    fun groceryListIsCalculatedFromMeals() {
        val list = plan.groceryList("A")
        val items = list.flatMap { it.items }.associateBy { it.name }
        assertTrue("chicken", items.containsKey("Chicken breast, boneless"))
        assertTrue("eggs", items.containsKey("Eggs"))
        assertTrue("staples last", list.last().title.startsWith("Staples"))
    }

    @Test
    fun swappingAMealUpdatesPlanAndGroceries() {
        val monday = plan.week("A").days[0]
        val lunchIndex = monday.meals.indexOfFirst { it.label == "Lunch" }
        val lunch = monday.meals[lunchIndex]
        val options = plan.swapCandidates(monday, lunch)
        assertTrue("has options", options.isNotEmpty())
        val pick = options.first()
        val swapped = plan.withSwaps(mapOf(MealPlanSchedule.swapKey("A", 0, lunchIndex) to pick.id))
        val newLunch = swapped.week("A").days[0].meals[lunchIndex]
        assertEquals(pick.id, newLunch.recipeIds.first())
        assertEquals(lunch.recipeIds.first(), newLunch.swappedFrom)
        assertTrue(kotlin.math.abs(swapped.dayKcal(swapped.week("A").days[0]) - plan.dayKcal(monday)) <= 2 * MealPlanSchedule.SWAP_KCAL)
        assertTrue("groceries change", swapped.groceryList("A") != plan.groceryList("A"))
    }

    @Test
    fun vegDaySwapsStayVeg() {
        val tuesday = plan.week("A").days[1]
        tuesday.meals.forEach { m -> plan.swapCandidates(tuesday, m).forEach { assertTrue(it.name, it.veg) } }
    }

    @Test
    fun quantitiesAreShoppingFriendly() {
        assertEquals("1 kg", MealPlanSchedule.formatQty(980.0, "g"))
        assertEquals("1.25 kg", MealPlanSchedule.formatQty(1210.0, "g"))
        assertEquals("150 g", MealPlanSchedule.formatQty(130.0, "g"))
        assertEquals("40 g", MealPlanSchedule.formatQty(32.0, "g"))
        assertEquals("20", MealPlanSchedule.formatQty(19.3, "pc"))
        assertEquals("7 scoops", MealPlanSchedule.formatQty(7.0, "scoop"))
    }

    @Test
    fun appCalculatesTheSameMacrosAndLinesAsTheGenerator() {
        val raw = JSONObject(File("src/main/assets/meal_plan.json").readText()).getJSONArray("recipes")
        for (i in 0 until raw.length()) {
            val o = raw.getJSONObject(i)
            val r = plan.recipes.getValue(o.getString("id"))
            assertTrue("${r.id} has parts", r.parts.isNotEmpty())
            for (p in r.parts) assertTrue("${r.id}: ${p.food}", p.food in plan.foods)
            assertEquals(r.id, o.getInt("kcal"), r.kcal)
            assertEquals(r.id, o.getInt("protein"), r.protein)
            val lines = o.getJSONArray("ingredients").let { a -> (0 until a.length()).map { a.getString(it) } }
            assertEquals(r.id, lines, r.ingredients)
        }
    }

    @Test
    fun grilledChickenBowlMatchesItsStatedMacros() {
        val r = plan.recipes.getValue("grillbowl")
        assertTrue("kcal ${r.kcal}", r.kcal in 590..630)
        assertTrue("protein ${r.protein}", r.protein in 66..70)
        assertEquals("200 g Chicken breast, skinless (raw) — cut or lightly scored", r.ingredients.first())
    }

    private fun userRecipe(id: String, parts: List<RecipePart>, veg: Boolean = false, category: String = "Lunch & dinner") = PlanRecipe(
        id = id, name = "Test $id", category = category, source = "Your recipe", veg = veg,
        kcal = 0, protein = 0, carbs = 0, fat = 0, ingredients = emptyList(), steps = listOf("Cook."), tip = "",
        reels = emptyList(), parts = parts, extras = listOf("Salt"),
    )

    @Test
    fun userRecipesAndFoodsGetCalculatedMacros() {
        val tofu = PlanFood("my_tofu", "Tofu", "g", 1.44, .17, .03, .09, shop = "Tofu", section = "Dairy", custom = true)
        val mine = userRecipe("my_1", listOf(RecipePart("my_tofu", 200.0, "cubed"), RecipePart("oil", 5.0)), veg = true)
        val p = plan.withUserContent(mapOf(tofu.key to tofu), mapOf(mine.id to mine))
        val r = p.recipes.getValue("my_1")
        assertTrue(r.custom)
        assertEquals(288 + 45, r.kcal)
        assertEquals(34, r.protein)
        assertEquals(listOf("200 g Tofu — cubed", "5 ml Cooking oil", "Salt"), r.ingredients)

        // Swap it in on a veg day: the grocery list picks up the new ingredient.
        val tuesday = p.week("A").days[1]
        val lunchIndex = tuesday.meals.indexOfFirst { it.label == "Lunch" }
        assertTrue(p.swapCandidates(tuesday, tuesday.meals[lunchIndex], anyCalories = true).any { it.id == "my_1" })
        val swapped = p.withSwaps(mapOf(MealPlanSchedule.swapKey("A", 1, lunchIndex) to "my_1"))
        val items = swapped.groceryList("A").flatMap { it.items }.associateBy { it.name }
        assertEquals("200 g", items.getValue("Tofu").qty)
    }

    @Test
    fun editingABundledRecipeReplacesItAndKeepsItsSource() {
        val original = plan.recipes.getValue("chx_rice")
        val edit = userRecipe("chx_rice", original.parts.map { if (it.food == "chicken") it.copy(qty = 200.0) else it })
        val r = plan.withUserContent(emptyMap(), mapOf("chx_rice" to edit)).recipes.getValue("chx_rice")
        assertTrue(r.edited)
        assertEquals(original.source, r.source)
        assertEquals(original.kcal + 60, r.kcal) // 50 g more chicken at 1.2 kcal/g
    }

    @Test
    fun userRecipesSurviveTheSettingsRoundTrip() {
        val mine = userRecipe("my_2", listOf(RecipePart("egg_pc", 2.0, "boiled")), category = "Snack")
        val food = PlanFood("my_x", "Peanut butter", "g", 5.88, .25, .2, .5, shop = null)
        val settings = mapOf(
            UserRecipes.RECIPE_PREFIX + "my_2" to UserRecipes.encodeRecipe(mine),
            UserRecipes.RECIPE_PREFIX + "gone" to "",
            UserRecipes.FOOD_PREFIX + "my_x" to UserRecipes.encodeFood(food),
        )
        val recipes = UserRecipes.recipes(settings)
        assertEquals(setOf("my_2"), recipes.keys)
        val back = recipes.getValue("my_2")
        assertEquals(mine.parts, back.parts)
        assertEquals(mine.steps, back.steps)
        assertEquals("Snack", back.category)
        assertEquals(food.copy(custom = true), UserRecipes.foods(settings).getValue("my_x"))
    }
}
