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
}
