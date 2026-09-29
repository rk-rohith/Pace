package com.pace.tracker.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.line.lineSpec
import com.patrykandpatrick.vico.compose.chart.scroll.rememberChartScrollSpec
import com.patrykandpatrick.vico.compose.component.lineComponent
import com.patrykandpatrick.vico.compose.component.shapeComponent
import com.patrykandpatrick.vico.compose.component.shape.textComponent
import com.patrykandpatrick.vico.compose.m3.style.m3ChartStyle
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.chart.decoration.ThresholdLine
import com.patrykandpatrick.vico.core.chart.values.AxisValuesOverrider
import com.patrykandpatrick.vico.core.chart.values.ChartValues
import com.patrykandpatrick.vico.core.component.shape.Shapes
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.pace.tracker.ui.components.shortDate
import java.util.Locale
import kotlin.math.roundToInt

/** One plotted series. x = days since programme start. */
data class ChartSeries(
    val name: String,
    val color: Color,
    val points: List<Pair<Float, Float>>,
    val dotsOnly: Boolean = false,
    val thickness: Dp = 2.5.dp,
)

private fun dateAxis(startDay: Long) = object : AxisValueFormatter<AxisPosition.Horizontal.Bottom> {
    override fun formatValue(value: Float, chartValues: ChartValues): CharSequence =
        (startDay + value.roundToInt()).shortDate()
}

private fun numberAxis(decimals: Int) = object : AxisValueFormatter<AxisPosition.Vertical.Start> {
    override fun formatValue(value: Float, chartValues: ChartValues): CharSequence =
        if (decimals == 0) String.format(Locale.US, "%,d", value.roundToInt()) else String.format(Locale.US, "%.${decimals}f", value)
}

private fun labelSpacing(points: Int): Int = when {
    points > 60 -> 14
    points > 20 -> 7
    points > 8 -> 2
    else -> 1
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Legend(items: List<Pair<String, Color>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { (name, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(color, RoundedCornerShape(50)))
                Spacer(Modifier.width(6.dp))
                Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Multi-series line chart (Vico). Series with no points are dropped. The chart fits the full range
 * (no horizontal scrolling) so the whole programme is visible at once.
 */
@Composable
fun PaceLineChart(
    series: List<ChartSeries>,
    startDay: Long,
    modifier: Modifier = Modifier,
    minY: Float? = null,
    maxY: Float? = null,
    decimals: Int = 1,
    threshold: Pair<Float, String>? = null,
    height: Dp = 260.dp,
) {
    val visible = series.filter { it.points.isNotEmpty() }
    if (visible.isEmpty()) return
    val producer = remember { ChartEntryModelProducer() }
    LaunchedEffect(visible) {
        producer.setEntries(visible.map { s -> s.points.sortedBy { it.first }.map { FloatEntry(it.first, it.second) } }) {}
    }
    val maxPoints = visible.maxOfOrNull { it.points.size } ?: 0
    val lines = visible.map { s ->
        if (s.dotsOnly) {
            lineSpec(
                lineColor = s.color.copy(alpha = 0f),
                lineThickness = 0.dp,
                lineBackgroundShader = null,
                point = shapeComponent(shape = Shapes.pillShape, color = s.color),
                pointSize = 6.dp,
            )
        } else {
            lineSpec(lineColor = s.color, lineThickness = s.thickness, lineBackgroundShader = null)
        }
    }
    val decorations = threshold?.let { (value, label) ->
        listOf(
            ThresholdLine(
                thresholdValue = value,
                thresholdLabel = label,
                lineComponent = shapeComponent(color = Color(0xFFFFB84D)),
                labelComponent = textComponent(color = Color(0xFFFFB84D)),
            ),
        )
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProvideChartStyle(m3ChartStyle()) {
            Chart(
                chart = lineChart(
                    lines = lines,
                    decorations = decorations,
                    axisValuesOverrider = AxisValuesOverrider.fixed(minY = minY, maxY = maxY),
                ),
                chartModelProducer = producer,
                startAxis = rememberStartAxis(valueFormatter = numberAxis(decimals)),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = dateAxis(startDay),
                    itemPlacer = AxisItemPlacer.Horizontal.default(spacing = labelSpacing(maxPoints)),
                ),
                chartScrollSpec = rememberChartScrollSpec(isScrollEnabled = false),
                modifier = Modifier.fillMaxWidth().height(height),
            )
        }
        Legend(visible.map { it.name to it.color })
    }
}

/** Column chart; multiple series are grouped side by side. */
@Composable
fun PaceColumnChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    xLabel: (Float) -> String,
    threshold: Pair<Float, String>? = null,
    height: Dp = 240.dp,
) {
    val visible = series.filter { it.points.isNotEmpty() }
    if (visible.isEmpty()) return
    val producer = remember { ChartEntryModelProducer() }
    LaunchedEffect(visible) {
        producer.setEntries(visible.map { s -> s.points.sortedBy { it.first }.map { FloatEntry(it.first, it.second) } }) {}
    }
    val maxPoints = visible.maxOfOrNull { it.points.size } ?: 0
    val columns = visible.map { s ->
        lineComponent(color = s.color, thickness = if (visible.size > 1) 6.dp else 10.dp, shape = Shapes.roundedCornerShape(40))
    }
    val decorations = threshold?.let { (value, label) ->
        listOf(
            ThresholdLine(
                thresholdValue = value,
                thresholdLabel = label,
                lineComponent = shapeComponent(color = Color(0xFFFFB84D)),
                labelComponent = textComponent(color = Color(0xFFFFB84D)),
            ),
        )
    }
    val formatter = object : AxisValueFormatter<AxisPosition.Horizontal.Bottom> {
        override fun formatValue(value: Float, chartValues: ChartValues): CharSequence = xLabel(value)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProvideChartStyle(m3ChartStyle()) {
            Chart(
                chart = columnChart(columns = columns, decorations = decorations),
                chartModelProducer = producer,
                startAxis = rememberStartAxis(valueFormatter = numberAxis(0)),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = formatter,
                    itemPlacer = AxisItemPlacer.Horizontal.default(spacing = labelSpacing(maxPoints)),
                ),
                chartScrollSpec = rememberChartScrollSpec(isScrollEnabled = false),
                modifier = Modifier.fillMaxWidth().height(height),
            )
        }
        if (visible.size > 1) Legend(visible.map { it.name to it.color })
    }
}

/**
 * Workout-frequency heatmap: one column per programme week, one row per weekday.
 * Cell intensity = minutes trained that day.
 */
@Composable
fun WorkoutHeatmap(
    startDay: Long,
    weeks: Int,
    minutesByDay: Map<Long, Int>,
    today: Long,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (weeks < 1) return
    val maxMinutes = (minutesByDay.values.maxOrNull() ?: 0).coerceAtLeast(30)
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val future = MaterialTheme.colorScheme.surface
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    val firstWeekday = java.time.LocalDate.ofEpochDay(startDay).dayOfWeek.value - 1
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            (0 until 7).forEach { r ->
                Text(labels[(firstWeekday + r) % 7], style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.height(16.dp))
            }
        }
        Canvas(Modifier.fillMaxWidth().height((7 * 18).dp)) {
            val gap = 2.dp.toPx()
            val cell = minOf((size.width - gap * (weeks - 1)) / weeks, (size.height - gap * 6) / 7)
            for (w in 0 until weeks) {
                for (d in 0 until 7) {
                    val day = startDay + w * 7L + d
                    val minutes = minutesByDay[day] ?: 0
                    val c = when {
                        day > today -> future
                        minutes <= 0 -> empty
                        else -> lerp(color.copy(alpha = 0.35f), color, (minutes.toFloat() / maxMinutes).coerceIn(0f, 1f))
                    }
                    drawRoundRect(
                        color = c,
                        topLeft = Offset(w * (cell + gap), d * (cell + gap)),
                        size = Size(cell, cell),
                        cornerRadius = CornerRadius(cell * 0.2f),
                    )
                }
            }
        }
    }
}
