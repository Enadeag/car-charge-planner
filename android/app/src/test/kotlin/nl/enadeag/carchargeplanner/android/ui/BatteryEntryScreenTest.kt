package nl.enadeag.carchargeplanner.android.ui

import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.afterTheSystemEndedTheApp
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.home.BatteryEntry
import nl.enadeag.carchargeplanner.android.home.HomeNotice
import nl.enadeag.carchargeplanner.android.home.HomeOutlook
import nl.enadeag.carchargeplanner.android.home.Plans
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.store.Saves
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.android.ui.home.HomeScreen
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowViewRootImpl

/** The entry of the battery level from today's battery card, on the dashboard on Robolectric. */
@RunWith(AndroidJUnit4::class)
class BatteryEntryScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val lisbon = ZoneId.of("Europe/Lisbon")
    private val today = LocalDate.of(2026, 6, 1)
    private val access = FakeEngineAccess(StartState.Ready(EngineConfig.setUpDraft(ZoneId.of("Europe/Amsterdam"),
        ProviderId("open-meteo"))))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val saves = Saves(access, scope)
    private val handle = SavedStateHandle()
    private lateinit var entry: BatteryEntry

    @After
    fun stop() {
        scope.cancel()
    }

    /** Monday in Lisbon, today with a window and the battery level assumed, and two more days. */
    private fun week(assumed: Boolean = true) = Plans.planned(today.atTime(9, 0).atZone(lisbon), listOf(
        listOf(Plans.window(lisbon, today, 13, 15)), listOf(), listOf()), assumed = assumed)

    private fun show(answer: Outlook = week(), kept: SavedStateHandle = handle) {
        val outlook = HomeOutlook(access, scope, Clock.systemUTC()) { }
        entry = BatteryEntry(kept, NumberText(Locale.ENGLISH), access, scope) { outlook.planAgain() }
        val notice = HomeNotice(saves, scope) { }
        compose.setContent { AppTheme { HomeScreen(outlook, notice, entry, { lisbon }) { } } }
        compose.waitForIdle()
        access.outlooks.last().complete(answer)
        compose.waitForIdle()
    }

    private val update get() = compose.onNodeWithText(text(R.string.battery_update))

    private val field get() = compose.onNode(hasSetTextAction() and hasText(text(R.string.battery_entry_field)))

    private fun openTheEntry() {
        update.performScrollTo().performClick()
        compose.waitForIdle()
        dialogWindowFocused()
    }

    /** Gives the dialog's window the focus, as Android does once it shows it and Robolectric does not. */
    private fun dialogWindowFocused() {
        compose.runOnIdle {
            // the decor view's parent is the window's ViewRootImpl, which the SDK hides
            Shadow.extract<ShadowViewRootImpl>(ShadowDialog.getLatestDialog().window!!.decorView.parent)
                .callWindowFocusChanged(true)
        }
        compose.waitForIdle()
    }

    private fun click(id: Int) {
        compose.onNodeWithText(text(id)).performClick()
        compose.waitForIdle()
    }

    private fun backInTheDialog() {
        compose.runOnIdle {
            (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed()
        }
        compose.waitForIdle()
    }

    @Test
    fun todaysBatteryCardHasAButtonThatSaysWhatItUpdates() {
        show()

        update.performScrollTo().assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun anotherDaysBatteryCardHasNoUpdate() {
        show()

        compose.onNode(hasContentDescription("Wednesday")).performClick()
        compose.waitForIdle()

        compose.onNode(hasContentDescription(text(R.string.battery_spoken_day, "40", "Wednesday", "40", "20", "80")))
            .performScrollTo().assertIsDisplayed()
        update.assertDoesNotExist()
    }

    @Test
    fun updateOpensMaterialsDialogWithItsTitleAsHeadingAndTheFocusInAnEmptyField() {
        show()

        openTheEntry()

        compose.onNode(isDialog()).assertExists()
        compose.onNode(isHeading() and hasText(text(R.string.battery_entry_title))).assertIsDisplayed()
        field.assertIsFocused().assert(hasText(""))
        compose.onNodeWithText(text(R.string.battery_entry_hint), substring = true).assertIsDisplayed()
    }

    @Test
    fun theFieldsKeyboardHasDoneWhichSavesASnackbarSaysSoAndANewPlanIsAsked() {
        show()
        openTheEntry()

        field.performTextInput("64")
        field.performImeAction()
        compose.waitForIdle()

        assertThat(access.batteryLevels).containsExactly(Percent(64.0))
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.battery_saved)).assertIsDisplayed()
        assertThat(compose.liveRegionOf(text(R.string.battery_saved))).isEqualTo(LiveRegionMode.Polite)
        assertThat(access.outlooks).hasSize(2)
    }

    @Test
    fun afterASaveTheFocusGoesBackToUpdate() {
        show()
        openTheEntry()
        field.performTextInput("64")

        click(R.string.action_save)

        update.assertIsFocused()
    }

    @Test
    fun theFieldIsTheEntrysOnlyFieldAndItsKeyIsDone() {
        show()
        openTheEntry()

        compose.assertOnlyTheLastFieldIsDone()
    }

    @Test
    fun aLevelWithDecimalsSaysToTypeAWholeNumberUnderTheField() {
        show()
        openTheEntry()
        field.performTextInput("64,5")

        click(R.string.action_save)

        field.assertIsFocused()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, text(R.string.problem_not_a_whole_number)))
        compose.onNodeWithText(text(R.string.problem_not_a_whole_number)).assertIsDisplayed()
        assertThat(access.batteryLevels).isEmpty()
    }

    @Test
    fun aRefusedLevelSaysWhyUnderTheFieldWhichKeepsTheFocusAndNothingIsSaved() {
        show()
        openTheEntry()
        field.performTextInput("101")

        click(R.string.action_save)

        field.assertIsFocused()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, text(R.string.problem_out_of_range)))
        compose.onNode(isDialog()).assertExists()
        assertThat(access.batteryLevels).isEmpty()
    }

    @Test
    fun aSaveThatFailedSaysSoInTheEntryAtOnceWithTheEnglishDetailsAndKeepsWhatWasTyped() {
        access.batteryFails = "No space left on device"
        show()
        openTheEntry()
        field.performTextInput("64")

        click(R.string.action_save)

        compose.onNodeWithText(text(R.string.battery_not_saved)).assertIsDisplayed()
        assertThat(compose.liveRegionOf(text(R.string.battery_not_saved))).isEqualTo(LiveRegionMode.Assertive)
        compose.onNodeWithText(text(R.string.details_in_english, "No space left on device")).assertIsDisplayed()
        field.assert(hasText("64"))
        compose.onNodeWithText(text(R.string.battery_saved)).assertDoesNotExist()
    }

    @Test
    fun cancelClosesWithoutAskingAndTheFocusGoesBackToUpdate() {
        show()
        openTheEntry()
        field.performTextInput("64")

        click(R.string.action_cancel)

        compose.onNode(isDialog()).assertDoesNotExist()
        update.assertIsFocused()
        assertThat(handle.keys().filter { it.startsWith("battery") }).isEmpty()
        assertThat(access.batteryLevels).isEmpty()
    }

    @Test
    fun backWithALevelTypedAsksAndDiscardThrowsItAway() {
        show()
        openTheEntry()
        field.performTextInput("64")

        backInTheDialog()

        compose.onNodeWithText(text(R.string.settings_discard_question)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.battery_discard_text)).assertIsDisplayed()
        click(R.string.settings_discard)
        compose.onNode(isDialog()).assertDoesNotExist()
        update.assertIsFocused()
        assertThat(access.batteryLevels).isEmpty()
    }

    @Test
    fun keepEditingKeepsWhatWasTyped() {
        show()
        openTheEntry()
        field.performTextInput("64")
        backInTheDialog()

        click(R.string.settings_keep_editing)

        compose.onNodeWithText(text(R.string.settings_discard_question)).assertDoesNotExist()
        field.assert(hasText("64"))
    }

    @Test
    fun backWithNothingTypedClosesTheEntry() {
        show()
        openTheEntry()

        backInTheDialog()

        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.settings_discard_question)).assertDoesNotExist()
    }

    @Test
    fun theEntryComesBackWithWhatWasTypedAfterTheSystemEndedTheApp() {
        BatteryEntry(handle, NumberText(Locale.ENGLISH), access, scope) { }.apply {
            open()
            edit("64,5")
        }

        show(kept = handle.afterTheSystemEndedTheApp())
        dialogWindowFocused()

        compose.onNode(isDialog()).assertExists()
        field.assert(hasText("64,5")).assertIsFocused()
    }

    @Test
    fun theEntrysAndTheQuestionsControlsAreNamedAndLargeEnoughAtTheNormalFontSize() {
        show()
        openTheEntry()
        compose.assertEveryControlIsNamedAndLargeEnough()

        field.performTextInput("64")
        backInTheDialog()

        compose.assertEveryControlIsNamedAndLargeEnough()
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp", fontScale = 2.0f)
    fun onASmallPhoneAtTwiceTheFontSizeTheEntryWithAFailureIsNamedLargeEnoughAndReachable() {
        access.batteryFails = "battery.json is not written: written by a newer version of the app (file version 2)"
        show()
        openTheEntry()
        field.performTextInput("64")
        click(R.string.action_save)

        compose.assertEveryControlIsNamedAndLargeEnough()
        compose.onNodeWithText(text(R.string.battery_not_saved)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_save)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_cancel)).assertIsDisplayed()
    }

    // a dialog with a text field never gets idle on Robolectric on a screen wider than high, so on a small phone on
    // its side only the way into the entry is checked here; the entry there is for the check on a phone
    @Test
    @Config(qualifiers = "w480dp-h320dp-land", fontScale = 2.0f)
    fun onASmallPhoneOnItsSideAtTwiceTheFontSizeUpdateIsNamedLargeEnoughAndReachable() {
        show()

        update.performScrollTo().assertIsDisplayed()
        compose.assertEveryControlIsNamedAndLargeEnough()
    }

    @Test
    fun theAssumedLevelIsSaidWithTodaysBatteryBarAndItsNoteSaysHowToEnterIt() {
        show()

        compose.assertReadAsOne(text(R.string.battery_spoken_today, "40", "50", "20", "80") + " " +
            text(R.string.battery_assumed), Role.Image)
        compose.onNodeWithText(text(R.string.day_battery_assumed, "40")).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun anEnteredLevelLeavesTheAssumedSentenceOut() {
        show(week(assumed = false))

        compose.assertReadAsOne(text(R.string.battery_spoken_today, "40", "50", "20", "80"), Role.Image)
        compose.onNodeWithText(text(R.string.day_battery_assumed, "40"), substring = true).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "nl")
    fun theEntryIsInDutch() {
        show()
        update.assertExists()
        openTheEntry()
        field.performTextInput("64,5")
        backInTheDialog()

        compose.assertShowsNoneOf(englishOnlyTexts())
    }
}
