package com.pace.tracker.domain

/** One quick-add food. Values are per [serving]. */
data class LibraryFood(
    val name: String,
    val serving: String,
    val kcal: Int,
    val protein: Double,
    val category: String,
)

/**
 * Built-in offline list of common Indian foods with typical home-style values.
 * Restaurant portions are usually larger and oilier; log 1.5× when eating out.
 */
object FoodLibrary {
    private fun f(cat: String, name: String, serving: String, kcal: Int, protein: Double) = LibraryFood(name, serving, kcal, protein, cat)

    val foods: List<LibraryFood> = listOf(
        // South Indian
        f("South Indian", "Idli", "1 piece", 58, 2.0),
        f("South Indian", "Plain dosa", "1 medium", 133, 3.0),
        f("South Indian", "Masala dosa", "1 medium", 250, 5.0),
        f("South Indian", "Pesarattu", "1 medium", 150, 7.0),
        f("South Indian", "Rava upma", "1 cup (200 g)", 250, 6.0),
        f("South Indian", "Poha", "1 cup (200 g)", 250, 5.0),
        f("South Indian", "Uttapam", "1 medium", 200, 5.0),
        f("South Indian", "Ven pongal", "1 cup", 300, 8.0),
        f("South Indian", "Medu vada", "1 piece", 140, 4.0),
        f("South Indian", "Sambar", "1 bowl (200 ml)", 150, 7.0),
        f("South Indian", "Rasam", "1 bowl", 60, 2.0),
        f("South Indian", "Coconut chutney", "2 tbsp", 100, 1.0),
        f("South Indian", "Tomato chutney", "2 tbsp", 40, 1.0),
        f("South Indian", "Curd rice", "1 cup", 250, 6.0),
        f("South Indian", "Lemon rice", "1 cup", 280, 5.0),
        // Breads & grains
        f("Breads & grains", "Chapati", "1 medium", 120, 3.5),
        f("Breads & grains", "Phulka", "1 small", 80, 2.5),
        f("Breads & grains", "Plain paratha", "1", 260, 5.0),
        f("Breads & grains", "Aloo paratha", "1", 300, 6.0),
        f("Breads & grains", "Cooked rice", "100 g", 130, 2.7),
        f("Breads & grains", "Cooked rice", "1 cup (150 g)", 195, 4.0),
        f("Breads & grains", "Brown rice, cooked", "100 g", 112, 2.6),
        f("Breads & grains", "Rolled oats", "40 g dry", 152, 5.2),
        f("Breads & grains", "Brown bread", "1 slice", 70, 3.0),
        f("Breads & grains", "Muesli", "40 g", 150, 4.0),
        // Dal & legumes
        f("Dal & legumes", "Toor dal", "1 bowl", 180, 9.0),
        f("Dal & legumes", "Moong dal", "1 bowl", 160, 10.0),
        f("Dal & legumes", "Rajma curry", "1 bowl", 220, 10.0),
        f("Dal & legumes", "Chole", "1 bowl", 250, 11.0),
        f("Dal & legumes", "Moong sprouts", "1 cup", 140, 10.0),
        f("Dal & legumes", "Roasted chana", "30 g", 110, 6.0),
        f("Dal & legumes", "Soya chunks", "30 g dry", 104, 15.6),
        // Eggs & dairy
        f("Eggs & dairy", "Boiled egg", "1", 72, 6.3),
        f("Eggs & dairy", "Egg white", "1", 17, 3.6),
        f("Eggs & dairy", "Omelette (2 eggs)", "1", 190, 13.0),
        f("Eggs & dairy", "Egg bhurji (2 eggs)", "1 plate", 210, 13.0),
        f("Eggs & dairy", "Paneer", "100 g", 265, 18.0),
        f("Eggs & dairy", "Low-fat paneer", "100 g", 110, 18.0),
        f("Eggs & dairy", "Low-fat curd", "100 g", 60, 3.1),
        f("Eggs & dairy", "Greek yogurt / hung curd", "100 g", 73, 10.0),
        f("Eggs & dairy", "Toned milk", "200 ml", 116, 6.4),
        f("Eggs & dairy", "Buttermilk", "1 glass", 45, 2.3),
        f("Eggs & dairy", "Whey protein", "1 scoop (38.5 g)", 153, 25.0),
        f("Eggs & dairy", "Cheese slice", "1", 60, 4.0),
        // Meat & fish
        f("Meat & fish", "Chicken breast, cooked", "100 g", 165, 31.0),
        f("Meat & fish", "Chicken curry (home)", "1 bowl", 250, 25.0),
        f("Meat & fish", "Tandoori chicken", "2 pieces (150 g)", 260, 35.0),
        f("Meat & fish", "Chicken biryani", "1 plate (300 g)", 500, 25.0),
        f("Meat & fish", "Fish fry", "100 g", 200, 22.0),
        f("Meat & fish", "Fish curry", "1 bowl", 200, 22.0),
        f("Meat & fish", "Prawns, cooked", "100 g", 100, 24.0),
        f("Meat & fish", "Mutton curry", "1 bowl", 330, 22.0),
        f("Meat & fish", "Egg curry (2 eggs)", "1 bowl", 260, 13.0),
        // Vegetables
        f("Vegetables", "Mixed veg sabzi", "1 bowl", 120, 3.0),
        f("Vegetables", "Palak paneer", "1 bowl", 280, 14.0),
        f("Vegetables", "Aloo sabzi", "1 bowl", 180, 3.0),
        f("Vegetables", "Bhindi fry", "1 bowl", 130, 3.0),
        f("Vegetables", "Green salad", "1 bowl", 40, 2.0),
        // Fruit
        f("Fruit", "Banana", "1 medium", 105, 1.3),
        f("Fruit", "Apple", "1 medium", 95, 0.5),
        f("Fruit", "Papaya", "150 g", 65, 0.7),
        f("Fruit", "Orange", "1", 62, 1.2),
        f("Fruit", "Guava", "1", 68, 2.6),
        f("Fruit", "Watermelon", "200 g", 60, 1.2),
        f("Fruit", "Mango", "1 cup", 100, 1.4),
        f("Fruit", "Pomegranate", "100 g arils", 83, 1.7),
        // Snacks & drinks
        f("Snacks & drinks", "Roasted makhana", "25 g", 87, 2.4),
        f("Snacks & drinks", "Peanuts", "30 g", 170, 7.7),
        f("Snacks & drinks", "Almonds", "10 pieces", 70, 2.5),
        f("Snacks & drinks", "Protein bar", "1", 200, 20.0),
        f("Snacks & drinks", "Samosa", "1", 260, 4.0),
        f("Snacks & drinks", "Pani puri", "6 pieces", 200, 4.0),
        f("Snacks & drinks", "Marie biscuits", "4", 120, 2.0),
        f("Snacks & drinks", "Dark chocolate", "20 g", 110, 1.5),
        f("Snacks & drinks", "Tea with milk & sugar", "1 cup", 70, 2.0),
        f("Snacks & drinks", "Coffee with milk", "1 cup", 60, 2.5),
        f("Snacks & drinks", "Black coffee", "1 cup", 2, 0.3),
        f("Snacks & drinks", "Gulab jamun", "1", 150, 2.0),
        f("Snacks & drinks", "Laddoo", "1", 180, 3.0),
        f("Snacks & drinks", "Oil or ghee", "1 tsp", 45, 0.0),
    )

    fun search(query: String): List<LibraryFood> =
        if (query.isBlank()) foods else foods.filter { it.name.contains(query, ignoreCase = true) }
}
