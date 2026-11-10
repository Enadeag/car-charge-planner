package nl.enadeag.carchargeplanner.android.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** [FocusOnWindow] with a window whose focus the test sets, counting each time it moves the focus, on Robolectric. */
@RunWith(AndroidJUnit4::class)
class FocusOnWindowTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var windowFocused by mutableStateOf(false)
    private var key by mutableIntStateOf(0)
    private var requests = 0

    private val window = object : WindowInfo {
        override val isWindowFocused: Boolean get() = windowFocused
    }

    private fun show() {
        compose.setContent {
            CompositionLocalProvider(LocalWindowInfo provides window) {
                FocusOnWindow(key) {
                    requests++
                    null
                }
            }
        }
        compose.waitForIdle()
    }

    private fun focus(on: Boolean) {
        compose.runOnIdle { windowFocused = on }
        compose.waitForIdle()
    }

    @Test
    fun noFocusMovesWhileTheWindowHasNoFocus() {
        show()

        compose.runOnIdle { key++ }
        compose.waitForIdle()

        assertThat(requests).isZero()
    }

    @Test
    fun theFocusMovesOnceTheWindowGetsTheFocus() {
        show()

        focus(true)

        assertThat(requests).isEqualTo(1)
    }

    @Test
    fun theFocusMovesAgainWhenTheWindowGetsTheFocusBack() {
        show()
        focus(true)

        focus(false)
        focus(true)

        assertThat(requests).isEqualTo(2)
    }

    @Test
    fun theFocusMovesAgainWhenAKeyChangesWhileTheWindowHasTheFocus() {
        show()
        focus(true)

        compose.runOnIdle { key++ }
        compose.waitForIdle()

        assertThat(requests).isEqualTo(2)
    }
}
