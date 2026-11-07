package nl.enadeag.carchargeplanner.android.ui

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Duration
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
import kotlinx.coroutines.runBlocking
import nl.enadeag.carchargeplanner.android.AppEngine
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.background.onDisk
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.home.AwayEntry
import nl.enadeag.carchargeplanner.android.home.BatteryEntry
import nl.enadeag.carchargeplanner.android.home.HomeNotice
import nl.enadeag.carchargeplanner.android.home.HomeOutlook
import nl.enadeag.carchargeplanner.android.home.Plans
import nl.enadeag.carchargeplanner.android.home.TripEntry
import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.android.store.EngineOnDispatcher
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.store.Saves
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.android.testPlatform
import nl.enadeag.carchargeplanner.android.ui.home.HomeScreen
import nl.enadeag.carchargeplanner.android.ui.home.clockOf
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.notify.Notifier
import nl.enadeag.carchargeplanner.engine.store.FilePrivacy
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles
import nl.enadeag.carchargeplanner.engine.trip.Trip
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Trips card on each day's page of the dashboard: its trips, their choices and Remove, on Robolectric. */
@RunWith(AndroidJUnit4::class)
class TripsCardScreenTest {

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
    private lateinit var inputMode: InputModeManager

    /** Under way when the plan was made, at 09:00 on Monday. */
    private val dayTrip = trip(today, "07:30", today, "18:00", 300.0)

    /** From tomorrow evening to Wednesday morning, charging at the destination. */
    private val overnight = trip(today.plusDays(1), "20:00", today.plusDays(2), "09:00", 120.4, canCharge = true)

    @After
    fun stop() {
        scope.cancel()
    }

    private fun trip(from: LocalDate, departure: String, to: LocalDate, back: String, km: Double,
        canCharge: Boolean = false, notWithThisCar: Boolean = false) =
        Trip(from.atTime(LocalTime.parse(departure)).atZone(lisbon).toInstant(),
            to.atTime(LocalTime.parse(back)).atZone(lisbon).toInstant(), km, canCharge, notWithThisCar)

    /** Monday 1 June in Lisbon, and two more days. */
    private fun week() = Plans.planned(today.atTime(9, 0).atZone(lisbon), listOf(listOf(), listOf(), listOf()))

    private fun clock(hours: String) {
        Settings.System.putString(compose.activity.contentResolver, Settings.System.TIME_12_24, hours)
    }

    /**
     * Shows the home on these reads and writes.
     *
     * @param on the engine's reads and writes, the fake's by default
     * @param answer the plan the fake gives; null leaves it asked
     * @param hours the phone's clock, "24" or "12"
     * @param wrap what the home is shown in, such as twice the font size
     */
    private fun show(on: EngineAccess = access, answer: Outlook? = week(), hours: String = "24",
        wrap: @Composable (@Composable () -> Unit) -> Unit = { it() }) {
        clock(hours)
        val outlook = HomeOutlook(on, scope, clock) { }
        val battery = BatteryEntry(SavedStateHandle(), NumberText(Locale.ENGLISH), on, scope) { }
        val away = AwayEntry(SavedStateHandle(), on, scope, clock, outlook::awayChanged)
        val trips = TripEntry(on, scope, outlook::tripsChanged)
        val notice = HomeNotice(saves, scope) { }
        compose.setContent {
            inputMode = LocalInputModeManager.current
            AppTheme { wrap { HomeScreen(outlook, notice, battery, away, trips, { lisbon }, noCard) { } } }
        }
        compose.waitForIdle()
        if (on === access && answer != null) {
            access.outlooks.last().complete(answer)
            compose.waitForIdle()
        }
    }

    private fun dayTripLine(start: String = "07:30", end: String = "18:00") =
        text(R.string.trip_line, text(R.string.time_span, start, end), text(R.string.trip_distance, "300"))

    private fun dayTripSpoken(start: String = "07:30", end: String = "18:00") =
        text(R.string.trip_line_spoken, text(R.string.time_span_spoken, start, end),
            text(R.string.trip_distance, "300"))

    private fun choice(label: Int, spoken: String) =
        compose.onNode(hasContentDescription(text(R.string.trip_choice_spoken, text(label), spoken)))

    private fun remove(spoken: String) =
        compose.onNode(hasContentDescription(text(R.string.trip_remove_spoken, spoken)))

    private fun tab(name: String) {
        compose.onNode(hasContentDescription(name) and hasClickAction()).performClick()
        compose.waitForIdle()
    }

    /** Puts the phone in keyboard mode, as a key pressed on a keyboard does, where a control can take the focus. */
    private fun keyboard() {
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        compose.waitForIdle()
    }

    /** Presses Enter on a keyboard, on the control with the focus. */
    private fun SemanticsNodeInteraction.enter() {
        performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
    }

    private fun refresh(answer: Outlook = week()) {
        compose.onNode(hasContentDescription(text(R.string.home_refresh))).performClick()
        compose.waitForIdle()
        access.outlooks.last().complete(answer)
        compose.waitForIdle()
    }

    private fun toggle(state: ToggleableState) =
        SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, state)

    @Test
    fun theTripsCardIsBelowTheBatteryCardAndListsTheTripUnderWayToday() {
        access.trips = listOf(dayTrip, overnight)
        show()

        val heading = compose.onNode(isHeading() and hasText(text(R.string.trips_heading))).performScrollTo()
            .fetchSemanticsNode().boundsInRoot
        val battery = compose.onNode(isHeading() and hasText(text(R.string.battery_heading))).fetchSemanticsNode()
            .boundsInRoot
        assertThat(heading.top).isGreaterThan(battery.bottom)
        compose.onNodeWithText(dayTripLine()).performScrollTo().assertIsDisplayed()
            .assert(hasContentDescription(dayTripSpoken()))
        remove(dayTripSpoken()).assertExists().assert(hasText(text(R.string.trip_remove)))
        compose.onNode(hasText(text(R.string.trip_distance, "120"), substring = true)).assertDoesNotExist()
    }

    @Test
    fun aTripOverTwoDaysShowsOnBothWithItsDaysAndADayWithoutOneSaysSo() {
        access.trips = listOf(overnight)
        show()
        compose.onNodeWithText(text(R.string.trips_none)).performScrollTo().assertIsDisplayed()

        val line = text(R.string.trip_line, text(R.string.trip_days, "Tomorrow", "20:00", "Wed, Jun 3", "09:00"),
            text(R.string.trip_distance, "120"))
        val spoken = text(R.string.trip_line_spoken,
            text(R.string.trip_days_spoken, "Tomorrow", "20:00", "Wed, Jun 3", "09:00"),
            text(R.string.trip_distance, "120"))
        for (day in listOf("Tomorrow", "Wednesday")) {
            tab(day)
            compose.onNodeWithText(line).assertExists().assert(hasContentDescription(spoken))
            compose.onNodeWithText(text(R.string.trips_none)).assertDoesNotExist()
        }
    }

    @Test
    fun onATwelveHourClockTheTimesShowAsThePhoneWritesThem() {
        access.trips = listOf(dayTrip)
        show(hours = "12")

        val twelve = clockOf(false, Locale.ENGLISH)
        compose.onNodeWithText(dayTripLine(twelve.format(LocalTime.of(7, 30)), twelve.format(LocalTime.of(18, 0))))
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theChoicesAreCheckboxRowsSpokenWithTheTripThatSaveAtOnce() {
        access.trips = listOf(dayTrip)
        show()
        val notWith = choice(R.string.trip_not_with_this_car, dayTripSpoken()).performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assert(toggle(ToggleableState.Off))
        choice(R.string.trip_can_charge, dayTripSpoken()).assert(toggle(ToggleableState.Off))

        notWith.performClick()
        compose.waitForIdle()

        val ticked = Trip(dayTrip.departure(), dayTrip.returnTime(), 300.0, false, true)
        assertThat(access.trips).containsExactly(ticked)
        choice(R.string.trip_not_with_this_car, dayTripSpoken()).assert(toggle(ToggleableState.On))
        choice(R.string.trip_can_charge, dayTripSpoken()).assert(toggle(ToggleableState.Off))
        assertThat(access.outlooks).hasSize(2)
        compose.onNodeWithText(text(R.string.trip_saved, dayTripLine())).assertDoesNotExist()
    }

    @Test
    fun aSavedChoiceKeepsItsRowAndFocusAlsoWhenTheNewPlanNoLongerHasTheTripBeforeIt() {
        val evening = trip(today, "19:00", today, "22:00", 300.0)
        access.trips = listOf(dayTrip, evening)
        show()
        keyboard()
        val canCharge = choice(R.string.trip_can_charge, dayTripSpoken("19:00", "22:00")).performScrollTo()
        canCharge.requestFocus()
        val before = canCharge.fetchSemanticsNode().id

        canCharge.enter()
        // the trip under way is no longer stored when the plan is made again, so the evening's row moves up
        access.trips = access.trips - dayTrip
        access.outlooks.last().complete(week())
        compose.waitForIdle()

        compose.onNodeWithText(dayTripLine()).assertDoesNotExist()
        val after = choice(R.string.trip_can_charge, dayTripSpoken("19:00", "22:00"))
            .assert(toggle(ToggleableState.On)).assertIsFocused().fetchSemanticsNode().id
        assertThat(after).isEqualTo(before)
    }

    @Test
    fun aChoiceWithTheKeyboardFocusKeepsItWhileItSavesAndAKeyPressedMeanwhileDoesNothing() {
        access.trips = listOf(dayTrip)
        val gate = CompletableDeferred<Unit>()
        access.tripGate = gate
        show()
        keyboard()
        val canCharge = choice(R.string.trip_can_charge, dayTripSpoken()).performScrollTo()
        canCharge.requestFocus()

        canCharge.enter()
        canCharge.assertIsFocused().assertIsNotEnabled().assert(toggle(ToggleableState.On))
        canCharge.enter()
        gate.complete(Unit)
        compose.waitForIdle()

        canCharge.assertIsFocused().assertIsEnabled().assert(toggle(ToggleableState.On))
        assertThat(access.tripChanges).hasSize(1)
    }

    @Test
    fun aRemoveWithTheKeyboardFocusKeepsItWhileItRunsAndAfterItFailed() {
        access.trips = listOf(dayTrip)
        access.tripFails = "No space left on device"
        val gate = CompletableDeferred<Unit>()
        access.tripGate = gate
        show()
        keyboard()
        val button = remove(dayTripSpoken()).performScrollTo()
        button.requestFocus()

        button.enter()
        button.assertIsFocused().assertIsNotEnabled()
        gate.complete(Unit)
        compose.waitForIdle()

        button.assertIsFocused().assertIsEnabled()
        compose.onNodeWithText(dayTripLine()).assertExists()
    }

    @Test
    fun aRemovePressedWhileAnotherRunsDoesNothingAndTheFocusFollowsTheFirst() {
        val evening = trip(today, "19:00", today, "22:00", 300.0)
        access.trips = listOf(dayTrip, evening)
        val gate = CompletableDeferred<Unit>()
        access.tripGate = gate
        show()

        remove(dayTripSpoken("19:00", "22:00")).performScrollTo().performClick()
        compose.waitForIdle()
        remove(dayTripSpoken()).performScrollTo().performClick()
        choice(R.string.trip_can_charge, dayTripSpoken()).performScrollTo().performClick()
        compose.waitForIdle()
        gate.complete(Unit)
        compose.waitForIdle()

        assertThat(access.tripChanges).containsExactly("remove" to evening)
        assertThat(access.trips).containsExactly(dayTrip)
        compose.onNodeWithText(dayTripLine()).assertIsFocused()
    }

    @Test
    fun whileAChoiceIsSavedItShowsTickedAndTheCardsControlsAreOff() {
        access.trips = listOf(dayTrip, overnight)
        val gate = CompletableDeferred<Unit>()
        access.tripGate = gate
        show()

        choice(R.string.trip_can_charge, dayTripSpoken()).performScrollTo().performClick()
        compose.waitForIdle()

        choice(R.string.trip_can_charge, dayTripSpoken()).assert(toggle(ToggleableState.On)).assertIsNotEnabled()
        choice(R.string.trip_not_with_this_car, dayTripSpoken()).assertIsNotEnabled()
        remove(dayTripSpoken()).assertIsNotEnabled()
        gate.complete(Unit)
        compose.waitForIdle()
        remove(dayTripSpoken()).assertIsEnabled()
    }

    @Test
    fun aChoiceThatFailedSpringsBackAndSaysNotSavedWithTheEnglishDetailsWithoutANewPlan() {
        access.trips = listOf(dayTrip)
        access.tripFails = "trips.json is not written: written by a newer version of the app (file version 2)"
        show()

        choice(R.string.trip_not_with_this_car, dayTripSpoken()).performScrollTo().performClick()
        compose.waitForIdle()

        choice(R.string.trip_not_with_this_car, dayTripSpoken()).assert(toggle(ToggleableState.Off))
        val failed = text(R.string.trip_not_saved) + " " + text(R.string.details_in_english, access.tripFails!!)
        compose.onNodeWithText(failed).assertIsDisplayed()
        assertThat(compose.liveRegionOf(failed)).isNotNull()
        assertThat(access.outlooks).hasSize(1)
    }

    @Test
    fun removeActsAtOnceAndItsSnackbarIsReadPolitelyWithUndoWhichAddsTheTripBack() {
        access.trips = listOf(dayTrip)
        show()

        remove(dayTripSpoken()).performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(access.trips).isEmpty()
        compose.onNodeWithText(dayTripLine()).assertDoesNotExist()
        val removed = text(R.string.trip_removed, dayTripLine())
        compose.onNodeWithText(removed).assertIsDisplayed()
        assertThat(compose.liveRegionOf(removed)).isEqualTo(LiveRegionMode.Polite)
        assertThat(access.outlooks).hasSize(2)
        compose.onNode(hasContentDescription(text(R.string.action_close)) and hasClickAction()).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_undo)).performClick()
        compose.waitForIdle()
        assertThat(access.trips).containsExactly(dayTrip)
        compose.onNodeWithText(dayTripLine()).assertExists()
        compose.onNodeWithText(text(R.string.trip_saved, dayTripLine())).assertIsDisplayed()
        compose.onNodeWithText(dayTripLine()).assertIsFocused()
    }

    @Test
    fun afterRemoveTheFocusGoesToTheTripBeforeOrTheLineThatSaysThereIsNone() {
        val evening = trip(today, "19:00", today, "22:00", 300.0)
        access.trips = listOf(dayTrip, evening)
        show()
        val eveningSpoken = dayTripSpoken("19:00", "22:00")

        remove(eveningSpoken).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText(dayTripLine()).assertIsFocused()

        // the snackbar covers the bottom of the page, so the second Remove is pressed as TalkBack does
        remove(dayTripSpoken()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.trips_none)).assertIsFocused()
    }

    @Test
    fun aRemoveThatFailedSaysSoWithTheEnglishDetailsAndTheTripStaysWithoutANewPlan() {
        access.trips = listOf(dayTrip)
        access.tripFails = "No space left on device"
        show()

        remove(dayTripSpoken()).performScrollTo().performClick()
        compose.waitForIdle()

        val failed = text(R.string.trip_not_removed) + " " +
            text(R.string.details_in_english, "No space left on device")
        compose.onNodeWithText(failed).assertIsDisplayed()
        compose.onNodeWithText(dayTripLine()).assertExists()
        assertThat(access.outlooks).hasSize(1)
    }

    @Test
    fun afterAFailedRemoveTheTripGoingByANewPlanDoesNotMoveTheFocusToItsNeighbour() {
        val evening = trip(today, "19:00", today, "22:00", 300.0)
        access.trips = listOf(dayTrip, evening)
        access.tripFails = "No space left on device"
        show()
        remove(dayTripSpoken("19:00", "22:00")).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText(dayTripLine("19:00", "22:00")).assertExists()

        // the trip goes another way, such as the desktop's file copied over, and the plan is made again
        access.trips = listOf(dayTrip)
        refresh()

        compose.onNodeWithText(dayTripLine("19:00", "22:00")).assertDoesNotExist()
        compose.onNodeWithText(dayTripLine()).assertIsNotFocused()
    }

    @Test
    fun afterAnUndoThatFailedTheFocusStaysWhereItWasAndALaterReturnDoesNotMoveIt() {
        val evening = trip(today, "19:00", today, "22:00", 300.0)
        access.trips = listOf(dayTrip, evening)
        show()
        remove(dayTripSpoken()).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText(dayTripLine("19:00", "22:00")).assertIsFocused()
        access.tripFails = "No space left on device"
        compose.onNodeWithText(text(R.string.action_undo)).performClick()
        compose.waitForIdle()
        access.tripFails = null

        // the trip comes back another way, with a new plan
        access.trips = listOf(dayTrip, evening)
        refresh()

        compose.onNodeWithText(dayTripLine()).assertExists().assertIsNotFocused()
        compose.onNodeWithText(dayTripLine("19:00", "22:00")).assertIsFocused()
    }

    @Test
    fun aRemovedTripsSnackbarIsSaidOnceAndNotAgainWhenTheHomeIsShownAgain() {
        access.trips = listOf(dayTrip)
        val shown = mutableStateOf(true)
        show(wrap = { if (shown.value) it() })
        remove(dayTripSpoken()).performScrollTo().performClick()
        compose.waitForIdle()
        val removed = text(R.string.trip_removed, dayTripLine())
        compose.onNodeWithText(removed).assertIsDisplayed()

        // as when the phone turns: the screen is drawn anew while the changes live on
        shown.value = false
        compose.waitForIdle()
        shown.value = true
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.trips_heading)).assertExists()
        compose.onNodeWithText(removed).assertDoesNotExist()
    }

    @Test
    fun anUndoThatFailedSaysNotSavedAndTheTripStaysGone() {
        access.trips = listOf(dayTrip)
        show()
        remove(dayTripSpoken()).performScrollTo().performClick()
        compose.waitForIdle()
        access.tripFails = "No space left on device"

        compose.onNodeWithText(text(R.string.action_undo)).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.trip_not_saved) + " " +
            text(R.string.details_in_english, "No space left on device")).assertIsDisplayed()
        compose.onNodeWithText(dayTripLine()).assertDoesNotExist()
    }

    @Test
    fun withoutAPlanThereIsNoTripsCard() {
        access.trips = listOf(dayTrip)
        show(answer = Outlook.NoForecast("Open-Meteo: no network"))

        compose.onNodeWithText(text(R.string.trips_heading)).assertDoesNotExist()
        compose.onNodeWithText(dayTripLine()).assertDoesNotExist()
    }

    @Test
    fun theCardsControlsAreNamedAndLargeEnoughAtTheNormalFontSize() {
        access.trips = listOf(dayTrip)
        show()

        compose.assertEveryControlIsNamedAndLargeEnough()
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp", fontScale = 2.0f)
    fun onASmallPhoneAtTwiceTheFontSizeTheChoicesWrapAndEveryControlIsNamedLargeEnoughAndReachable() {
        access.trips = listOf(dayTrip)
        show(wrap = { AtFontScaleTwo(it) })

        compose.assertEveryControlIsNamedAndLargeEnough()
        val row = choice(R.string.trip_can_charge, dayTripSpoken()).performScrollTo().fetchSemanticsNode()
        val words = compose.onNode(hasText(text(R.string.trip_can_charge)), useUnmergedTree = true)
            .fetchSemanticsNode()
        assertThat(words.boundsInRoot.right).isLessThanOrEqualTo(row.boundsInRoot.right + 0.5f)
        assertThat(words.boundsInRoot.bottom).isLessThanOrEqualTo(row.boundsInRoot.bottom + 0.5f)
    }

    @Test
    @Config(qualifiers = "nl")
    fun theCardIsInDutch() {
        access.trips = listOf(dayTrip)
        show()

        compose.onNode(isHeading() and hasText("Ritten")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Afwezig 07:30 – 18:00 · 300 km heen en terug").assertExists()
            .assert(hasContentDescription("Afwezig van 07:30 tot 18:00, 300 km heen en terug"))
        compose.onNode(hasContentDescription("Niet met deze auto, Afwezig van 07:30 tot 18:00, 300 km heen en terug"))
            .assertExists()
        compose.onNode(hasContentDescription("Verwijderen: rit Afwezig van 07:30 tot 18:00, 300 km heen en terug"))
            .assert(hasText("Verwijderen"))
        compose.assertShowsNoneOf(englishOnlyTexts())
    }

    /**
     * A trip file copied from the desktop, read by the app's own engine: a decimal distance shown in whole km, "Not
     * with this car" ticked, on the days it covers, and a trip that returned 25 hours ago left out.
     */
    @Test
    fun aTripFileFromTheDesktopShowsItsTripsOnTheirDaysTickedWithoutTheOldOne() {
        val folder = onDisk { Files.createDirectories(compose.activity.filesDir.toPath().resolve("desktop-trips")) }
        val back = clock.now.minus(Duration.ofHours(25))
        val old = Trip(back.minus(Duration.ofHours(8)), back, 50.0, false)
        onDisk { folder.resolve("trips.json").toFile().writeText("""{
          "version": 1,
          "trips": [
            {"departure": "${old.departure()}", "return": "${old.returnTime()}", "distanceKm": 50,
             "canChargeAtDestination": false},
            {"departure": "${overnight.departure()}", "return": "${overnight.returnTime()}", "distanceKm": 120.6,
             "canChargeAtDestination": true, "notWithThisCar": true}
          ]
        }""") }
        val platform = testPlatform(folder.toFile(), File(folder.toFile(), "no_backup"))
        val engine = EngineOnDispatcher(AppEngine.build(folder, platform.http(), PrivateFiles(FilePrivacy.NONE),
            platform.apiKeys(), Notifier { }, clock), Dispatchers.IO)
        val planned = week()
        show(on = object : EngineAccess by engine {
            override suspend fun outlook(): Outlook = planned
        })
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText(text(R.string.trips_heading))).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(text(R.string.trips_none)).performScrollTo().assertIsDisplayed()

        val spoken = text(R.string.trip_line_spoken,
            text(R.string.trip_days_spoken, "Tomorrow", "20:00", "Wed, Jun 3", "09:00"),
            text(R.string.trip_distance, "121"))
        for (day in listOf("Tomorrow", "Wednesday")) {
            tab(day)
            choice(R.string.trip_not_with_this_car, spoken).performScrollTo().assert(toggle(ToggleableState.On))
            choice(R.string.trip_can_charge, spoken).assert(toggle(ToggleableState.On))
        }
        compose.onNode(hasText(text(R.string.trip_distance, "50"), substring = true)).assertDoesNotExist()
        assertThat(runBlocking { engine.trips() }.map { it.distanceKm() }).containsExactly(120.6)
    }
}
