package nl.enadeag.carchargeplanner.android.ui.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import nl.enadeag.carchargeplanner.android.ui.ChartInk
import nl.enadeag.carchargeplanner.android.ui.SourceColours
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The charts' replay of the shapes, through Compose and Android's own graphics, by pixel probes at 1 dp = 1 px. */
@RunWith(AndroidJUnit4::class)
class DrawnShapesTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val colours = SourceColours.LIGHT.chart

    /** Draws [shapes] on the ground in a box of 100 by 140 px, as the screen does, and returns what it shows. */
    private fun draw(vararg shapes: Shape): Bitmap {
        compose.setContent {
            Box(Modifier.size(100.dp, 140.dp).background(colours.ground).drawn(shapes.toList(), colours))
        }
        compose.waitForIdle()
        val screen = compose.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val shot = Bitmap.createBitmap(screen.width, screen.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { screen.draw(Canvas(shot)) }
        return shot
    }

    private fun Bitmap.at(x: Int, y: Int) = Color(getPixel(x, y))

    private fun ink(ink: ChartInk) = colours.of(ink)

    @Test
    fun anOutlinedRectangleIsHollowAndAFilledOneIsNot() {
        val shot = draw(Shape.Rect(10f, 10f, 30f, 30f, ChartInk.CAR, outline = 4f),
            Shape.Rect(50f, 10f, 30f, 30f, ChartInk.SOLAR))

        assertThat(shot.at(11, 25)).isEqualTo(ink(ChartInk.CAR))
        assertThat(shot.at(25, 25)).isEqualTo(colours.ground)
        assertThat(shot.at(65, 25)).isEqualTo(ink(ChartInk.SOLAR))
    }

    @Test
    fun aRectangleHasRoundCornersOfItsRadius() {
        val shot = draw(Shape.Rect(10f, 10f, 40f, 40f, ChartInk.CAR_AFTER, corner = 20f))

        assertThat(shot.at(11, 11)).isEqualTo(colours.ground)
        assertThat(shot.at(30, 30)).isEqualTo(ink(ChartInk.CAR_AFTER))
    }

    @Test
    fun aDashedLineHasGapsAndAWholeOneHasNone() {
        // dashes of 5 px with 3 px between, from the line's start at 10
        val shot = draw(Shape.Line(Point(10f, 60f), Point(90f, 60f), ChartInk.MUTED, 4f, dashed = true),
            Shape.Line(Point(10f, 75f), Point(90f, 75f), ChartInk.MUTED, 4f))

        assertThat(shot.at(12, 60)).isEqualTo(ink(ChartInk.MUTED))
        assertThat(shot.at(16, 60)).isEqualTo(colours.ground)
        assertThat(shot.at(12, 75)).isEqualTo(ink(ChartInk.MUTED))
        assertThat(shot.at(16, 75)).isEqualTo(ink(ChartInk.MUTED))
    }

    @Test
    fun aRoundLinesEndsReachBeyondItsPointsAndACutOffLinesDoNot() {
        val shot = draw(Shape.Line(Point(20f, 90f), Point(40f, 90f), ChartInk.TEXT, 8f, round = true),
            Shape.Line(Point(60f, 90f), Point(80f, 90f), ChartInk.TEXT, 8f))

        assertThat(shot.at(18, 90)).isEqualTo(ink(ChartInk.TEXT))
        assertThat(shot.at(58, 90)).isEqualTo(colours.ground)
    }

    @Test
    fun anOutlinedAreaIsClosedBackToItsFirstPoint() {
        // a right triangle whose last side, from (50, 130) back to (10, 105), crosses at (30, 117)
        val shot = draw(Shape.Area(listOf(Point(10f, 105f), Point(50f, 105f), Point(50f, 130f)), ChartInk.SOLAR, 4f))

        assertThat(shot.at(30, 105)).isEqualTo(ink(ChartInk.SOLAR))
        assertThat(shot.at(30, 117)).isEqualTo(ink(ChartInk.SOLAR))
    }
}
