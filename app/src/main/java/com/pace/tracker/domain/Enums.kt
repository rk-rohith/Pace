package com.pace.tracker.domain

enum class Sex(val label: String) { MALE("Male"), FEMALE("Female") }

enum class ActivityLevel(val multiplier: Double, val label: String, val description: String) {
    SEDENTARY(1.2, "Sedentary", "Desk job, little exercise"),
    LIGHT(1.375, "Light", "Exercise 1–3 days/week"),
    MODERATE(1.55, "Moderate", "Exercise 3–5 days/week"),
    ACTIVE(1.725, "Active", "Hard exercise 6–7 days/week"),
    VERY_ACTIVE(1.9, "Very active", "Physical job + training"),
}

/** Weekly loss rate the plan aims for. All sit in the 0.7–0.9 kg/week healthy band. */
enum class PacePreference(val kgPerWeek: Double, val label: String) {
    CONSERVATIVE(0.7, "Conservative"),
    MODERATE(0.8, "Moderate"),
    AGGRESSIVE(0.9, "Aggressive"),
}

enum class MealType(val label: String) {
    BREAKFAST("Breakfast"), LUNCH("Lunch"), DINNER("Dinner"), SNACK("Snacks"),
}

enum class PhotoPose(val label: String) { FRONT("Front"), SIDE("Side"), BACK("Back") }

enum class RecalStatus(val label: String) {
    START("Plan created"),
    ON_PACE("On pace"),
    AHEAD("Ahead of pace"),
    BEHIND("Behind pace"),
    TOO_FAST("Losing too fast"),
    STALLED("Stalled"),
    GOAL_REACHED("Goal reached"),
    INSUFFICIENT_DATA("Not enough data"),
    PROFILE_UPDATE("Profile updated"),
    GOAL_EXTENDED("Goal extended"),
}

/** Day-to-day indicator shown on the home screen. */
enum class PaceIndicator(val label: String) {
    AHEAD("Ahead of pace"), ON_PACE("On pace"), BEHIND("Behind pace"), NO_DATA("Log weight to see pace"),
}
