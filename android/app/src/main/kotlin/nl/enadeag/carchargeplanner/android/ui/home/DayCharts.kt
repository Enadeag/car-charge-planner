package nl.enadeag.carchargeplanner.android.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import kotlin.math.max
import kotlin.math.roundToInt
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.ui.LocalSourceColours
import nl.enadeag.carchargeplanner.android.ui.SourceColours
import nl.enadeag.carchargeplanner.android.ui.components.AppButton
import nl.enadeag.carchargeplanner.engine.dashboard.ChargeLevels
import nl.enadeag.carchargeplanner.engine.dashboard.DashboardDay

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
internal fun SolarCard(day: DashboardDay, words: DashboardWords) {
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
 * Today's way into the entry of the battery level, at the end of its battery card.
 *
 * @property open opens the entry
 * @property focus moves the focus to the button that opens it
 */
internal class BatteryAction(val open: () -> Unit, val focus: FocusRequester)

/**
 * A day's battery, as the desktop's bar: the level at the start, the level after charging outlined beyond it, and
 * marks at the minimum and target charge levels with their words under them; the levels in words above it. A screen
 * reader reads the bar as one sentence, with the levels; their words are not read apart. Today's card ends with the
 * button that opens the entry of the level the car shows.
 *
 * @param day the day, with its levels
 * @param levels the minimum and target charge levels
 * @param words the dashboard's words
 * @param update the button that opens the entry of the level; null on a day whose level cannot be entered, any but
 *     today
 */
@Composable
internal fun BatteryCard(day: DashboardDay, levels: ChargeLevels, words: DashboardWords,
    update: BatteryAction? = null) {
    val colours = LocalSourceColours.current.chart
    ChartCard(colours) {
        Text(stringResource(R.string.battery_heading), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() })
        Text(words.batteryLevels(day.battery()), style = MaterialTheme.typography.titleMedium, color = colours.carText,
            modifier = Modifier.semantics { hideFromAccessibility() })
        BatteryBar(day, levels, words, colours)
        if (update != null) {
            // filled, so its words stand on Material's own button colours, whatever the card's fixed ground; it may
            // take the focus also in touch mode, where a button takes none, so the focus comes back to it, and a
            // screen reader with it, when the entry closes
            AppButton(onClick = update.open,
                modifier = Modifier.focusProperties { canFocus = true }.focusRequester(update.focus)) {
                Text(stringResource(R.string.battery_update))
            }
        }
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
private fun SolarCanvas(day: DashboardDay, words: DashboardWords, colours: SourceColours.Chart, spoken: String) {
    LeftToRight {
        SolarPlot(day, words, colours, spoken)
    }
}

@Composable
private fun SolarPlot(day: DashboardDay, words: DashboardWords, colours: SourceColours.Chart, spoken: String) {
    val chart = day.chart()
    val measurer = rememberTextMeasurer()
    val axis = MaterialTheme.typography.labelSmall.copy(color = colours.muted)
    val nowStyle = axis.copy(color = colours.text)
    val nowText = stringResource(R.string.chart_now)
    val scale = remember(measurer, axis, chart, words) { measurer.measure(words.scale(chart.scaleKw()), axis) }
    val zero = remember(measurer, axis, words) { measurer.measure(words.whole(0.0), axis) }
    val now = remember(measurer, nowStyle, nowText) { measurer.measure(nowText, nowStyle) }
    val labels = remember(measurer, axis, chart, words) {
        chart.ticks().associateWith { measurer.measure(words.tick(it), axis) }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val height = max(maxWidth / 2, MIN_CHART_HEIGHT)
        val width = constraints.maxWidth.toFloat()
        val heightPx = with(density) { height.toPx() }
        val drawing = remember(chart, width, heightPx, density.density, scale, zero, now, labels) {
            SolarDrawing.of(chart, width, heightPx, density.density, scale.size.height.toFloat(),
                scale.size.width.toFloat(), zero.size.width.toFloat(), now.size.width.toFloat()) {
                labels.getValue(it).size.width.toFloat()
            }
        }
        Spacer(Modifier.fillMaxWidth().height(height).semantics {
            contentDescription = spoken
            role = Role.Image
            axisLabels = drawing.ticks.map { words.tick(it.tick) }
        }.drawn(drawing.shapes, colours) {
            drawing.nowAt?.let { drawText(now, topLeft = it.offset) }
            drawText(scale, topLeft = drawing.scaleAt.offset)
            drawText(zero, topLeft = drawing.zeroAt.offset)
            drawing.ticks.forEach { drawText(labels.getValue(it.tick), topLeft = Offset(it.left, drawing.tickTop)) }
        })
    }
}

/** The bar, as wide as the column, with the words of its marks under it, on a second line when they would overlap. */
@Composable
private fun BatteryBar(day: DashboardDay, levels: ChargeLevels, words: DashboardWords, colours: SourceColours.Chart) {
    LeftToRight {
        BatteryBarDrawing(day, levels, words, colours)
    }
}

@Composable
private fun BatteryBarDrawing(day: DashboardDay, levels: ChargeLevels, words: DashboardWords,
    colours: SourceColours.Chart) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall.copy(color = colours.muted)
    val minimumText = words.minimum(levels)
    val targetText = words.target(levels)
    val spoken = words.battery(day, levels)
    val minimum = remember(measurer, style, minimumText) { measurer.measure(minimumText, style) }
    val target = remember(measurer, style, targetText) { measurer.measure(targetText, style) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val width = constraints.maxWidth.toFloat()
        val layout = remember(day.battery(), levels, width, minimum, target, density.density) {
            BatteryBarLayout.of(day.battery(), levels, width, minimum.size.width.toFloat(),
                target.size.width.toFloat(), max(minimum.size.height, target.size.height).toFloat(),
                density.density)
        }
        val height = with(density) { layout.height.toDp() }
        Box(Modifier.fillMaxWidth().height(height)) {
            Spacer(Modifier.fillMaxWidth().height(height).semantics {
                contentDescription = spoken
                role = Role.Image
            }.drawn(layout.shapes, colours))
            MarkLabel(minimumText, layout.minimum, layout.labelTop(layout.minimum), style.color)
            MarkLabel(targetText, layout.target, layout.labelTop(layout.target), style.color)
        }
    }
}

/**
 * The charts' drawings run left to right in every language, as their hours and levels do, so the words placed over
 * them are too.
 */
@Composable
private fun LeftToRight(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, content = content)
}

/** A mark's words, shown as text so that they are the same facts as the bar's sentence. */
@Composable
private fun MarkLabel(text: String, mark: BatteryBarLayout.Mark, top: Float, colour: Color) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = colour, maxLines = 1, softWrap = false,
        modifier = Modifier.offset { IntOffset(mark.labelLeft.roundToInt(), top.roundToInt()) }
            .semantics { hideFromAccessibility() })
}

/**
 * Draws the shapes in the charts' colours, then what [over] adds; the paths and the dashes are made once per size
 * and set of shapes, not per frame.
 *
 * @param shapes what to draw, in order
 * @param colours the colours the shapes' inks stand for
 * @param over what is drawn on top of the shapes, such as words
 * @return the modifier that draws them behind its content
 */
internal fun Modifier.drawn(shapes: List<Shape>, colours: SourceColours.Chart, over: DrawScope.() -> Unit = {}) =
    drawWithCache {
        val dash = dashes()
        val steps = shapes.map { shape -> step(shape, colours.of(shape.ink), dash) }
        onDrawBehind {
            steps.forEach { it() }
            over()
        }
    }

/** How a shape is drawn, with its path made once. */
private fun step(shape: Shape, colour: Color, dash: PathEffect): DrawScope.() -> Unit = when (shape) {
    is Shape.Line -> {
        val cap = if (shape.round) StrokeCap.Round else StrokeCap.Butt
        val effect = if (shape.dashed) dash else null
        val draw: DrawScope.() -> Unit = {
            drawLine(colour, shape.from.offset, shape.to.offset, shape.width, cap, effect)
        }
        draw
    }
    is Shape.Rect -> {
        val style = styleOf(shape.outline)
        val draw: DrawScope.() -> Unit = {
            drawRoundRect(colour, Offset(shape.left, shape.top), Size(shape.width, shape.height),
                CornerRadius(shape.corner), style)
        }
        draw
    }
    is Shape.Area -> {
        val path = Path().apply {
            shape.points.forEachIndexed { index, point ->
                if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
            }
            close()
        }
        val style = styleOf(shape.outline)
        val draw: DrawScope.() -> Unit = { drawPath(path, colour, style = style) }
        draw
    }
}

private fun styleOf(outline: Float?): DrawStyle = outline?.let { Stroke(it) } ?: Fill

private val Point.offset: Offset get() = Offset(x, y)

private fun Density.dashes() = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx()))

private fun DrawScope.swatch(colour: Color) =
    drawRoundRect(colour, cornerRadius = CornerRadius(2.dp.toPx()))

private fun DrawScope.dashed(colour: Color, from: Offset, to: Offset) =
    drawLine(colour, from, to, 1.5.dp.toPx(), pathEffect = dashes())
