package com.pace.tracker

import com.pace.tracker.data.MealPlanLoader
import com.pace.tracker.domain.MealPlanSchedule
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
}
