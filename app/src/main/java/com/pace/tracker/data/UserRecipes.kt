package com.pace.tracker.data

import com.pace.tracker.domain.PlanFood
import com.pace.tracker.domain.PlanRecipe
import org.json.JSONArray
import org.json.JSONObject

/**
 * The user's own recipes, edits of bundled recipes, and own ingredients. They live in the settings
 * table ("recipe_<id>" and "food_<key>", JSON values) so backups carry them with no extra work.
 * A blank value means deleted (or, for a bundled recipe, back to the original).
 */
object UserRecipes {
    const val RECIPE_PREFIX = "recipe_"
    const val FOOD_PREFIX = "food_"

    fun recipes(settings: Map<String, String>): Map<String, PlanRecipe> = settings
        .filterKeys { it.startsWith(RECIPE_PREFIX) }
        .filterValues { it.isNotBlank() }
        .mapNotNull { (k, v) -> runCatching { decodeRecipe(k.removePrefix(RECIPE_PREFIX), JSONObject(v)) }.getOrNull()?.let { k.removePrefix(RECIPE_PREFIX) to it } }
        .toMap()

    fun foods(settings: Map<String, String>): Map<String, PlanFood> = settings
        .filterKeys { it.startsWith(FOOD_PREFIX) }
        .filterValues { it.isNotBlank() }
        .mapNotNull { (k, v) ->
            val key = k.removePrefix(FOOD_PREFIX)
            runCatching { MealPlanLoader.food(key, JSONObject(v), custom = true) }.getOrNull()?.let { key to it }
        }
        .toMap()

    fun encodeRecipe(r: PlanRecipe): String = JSONObject()
        .put("name", r.name)
        .put("category", r.category)
        .put("veg", r.veg)
        .put("parts", JSONArray(r.parts.map { JSONObject().put("food", it.food).put("qty", it.qty).put("note", it.note) }))
        .put("extras", JSONArray(r.extras))
        .put("steps", JSONArray(r.steps))
        .put("tip", r.tip)
        .toString()

    fun decodeRecipe(id: String, o: JSONObject): PlanRecipe {
        fun strings(name: String) = o.optJSONArray(name)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        return PlanRecipe(
            id = id,
            name = o.getString("name"),
            category = o.optString("category", "Lunch & dinner"),
            source = "Your recipe",
            veg = o.optBoolean("veg", false),
            kcal = 0, protein = 0, carbs = 0, fat = 0,
            ingredients = emptyList(),
            steps = strings("steps"),
            tip = o.optString("tip", ""),
            reels = emptyList(),
            parts = o.optJSONArray("parts")?.let(MealPlanLoader::parts) ?: emptyList(),
            extras = strings("extras"),
        )
    }

    fun encodeFood(f: PlanFood): String = JSONObject()
        .put("name", f.name)
        .put("unit", f.unit)
        .put("kcal", f.kcal)
        .put("protein", f.protein)
        .put("carbs", f.carbs)
        .put("fat", f.fat)
        .put("shop", f.shop ?: JSONObject.NULL)
        .put("section", f.section)
        .toString()
}
