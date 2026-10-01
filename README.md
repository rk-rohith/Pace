# Pace — 80-Day Adaptive Fat-Loss Tracker (Android)

A single-user, offline-first Android app that treats your fat-loss goal as **adaptive**. Every 7 days it
recalibrates your daily calorie target from your *actual* weight trend, explains the change in plain English,
and — when you're ahead — offers to move the goal further ("more cutdown").

- Kotlin · Jetpack Compose (Material 3, dark theme) · MVVM · single module
- Room (SQLite) for all data · photos in app-private storage
- CameraX + system photo picker · Vico charts · WorkManager (+ optional exact alarms) · Health Connect
- No account, no backend, no network access needed. `minSdk 29` (Android 10), `targetSdk 34`.

---

## Build & install

Requirements: **Android Studio Koala (2024.1) or newer** (or just JDK 17 + Android SDK 34 on the command line).

```bash
git clone <this repo>
cd Pace
./gradlew assembleDebug          # builds the debug-signed APK
./gradlew testDebugUnitTest      # runs the adaptive-engine unit tests
```

**APK output:** `app/build/outputs/apk/debug/app-debug.apk`

Install on a phone with USB debugging enabled:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

…or copy the APK to the phone and open it (allow "Install unknown apps" for your file manager).

In Android Studio: *File → Open* the `Pace` folder, let Gradle sync, then press **Run ▶**.

### No Android Studio? Build in GitHub Actions

`.github/workflows/android.yml` builds the APK and runs the unit tests on every push. Open the run under
the **Actions** tab and download the **`pace-debug-apk`** artifact.

### Database migrations

Room exports its schema to `app/schemas/` on every build — commit that folder. `DatabaseMigrationTest`
checks each hand-written migration against the exported schema so an upgrade can't crash on launch. When you change an entity,
bump `version` in `PaceDatabase` and add a `Migration` to `PaceDatabase.MIGRATIONS`. The app never uses
destructive migration, so logged data survives upgrades.

---

## Permissions & setup

| Permission | Why | If denied |
|---|---|---|
| Notifications (Android 13+) | Reminders & weekly summary | Reminders stay scheduled but silent; *More → Reminders* shows a banner with a one-tap fix |
| Camera | In-app CameraX capture for meals, workouts, progress photos | Gallery picker still works |
| Physical activity | In-app step counter (`Sensor.TYPE_STEP_COUNTER`) while the app is open | Health Connect / manual steps still work |
| Health Connect → Steps (read) | Daily step totals from Google Fit, Samsung Health, Fitbit, etc. | Manual entry + in-app counter |
| Alarms & reminders (exact alarms, optional) | "Precise timing" reminders | Falls back to WorkManager |

### Health Connect

1. **Android 14+:** Health Connect is built in. **Android 10–13:** install *Health Connect* from the Play Store
   (the app offers a button for this).
2. Make sure your step source (Google Fit, Samsung Health, Fitbit…) is writing steps to Health Connect
   (in that app's settings → Health Connect).
3. In Pace: **More → Health & steps → Connect**, allow *Steps*.
4. Steps for the last 7 days sync every time the app opens (toggle *Auto-sync*), or tap **Sync** on the log screen.

Health Connect data is read only while the app is in the foreground; nothing leaves the device.

**How steps are combined per day:** manual entry wins; otherwise the higher of Health Connect and the
in-app sensor count (they overlap, so they are never added).

### Notifications & reminders

All reminders live in **More → Reminders**; each one is individually toggleable with its own time:

| Reminder | Default | Smart skip |
|---|---|---|
| Morning weigh-in | On, 07:30 daily | Skipped if today's weight is already logged |
| Breakfast / lunch / dinner | Off, 09:00 / 13:30 / 20:00 | Skipped if that meal is logged |
| Workout | Off, Mon/Wed/Fri 18:00 | Skipped if a workout is logged |
| Water | Off, every 90 min 09:00–21:00 | Skipped once the water goal is hit |
| Progress photos & measurements | On, Sunday 09:00 | — |
| Weekly recalibration summary | On, the weekday each programme week completes, 20:00 | Only sent when there's a new recalibration |

- Scheduling uses **WorkManager** one-shot jobs chained to the next occurrence. WorkManager persists jobs
  across reboots on its own; `BootReceiver` additionally re-arms everything after reboot, app update,
  time or timezone change.
- **Precise timing** switches to `AlarmManager.setExactAndAllowWhileIdle`. On Android 12+ this needs the
  *Alarms & reminders* permission (Android 14 denies it by default — the screen links to the setting);
  without it Pace silently falls back to WorkManager.
- If reminders arrive late, exclude Pace from battery optimisation (*Settings → Apps → Pace → Battery →
  Unrestricted*). Some OEMs (Xiaomi, Huawei, OnePlus…) are aggressive about killing background work.
- Notification denied? Everything else keeps working; the Reminders screen explains how to re-enable.

---

## Features

- **Onboarding** — sex, age, height, current & target weight, activity level, pace preference
  (conservative / moderate / aggressive), step and water goals → BMR, TDEE, starting calorie target,
  projected finish, soft goal date (start + 80 days).
- **Home** — day X of N & countdown, progress ring, today's steps & calories, logging streak, current
  adaptive calorie target, on-pace / ahead / behind indicator, newest recalibration explanation,
  "push further?" goal offer, quick water button, big **Log Today** button.
- **Daily log** (auto-saves) — weight (0.1 kg) + body-fat %, meals per slot with calories and optional
  photo (camera or gallery), water counter, workouts (type, duration, kcal with MET estimate, notes, photo),
  steps (manual / Health Connect / in-app sensor), mood / energy / sleep sliders, notes. Browse previous days.
- **Progress photos** — front/side/back per week, timeline grid with date + weight overlay, full-screen
  viewer, side-by-side comparison of any two dates with weight/waist change, weekly tape measurements.
- **Charts** (Vico) — weight with 7-day average, adaptive target trajectory, optional "what-if / more cutdown"
  line and goal line; calories in vs out (daily & weekly); steps with goal line; workout heatmap and
  weekly counts; measurements; BMI; body-fat estimates (logged, US-Navy tape formula, BMI-based).
- **Weekly progress card** — in each week's review: a 1080×1350 image with weight change, progress to
  goal, average calories and protein, steps, workouts and streak; share it or save it to Pictures/Pace.
- **Weekly review** — automatic summary (weight change, average calories, total steps, workouts, photos,
  streak status), that week's recalibration explanation, editable reflection, comparison table across weeks.
- **Target history** — every calorie-target change with its explanation and a chart of the target over time.
- **Protein tracking** — every meal can carry grams of protein; daily goal = 1.8 g × goal weight
  (`PROTEIN_G_PER_KG_GOAL`). Shown on Home, the log, the widget, the weekly review and a daily chart.
- **Quick add** — ~80 common Indian foods built in, recent foods, your saved "My foods", servings stepper,
  and "repeat yesterday" per meal or for the whole day.
- **Backup & restore** (*More → Backup & restore*) — one .zip holds every table and every photo.
  Pick a folder (e.g. Downloads/Pace or a Google Drive folder) for an automatic daily backup that keeps the
  last 7 and survives uninstalling. After a reinstall, tap **Restore from backup** on the first setup
  screen to get the whole programme back.
- **Home-screen widget** — long-press the home screen → Widgets → Pace. Shows day X of N, pace, calories,
  steps and water against today's goals, latest weight and streak, with **+1 water** and **Log** buttons.
  Updates live while the app runs and every 30 minutes otherwise.
- **Meal plan** — two alternating weeks (Week A from the High-Protein Recipes handbook, Week B from
  Instagram reels) at 1,650–1,900 kcal and ~150 g protein a day: today's menu with one-tap logging,
  44 recipes with macros and method, weekly grocery checklists and Sunday prep. **Swap dish** replaces
  any breakfast, lunch, dinner or snack with a similar-calorie option (veg-only on veg days); the
  grocery list is calculated from the planned meals, so it updates with every swap. Generated from
  `tools/mealplan/` into `app/src/main/assets/meal_plan.json`.
- **Streaks & badges** — daily logging, step goal, calorie adherence (days) and workouts (weeks);
  badges at 7 / 30 / 50 / 80 days; adaptive wins ("3 weeks on pace in a row", "new low weight", "5 kg down").

---

## Adaptive formula

All constants live in [`AdaptiveConfig.kt`](app/src/main/java/com/pace/tracker/domain/AdaptiveConfig.kt)
and the rules in [`AdaptiveEngine.kt`](app/src/main/java/com/pace/tracker/domain/AdaptiveEngine.kt) — pure
Kotlin, covered by unit tests in `app/src/test`. The same explanation is available in the app under
*More → How the adaptive engine works*.

### 1. Starting plan

```
BMR  = 10·kg + 6.25·cm − 5·age + 5      (men)      Mifflin–St Jeor
BMR  = 10·kg + 6.25·cm − 5·age − 161    (women)
TDEE = BMR × activity   (1.2 sedentary · 1.375 light · 1.55 moderate · 1.725 active · 1.9 very active)

plannedRate (kg/week) = min(pace, MAX_PLANNED_LOSS_FRACTION × weight)
                        pace: conservative 0.7 · moderate 0.8 · aggressive 0.9
deficit (kcal/day)    = min(plannedRate × KCAL_PER_KG / 7, MAX_DEFICIT_FRACTION × TDEE)
target                = round10(TDEE − deficit), clamped to [MIN_KCAL_(sex), TDEE]
```

Example: male, 30 y, 178 cm, 90 kg, light activity, moderate pace → BMR 1,868, TDEE 2,568,
deficit 880 → **1,690 kcal/day**.

### 2. Weekly recalibration

Programme week *k* covers days `start + 7(k−1)` … `start + 7k − 1`. When a week completes, the engine runs
(on app open or from the weekly reminder; missed weeks are caught up in order). Inputs: that week's
weigh-ins, daily food totals, steps and workouts.

1. **Data check** — fewer than `MIN_WEIGH_INS` weigh-ins → *Not enough data*, target held.
2. `avg` = mean of the week's weigh-ins; `prevAvg` = previous valid week's mean (start weight for week 1);
   `loss = prevAvg − avg`; `loss% = loss / prevAvg × 100`.
3. **Classify** (first match wins):

| Status | Condition | Target change |
|---|---|---|
| Goal reached | `avg ≤ target weight` | hold, offer a lower goal |
| Losing too fast | `loss% > TOO_FAST_PCT` (week 1: `TOO_FAST_PCT_FIRST_WEEK`, water weight) | **+EASE_STEP_KCAL** (protect muscle) |
| Stalled | `loss < STALL_KG` for `STALL_WEEKS` consecutive weeks | **−STALL_STEP_KCAL**; at the calorie floor → advice to add ~2,000 steps/day or a workout instead |
| Behind | `loss < BEHIND_RATIO × plannedRate` | **−BEHIND_STEP_KCAL** only if more than `PACE_TOLERANCE_KG` behind the original plan line, else hold |
| Ahead | `loss > AHEAD_RATIO × plannedRate` or more than `PACE_TOLERANCE_KG` ahead of the plan line | hold, offer more cutdown |
| On pace | otherwise | hold |

   The new target is always clamped to `[MIN_KCAL_(sex), max(TDEE estimate, formula TDEE)]`.

4. **Real TDEE estimate** (energy balance) — if food was logged on ≥ `MIN_INTAKE_DAYS` days:
   `observed = avgIntake + loss × KCAL_PER_KG / 7`, clamped to formula TDEE × (1 ± `TDEE_CLAMP_FRACTION`),
   then `tdee = TDEE_SMOOTHING × observed + (1 − TDEE_SMOOTHING) × previous`. Otherwise the previous
   estimate is blended 50/50 with the formula TDEE at the new weight. Shown in explanations and used as the
   calorie ceiling.
5. **Projection** — `trend` = mean loss of the last `TREND_WEEKS` weeks;
   `projectedFinish = avg − trend × weeksRemaining`.
6. **More cutdown** — when on pace or ahead and the projection (trend capped at 1 %/week) beats the current
   goal by more than `EXTEND_MARGIN_KG`, a new goal is suggested (rounded to 0.5 kg, never below BMI
   `MIN_HEALTHY_BMI`). Accepting it updates the goal and extends the timeline if the planned rate needs more
   days. Reaching the goal offers a further step over at least `EXTENSION_DAYS`.
7. **Explanation** — e.g. *"Week 3: you lost 0.9 kg (avg 88.1 → 87.2 kg, 1.0% of body weight). Plan: 0.8
   kg/week. On pace. Target stays at 1,690 kcal. Avg intake 1,720 kcal; estimated real TDEE 2,590 kcal.
   Projected finish: 80.6 kg."*

### 3. Adaptive target trajectory & pace indicator

The trajectory starts at the start weight and is **re-anchored** to each week's real average (at mid-week),
then descends at the planned rate toward the goal. Home compares your 7-day weight average with the
trajectory at the same point: more than `PACE_BAND_KG` below → *Ahead*, above → *Behind*, else *On pace*.
The chart's *what-if* line continues from today at `max(trend, plan)` capped at 1 %/week.

### Constants

| Constant | Default | Meaning |
|---|---|---|
| `KCAL_PER_KG` | 7700 | Energy in 1 kg of fat |
| `DEFAULT_DURATION_DAYS` | 80 | Programme length (soft) |
| `MAX_PLANNED_LOSS_FRACTION` | 0.01 | Plan never exceeds 1 % body weight/week |
| `TOO_FAST_PCT` / `TOO_FAST_PCT_FIRST_WEEK` | 1.0 / 1.5 | "Too fast" threshold (% body weight/week) |
| `STALL_KG` / `STALL_WEEKS` | 0.3 / 2 | Stall definition |
| `BEHIND_RATIO` / `AHEAD_RATIO` | 0.7 / 1.1 | × planned rate |
| `PACE_TOLERANCE_KG` | 0.5 | Cumulative distance from plan line for ahead/behind |
| `EASE_STEP_KCAL` / `STALL_STEP_KCAL` / `BEHIND_STEP_KCAL` | 150 / 150 / 75 | Target adjustments |
| `MAX_DEFICIT_FRACTION` | 0.35 | Max deficit vs TDEE |
| `MIN_KCAL_MALE` / `MIN_KCAL_FEMALE` | 1500 / 1200 | Calorie floors |
| `MIN_WEIGH_INS` / `MIN_INTAKE_DAYS` | 3 / 4 | Data sufficiency per week |
| `TDEE_SMOOTHING` / `TDEE_CLAMP_FRACTION` | 0.5 / 0.25 | Real-TDEE estimate |
| `TREND_WEEKS` | 3 | Weeks in the trend average |
| `EXTEND_MARGIN_KG` / `MIN_HEALTHY_BMI` / `EXTENSION_DAYS` | 0.5 / 20 / 28 | More-cutdown offers |
| `PACE_BAND_KG` | 0.3 | Home on-pace band |
| `ADHERENCE_TOLERANCE_KCAL` / `ADHERENCE_MIN_FRACTION` | 100 / 0.5 | Calorie-adherence streak day |
| `KCAL_PER_STEP_70KG` | 0.04 | Step energy (scaled by weight) for calories-out |
| `DEFAULT_STEP_GOAL` / `DEFAULT_WORKOUTS_PER_WEEK` | 8000 / 3 | Defaults (editable in Profile) |

Other derived numbers: **calories out** = BMR × 1.2 + steps × 0.04 kcal × (weight/70) + logged workout kcal
(days without step data use TDEE + workouts). **Workout kcal estimate** = MET × kg × hours
(walk 3.5, run 9.8, cycling 7.5, strength 5, HIIT 8, yoga 2.5, swim 7, sports 7). **Body fat**: US Navy
tape formula and Deurenberg BMI formula.

### Streak rules

- **Logging:** a day with a weigh-in, a meal or a workout.
- **Step goal:** steps ≥ goal. **Calorie adherence:** intake ≤ that day's target + 100 kcal and ≥ 50 % of it.
- **Workouts:** consecutive programme weeks with ≥ N workouts (default 3).
- Today never breaks a streak until the day is over.

---

## Project layout

```
app/src/main/java/com/pace/tracker/
├── domain/        Pure Kotlin: BodyMath, AdaptiveConfig, AdaptiveEngine, Trajectory, Streaks, ReminderTiming
├── data/          Room entities/DAOs/database, PaceRepository (engine runner), ProgramData (derived views)
├── photo/         PhotoStorage (app-private JPEGs), CameraX capture + photo picker composables
├── health/        HealthConnectManager, StepSensorTracker
├── reminders/     ReminderType, ReminderScheduler (WorkManager/AlarmManager), worker, receivers, notifications
├── widget/      Home-screen progress widget (RemoteViews)
└── ui/            Compose screens: onboarding, home, log, photos, charts, review, history, streaks, settings
```

Tests: `app/src/test/java/com/pace/tracker/AdaptiveEngineTest.kt` (engine, BMR, trajectory, streaks,
reminder timing).
