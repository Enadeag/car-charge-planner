package nl.enadeag.carchargeplanner.android.ui

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.afterTheSystemEndedTheApp
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.home.AwayEntry
import nl.enadeag.carchargeplanner.android.home.BatteryEntry
import nl.enadeag.carchargeplanner.android.home.HomeNotice
import nl.enadeag.carchargeplanner.android.home.HomeOutlook
import nl.enadeag.carchargeplanner.android.home.Plans
import nl.enadeag.carchargeplanner.android.home.TripEntry
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.store.Saves
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.android.ui.home.HomeScreen
import nl.enadeag.carchargeplanner.android.ui.home.clockOf
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
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

/** Adding and removing one-off away periods on the dashboard, on Robolectric. */
@RunWith(AndroidJUnit4::class)
class AwayEntryScreenTest {

    /** A clock that stands still. */
    private class TestClock(val now: Instant) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
    }

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val lisbon = ZoneId.of("Europe/Lisbon")
    private val today = LocalDate.of(2026, 6, 1)
    private val clock = TestClock(today.atTime(9, 0).atZone(lisbon).toInstant())
    private val access = FakeEngineAccess(StartState.Ready(EngineConfig.setUpDraft(lisbon, ProviderId("open-meteo"))))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** A card that never shows, for the tests of the home's other parts. */
    private val noCard by lazy { allowedCard(scope) }
    private val saves = Saves(access, scope)
    private val handle = SavedStateHandle()
    private lateinit var away: AwayEntry
    private lateinit var entryDialog: ComponentDialog
    private lateinit var focus: FocusManager

    private val tomorrow = OneOffAwayPeriod.allDay(today.plusDays(1), lisbon)
    private val wednesday = OneOffAwayPeriod.on(today.plusDays(2), LocalTime.of(10, 0), LocalTime.of(12, 0), lisbon)

    @After
    fun stop() {
        scope.cancel()
    }

    /** Monday 1 June in Lisbon, and two more days. */
    private fun week() = Plans.planned(today.atTime(9, 0).atZone(lisbon), listOf(listOf(), listOf(), listOf()))

    private fun clock(hours: String) {
        Settings.System.putString(compose.activity.contentResolver, Settings.System.TIME_12_24, hours)
    }

    /**
     * Shows the home with its plan.
     *
     * @param kept the saved state the entry comes back from
     * @param wrap what the home is shown in, such as twice the font size
     * @param planned false to leave the plan still asked
     */
    private fun show(kept: SavedStateHandle = handle, planned: Boolean = true,
        wrap: @Composable (@Composable () -> Unit) -> Unit = { it() }) {
        clock("24")
        val outlook = HomeOutlook(access, scope, clock) { }
        val battery = BatteryEntry(kept, NumberText(Locale.ENGLISH), access, scope) { }
        away = AwayEntry(kept, access, scope, clock, outlook::awayChanged)
        val trips = TripEntry(access, scope) { }
        val notice = HomeNotice(saves, scope) { }
        compose.setContent {
            focus = LocalFocusManager.current
            AppTheme { wrap { HomeScreen(outlook, notice, battery, away, trips, { lisbon }, noCard) { } } }
        }
        compose.waitForIdle()
        if (planned) {
            access.outlooks.last().complete(week())
            compose.waitForIdle()
        }
    }

    private val add get() = compose.onNode(hasText(text(R.string.one_off_add)) and !hasAnyAncestor(isDialog()))

    private val save get() = compose.onNodeWithText(text(R.string.action_save))

    private fun timeButton(name: Int) = compose.onNode(hasText(text(name)) and hasAnyAncestor(isDialog())
        and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))

    private fun line(text: String) = compose.onNode(hasText(text) and !hasAnyAncestor(isDialog()))

    private fun remove(period: String) =
        compose.onNode(hasContentDescription(text(R.string.one_off_remove_spoken, period)))

    private fun openTheEntry() {
        add.performScrollTo().performClick()
        compose.waitForIdle()
        entryDialog = ShadowDialog.getLatestDialog() as ComponentDialog
        windowFocused(entryDialog, true)
    }

    /** Gives a dialog's window the focus, or takes it, as Android does as dialogs show and go; Robolectric does not. */
    private fun windowFocused(dialog: ComponentDialog, focused: Boolean) {
        compose.runOnIdle {
            // the decor view's parent is the window's ViewRootImpl, which the SDK hides
            Shadow.extract<ShadowViewRootImpl>(dialog.window!!.decorView.parent).callWindowFocusChanged(focused)
        }
        compose.waitForIdle()
    }

    private fun partOfTheDay() {
        click(R.string.one_off_part_of_day)
    }

    private fun click(id: Int) {
        compose.onNodeWithText(text(id)).performClick()
        compose.waitForIdle()
    }

    private fun backInTheDialog() {
        compose.runOnIdle { entryDialog.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun twentyFour(time: LocalTime): String = clockOf(true, Locale.ENGLISH).format(time)

    @Test
    fun theCarAwayHeadingRowEndsWithAddAndEachPeriodHasARemoveSaidWithIt() {
        access.away = listOf(tomorrow)
        show()

        add.performScrollTo().assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        val period = text(R.string.one_off_all_day, "Tomorrow")
        compose.onNode(hasContentDescription(text(R.string.one_off_remove_spoken, period))).performScrollTo()
            .assertIsDisplayed().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(hasText(text(R.string.one_off_remove)))
    }

    @Test
    fun addOpensMaterialsDialogWithTheDayInItsTitleAsHeadingOnAllDayWhichHasTheFocus() {
        show()

        openTheEntry()

        compose.onNode(isDialog()).assertExists()
        compose.onNode(isHeading() and hasText(text(R.string.one_off_entry_title, "Monday, June 1")))
            .assertIsDisplayed()
        compose.onNodeWithText(text(R.string.one_off_whole_day)).assertIsSelected().assertIsFocused()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        timeButton(R.string.one_off_from).assertDoesNotExist()
    }

    @Test
    fun allDaySavedClosesTheEntrySaysSoShowsThePeriodAtOnceAsksANewPlanAndPutsTheFocusOnAdd() {
        show()
        openTheEntry()

        save.performClick()
        compose.waitForIdle()

        assertThat(access.away).containsExactly(OneOffAwayPeriod.allDay(today, lisbon))
        compose.onNode(isDialog()).assertDoesNotExist()
        val saved = text(R.string.one_off_saved, text(R.string.one_off_all_day, "Today"))
        compose.onNodeWithText(saved).assertIsDisplayed()
        assertThat(compose.liveRegionOf(saved)).isEqualTo(LiveRegionMode.Polite)
        // the new plan is still asked: the list shows what the engine stored
        assertThat(access.outlooks).hasSize(2)
        line(text(R.string.one_off_all_day, "Today")).assertExists()
        add.assertIsFocused()
    }

    @Test
    fun anotherDaysAddEntersAPeriodOnThatDay() {
        show()
        compose.onNode(hasContentDescription("Wednesday")).performClick()
        compose.waitForIdle()

        openTheEntry()
        compose.onNode(isHeading() and hasText(text(R.string.one_off_entry_title, "Wednesday, June 3")))
            .assertIsDisplayed()
        save.performClick()
        compose.waitForIdle()

        assertThat(access.away).containsExactly(OneOffAwayPeriod.allDay(today.plusDays(2), lisbon))
    }

    @Test
    fun partOfTheDayShowsBothTimesReadyAtNineAndFiveAndSavesThemInTheHomesZone() {
        show()
        openTheEntry()

        partOfTheDay()

        timeButton(R.string.one_off_from).assert(hasText(twentyFour(LocalTime.of(9, 0))))
        timeButton(R.string.one_off_until).assert(hasText(twentyFour(LocalTime.of(17, 0))))
        save.performClick()
        compose.waitForIdle()
        assertThat(access.away).containsExactly(OneOffAwayPeriod.on(today, LocalTime.of(9, 0), LocalTime.of(17, 0),
            lisbon))
    }

    @Test
    fun onATwelveHourClockTheTimesShowAsThePhoneWritesThem() {
        show()
        clock("12")
        openTheEntry()

        partOfTheDay()

        timeButton(R.string.one_off_from).assert(hasText(clockOf(false, Locale.ENGLISH).format(LocalTime.of(9, 0))))
    }

    @Test
    fun aTimesButtonOpensMaterialsTimePickerWhoseOkPutsTheChosenTimeAndTheFocusBackOnTheButton() {
        show()
        openTheEntry()
        partOfTheDay()

        timeButton(R.string.one_off_from).performClick()
        compose.waitForIdle()
        windowFocused(entryDialog, false)
        compose.onNode(isHeading() and hasText(text(R.string.one_off_from)) and hasAnyAncestor(isDialog()))
            .assertIsDisplayed()
        compose.onNode(hasContentDescription("10 hours") and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        click(R.string.action_ok)
        windowFocused(entryDialog, true)

        assertThat(away.draft.value?.start).isEqualTo(LocalTime.of(10, 0))
        timeButton(R.string.one_off_from).assert(hasText(twentyFour(LocalTime.of(10, 0)))).assertIsFocused()
    }

    @Test
    fun backInTheTimePickerKeepsTheTime() {
        show()
        openTheEntry()
        partOfTheDay()
        timeButton(R.string.one_off_until).performClick()
        compose.waitForIdle()
        compose.onNode(hasContentDescription("10 hours") and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        backInTheDialog()
        compose.waitForIdle()

        assertThat(away.draft.value?.end).isEqualTo(LocalTime.of(17, 0))
        timeButton(R.string.one_off_until).assert(hasText(twentyFour(LocalTime.of(17, 0))))
    }

    @Test
    fun anEndBeforeTheStartIsRefusedUnderTheEndTimeInAPoliteLiveRegionAndNothingIsSaved() {
        show()
        openTheEntry()
        partOfTheDay()
        compose.runOnIdle { away.end(LocalTime.of(8, 0)) }

        save.performClick()
        compose.waitForIdle()

        val refusal = text(R.string.one_off_end_before_start)
        compose.onNodeWithText(refusal).assertIsDisplayed()
        assertThat(compose.liveRegionOf(refusal)).isEqualTo(LiveRegionMode.Polite)
        timeButton(R.string.one_off_until).assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, refusal))
        assertThat(compose.onNodeWithText(refusal).fetchSemanticsNode().boundsInRoot.top)
            .isGreaterThanOrEqualTo(timeButton(R.string.one_off_until).fetchSemanticsNode().boundsInRoot.bottom)
        compose.onNode(isDialog()).assertExists()
        assertThat(access.awayChanges).isEmpty()
    }

    @Test
    fun aPeriodThatHasPassedIsRefused() {
        show()
        openTheEntry()
        partOfTheDay()
        compose.runOnIdle {
            away.start(LocalTime.of(6, 0))
            away.end(LocalTime.of(8, 0))
        }

        save.performClick()
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.one_off_passed)).assertIsDisplayed()
        assertThat(access.awayChanges).isEmpty()
    }

    @Test
    fun whileThePeriodIsSavedSaveAndCancelAreOffAndSaveShowsItsProgress() {
        val gate = CompletableDeferred<Unit>()
        access.awayGate = gate
        show()
        openTheEntry()

        save.performClick()
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.saving)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.action_cancel)).assertIsNotEnabled()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo) and
            hasAnyAncestor(isDialog()), useUnmergedTree = true).assertExists()
        gate.complete(Unit)
        compose.waitForIdle()
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun aSaveThatFailedSaysSoInTheEntryAtOnceWithTheEnglishDetailsAndKeepsWhatWasEntered() {
        access.awayFails = "No space left on device"
        show()
        openTheEntry()
        partOfTheDay()

        save.performClick()
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.one_off_not_saved)).assertIsDisplayed()
        assertThat(compose.liveRegionOf(text(R.string.one_off_not_saved))).isEqualTo(LiveRegionMode.Assertive)
        compose.onNodeWithText(text(R.string.details_in_english, "No space left on device")).assertIsDisplayed()
        timeButton(R.string.one_off_from).assertExists()
        assertThat(access.outlooks).hasSize(1)
    }

    @Test
    fun backWithAChangeAsksAndDiscardThrowsItAwayAndPutsTheFocusOnAdd() {
        show()
        openTheEntry()
        partOfTheDay()

        backInTheDialog()

        compose.onNodeWithText(text(R.string.discard_question)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.one_off_discard_text)).assertIsDisplayed()
        click(R.string.discard)
        compose.onNode(isDialog()).assertDoesNotExist()
        add.assertIsFocused()
        assertThat(access.awayChanges).isEmpty()
    }

    @Test
    fun backWithNothingChangedClosesTheEntry() {
        show()
        openTheEntry()

        backInTheDialog()

        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.discard_question)).assertDoesNotExist()
    }

    @Test
    fun removeActsAtOnceAndItsSnackbarIsReadPolitelyWithUndoWhichAddsThePeriodBack() {
        access.away = listOf(tomorrow)
        show()
        val period = text(R.string.one_off_all_day, "Tomorrow")

        compose.onNode(hasContentDescription(text(R.string.one_off_remove_spoken, period))).performScrollTo()
            .performClick()
        compose.waitForIdle()

        assertThat(access.away).isEmpty()
        line(period).assertDoesNotExist()
        val removed = text(R.string.one_off_removed, period)
        compose.onNodeWithText(removed).assertIsDisplayed()
        assertThat(compose.liveRegionOf(removed)).isEqualTo(LiveRegionMode.Polite)
        assertThat(access.outlooks).hasSize(2)
        compose.onNodeWithText(text(R.string.action_undo)).assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        compose.waitForIdle()
        assertThat(access.away).containsExactly(tomorrow)
        line(period).assertExists()
        compose.onNodeWithText(text(R.string.one_off_saved, period)).assertIsDisplayed()
    }

    @Test
    fun afterRemoveTheFocusGoesToThePeriodBeforeOrAfterOrTheLineThatSaysThereIsNone() {
        clock("24")
        access.away = listOf(tomorrow, wednesday)
        show()
        val first = text(R.string.one_off_all_day, "Tomorrow")
        val second = text(R.string.one_off_part, "Wed, Jun 3", text(R.string.time_span, "10:00", "12:00"))

        compose.onNode(hasContentDescription(text(R.string.one_off_remove_spoken, second))).performScrollTo()
            .performClick()
        compose.waitForIdle()
        line(first).assertIsFocused()

        // the first Remove's snackbar covers the bottom of the page, so the second is pressed as TalkBack does
        compose.onNode(hasContentDescription(text(R.string.one_off_remove_spoken, first)))
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        line(text(R.string.one_off_none)).assertIsFocused()
    }

    @Test
    fun aRemoveThatFailedSaysSoWithTheEnglishDetailsAndThePeriodStaysWithoutANewPlan() {
        access.away = listOf(tomorrow)
        access.awayFails = "No space left on device"
        show()
        val period = text(R.string.one_off_all_day, "Tomorrow")

        compose.onNode(hasContentDescription(text(R.string.one_off_remove_spoken, period))).performScrollTo()
            .performClick()
        compose.waitForIdle()

        val failed = text(R.string.one_off_not_removed) + " " +
            text(R.string.details_in_english, "No space left on device")
        compose.onNodeWithText(failed).assertIsDisplayed()
        assertThat(compose.liveRegionOf(failed)).isNotNull()
        line(period).assertExists()
        assertThat(access.outlooks).hasSize(1)
    }

    @Test
    fun whileARemoveRunsAddAndRemoveAreOff() {
        val gate = CompletableDeferred<Unit>()
        access.awayGate = gate
        access.away = listOf(tomorrow, wednesday)
        show()
        val period = text(R.string.one_off_all_day, "Tomorrow")

        compose.onNode(hasContentDescription(text(R.string.one_off_remove_spoken, period))).performScrollTo()
            .performClick()
        compose.waitForIdle()

        add.assertIsNotEnabled()
        compose.onNode(hasContentDescription(text(R.string.one_off_remove_spoken, period))).assertIsNotEnabled()
        gate.complete(Unit)
        compose.waitForIdle()
        assertThat(access.away).containsExactly(wednesday)
    }

    @Test
    fun theEntryComesBackWithWhatWasEnteredAfterTheSystemEndedTheApp() {
        AwayEntry(handle, access, scope, clock) { }.apply {
            open(today.plusDays(1), lisbon)
            choose(AwayEntry.Kind.PART_OF_DAY)
            start(LocalTime.of(10, 15))
        }

        show(kept = handle.afterTheSystemEndedTheApp())
        entryDialog = ShadowDialog.getLatestDialog() as ComponentDialog

        compose.onNode(isHeading() and hasText(text(R.string.one_off_entry_title, "Tuesday, June 2")))
            .assertIsDisplayed()
        timeButton(R.string.one_off_from).assert(hasText(twentyFour(LocalTime.of(10, 15))))
    }

    @Test
    fun thePagesAndTheEntrysControlsAreNamedAndLargeEnoughAtTheNormalFontSize() {
        access.away = listOf(tomorrow)
        show()
        compose.assertEveryControlIsNamedAndLargeEnough()

        openTheEntry()
        partOfTheDay()
        compose.assertEveryControlIsNamedAndLargeEnough()

        backInTheDialog()
        compose.assertEveryControlIsNamedAndLargeEnough()
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp", fontScale = 2.0f)
    fun onASmallPhoneAtTwiceTheFontSizeTheEntryWithARefusalAndAFailureIsNamedLargeEnoughAndReachable() {
        access.awayFails = "away.json is not written: written by a newer version of the app (file version 2)"
        show(wrap = { AtFontScaleTwo(it) })
        add.performScrollTo()
        compose.assertEveryControlIsNamedAndLargeEnough()
        openTheEntry()
        partOfTheDay()
        save.performClick()
        compose.waitForIdle()

        compose.assertEveryControlIsNamedAndLargeEnough()
        compose.onNodeWithText(text(R.string.one_off_not_saved)).performScrollTo().assertIsDisplayed()
        save.assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_cancel)).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "nl")
    fun theListAndTheEntryAreInDutch() {
        access.away = listOf(tomorrow)
        show()
        add.assertExists().assert(hasContentDescription("Afwezig toevoegen"))
        compose.assertShowsNoneOf(englishOnlyTexts())
        compose.onNode(hasContentDescription(" verwijderen", substring = true)).performScrollTo()
            .assert(hasText("Verwijderen"))
        openTheEntry()
        compose.onNode(isHeading() and hasText("Afwezig op ", substring = true) and hasAnyAncestor(isDialog()))
            .assertIsDisplayed()
        partOfTheDay()
        backInTheDialog()

        compose.assertShowsNoneOf(englishOnlyTexts())
    }

    @Test
    fun theUndoSnackbarStaysPastTheTimeMaterialGivesOtherSnackbarsAndHasTheAppsCloseButton() {
        access.away = listOf(tomorrow)
        show()
        val period = text(R.string.one_off_all_day, "Tomorrow")
        remove(period).performScrollTo().performClick()
        compose.waitForIdle()

        compose.mainClock.advanceTimeBy(60_000)

        compose.onNodeWithText(text(R.string.one_off_removed, period)).assertIsDisplayed()
        // the app's own name for it, as on the failure snackbars, not Material's "Dismiss"
        compose.onNode(hasContentDescription(text(R.string.action_close)) and hasClickAction()).assertIsDisplayed()
        compose.onNode(hasContentDescription("Dismiss")).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.action_undo)).performClick()
        compose.waitForIdle()
        assertThat(access.away).containsExactly(tomorrow)
    }

    @Test
    fun closeClosesTheUndoSnackbarAndLeavesThePeriodRemoved() {
        access.away = listOf(tomorrow)
        show()
        val period = text(R.string.one_off_all_day, "Tomorrow")
        remove(period).performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNode(hasContentDescription(text(R.string.action_close)) and hasClickAction()).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.one_off_removed, period)).assertDoesNotExist()
        assertThat(access.away).isEmpty()
    }

    @Test
    fun aFailureSnackbarStaysPastTheTimeMaterialGivesOtherSnackbarsUntilItIsClosed() {
        access.away = listOf(tomorrow)
        access.awayFails = "No space left on device"
        show()
        val period = text(R.string.one_off_all_day, "Tomorrow")
        remove(period).performScrollTo().performClick()
        compose.waitForIdle()
        val failed = text(R.string.one_off_not_removed) + " " +
            text(R.string.details_in_english, "No space left on device")

        compose.mainClock.advanceTimeBy(60_000)

        compose.onNodeWithText(failed).assertIsDisplayed()
        compose.onNode(hasContentDescription(text(R.string.action_close)) and hasClickAction()).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(failed).assertDoesNotExist()
    }

    @Test
    fun whileTheUndoSnackbarShowsTheLastRowAndTheFooterScrollIntoViewAboveIt() {
        access.away = listOf(tomorrow, wednesday)
        show()
        val first = text(R.string.one_off_all_day, "Tomorrow")
        val second = text(R.string.one_off_part, "Wed, Jun 3", text(R.string.time_span, "10:00", "12:00"))
        remove(first).performScrollTo().performClick()
        compose.waitForIdle()
        val snackbar = compose.onNodeWithText(text(R.string.one_off_removed, first))
        snackbar.assertIsDisplayed()

        val top = snackbar.fetchSemanticsNode().boundsInRoot.top
        listOf(remove(second), compose.onNode(hasText(text(R.string.footer_updated, "09:00"), substring = true)))
            .forEach { last ->
                last.performScrollTo()
                assertThat(last.fetchSemanticsNode().boundsInRoot.bottom).isLessThanOrEqualTo(top)
            }
    }

    @Test
    fun aSecondNoticeTakesTheFirstSnackbarsPlace() {
        access.away = listOf(tomorrow, wednesday)
        show()
        val first = text(R.string.one_off_all_day, "Tomorrow")
        val second = text(R.string.one_off_part, "Wed, Jun 3", text(R.string.time_span, "10:00", "12:00"))
        remove(first).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.one_off_removed, first)).assertIsDisplayed()

        remove(second).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.one_off_removed, second)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.one_off_removed, first)).assertDoesNotExist()
    }

    @Test
    fun theEnglishDetailsOfAFailureInTheSnackbarAreMarkedAsEnglishForTheScreenReader() {
        access.away = listOf(tomorrow)
        access.awayFails = "No space left on device"
        show()
        val period = text(R.string.one_off_all_day, "Tomorrow")

        remove(period).performScrollTo().performClick()
        compose.waitForIdle()

        val failed = text(R.string.one_off_not_removed) + " " +
            text(R.string.details_in_english, "No space left on device")
        val shown = compose.onNodeWithText(failed).fetchSemanticsNode().config[SemanticsProperties.Text].single()
        val english = shown.spanStyles.filter { it.item.localeList?.get(0)?.language == "en" }.single()
        assertThat(shown.text.substring(english.start, english.end)).isEqualTo("No space left on device")
        compose.onNode(hasContentDescription(text(R.string.action_close)) and hasClickAction()).assertIsDisplayed()
    }

    @Test
    fun anUndoThatFailedSaysNotSavedWithTheEnglishDetailsAndTheRowStaysGone() {
        access.away = listOf(tomorrow)
        show()
        val period = text(R.string.one_off_all_day, "Tomorrow")
        remove(period).performScrollTo().performClick()
        compose.waitForIdle()
        access.awayFails = "No space left on device"

        compose.onNodeWithText(text(R.string.action_undo)).performClick()
        compose.waitForIdle()

        val failed = text(R.string.one_off_not_saved) + " " +
            text(R.string.details_in_english, "No space left on device")
        compose.onNodeWithText(failed).assertIsDisplayed()
        assertThat(compose.liveRegionOf(failed)).isEqualTo(LiveRegionMode.Polite)
        compose.onNode(hasContentDescription(text(R.string.action_close)) and hasClickAction()).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.one_off_not_removed), substring = true).assertDoesNotExist()
        line(period).assertDoesNotExist()
        assertThat(access.away).isEmpty()
    }

    @Test
    fun anEntryClosedWhileThePlanIsStillAskedPutsTheFocusOnTheTitle() {
        AwayEntry(handle, access, scope, clock) { }.open(today, lisbon)
        show(kept = handle.afterTheSystemEndedTheApp(), planned = false)
        entryDialog = ShadowDialog.getLatestDialog() as ComponentDialog
        windowFocused(entryDialog, true)
        val title = compose.onNode(isHeading() and hasText(text(R.string.app_name)) and !hasAnyAncestor(isDialog()))
        // the title took the focus when the home opened; the focus is off it, so the entry's close has to bring it back
        compose.runOnIdle { focus.clearFocus() }
        title.assertIsNotFocused()

        click(R.string.action_cancel)

        add.assertDoesNotExist()
        title.assertIsFocused()
    }

    @Test
    fun theTimePickerFollowsThePhonesTwelveOrTwentyFourHourClock() {
        show()
        clock("12")
        openTheEntry()
        partOfTheDay()
        timeButton(R.string.one_off_from).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("AM").assertExists()
        compose.onNodeWithText("PM").assertExists()
        click(R.string.action_ok)
    }

    @Test
    fun onATwentyFourHourClockTheTimePickerHasNoAmPm() {
        show()
        openTheEntry()
        partOfTheDay()
        timeButton(R.string.one_off_from).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.action_ok)).assertExists()
        compose.onNodeWithText("AM").assertDoesNotExist()
        compose.onNodeWithText("PM").assertDoesNotExist()
    }

    @Test
    fun whilePartOfTheDayIsSavedTheChoicesAndBothTimesAreOff() {
        val gate = CompletableDeferred<Unit>()
        access.awayGate = gate
        show()
        openTheEntry()
        partOfTheDay()

        save.performClick()
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.one_off_whole_day)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.one_off_part_of_day)).assertIsNotEnabled()
        timeButton(R.string.one_off_from).assertIsNotEnabled()
        timeButton(R.string.one_off_until).assertIsNotEnabled()
        gate.complete(Unit)
        compose.waitForIdle()
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp", fontScale = 2.0f)
    fun onASmallPhoneAtTwiceTheFontSizeTheTimePickersToggleOkAndCancelAreShownNamedAndLargeEnough() {
        show(wrap = { AtFontScaleTwo(it) })
        openTheEntry()
        partOfTheDay()
        timeButton(R.string.one_off_from).performClick()
        compose.waitForIdle()

        listOf(R.string.one_off_keyboard, R.string.action_ok, R.string.action_cancel).forEach { id ->
            val buttons = compose.onAllNodes(hasText(text(id)) and hasClickAction())
            val nodes = buttons.fetchSemanticsNodes()
            assertThat(nodes).`as`(text(id)).isNotEmpty()
            nodes.indices.forEach { index ->
                buttons[index].assertIsDisplayed()
                val node = nodes[index]
                assertThat(node.config[SemanticsProperties.Role]).isEqualTo(Role.Button)
                val dp = node.layoutInfo.density.density
                assertThat(minOf(node.size.width, node.size.height) / dp).`as`(text(id)).isGreaterThanOrEqualTo(47.5f)
            }
        }
    }

    @Test
    fun addIsSpokenAsAddAwayPeriodWhichStartsWithTheWordOnTheButton() {
        show()

        add.performScrollTo().assert(hasContentDescription(text(R.string.one_off_add_spoken)))
        assertThat(text(R.string.one_off_add_spoken)).startsWith(text(R.string.one_off_add))
    }

    @Test
    fun theTimePickersToggleSwitchesBetweenTheDialAndTypingTheTime() {
        show()
        openTheEntry()
        partOfTheDay()
        timeButton(R.string.one_off_from).performClick()
        compose.waitForIdle()
        val dial = hasContentDescription("10 hours") and hasClickAction()
        val fields = hasSetTextAction() and hasAnyAncestor(isDialog())
        compose.onNode(dial).assertExists()
        compose.onAllNodes(fields).assertCountEquals(0)
        compose.onNodeWithText(text(R.string.one_off_clock)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.one_off_keyboard))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))

        click(R.string.one_off_keyboard)

        compose.onNode(dial).assertDoesNotExist()
        compose.onAllNodes(fields).assertCountEquals(2)
        compose.onNodeWithText(text(R.string.one_off_keyboard)).assertDoesNotExist()

        click(R.string.one_off_clock)

        compose.onNode(dial).assertExists()
        compose.onAllNodes(fields).assertCountEquals(0)
    }

    @Test
    fun aTimeTypedInThePickerAndConfirmedWithOkIsTheTime() {
        show()
        openTheEntry()
        partOfTheDay()
        timeButton(R.string.one_off_until).performClick()
        compose.waitForIdle()
        click(R.string.one_off_keyboard)

        val fields = compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog()))
        fields[0].performTextReplacement("18")
        fields[1].performTextReplacement("45")
        compose.waitForIdle()
        click(R.string.action_ok)

        assertThat(away.draft.value?.end).isEqualTo(LocalTime.of(18, 45))
    }

    @Test
    fun savingBeforeAnyPlanWasShownStillNamesThePeriodInTheSnackbar() {
        AwayEntry(handle, access, scope, clock) { }.open(today, lisbon)
        show(kept = handle.afterTheSystemEndedTheApp(), planned = false)
        entryDialog = ShadowDialog.getLatestDialog() as ComponentDialog
        windowFocused(entryDialog, true)

        save.performClick()
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.one_off_saved, text(R.string.one_off_all_day, "Today")))
            .assertIsDisplayed()
    }

    @Test
    fun afterAFailedRemoveAReplanThatDropsThePeriodDoesNotMoveTheFocusToItsNeighbour() {
        val ending = OneOffAwayPeriod.on(today, LocalTime.of(10, 0), LocalTime.of(12, 0), lisbon)
        access.away = listOf(ending, tomorrow)
        access.awayFails = "No space left on device"
        show()
        val first = text(R.string.one_off_part, "Today", text(R.string.time_span, "10:00", "12:00"))
        val neighbour = text(R.string.one_off_all_day, "Tomorrow")
        remove(first).performScrollTo().performClick()
        compose.waitForIdle()
        line(first).assertExists()

        // the plan is made again after 12:00, so the period that did not go has ended, as nobody pressed a button
        compose.onNode(hasContentDescription(text(R.string.home_refresh))).performClick()
        compose.waitForIdle()
        access.outlooks.last().complete(
            Plans.planned(today.atTime(13, 0).atZone(lisbon), listOf(listOf(), listOf(), listOf())))
        compose.waitForIdle()

        line(first).assertDoesNotExist()
        line(neighbour).assertIsNotFocused()
    }

    @Test
    fun afterUndoTheFocusGoesToTheRestoredRow() {
        access.away = listOf(tomorrow, wednesday)
        show()
        val first = text(R.string.one_off_all_day, "Tomorrow")
        val second = text(R.string.one_off_part, "Wed, Jun 3", text(R.string.time_span, "10:00", "12:00"))
        remove(first).performScrollTo().performClick()
        compose.waitForIdle()
        line(second).assertIsFocused()

        compose.onNodeWithText(text(R.string.action_undo)).performClick()
        compose.waitForIdle()

        line(first).assertExists().assertIsFocused()
    }

    @Test
    fun afterAnUndoThatFailedTheFocusStaysWhereItWasAndALaterRestoreDoesNotMoveIt() {
        access.away = listOf(tomorrow, wednesday)
        show()
        val first = text(R.string.one_off_all_day, "Tomorrow")
        val second = text(R.string.one_off_part, "Wed, Jun 3", text(R.string.time_span, "10:00", "12:00"))
        remove(first).performScrollTo().performClick()
        compose.waitForIdle()
        line(second).assertIsFocused()
        access.awayFails = "No space left on device"
        compose.onNodeWithText(text(R.string.action_undo)).performClick()
        compose.waitForIdle()
        access.awayFails = null

        // the period comes back by another way, with a new plan
        access.away = listOf(tomorrow, wednesday)
        compose.onNode(hasContentDescription(text(R.string.home_refresh))).performClick()
        compose.waitForIdle()
        access.outlooks.last().complete(week())
        compose.waitForIdle()

        line(first).assertExists().assertIsNotFocused()
        line(second).assertIsFocused()
    }
}
