# Meal plan generator

Source data for the bundled two-week meal plan (`app/src/main/assets/meal_plan.json`).

| File | Contents |
|---|---|
| `plan.py` | Ingredient nutrition table, recipe macros, Week A plan |
| `reels.py` | Recipes adapted from Instagram reels, Week B plan |
| `recipes_text.py`, `reels_text.py` | Ingredients, method and notes shown in the app |
| `grocery.py` | Weekly grocery lists |
| `export_json.py` | Builds `meal_plan.json` |

To add a recipe: add its ingredients to `reels.py` (macros are calculated from the table in
`plan.py`), its text to `reels_text.py`, place it in a week, then run:

```bash
python3 tools/mealplan/export_json.py
./gradlew testDebugUnitTest   # MealPlanTest checks every day's calories, protein and veg days
```
