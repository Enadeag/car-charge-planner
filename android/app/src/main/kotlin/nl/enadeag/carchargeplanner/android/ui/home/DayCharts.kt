package nl.enadeag.carchargeplanner.android.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import kotlin.math.max
import kotlin.math.roundToInt
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.home.ChargeLevels
import nl.enadeag.carchargeplanner.android.home.DayModel
import nl.enadeag.carchargeplanner.android.ui.LocalSourceColours
import nl.enadeag.carchargeplanner.android.ui.SourceColours

/** The tick labels the chart's time axis shows, for the tests: what is drawn has no semantics of its own. */
internal val AxisLabels = SemanticsPropertyKey<List<String>>("AxisLabels")
private var SemanticsPropertyReceiver.axisLabels by AxisLabels

/** The least height of the chart, so that a narrow column still shows its curve. */
private val MIN_CHART_HEIGHT = 160.dp

/**
 * A day's solar chart, as the desktop's: the expected solar power as an amber area, the house's use as a dashed line,
 * the charge windows as green bands with a mark on the axis, and "now" on today; a legend in words above it and the
 * same facts in a sentence under it. It takes the column's width, half as high but never lower than a minimum. A
 * screen reader reads the chart as one part, that sentence; the drawing, the legend and the line under it are not
 * read apart. The colours are fixed, on a ground of their own.
 *
 * @param day the day, with its chart
 * @param words the dashboard's words
 */
@Composable
internal fun SolarCard(day: DayModel, words: DashboardWords) {
    val colours = LocalSourceColours.current.chart
    val spoken = words.chart(day)
    ChartCard(colours) {
        Text(stringResource(R.string.chart_heading), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() })
        Legend(colours)
        SolarCanvas(day, words, colours, spoken)
        Text(spoken, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics {
            hideFromAccessibility()
        })
    }
}

/**
 * A day's battery, as the desktop's bar: the level at the start, the level after charging outlined beyond it, and
 * marks at the minimum and target charge levels with their words under them; the levels in words above it. A screen
 * reader reads the bar as one sentence, with the levels; their words are not read apart.
 *
 * @param day the day, with its levels
 * @param levels the minimum and target charge levels
 * @param words the dashboard's words
 */
@Composable
internal fun BatteryCard(day: DayModel, levels: ChargeLevels, words: DashboardWords) {
    val colours = LocalSourceColours.current.chart
    ChartCard(colours) {
        Text(stringResource(R.string.battery_heading), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() })
        Text(words.batteryLevels(day.battery), style = MaterialTheme.typography.titleMedium, color = colours.carText,
            modifier = Modifier.semantics { hideFromAccessibility() })
        BatteryBar(day, levels, words, colours)
    }
}

/** A card in the charts' fixed colours, whose contrast is checked, rather than the wallpaper's. */
@Composable
private fun ChartCard(colours: SourceColours.Chart, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = colours.ground,
        contentColor = colours.text)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

/** What each sign of the chart is, in words beside the sign. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(colours: SourceColours.Chart) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LegendEntry(stringResource(R.string.chart_legend_solar), colours) { swatch(colours.solar) }
        LegendEntry(stringResource(R.string.chart_legend_house), colours) {
            dashed(colours.muted, Offset(0f, size.height / 2), Offset(size.width, size.height / 2))
        }
        LegendEntry(stringResource(R.string.chart_legend_window), colours) { swatch(colours.car) }
    }
}

@Composable
private fun LegendEntry(text: String, colours: SourceColours.Chart, sign: DrawScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(14.dp), onDraw = sign)
        Text(text, style = MaterialTheme.typography.labelMedium, color = colours.muted,
            modifier = Modifier.semantics { hideFromAccessibility() })
    }
}

/** The drawing of the chart, measured to the column: the labels of the axis are those that fit side by side. */
@Composable
private fun SolarCanvas(day: DayModel, words: DashboardWords, colours: SourceColours.Chart, spoken: String) {
    val chart = day.chart
    val measurer = rememberTextMeasurer()
    val axis = MaterialTheme.typography.labelSmall.copy(color = colours.muted)
    val nowStyle = axis.copy(color = colours.text)
    val nowText = stringResource(R.string.chart_now)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val height = max(maxWidth / 2, MIN_CHART_HEIGHT)
        val width = constraints.maxWidth.toFloat()
        val gap = with(density) { 4.dp.toPx() }
        val scale = measurer.measure(words.scale(chart.scaleKw), axis)
        val zero = measurer.measure(words.whole(0.0), axis)
        val now = measurer.measure(nowText, nowStyle)
        val lineHeight = scale.size.height.toFloat()
        val left = max(scale.size.width, zero.size.width) + gap
        val plot = PlotArea(left, lineHeight / 2, max(0f, width - left - gap),
            max(0f, with(density) { height.toPx() } - lineHeight * 1.5f - gap), chart.fromHour, chart.toHour,
            chart.scaleKw)
        val labels = chart.ticks.associateWith { measurer.measure(words.tick(it), axis) }
        val ticks = TimeAxis.fit(chart, plot, width, gap * 2) { labels.getValue(it).size.width.toFloat() }
        val stroke = with(density) { 2.dp.toPx() }
        Canvas(Modifier.fillMaxWidth().height(height).semantics {
            contentDescription = spoken
            role = Role.Image
            axisLabels = ticks.map { words.tick(it.tick) }
        }) {
            listOf(chart.scaleKw, chart.scaleKw / 2, 0.0).forEach {
                drawLine(colours.gridLine, Offset(plot.left, plot.y(it)), Offset(plot.right, plot.y(it)), 1f)
            }
            chart.windows.forEach {
                val start = plot.x(it.startHour)
                val end = plot.x(it.endHour)
                drawRect(colours.carArea, Offset(start, plot.top), Size(end - start, plot.height))
                drawLine(colours.car, Offset(start, plot.bottom), Offset(end, plot.bottom), stroke * 2,
                    StrokeCap.Round)
            }
            val curve = Path().apply {
                curveOf(chart.steps, chart.fromHour, chart.toHour).forEachIndexed { index, point ->
                    if (index == 0) moveTo(plot.x(point.hour), plot.y(point.kw))
                    else lineTo(plot.x(point.hour), plot.y(point.kw))
                }
                close()
            }
            drawPath(curve, colours.solarArea)
            drawPath(curve, colours.solar, style = Stroke(stroke))
            dashed(colours.muted, Offset(plot.left, plot.y(chart.houseKw)), Offset(plot.right, plot.y(chart.houseKw)))
            chart.now?.let {
                val x = plot.x(it.hour)
                dashed(colours.text, Offset(x, plot.top), Offset(x, plot.bottom))
                drawText(now, topLeft = Offset(minOf(x + gap / 2, plot.right - now.size.width), plot.top))
            }
            drawText(scale, topLeft = Offset(0f, plot.top - lineHeight / 2))
            drawText(zero, topLeft = Offset(0f, plot.bottom - lineHeight / 2))
            ticks.forEach { drawText(labels.getValue(it.tick), topLeft = Offset(it.left, plot.bottom + gap)) }
        }
    }
}

/** The bar, as wide as the column, with the words of its marks under it, on a second line when they would overlap. */
@Composable
private fun BatteryBar(day: DayModel, levels: ChargeLevels, words: DashboardWords, colours: SourceColours.Chart) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall.copy(color = colours.muted)
    val minimumText = words.minimum(levels)
    val targetText = words.target(levels)
    val spoken = words.battery(day, levels)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val width = constraints.maxWidth.toFloat()
        val minimum = measurer.measure(minimumText, style)
        val target = measurer.measure(targetText, style)
        val gap = with(density) { 8.dp.toPx() }
        val layout = BatteryBarLayout.of(day.battery, levels, width, minimum.size.width.toFloat(),
            target.size.width.toFloat(), gap)
        val overhang = with(density) { 4.dp.toPx() }
        val bar = with(density) { 16.dp.toPx() }
        val line = max(minimum.size.height, target.size.height).toFloat()
        val labelsTop = overhang * 2 + bar + gap / 2
        val height = with(density) { (labelsTop + line * layout.rows).toDp() }
        Box(Modifier.fillMaxWidth().height(height)) {
            Canvas(Modifier.fillMaxWidth().height(height).semantics {
                contentDescription = spoken
                role = Role.Image
            }) {
                val radius = CornerRadius(bar / 2)
                drawRoundRect(colours.gridLine, Offset(0f, overhang), Size(width, bar), radius)
                if (layout.afterEnd > 0) {
                    drawRoundRect(colours.carAfter, Offset(0f, overhang), Size(layout.afterEnd, bar), radius)
                    drawRoundRect(colours.car, Offset(0f, overhang), Size(layout.afterEnd, bar), radius,
                        Stroke(with(density) { 1.5.dp.toPx() }))
                }
                if (layout.levelEnd > 0) {
                    drawRoundRect(colours.car, Offset(0f, overhang), Size(layout.levelEnd, bar), radius)
                }
                // a line of the ground between the level and the level after charging, as their two greens are
                // too close to tell apart by contrast alone
                if (layout.afterEnd > layout.levelEnd && layout.levelEnd > 0) {
                    drawLine(colours.ground, Offset(layout.levelEnd, overhang), Offset(layout.levelEnd, overhang + bar),
                        with(density) { 2.dp.toPx() })
                }
                listOf(layout.minimum, layout.target).forEach {
                    drawLine(colours.text, Offset(it.x, 0f), Offset(it.x, overhang * 2 + bar),
                        with(density) { 2.dp.toPx() })
                }
            }
            MarkLabel(minimumText, layout.minimum, labelsTop, line, style.color)
            MarkLabel(targetText, layout.target, labelsTop, line, style.color)
        }
    }
}

/** A mark's words, shown as text so that they are the same facts as the bar's sentence. */
@Composable
private fun MarkLabel(text: String, mark: BatteryBarLayout.Mark, top: Float, line: Float,
    colour: Color) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = colour, maxLines = 1, softWrap = false,
        modifier = Modifier.offset { IntOffset(mark.labelLeft.roundToInt(), (top + line * mark.row).roundToInt()) }
            .semantics { hideFromAccessibility() })
}

private fun DrawScope.swatch(colour: Color) =
    drawRoundRect(colour, cornerRadius = CornerRadius(2.dp.toPx()))

private fun DrawScope.dashed(colour: Color, from: Offset, to: Offset) =
    drawLine(colour, from, to, 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(
        5.dp.toPx(), 3.dp.toPx())))
