package com.pace.tracker.ui.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pace.tracker.data.PaceRepository
import com.pace.tracker.data.ProgramData
import com.pace.tracker.data.anchors
import com.pace.tracker.data.caloriesOut
import com.pace.tracker.data.db.MeasurementEntity
import com.pace.tracker.data.currentWeekIndex
import com.pace.tracker.data.goalDay
import com.pace.tracker.data.sevenDayAverage
import com.pace.tracker.data.stepsFor
import com.pace.tracker.data.targetForDay
import com.pace.tracker.data.today
import com.pace.tracker.data.toEngine
import com.pace.tracker.data.weeklyRecals
import com.pace.tracker.data.weights
import com.pace.tracker.data.whatIfRate
import com.pace.tracker.domain.AdaptiveConfig
import com.pace.tracker.domain.BodyMath
import com.pace.tracker.domain.Trajectory
import com.pace.tracker.ui.components.EmptyState
import com.pace.tracker.ui.components.LabeledValue
import com.pace.tracker.ui.components.ScreenScaffold
import com.pace.tracker.ui.components.SectionCard
import com.pace.tracker.ui.components.grouped
import com.pace.tracker.ui.components.kg
import com.pace.tracker.ui.components.oneDecimal
import com.pace.tracker.ui.components.paceViewModel
import com.pace.tracker.ui.components.shortDate
import com.pace.tracker.ui.theme.PaceColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlin.math.max

class ChartsViewModel(repository: PaceRepository) : ViewModel() {
    val data: StateFlow<ProgramData> = repository.programData
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgramData(null))
}

@Composable
fun ChartsScreen() {
    val vm = paceViewModel { ChartsViewModel(it.repository) }
    val data by vm.data.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Weight", "Calories", "Steps", "Workouts", "Body")
    ScreenScaffold("Charts") { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background, edgePadding = 12.dp) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }, modifier = Modifier.height(52.dp))
                }
            }
            if (data.profile == null) return@Column
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (tab) {
                    0 -> WeightTab(data)
                    1 -> CaloriesTab(data)
                    2 -> StepsTab(data)
                    3 -> WorkoutsTab(data)
                    else -> BodyTab(data)
                }
            }
        }
    }
}

private fun ProgramData.x(day: Long): Float = (day - profile!!.startEpochDay).toFloat()

@Composable
private fun NotEnough(what: String) {
    EmptyState("Not enough data yet", "Log $what on at least two days to see this chart.", icon = Icons.Filled.Insights)
}

@Composable
private fun WeightTab(data: ProgramData) {
    val p = data.profile!!
    val t = today()
    var showWhatIf by rememberSaveable { mutableStateOf(true) }
    val weights = data.weights
    val ep = p.toEngine()
    val anchors = data.anchors()
    val lastDay = max(p.startEpochDay + p.durationDays, weights.lastOrNull()?.first ?: t)

    val actual = weights.map { data.x(it.first) to it.second.toFloat() }
    val movingAvg = weights.mapNotNull { (d, _) -> data.sevenDayAverage(d)?.let { data.x(d) to it.first.toFloat() } }
    val trajectory = (p.startEpochDay..lastDay step 2).map { d ->
        data.x(d) to Trajectory.expected(ep, anchors, d).toFloat()
    }
    val current = data.sevenDayAverage(t)?.first
    val rate = data.whatIfRate()
    val minHealthy = BodyMath.weightForBmi(AdaptiveConfig.MIN_HEALTHY_BMI, p.heightCm)
    val whatIfEnd = max(p.startEpochDay + p.durationDays, t + AdaptiveConfig.EXTENSION_DAYS)
    val whatIf = if (showWhatIf && current != null) {
        (t..whatIfEnd step 3).map { d -> data.x(d) to max(minHealthy, current - rate * (d - t) / 7.0).toFloat() }
    } else emptyList()
    val allY = (actual + trajectory + whatIf).map { it.second } + p.targetWeightKg.toFloat()

    SectionCard("Weight trend") {
        if (actual.size < 2) {
            NotEnough("your weight")
        } else {
            PaceLineChart(
                series = listOf(
                    ChartSeries("Daily weigh-ins", PaceColors.Muted, actual, dotsOnly = true),
                    ChartSeries("7-day average", PaceColors.Primary, movingAvg, thickness = 3.dp),
                    ChartSeries("Adaptive target", PaceColors.Tertiary, trajectory),
                    ChartSeries("What-if: more cutdown", PaceColors.Purple, whatIf),
                ),
                startDay = p.startEpochDay,
                minY = (allY.min() - 1f).let { kotlin.math.floor(it) },
                maxY = (allY.max() + 1f).let { kotlin.math.ceil(it) },
                threshold = p.targetWeightKg.toFloat() to "Goal ${p.targetWeightKg.oneDecimal()}",
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Show “more cutdown” scenario", modifier = Modifier.weight(1f))
            Switch(checked = showWhatIf, onCheckedChange = { showWhatIf = it })
        }
        Text(
            "Adaptive target re-anchors to your real weekly average after each recalibration. " +
                "What-if continues at ${rate.oneDecimal()} kg/week (your trend, capped at 1% body weight) " +
                "until ${whatIfEnd.shortDate()}, never below BMI ${AdaptiveConfig.MIN_HEALTHY_BMI.toInt()}.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    SectionCard("Numbers") {
        LabeledValue("Start", p.startWeightKg.kg())
        current?.let { LabeledValue("7-day average", it.kg()) }
        LabeledValue("Goal", p.targetWeightKg.kg() + if (p.targetWeightKg != p.originalTargetWeightKg) " (was ${p.originalTargetWeightKg.oneDecimal()})" else "")
        data.weeklyRecals.lastOrNull()?.let {
            it.projectedFinishKg?.let { f -> LabeledValue("Projected at goal date", f.kg()) }
            LabeledValue("Recent trend", "${it.plannedRateKg.oneDecimal()} kg/wk planned")
        }
        data.goalDay()?.let { LabeledValue("Goal date", it.shortDate()) }
    }
}

@Composable
private fun CaloriesTab(data: ProgramData) {
    val p = data.profile!!
    val t = today()
    val days = (p.startEpochDay..t).toList()
    val inPts = data.calories.filter { it.key in p.startEpochDay..t && it.value > 0 }.map { data.x(it.key) to it.value.toFloat() }
    val loggedDays = days.filter { (data.calories[it] ?: 0) > 0 || data.logs.containsKey(it) || data.workouts.containsKey(it) }
    val outPts = loggedDays.mapNotNull { d -> data.caloriesOut(d)?.let { data.x(d) to it.toFloat() } }
    val targetPts = days.map { data.x(it) to data.targetForDay(it).toFloat() }

    SectionCard("Calories in vs out (daily)") {
        if (inPts.size < 2) NotEnough("meals") else PaceLineChart(
            series = listOf(
                ChartSeries("In (logged)", PaceColors.Secondary, inPts),
                ChartSeries("Out (estimated)", PaceColors.Primary, outPts),
                ChartSeries("Target", PaceColors.Tertiary, targetPts, thickness = 1.5.dp),
            ),
            startDay = p.startEpochDay,
            decimals = 0,
        )
        Text(
            "Out = BMR × 1.2 + steps × ${AdaptiveConfig.KCAL_PER_STEP_70KG} kcal (scaled to weight) + logged workouts. " +
                "Days without step data use your TDEE.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    val weeks = data.currentWeekIndex(t)
    val ep = p.toEngine()
    val weeklyIn = mutableListOf<Pair<Float, Float>>()
    val weeklyOut = mutableListOf<Pair<Float, Float>>()
    for (w in 1..weeks) {
        val range = ep.weekStart(w)..minOf(ep.weekEnd(w), t)
        val ins = range.mapNotNull { d -> data.calories[d]?.takeIf { it > 0 } }
        val outs = range.filter { (data.calories[it] ?: 0) > 0 }.mapNotNull { data.caloriesOut(it) }
        if (ins.isNotEmpty()) weeklyIn += w.toFloat() to ins.average().toFloat()
        if (outs.isNotEmpty()) weeklyOut += w.toFloat() to outs.average().toFloat()
    }
    SectionCard("Weekly average in vs out") {
        if (weeklyIn.isEmpty()) NotEnough("meals") else PaceColumnChart(
            series = listOf(
                ChartSeries("Avg in", PaceColors.Secondary, weeklyIn),
                ChartSeries("Avg out", PaceColors.Primary, weeklyOut),
            ),
            xLabel = { "W${it.toInt()}" },
        )
        if (weeklyIn.isNotEmpty() && weeklyOut.isNotEmpty()) {
            val deficit = weeklyOut.map { it.second }.average() - weeklyIn.map { it.second }.average()
            LabeledValue("Average daily deficit", "${deficit.toInt().grouped()} kcal ≈ ${(deficit * 7 / AdaptiveConfig.KCAL_PER_KG).oneDecimal()} kg/week")
        }
    }
}

@Composable
private fun StepsTab(data: ProgramData) {
    val p = data.profile!!
    val t = today()
    val pts = (p.startEpochDay..t).mapNotNull { d -> data.stepsFor(d)?.let { data.x(d) to it.toFloat() } }
    SectionCard("Daily steps") {
        if (pts.isEmpty()) NotEnough("steps") else PaceColumnChart(
            series = listOf(ChartSeries("Steps", PaceColors.Tertiary, pts)),
            xLabel = { (p.startEpochDay + it.toLong()).shortDate() },
            threshold = p.stepGoal.toFloat() to "Goal ${p.stepGoal.grouped()}",
        )
    }
    val last7 = (t - 6..t).mapNotNull { data.stepsFor(it) }
    SectionCard("Summary") {
        LabeledValue("Goal", p.stepGoal.grouped())
        LabeledValue("7-day average", if (last7.isEmpty()) "—" else last7.average().toInt().grouped())
        LabeledValue("Total since start", pts.sumOf { it.second.toInt() }.grouped())
        LabeledValue("Days goal met", "${pts.count { it.second >= p.stepGoal }} of ${pts.size}")
    }
}

@Composable
private fun WorkoutsTab(data: ProgramData) {
    val p = data.profile!!
    val t = today()
    val weeks = max(data.currentWeekIndex(t), (p.durationDays + 6) / 7)
    val minutes = data.workouts.mapValues { it.value.minutes.coerceAtLeast(if (it.value.count > 0) 1 else 0) }
    SectionCard("Workout frequency") {
        WorkoutHeatmap(p.startEpochDay, weeks, minutes, t, PaceColors.Primary, Modifier.fillMaxWidth())
        Text("Each column is a programme week; brighter = more minutes.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val ep = p.toEngine()
    val perWeek = (1..data.currentWeekIndex(t)).map { w ->
        w.toFloat() to (ep.weekStart(w)..ep.weekEnd(w)).sumOf { data.workouts[it]?.count ?: 0 }.toFloat()
    }
    SectionCard("Workouts per week") {
        if (perWeek.all { it.second == 0f }) NotEnough("workouts") else PaceColumnChart(
            series = listOf(ChartSeries("Workouts", PaceColors.Primary, perWeek)),
            xLabel = { "W${it.toInt()}" },
            threshold = p.workoutsPerWeek.toFloat() to "Goal ${p.workoutsPerWeek}/wk",
        )
        LabeledValue("Total workouts", data.workouts.values.sumOf { it.count }.toString())
        LabeledValue("Total minutes", data.workouts.values.sumOf { it.minutes }.grouped())
        LabeledValue("Calories burned", data.workouts.values.sumOf { it.kcal }.grouped() + " kcal")
    }
}

@Composable
private fun BodyTab(data: ProgramData) {
    val p = data.profile!!
    val ms = data.measurements
    val mSeries = listOf<Triple<String, androidx.compose.ui.graphics.Color, (MeasurementEntity) -> Double?>>(
        Triple("Chest", PaceColors.Tertiary, { m: MeasurementEntity -> m.chestCm }),
        Triple("Waist", PaceColors.Primary, { m: MeasurementEntity -> m.waistCm }),
        Triple("Hips", PaceColors.Secondary, { m: MeasurementEntity -> m.hipsCm }),
        Triple("Arms", PaceColors.Purple, { m: MeasurementEntity -> m.armsCm }),
        Triple("Thighs", PaceColors.Pink, { m: MeasurementEntity -> m.thighsCm }),
        Triple("Neck", PaceColors.Muted, { m: MeasurementEntity -> m.neckCm }),
    ).map { (name, color, get) ->
        ChartSeries(name, color, ms.mapNotNull { m -> get(m)?.let { data.x(m.epochDay) to it.toFloat() } })
    }
    SectionCard("Measurements (cm)") {
        if (ms.size < 2) NotEnough("measurements") else {
            val ys = mSeries.flatMap { s -> s.points.map { it.second } }
            PaceLineChart(mSeries, p.startEpochDay, minY = kotlin.math.floor(ys.min() - 2f), maxY = kotlin.math.ceil(ys.max() + 2f))
        }
    }

    val weights = data.weights
    val bmi = weights.map { (d, w) -> data.x(d) to BodyMath.bmi(w, p.heightCm).toFloat() }
    SectionCard("BMI") {
        if (bmi.size < 2) NotEnough("your weight") else PaceLineChart(
            listOf(ChartSeries("BMI", PaceColors.Tertiary, bmi)),
            p.startEpochDay,
            minY = kotlin.math.floor(bmi.minOf { it.second } - 1f),
            maxY = kotlin.math.ceil(bmi.maxOf { it.second } + 1f),
            threshold = 25f to "BMI 25",
        )
        weights.lastOrNull()?.let { LabeledValue("Current BMI", BodyMath.bmi(it.second, p.heightCm).oneDecimal()) }
    }

    val logged = data.logs.values.filter { it.bodyFatPct != null }.sortedBy { it.epochDay }
        .map { data.x(it.epochDay) to it.bodyFatPct!!.toFloat() }
    val navy = ms.mapNotNull { m ->
        BodyMath.bodyFatNavy(p.sex, p.heightCm, m.waistCm, m.neckCm, m.hipsCm)?.let { data.x(m.epochDay) to it.toFloat() }
    }
    val bmiBased = weights.map { (d, w) -> data.x(d) to BodyMath.bodyFatFromBmi(BodyMath.bmi(w, p.heightCm), p.age, p.sex).toFloat() }
    SectionCard("Estimated body fat %") {
        val all = logged + navy + bmiBased
        if (all.size < 2) NotEnough("weight or measurements") else PaceLineChart(
            listOf(
                ChartSeries("Logged", PaceColors.Secondary, logged, dotsOnly = true),
                ChartSeries("US Navy (tape)", PaceColors.Primary, navy),
                ChartSeries("BMI-based", PaceColors.Muted, bmiBased),
            ),
            p.startEpochDay,
            minY = kotlin.math.floor(all.minOf { it.second } - 2f),
            maxY = kotlin.math.ceil(all.maxOf { it.second } + 2f),
        )
        Text(
            "US Navy uses waist, neck${if (p.sex == com.pace.tracker.domain.Sex.FEMALE) ", hips" else ""} and height. " +
                "BMI-based (Deurenberg) is a rough population estimate.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
