package nl.enadeag.carchargeplanner.android.ui

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.settings.SettingsPage
import nl.enadeag.carchargeplanner.android.ui.components.AppTextButton
import nl.enadeag.carchargeplanner.android.ui.components.BarIconButton
import nl.enadeag.carchargeplanner.android.ui.components.BarScaffold
import nl.enadeag.carchargeplanner.android.ui.settings.title
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The top app bar's title at twice the font size, on the screens that have the bar: the home and Settings' pages. */
@RunWith(AndroidJUnit4::class)
class BarScaffoldTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** A bar as a screen of the app has it. */
    private sealed interface Bar {

        val title: String

        /** The home's: the app's name, Refresh and Settings. */
        data object Home : Bar {
            override val title get() = text(R.string.app_name)
        }

        /**
         * A Settings page's: Up, its name and Save.
         *
         * @property page the page
         */
        data class Settings(val page: SettingsPage) : Bar {
            override val title get() = text(page.title())
        }
    }

    private fun assertEveryTitleIsWhole() {
        val bars = listOf(Bar.Home) + SettingsPage.entries.map { Bar.Settings(it) }
        val shown = mutableStateOf<Bar>(Bar.Home)
        compose.setContent {
            AppTheme {
                AtFontScaleTwo {
                    val bar = shown.value
                    BarScaffold(bar.title, titleFocus = null, onUp = if (bar is Bar.Settings) ({}) else null,
                        actions = {
                            if (bar is Bar.Settings) {
                                AppTextButton(onClick = {}) { Text(text(R.string.action_save)) }
                            } else {
                                BarIconButton(Icons.Filled.Refresh, text(R.string.home_refresh)) {}
                                BarIconButton(Icons.Filled.Settings, text(R.string.home_settings)) {}
                            }
                        }) {}
                }
            }
        }
        for (bar in bars) {
            shown.value = bar
            compose.waitForIdle()
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNode(isHeading() and hasText(bar.title)).fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
            val title = layouts.single()
            // not hasVisualOverflow: the layout a screen reader gets is as wide as the bar allows, the text no wider
            // than its longest line, so that reads as too wide whenever the title is shorter than the room
            assertThat(title.didOverflowHeight).`as`("whether the title of %s is cut off below", bar).isFalse()
            assertThat((0 until title.lineCount).filter { title.isLineEllipsized(it) })
                .`as`("the lines of the title of %s that end in an ellipsis", bar).isEmpty()
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun onAPhoneEveryTitleIsWholeAtTwiceTheFontSize() = assertEveryTitleIsWhole()

    /**
     * Guards that the bar does not crowd the page on a small screen on its side; it does not test the title's
     * wrapping, as the title fits on one line there.
     */
    @Test
    @Config(qualifiers = "w480dp-h320dp-land")
    fun onASmallPhoneOnItsSideEveryTitleIsWholeAtTwiceTheFontSize() = assertEveryTitleIsWhole()

    @Test
    @Config(qualifiers = "nl-w360dp-h640dp")
    fun inDutchEveryTitleIsWholeAtTwiceTheFontSize() = assertEveryTitleIsWhole()
}
