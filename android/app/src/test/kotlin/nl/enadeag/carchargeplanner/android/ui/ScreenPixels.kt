package nl.enadeag.carchargeplanner.android.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import kotlin.math.roundToInt

/** The screen as Android draws it; Compose's captureToImage waits for a frame that Robolectric never draws. */
internal fun AndroidComposeTestRule<*, ComponentActivity>.screenshot(): Bitmap {
    val screen = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
    val shot = Bitmap.createBitmap(screen.width, screen.height, Bitmap.Config.ARGB_8888)
    runOnIdle { screen.draw(Canvas(shot)) }
    return shot
}

/** What the screen shows within [area], in pixels. */
internal fun AndroidComposeTestRule<*, ComponentActivity>.picture(area: Rect): Bitmap =
    Bitmap.createBitmap(screenshot(), area.left.roundToInt(), area.top.roundToInt(), area.width.roundToInt(),
        area.height.roundToInt())
