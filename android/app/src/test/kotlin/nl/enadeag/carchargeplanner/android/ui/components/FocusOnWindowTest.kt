package nl.enadeag.carchargeplanner.android.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [FocusOnWindow] with a window whose focus the test sets, and two focusable elements, "first" and "second", one of
 * which is its target: each test checks which element has the focus, on Robolectric.
 */
@RunWith(AndroidJUnit4::class)
class FocusOnWindowTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var windowFocused by mutableStateOf(false)
    private var key by mutableIntStateOf(0)
    private var second by mutableStateOf(false)
    private val first = FocusRequester()
    private val other = FocusRequester()

    private val window = object : WindowInfo {
        override val isWindowFocused: Boolean get() = windowFocused
    }

    /** Out of touch mode, as after a key pressed on a keyboard, where an element can take the focus. */
    @Before
    fun keyboard() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    @After
    fun touch() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
    }

    private fun show() {
        compose.setContent {
            CompositionLocalProvider(LocalWindowInfo provides window) {
                val chosen = if (second) other else first
                Column {
                    Box(Modifier.size(48.dp).focusRequester(first).focusable().testTag("first"))
                    Box(Modifier.size(48.dp).focusRequester(other).focusable().testTag("second"))
                }
                FocusOnWindow(key) { chosen }
            }
        }
        compose.waitForIdle()
    }

    private fun focus(on: Boolean) {
        compose.runOnIdle { windowFocused = on }
        compose.waitForIdle()
    }

    @Test
    fun noElementTakesTheFocusWhileTheWindowHasNoFocus() {
        show()

        compose.runOnIdle { key++ }
        compose.waitForIdle()

        compose.onNodeWithTag("first").assertIsNotFocused()
        compose.onNodeWithTag("second").assertIsNotFocused()
    }

    @Test
    fun theTargetTakesTheFocusOnceTheWindowGetsTheFocus() {
        show()

        focus(true)

        compose.onNodeWithTag("first").assertIsFocused()
    }

    @Test
    fun theTargetTakesTheFocusAgainWhenTheWindowGetsTheFocusBack() {
        show()
        focus(true)
        compose.runOnIdle { other.requestFocus() }

        focus(false)
        focus(true)

        compose.onNodeWithTag("first").assertIsFocused()
    }

    @Test
    fun theTargetTakesTheFocusAgainWhenAKeyChangesWhileTheWindowHasTheFocus() {
        show()
        focus(true)
        compose.runOnIdle { other.requestFocus() }

        compose.runOnIdle { key++ }
        compose.waitForIdle()

        compose.onNodeWithTag("first").assertIsFocused()
    }

    @Test
    fun aTargetThatChangedBetweenCompositionsIsTheOneThatTakesTheFocus() {
        show()
        compose.runOnIdle { second = true }
        compose.waitForIdle()

        focus(true)

        compose.onNodeWithTag("second").assertIsFocused()
        compose.onNodeWithTag("first").assertIsNotFocused()
    }
}
