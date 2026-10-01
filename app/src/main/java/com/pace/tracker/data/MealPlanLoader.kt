package com.pace.tracker.data

import android.content.Context
import com.pace.tracker.domain.CalorieAdjustment
import com.pace.tracker.domain.GroceryItem
import com.pace.tracker.domain.GrocerySection
import com.pace.tracker.domain.MealPlan
import com.pace.tracker.domain.MealType
import com.pace.tracker.domain.PlanDay
import com.pace.tracker.domain.PlanFood
import com.pace.tracker.domain.PlanIngredient
import com.pace.tracker.domain.PlanMeal
import com.pace.tracker.domain.PlanRecipe
import com.pace.tracker.domain.PlanWeek
import com.pace.tracker.domain.RecipePart
import org.json.JSONArray
import org.json.JSONObject

/** Reads the bundled meal plan from assets/meal_plan.json. */
object MealPlanLoader {
    const val ASSET = "meal_plan.json"

    fun load(context: Context): MealPlan = try {
        val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        parse(JSONObject(text))
    } catch (e: Exception) {
        MealPlan.EMPTY
    }

    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    fun parse(root: JSONObject): MealPlan {
        val recipes = root.getJSONArray("recipes").objects().map { r ->
            PlanRecipe(
                id = r.getString("id"),
                name = r.getString("name"),
                category = r.getString("category"),
                source = r.getString("source"),
                veg = r.getBoolean("veg"),
                kcal = r.getInt("kcal"),
                protein = r.getInt("protein"),
                carbs = r.getInt("carbs"),
                fat = r.getInt("fat"),
                ingredients = r.optJSONArray("ingredients")?.strings() ?: emptyList(),
                steps = r.getJSONArray("steps").strings(),
                tip = r.optString("tip", ""),
                reels = r.optJSONArray("reels")?.strings() ?: emptyList(),
                items = r.optJSONObject("items")?.let { o -> o.keys().asSequence().associateWith { o.getDouble(it) } } ?: emptyMap(),
                parts = r.optJSONArray("parts")?.let(::parts) ?: emptyList(),
                extras = r.optJSONArray("extras")?.strings() ?: emptyList(),
            )
        }.associateBy { it.id }
        val weeks = root.getJSONArray("weeks").objects().map { w ->
            PlanWeek(
                id = w.getString("id"),
                title = w.getString("title"),
                subtitle = w.optString("subtitle", ""),
                days = w.getJSONArray("days").objects().map { d ->
                    PlanDay(
                        day = d.getString("day"),
                        kind = d.getString("kind"),
                        veg = d.getBoolean("veg"),
                        meals = d.getJSONArray("meals").objects().map { m ->
                            PlanMeal(
                                time = m.getString("time"),
                                label = m.getString("label"),
                                mealType = runCatching { MealType.valueOf(m.getString("mealType")) }.getOrDefault(MealType.SNACK),
                                recipeIds = m.getJSONArray("recipes").strings(),
                            )
                        },
                    )
                },
            )
        }
        val groceriesObj = root.getJSONObject("groceries")
        val groceries = groceriesObj.keys().asSequence().associateWith { key ->
            groceriesObj.getJSONArray(key).objects().map { s ->
                GrocerySection(
                    title = s.getString("section"),
                    items = s.getJSONArray("items").objects().map { GroceryItem(it.getString("name"), it.getString("qty"), it.optString("note", "")) },
                )
            }
        }
        val prep = root.optJSONArray("prep")?.strings() ?: emptyList()
        val adjustments = root.optJSONArray("adjustments")?.objects()?.map { CalorieAdjustment(it.getString("change"), it.getString("how")) } ?: emptyList()
        val ingredients = root.optJSONObject("ingredients")?.let { o ->
            o.keys().asSequence().associateWith { k ->
                val i = o.getJSONObject(k)
                PlanIngredient(k, i.getString("name"), i.getString("section"), i.getString("unit"))
            }
        } ?: emptyMap()
        val staples = root.optJSONArray("staples")?.objects()?.map { GroceryItem(it.getString("name"), it.getString("qty"), it.optString("note", "")) } ?: emptyList()
        val foods = root.optJSONObject("foods")?.let { o -> o.keys().asSequence().associateWith { k -> food(k, o.getJSONObject(k)) } } ?: emptyMap()
        val plan = MealPlan(recipes, weeks, groceries, prep, adjustments, ingredients, staples, foods)
        return plan.copy(recipes = recipes.mapValues { plan.calculated(it.value) })
    }

    fun parts(a: JSONArray): List<RecipePart> =
        a.objects().map { RecipePart(it.getString("food"), it.getDouble("qty"), it.optString("note", "")) }

    fun food(key: String, f: JSONObject, custom: Boolean = false) = PlanFood(
        key = key,
        name = f.getString("name"),
        unit = f.getString("unit"),
        kcal = f.getDouble("kcal"),
        protein = f.getDouble("protein"),
        carbs = f.getDouble("carbs"),
        fat = f.getDouble("fat"),
        shop = if (f.isNull("shop")) null else f.optString("shop", f.getString("name")),
        section = f.optString("section", "Pantry"),
        custom = custom,
    )
}
