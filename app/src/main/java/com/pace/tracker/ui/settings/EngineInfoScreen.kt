package com.pace.tracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pace.tracker.domain.AdaptiveConfig as C
import com.pace.tracker.ui.components.LabeledValue
import com.pace.tracker.ui.components.ScreenScaffold
import com.pace.tracker.ui.components.SectionCard

/** In-app copy of the README's adaptive-formula section, showing the live constants. */
@Composable
fun EngineInfoScreen(onBack: () -> Unit) {
    ScreenScaffold("Adaptive engine", onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard("1 · Starting plan") {
                Body(
                    "BMR = 10·kg + 6.25·cm − 5·age + 5 (men) / −161 (women)  (Mifflin–St Jeor)\n" +
                        "TDEE = BMR × activity multiplier (1.2 – 1.9)\n" +
                        "Planned rate = pace (0.7 / 0.8 / 0.9 kg/wk), capped at ${pct(C.MAX_PLANNED_LOSS_FRACTION)} of body weight\n" +
                        "Deficit = rate × ${C.KCAL_PER_KG.toInt()} ÷ 7, capped at ${pct(C.MAX_DEFICIT_FRACTION)} of TDEE\n" +
                        "Target = TDEE − deficit, never below ${C.MIN_KCAL_MALE} (men) / ${C.MIN_KCAL_FEMALE} (women) kcal",
                )
            }
            SectionCard("2 · Weekly recalibration (every 7 days)") {
                Body(
                    "Needs ≥ ${C.MIN_WEIGH_INS} weigh-ins in the week, otherwise the target is held.\n" +
                        "Loss = last week's average − this week's average.\n\n" +
                        "Checked in this order:\n" +
                        "• Goal reached → hold, offer a lower goal\n" +
                        "• Loss > ${C.TOO_FAST_PCT}% body weight/week (${C.TOO_FAST_PCT_FIRST_WEEK}% in week 1) → +${C.EASE_STEP_KCAL} kcal\n" +
                        "• Loss < ${C.STALL_KG} kg for ${C.STALL_WEEKS} weeks in a row → −${C.STALL_STEP_KCAL} kcal (or more activity if at the floor)\n" +
                        "• Loss < ${C.BEHIND_RATIO} × plan → behind; −${C.BEHIND_STEP_KCAL} kcal only if > ${C.PACE_TOLERANCE_KG} kg behind the original plan\n" +
                        "• Loss > ${C.AHEAD_RATIO} × plan, or > ${C.PACE_TOLERANCE_KG} kg ahead of plan → ahead; hold and offer more cutdown\n" +
                        "• Otherwise → on pace; hold",
                )
            }
            SectionCard("3 · Real TDEE estimate") {
                Body(
                    "With food logged on ≥ ${C.MIN_INTAKE_DAYS} days: observed TDEE = avg intake + loss × ${C.KCAL_PER_KG.toInt()} ÷ 7, " +
                        "clamped to ±${pct(C.TDEE_CLAMP_FRACTION)} of the formula, then smoothed " +
                        "(${C.TDEE_SMOOTHING} × new + ${1 - C.TDEE_SMOOTHING} × previous). Shown for insight and used as the calorie ceiling.",
                )
            }
            SectionCard("4 · Projection & “more cutdown”") {
                Body(
                    "Trend = mean loss of the last ${C.TREND_WEEKS} weeks. Projected finish = current avg − trend × weeks left.\n" +
                        "If on pace/ahead and the projection beats your goal by > ${C.EXTEND_MARGIN_KG} kg, the app suggests a new goal " +
                        "(never below BMI ${C.MIN_HEALTHY_BMI}). Accepting it extends the timeline if needed; " +
                        "reaching the goal near the end offers ${C.EXTENSION_DAYS} more days.",
                )
            }
            SectionCard("5 · Home pace indicator") {
                Body(
                    "Your 7-day average is compared with the adaptive target trajectory (re-anchored to each week's real " +
                        "average). Within ±${C.PACE_BAND_KG} kg = on pace.",
                )
            }
            SectionCard("Constants") {
                LabeledValue("KCAL_PER_KG", C.KCAL_PER_KG.toInt().toString())
                LabeledValue("TOO_FAST_PCT", C.TOO_FAST_PCT.toString())
                LabeledValue("STALL_KG / STALL_WEEKS", "${C.STALL_KG} / ${C.STALL_WEEKS}")
                LabeledValue("EASE / STALL / BEHIND step", "${C.EASE_STEP_KCAL} / ${C.STALL_STEP_KCAL} / ${C.BEHIND_STEP_KCAL}")
                LabeledValue("AHEAD_RATIO / BEHIND_RATIO", "${C.AHEAD_RATIO} / ${C.BEHIND_RATIO}")
                LabeledValue("MAX_DEFICIT_FRACTION", C.MAX_DEFICIT_FRACTION.toString())
                Body("Edit app/src/main/java/com/pace/tracker/domain/AdaptiveConfig.kt to tune these, then rebuild.")
            }
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun pct(fraction: Double) = "${(fraction * 100).toInt()}%"
