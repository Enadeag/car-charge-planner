package nl.enadeag.carchargeplanner.android.ui

import android.provider.Settings
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
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
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.toSize
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
import nl.enadeag.carchargeplanner.android.home.TripChoice
import nl.enadeag.carchargeplanner.android.home.TripEntry
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.store.Saves
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.android.ui.components.DISTANCE_UNIT
import nl.enadeag.carchargeplanner.android.ui.home.HomeScreen
import nl.enadeag.carchargeplanner.android.ui.components.clockOf
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.trip.Trip
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowViewRootImpl

/** The entry of a trip, Add and Change on the Trips card, on Robolectric. */
@RunWith(AndroidJUnit4::class)
class TripEntryScreenTest {

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
    private val noCard by lazy { allowedCard(scope) }
    private val saves = Saves(access, scope)
    private val handle = SavedStateHandle()
    private lateinit var trips: TripEntry
    private lateinit var entryDialog: ComponentDialog

    /** From tomorrow 07:30 to 18:00, 120.6 km, so it lists as 121 km. */
    private val tomorrow = trip(today.plusDays(1), "07:30", today.plusDays(1), "18:00", 120.6)

    @After
    fun stop() {
        scope.cancel()
    }

    private fun trip(from: LocalDate, departure: String, to: LocalDate, back: String, km: Double,
        canCharge: Boolean = false) =
        Trip(from.atTime(LocalTime.parse(departure)).atZone(lisbon).toInstant(),
            to.atTime(LocalTime.parse(back)).atZone(lisbon).toInstant(), km, canCharge, false)

    /** Monday 1 June in Lisbon, and two more days. */
    private fun week() = Plans.planned(today.atTime(9, 0).atZone(lisbon), listOf(listOf(), listOf(), listOf()))

    /**
     * Shows the home.
     *
     * @param kept the saved state the entry comes back from
     * @param planned false to leave the plan still asked
     * @param hours the phone's clock, "24" or "12"
     * @param phone the phone's time zone
     * @param wrap what the home is shown in, such as twice the font size
     */
    private fun show(kept: SavedStateHandle = handle, planned: Boolean = true, hours: String = "24",
        phone: ZoneId = lisbon, wrap: @Composable (@Composable () -> Unit) -> Unit = { it() }) {
        Settings.System.putString(compose.activity.contentResolver, Settings.System.TIME_12_24, hours)
        val outlook = HomeOutlook(access, scope, clock) { }
        val battery = BatteryEntry(kept, NumberText(Locale.ENGLISH), access, scope) { }
        val away = AwayEntry(kept, access, scope, clock, outlook::awayChanged)
        trips = TripEntry(kept, NumberText(Locale.ENGLISH), access, scope, clock, outlook::tripsChanged)
        val notice = HomeNotice(saves, scope) { }
        compose.setContent {
            AppTheme { wrap { HomeScreen(outlook, notice, battery, away, trips, { phone }, noCard) { } } }
        }
        compose.waitForIdle()
        if (planned) {
            access.outlooks.last().complete(week())
            compose.waitForIdle()
        }
    }

    private val add get() = compose.onNode(hasContentDescription(text(R.string.trip_add_spoken)))

    private val save get() = compose.onNode(hasText(text(R.string.action_save)) and hasAnyAncestor(isDialog()))

    private val close get() = compose.onNode(hasContentDescription(text(R.string.action_close))
        and hasAnyAncestor(isDialog()))

    private fun button(name: Int) = compose.onNode(hasText(text(name)) and hasAnyAncestor(isDialog())
        and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))

    private val distance get() = compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog()))

    private fun spoken(trip: String, start: String = "07:30", end: String = "18:00") = text(R.string.trip_line_spoken,
        text(R.string.time_span_spoken, start, end), text(R.string.trip_distance, trip))

    private fun change(spokenTrip: String) =
        compose.onNode(hasContentDescription(text(R.string.trip_change_spoken, spokenTrip)))

    private fun opened() {
        compose.waitForIdle()
        entryDialog = ShadowDialog.getLatestDialog() as ComponentDialog
        windowFocused(entryDialog, true)
    }

    private fun openAdd() {
        add.performScrollTo().performClick()
        opened()
    }

    /** Gives a dialog's window the focus, or takes it, as Android does as dialogs show and go; Robolectric does not. */
    private fun windowFocused(dialog: ComponentDialog, focused: Boolean) {
        compose.runOnIdle {
            Shadow.extract<ShadowViewRootImpl>(dialog.window!!.decorView.parent).callWindowFocusChanged(focused)
        }
        compose.waitForIdle()
    }

    private fun backInTheDialog() {
        compose.runOnIdle { entryDialog.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun typeDistance(km: String) {
        distance.performTextReplacement(km)
        compose.waitForIdle()
    }

    private fun click(node: androidx.compose.ui.test.SemanticsNodeInteraction) {
        node.performClick()
        compose.waitForIdle()
    }

    /**
     * Scrolls every page the control is on, inner to outer, so it shows whole, as the owner scrolls a page sideways
     * and then up or down; `performScrollTo` scrolls only the innermost.
     */
    private fun scrolledIntoView(control: androidx.compose.ui.test.SemanticsNodeInteraction) {
        var page = control.fetchSemanticsNode().parent
        while (page != null) {
            val scroll = page.config.getOrNull(SemanticsActions.ScrollBy)?.action
            if (scroll != null) {
                val node = control.fetchSemanticsNode()
                val shown = Rect(page.positionInRoot, page.size.toSize())
                val target = Rect(node.positionInRoot, node.size.toSize())
                val dx = when {
                    target.left < shown.left -> target.left - shown.left
                    target.right > shown.right -> target.right - shown.right
                    else -> 0f
                }
                val dy = when {
                    target.top < shown.top -> target.top - shown.top
                    target.bottom > shown.bottom -> target.bottom - shown.bottom
                    else -> 0f
                }
                if (dx != 0f || dy != 0f) {
                    compose.runOnIdle { scroll(dx, dy) }
                    compose.waitForIdle()
                }
            }
            page = page.parent
        }
    }

    private fun twentyFour(time: LocalTime): String = clockOf(true, Locale.ENGLISH).format(time)

    @Test
    fun addOpensAFullScreenDialogWithItsTitleAsHeadingCloseAndSaveAndTheFocusOnTheDepartureDay() {
        show()

        openAdd()

        compose.onNode(isHeading() and hasText(text(R.string.trip_add_title)) and hasAnyAncestor(isDialog()))
            .assertIsDisplayed()
        close.assertIsDisplayed().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        save.assertIsDisplayed()
        button(R.string.trip_departure_day).assertIsFocused().assert(hasText("Today"))
        button(R.string.trip_departure_time).assert(hasText(twentyFour(LocalTime.of(8, 0))))
        button(R.string.trip_return_day).assert(hasText("Today"))
        button(R.string.trip_return_time).assert(hasText(twentyFour(LocalTime.of(18, 0))))
        // named by its label, as every field of the app, with no spoken name over what is typed
        distance.assert(hasText(text(R.string.trip_distance_field)))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        compose.onNode(hasText(text(R.string.trip_can_charge)) and hasAnyAncestor(isDialog()))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        compose.onNodeWithText(text(R.string.home_zone, lisbon.id)).assertDoesNotExist()
        val view = entryDialog.window!!.decorView
        assertThat(view.width).isEqualTo(compose.activity.window.decorView.width)
    }

    @Test
    fun addAndChangeAreSpokenStartingWithTheWordOnTheButton() {
        access.trips = listOf(tomorrow)
        show()
        add.performScrollTo().assert(hasText(text(R.string.trip_add)))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        assertThat(text(R.string.trip_add_spoken)).startsWith(text(R.string.trip_add))

        compose.onNode(hasContentDescription("Tomorrow") and hasClickAction()).performClick()
        compose.waitForIdle()
        val spokenTrip = spoken("121")
        change(spokenTrip).performScrollTo().assert(hasText(text(R.string.trip_change)))
        assertThat(text(R.string.trip_change_spoken, spokenTrip)).startsWith(text(R.string.trip_change))
    }

    @Test
    fun aNewTripSavedClosesTheEntrySaysSoListsItAtOnceAndPutsTheFocusOnAdd() {
        show()
        openAdd()

        typeDistance("120")
        click(save)

        val saved = trip(today, "08:00", today, "18:00", 120.0)
        assertThat(access.trips).containsExactly(saved)
        compose.onNode(isDialog()).assertDoesNotExist()
        val line = text(R.string.trip_line, text(R.string.time_span, "08:00", "18:00"),
            text(R.string.trip_distance, "120"))
        compose.onNodeWithText(text(R.string.trip_saved, line)).assertIsDisplayed()
        assertThat(compose.liveRegionOf(text(R.string.trip_saved, line))).isEqualTo(LiveRegionMode.Polite)
        compose.onNodeWithText(line).assertExists()
        assertThat(access.outlooks).hasSize(2)
        add.assertIsFocused()
    }

    @Test
    fun theDistanceFieldShowsKmAfterWhatIsTypedAndItsDoneSaves() {
        show()
        openAdd()
        typeDistance("80")
        compose.onNode(hasText(DISTANCE_UNIT) and hasAnyAncestor(isDialog())).assertIsDisplayed()

        distance.performImeAction()
        compose.waitForIdle()

        assertThat(access.trips.single().distanceKm()).isEqualTo(80.0)
    }

    @Test
    fun changeOpensOnTheTripAndItsSaveKeepsTheExactDistanceAndPutsTheFocusOnItsChange() {
        access.trips = listOf(tomorrow)
        show()
        compose.onNode(hasContentDescription("Tomorrow") and hasClickAction()).performClick()
        compose.waitForIdle()
        change(spoken("121")).performScrollTo().performClick()
        opened()

        compose.onNode(isHeading() and hasText(text(R.string.trip_change_title))).assertIsDisplayed()
        button(R.string.trip_departure_day).assert(hasText("Tomorrow"))
        button(R.string.trip_departure_time).assert(hasText(twentyFour(LocalTime.of(7, 30))))
        distance.assert(hasText("121"))
        compose.runOnIdle { trips.returnTime(LocalTime.of(19, 0)) }
        click(save)

        val changed = trip(today.plusDays(1), "07:30", today.plusDays(1), "19:00", 120.6)
        assertThat(access.trips).containsExactly(changed)
        change(spoken("121", end = "19:00")).assertIsFocused()
    }

    @Test
    fun aChangeThatMovesTheTripOffThePageInViewPutsTheFocusOnThatPagesAdd() {
        access.trips = listOf(tomorrow)
        show()
        compose.onNode(hasContentDescription("Tomorrow") and hasClickAction()).performClick()
        compose.waitForIdle()
        change(spoken("121")).performScrollTo().performClick()
        opened()
        compose.runOnIdle {
            trips.departureDay(today.plusDays(2))
            trips.returnDay(today.plusDays(2))
        }

        click(save)

        assertThat(access.trips).containsExactly(trip(today.plusDays(2), "07:30", today.plusDays(2), "18:00", 120.6))
        compose.onNode(isDialog()).assertDoesNotExist()
        change(spoken("121")).assertDoesNotExist()
        add.assertIsFocused()
    }

    @Test
    fun aTripSavedBeforeAnyPlanIsNamedAsTheEntryReadItAndTheFocusGoesToTheTitle() {
        TripEntry(handle, NumberText(Locale.ENGLISH), access, scope, clock) { }.apply {
            add(today, lisbon)
            distance("75")
        }
        show(kept = handle.afterTheSystemEndedTheApp(), planned = false)
        entryDialog = ShadowDialog.getLatestDialog() as ComponentDialog

        click(save)

        assertThat(access.trips).containsExactly(trip(today, "08:00", today, "18:00", 75.0))
        compose.onNode(isDialog()).assertDoesNotExist()
        val line = text(R.string.trip_line, text(R.string.time_span, "08:00", "18:00"),
            text(R.string.trip_distance, "75"))
        compose.onNodeWithText(text(R.string.trip_saved, line)).assertIsDisplayed()
        add.assertDoesNotExist()
        compose.onNode(isHeading() and hasText(text(R.string.app_name))).assertIsFocused()
    }

    @Test
    fun aChangeClosedWithoutSavingPutsTheFocusBackOnItsChange() {
        access.trips = listOf(tomorrow)
        show()
        compose.onNode(hasContentDescription("Tomorrow") and hasClickAction()).performClick()
        compose.waitForIdle()
        change(spoken("121")).performScrollTo().performClick()
        opened()

        click(close)

        compose.onNode(isDialog()).assertDoesNotExist()
        change(spoken("121")).assertIsFocused()
    }

    @Test
    fun onATwelveHourClockTheTimesShowAsThePhoneWritesThemAndADayAfterTomorrowByItsShortDate() {
        show(hours = "12")
        compose.onNode(hasContentDescription("Wednesday") and hasClickAction()).performClick()
        compose.waitForIdle()
        openAdd()

        button(R.string.trip_departure_day).assert(hasText("Wed, Jun 3"))
        button(R.string.trip_departure_time).assert(hasText(clockOf(false, Locale.ENGLISH).format(LocalTime.of(8, 0))))
    }

    @Test
    fun aTimesButtonOpensTheTimePickerWhoseOkPutsTheTimeAndTheFocusBackOnTheButton() {
        show()
        openAdd()

        click(button(R.string.trip_return_time))
        windowFocused(entryDialog, false)
        compose.onNode(isHeading() and hasText(text(R.string.trip_return_time)) and hasAnyAncestor(isDialog()))
            .assertIsDisplayed()
        compose.onNode(hasContentDescription("10 hours") and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        click(compose.onNodeWithText(text(R.string.action_ok)))
        windowFocused(entryDialog, true)

        assertThat(trips.draft.value?.fields?.returnTime).isEqualTo(LocalTime.of(10, 0))
        button(R.string.trip_return_time).assert(hasText(twentyFour(LocalTime.of(10, 0)))).assertIsFocused()
    }

    @Test
    fun aDaysButtonOpensTheDatePickerWithAKeyboardButtonInWordsAndItsOkPutsTheDayChosen() {
        show()
        openAdd()

        click(button(R.string.trip_return_day))
        windowFocused(entryDialog, false)
        compose.onNode(isHeading() and hasText(text(R.string.trip_return_day)) and hasAnyAncestor(isDialog()))
            .assertIsDisplayed()
        compose.onNodeWithText(text(R.string.one_off_keyboard))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        compose.onNodeWithText(text(R.string.picker_calendar)).assertDoesNotExist()
        compose.onNode(hasText("June 3, 2026", substring = true) and hasClickAction()).performClick()
        compose.waitForIdle()
        click(compose.onNodeWithText(text(R.string.action_ok)))
        windowFocused(entryDialog, true)

        assertThat(trips.draft.value?.fields?.returnDay).isEqualTo(today.plusDays(2))
        assertThat(trips.draft.value?.fields?.departureDay).isEqualTo(today)
        button(R.string.trip_return_day).assert(hasText("Wed, Jun 3")).assertIsFocused()
    }

    @Test
    fun theDatePickerOffersOnlyTheWindowsDaysAndItsToggleSwitchesToTypingAndBack() {
        show()
        openAdd()
        click(button(R.string.trip_departure_day))

        compose.onNode(hasText("June 22, 2026", substring = true) and hasClickAction()).assertIsNotEnabled()
        compose.onNode(hasText("June 21, 2026", substring = true) and hasClickAction()).assertIsEnabled()
        click(compose.onNodeWithText(text(R.string.one_off_keyboard)).performScrollTo())
        compose.onNode(hasText("June 21, 2026", substring = true) and hasClickAction()).assertDoesNotExist()
        click(compose.onNodeWithText(text(R.string.picker_calendar)).performScrollTo())
        compose.onNodeWithText(text(R.string.one_off_keyboard)).assertExists()
    }

    @Test
    fun theDatePickersYearListOffersOnlyTheWindowsYear() {
        show()
        openAdd()
        click(button(R.string.trip_departure_day))

        click(compose.onNode(hasContentDescription("Switch to selecting a year") and hasClickAction()))

        val years = compose.onAllNodes(hasText("Navigate to year", substring = true) and hasClickAction())
            .fetchSemanticsNodes().flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
        assertThat(years).containsExactly("Navigate to year 2026")
    }

    @Test
    fun okWithoutADayAsAfterTheTypedDateWasClearedOrADayOutsideTheWindowTypedKeepsTheDay() {
        show()
        openAdd()
        val typed = compose.onNode(hasSetTextAction() and hasText("Date"))

        click(button(R.string.trip_departure_day))
        click(compose.onNodeWithText(text(R.string.one_off_keyboard)).performScrollTo())
        typed.performTextReplacement("")
        click(compose.onNodeWithText(text(R.string.action_ok)))
        compose.onNodeWithText(text(R.string.action_ok)).assertDoesNotExist()
        assertThat(trips.draft.value?.fields?.departureDay).isEqualTo(today)

        click(button(R.string.trip_departure_day))
        click(compose.onNodeWithText(text(R.string.one_off_keyboard)).performScrollTo())
        typed.performTextReplacement("06302026")
        click(compose.onNodeWithText(text(R.string.action_ok)))
        assertThat(trips.draft.value?.fields?.departureDay).isEqualTo(today)
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp", fontScale = 2.0f)
    fun onASmallPhoneAtTwiceTheFontSizeTheDatePickerReachesEveryControlWithinTheScreen() {
        show(wrap = { AtFontScaleTwo(it) })
        openAdd()
        click(button(R.string.trip_departure_day).performScrollTo())
        val screen = compose.activity.window.decorView
        val reachable = { matcher: SemanticsMatcher ->
            val control = compose.onNode(matcher)
            scrolledIntoView(control)
            // where it is drawn whole, not clipped to the window, which would hide a part off the screen
            val node = control.assertIsDisplayed().fetchSemanticsNode()
            val bounds = Rect(node.positionInWindow, node.size.toSize())
            assertThat(bounds.left).`as`("the left of %s", matcher.description).isGreaterThanOrEqualTo(-0.5f)
            assertThat(bounds.right).`as`("the right of %s", matcher.description)
                .isLessThanOrEqualTo(screen.width + 0.5f)
            assertThat(bounds.top).`as`("the top of %s", matcher.description).isGreaterThanOrEqualTo(-0.5f)
            assertThat(bounds.bottom).`as`("the bottom of %s", matcher.description)
                .isLessThanOrEqualTo(screen.height + 0.5f)
            control
        }

        // a Sunday and a Saturday, the calendar's first and last columns, the month's arrows, the year's button, the
        // toggle, Cancel and OK
        val sunday = reachable(hasText("June 7, 2026", substring = true) and hasClickAction())
        // the days keep Material's 48 dp to a column, so the picker scrolls rather than squeezes them
        val saturday = compose.onNode(hasText("June 13, 2026", substring = true) and hasClickAction())
            .fetchSemanticsNode()
        val apart = saturday.positionInRoot.x - sunday.fetchSemanticsNode().positionInRoot.x
        assertThat(apart / saturday.layoutInfo.density.density).isGreaterThanOrEqualTo(6 * 48f - 0.5f)
        reachable(hasContentDescription("Change to previous month") and hasClickAction())
        reachable(hasContentDescription("Change to next month") and hasClickAction())
        reachable(hasContentDescription("Switch to selecting a year") and hasClickAction())
        reachable(hasText(text(R.string.one_off_keyboard)) and hasClickAction())
        reachable(hasText(text(R.string.action_cancel)) and hasClickAction())
        reachable(hasText(text(R.string.action_ok)) and hasClickAction())
        click(reachable(hasText("June 13, 2026", substring = true) and hasClickAction()))
        click(reachable(hasText(text(R.string.action_ok)) and hasClickAction()))

        assertThat(trips.draft.value?.fields?.departureDay).isEqualTo(LocalDate.of(2026, 6, 13))
    }

    @Test
    fun aTapBesideTheEntryDoesNotCloseIt() {
        show()
        openAdd()

        compose.runOnIdle {
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
                val tap = MotionEvent.obtain(0L, 0L, action, -10f, -10f, 0)
                entryDialog.onTouchEvent(tap)
                tap.recycle()
            }
        }
        compose.waitForIdle()

        compose.onNode(isHeading() and hasText(text(R.string.trip_add_title))).assertIsDisplayed()
        assertThat(trips.draft.value).isNotNull()
    }

    @Test
    fun aReturnBeforeTheDepartureIsRefusedUnderTheReturnPolitelyTheReturnCarriesItAndTakesTheFocus() {
        show()
        openAdd()
        typeDistance("120")
        compose.runOnIdle { trips.returnTime(LocalTime.of(7, 0)) }

        click(save)

        val problem = text(R.string.trip_return_before_departure)
        compose.onNodeWithText(problem).assertIsDisplayed()
        assertThat(compose.liveRegionOf(problem)).isEqualTo(LiveRegionMode.Polite)
        button(R.string.trip_return_day).assertIsFocused()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, problem))
        button(R.string.trip_return_time).assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, problem))
        assertThat(access.tripChanges).isEmpty()

        compose.runOnIdle { trips.returnTime(LocalTime.of(9, 30)) }
        compose.onNodeWithText(problem).assertDoesNotExist()
    }

    @Test
    fun aDistanceThatIsNoNumberIsRefusedUnderTheFieldWhichCarriesItAndTakesTheFocus() {
        show()
        openAdd()
        typeDistance("abc")

        click(save)

        val problem = text(R.string.trip_distance_not_a_number)
        compose.onNodeWithText(problem).assertIsDisplayed()
        assertThat(compose.liveRegionOf(problem)).isEqualTo(LiveRegionMode.Polite)
        distance.assertIsFocused().assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, problem))
    }

    @Test
    fun aSaveThatFailedSaysSoAtTheTopAtOnceWithTheEnglishDetailsAndPutsTheFocusOnTheTitle() {
        access.tripFails = "No space left on device"
        show()
        openAdd()
        typeDistance("120")

        click(save)

        compose.onNodeWithText(text(R.string.trip_not_saved)).assertIsDisplayed()
        assertThat(compose.liveRegionOf(text(R.string.trip_not_saved))).isEqualTo(LiveRegionMode.Assertive)
        compose.onNodeWithText(text(R.string.details_in_english, "No space left on device")).assertIsDisplayed()
        compose.onNode(isHeading() and hasText(text(R.string.trip_add_title))).assertIsFocused()
        distance.assert(hasText("120"))
    }

    @Test
    fun whileTheTripIsSavedSaveShowsItsProgressIsHeldAndCloseIsOff() {
        val gate = CompletableDeferred<Unit>()
        access.tripGate = gate
        show()
        openAdd()
        typeDistance("120")

        click(save)

        compose.onNode(hasText(text(R.string.saving)) and hasAnyAncestor(isDialog()))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
        close.assertIsNotEnabled()
        button(R.string.trip_departure_day).assertIsNotEnabled()
        gate.complete(Unit)
        compose.waitForIdle()
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun whileAnotherChangeOfTheTripsRunsSaveIsHeldKeepsTheFocusAndAPressDoesNothing() {
        access.trips = listOf(tomorrow)
        val gate = CompletableDeferred<Unit>()
        show()
        openAdd()
        typeDistance("120")
        access.tripGate = gate
        compose.runOnIdle { trips.choose(tomorrow, TripChoice.CAN_CHARGE, true) }
        compose.waitForIdle()

        save.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
        click(save)
        assertThat(access.tripChanges).hasSize(1)

        gate.complete(Unit)
        compose.waitForIdle()
        click(save)
        assertThat(access.tripChanges.map { it.first }).containsExactly("change", "add")
    }

    @Test
    fun backWithNothingChangedClosesAtOnceAndPutsTheFocusOnAdd() {
        show()
        openAdd()

        backInTheDialog()

        compose.onNode(isDialog()).assertDoesNotExist()
        add.assertIsFocused()
    }

    @Test
    fun backOrCloseWithAChangeAsksKeepEditingPutsTheFocusBackAndDiscardThrowsItAway() {
        show()
        openAdd()
        typeDistance("12")

        backInTheDialog()
        compose.onNodeWithText(text(R.string.discard_question)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.trip_discard_text)).assertIsDisplayed()
        click(compose.onNodeWithText(text(R.string.keep_editing)))
        windowFocused(entryDialog, true)
        distance.assertIsFocused().assert(hasText("12"))

        click(close)
        click(compose.onNodeWithText(text(R.string.discard)))

        compose.onNode(isDialog()).assertDoesNotExist()
        assertThat(access.tripChanges).isEmpty()
        add.assertIsFocused()
    }

    @Test
    fun aChangedChangeAsksTooBeforeItIsThrownAway() {
        access.trips = listOf(tomorrow)
        show()
        compose.onNode(hasContentDescription("Tomorrow") and hasClickAction()).performClick()
        compose.waitForIdle()
        change(spoken("121")).performScrollTo().performClick()
        opened()
        click(compose.onNode(hasText(text(R.string.trip_can_charge)) and hasAnyAncestor(isDialog())))

        backInTheDialog()

        compose.onNodeWithText(text(R.string.trip_discard_text)).assertIsDisplayed()
    }

    @Test
    fun whenTheHomesZoneIsNotThePhonesTheEntrySaysSoFirst() {
        show(phone = ZoneId.of("America/New_York"))
        openAdd()

        compose.onNode(hasText(text(R.string.home_zone, lisbon.id)) and hasAnyAncestor(isDialog())).assertIsDisplayed()
    }

    @Test
    fun theEntryComesBackWithWhatWasEnteredAfterTheSystemEndedTheAppAlsoBeforeAPlan() {
        TripEntry(handle, NumberText(Locale.ENGLISH), access, scope, clock) { }.apply {
            add(today.plusDays(1), lisbon)
            departureTime(LocalTime.of(10, 15))
            distance("75")
        }

        show(kept = handle.afterTheSystemEndedTheApp(), planned = false)
        entryDialog = ShadowDialog.getLatestDialog() as ComponentDialog

        compose.onNode(isHeading() and hasText(text(R.string.trip_add_title))).assertIsDisplayed()
        button(R.string.trip_departure_day).assert(hasText("Tomorrow"))
        button(R.string.trip_departure_time).assert(hasText(twentyFour(LocalTime.of(10, 15))))
        distance.assert(hasText("75"))
    }

    @Test
    fun theCardsAndTheEntrysControlsAreNamedAndLargeEnoughAtTheNormalFontSize() {
        access.trips = listOf(tomorrow)
        show()
        compose.onNode(hasContentDescription("Tomorrow") and hasClickAction()).performClick()
        compose.waitForIdle()
        compose.assertEveryControlIsNamedAndLargeEnough()

        openAdd()
        compose.assertEveryControlIsNamedAndLargeEnough()
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp", fontScale = 2.0f)
    fun onASmallPhoneAtTwiceTheFontSizeTheEntryWrapsItsButtonsAndIsNamedLargeEnoughAndReachable() {
        access.tripFails = "trips.json is not written: written by a newer version of the app (file version 2)"
        show(wrap = { AtFontScaleTwo(it) })
        add.performScrollTo()
        compose.assertEveryControlIsNamedAndLargeEnough()
        openAdd()

        val day = button(R.string.trip_departure_day).fetchSemanticsNode().boundsInRoot
        val time = button(R.string.trip_departure_time).fetchSemanticsNode().boundsInRoot
        assertThat(time.top).isGreaterThanOrEqualTo(day.bottom)
        typeDistance("120")
        click(save)
        compose.assertEveryControlIsNamedAndLargeEnough()
        compose.onNodeWithText(text(R.string.trip_not_saved)).performScrollTo().assertIsDisplayed()
        save.assertIsDisplayed()
        close.assertIsDisplayed()
        distance.performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "nl")
    fun theCardAndTheEntryAreInDutch() {
        access.trips = listOf(tomorrow)
        show()
        add.assertExists().assert(hasContentDescription("Toevoegen: rit")).assert(hasText("Toevoegen"))
        compose.onNode(hasContentDescription("Morgen") and hasClickAction()).performClick()
        compose.waitForIdle()
        compose.onNode(hasContentDescription("Wijzigen: rit ", substring = true)).performScrollTo()
            .assert(hasText("Wijzigen"))
        compose.assertShowsNoneOf(englishOnlyTexts())

        add.performScrollTo().performClick()
        opened()
        compose.onNode(isHeading() and hasText("Rit toevoegen")).assertIsDisplayed()
        button(R.string.trip_departure_day).assert(hasText("Vertrekdag", substring = true))
        compose.assertShowsNoneOf(englishOnlyTexts())
    }
}
