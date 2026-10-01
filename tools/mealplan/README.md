# Meal plan generator

Source data for the bundled two-week meal plan (`app/src/main/assets/meal_plan.json`).

| File | Contents |
|---|---|
| `plan.py` | Ingredient nutrition table, recipe macros, Week A plan |
| `reels.py` | Recipes adapted from Instagram reels, Week B plan |
| `recipes_text.py`, `reels_text.py` | Method and notes shown in the app |
| `structured.py` | Every recipe's measured ingredients (food, amount, note) and unmeasured extras; food display names |
| `grocery.py` | Weekly grocery lists |
| `export_json.py` | Builds `meal_plan.json` |

Macros are calculated from `structured.py` and the nutrition table in `plan.py` (per g, ml or piece) — the
app repeats the same calculation, and `MealPlanTest` checks the two agree. To add a recipe: add its name to
`reels.py`, its measured ingredients to `structured.py`, its method to `reels_text.py`, place it in a week
if wanted, then run:

```bash
python3 tools/mealplan/export_json.py
./gradlew testDebugUnitTest   # MealPlanTest checks every day's calories, protein and veg days
```
