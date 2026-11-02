package nl.enadeag.carchargeplanner.android.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.setup.SetUpAgain
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.android.ui.start.NewerVersionScreen
import nl.enadeag.carchargeplanner.android.ui.start.UnusableScreen
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The screens for a settings file the app cannot use, on Robolectric. */
@RunWith(AndroidJUnit4::class)
class StartScreensTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val unusable = StartState.Unusable("engine.json", "not valid JSON (line 1, column 2)")
    private val access = FakeEngineAccess(unusable)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After
    fun stop() {
        scope.cancel()
    }

    private fun showUnusable(fontScaleTwo: Boolean = false): SetUpAgain {
        val again = SetUpAgain(unusable, access, scope)
        compose.setContent {
            AppTheme { if (fontScaleTwo) AtFontScaleTwo { UnusableScreen(again) } else UnusableScreen(again) }
        }
        return again
    }

    @Test
    fun anUnusableFileShowsItsEnglishDetailsAndSetUpAgainAsksNamingTheFileOnly() {
        val again = showUnusable()

        compose.onNodeWithText(text(R.string.details_in_english, unusable.details)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.set_up_again)).performScrollTo().performClick()

        compose.onNode(isDialog()).assertExists()
        compose.onNodeWithText(text(R.string.set_up_again_explained, "engine.json")).assertIsDisplayed()
        compose.onNode(hasText(text(R.string.set_up_again)) and hasClickAction() and
            androidx.compose.ui.test.hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()
        assertThat(again.state.value).isInstanceOf(SetUpAgain.State.Moved::class.java)
    }

    @Test
    fun cancelLeavesTheFileAsItIs() {
        val again = showUnusable()
        compose.onNodeWithText(text(R.string.set_up_again)).performScrollTo().performClick()

        compose.onNodeWithText(text(R.string.action_cancel)).performClick()

        assertThat(again.state.value).isEqualTo(SetUpAgain.State.Offered)
        assertThat(access.settings).isEqualTo(unusable)
    }

    @Test
    fun aMoveThatFailsShowsItsDetailsAndMayBeTriedAgain() {
        access.moveFails = true
        val again = showUnusable()
        again.ask()
        again.confirm()
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.set_up_again_failed)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.details_in_english, "Permission denied")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_try_again)).performScrollTo().performClick()

        assertThat(again.state.value).isEqualTo(SetUpAgain.State.Confirming)
    }

    @Test
    fun theUnusableScreenPassesTheChecksAtTwiceTheFontSize() {
        showUnusable(fontScaleTwo = true)
        compose.waitForIdle()

        compose.assertEveryControlIsNamedAndLargeEnough()
        compose.onNodeWithText(text(R.string.set_up_again)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aFileOfANewerVersionOnlyAsksForAnUpdate() {
        compose.setContent { AppTheme { NewerVersionScreen() } }

        compose.onNode(isHeading() and hasText(text(R.string.newer_title))).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.newer_text)).assertIsDisplayed()
        assertThat(compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()).isEmpty()
    }

    @Test
    @Config(qualifiers = "nl")
    fun theScreensAreInDutch() {
        showUnusable()
        compose.onNodeWithText(text(R.string.set_up_again)).performScrollTo().performClick()

        compose.assertShowsNoneOf(englishOnlyTexts())
    }
}
