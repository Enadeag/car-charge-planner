package nl.enadeag.carchargeplanner.android.ui

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import java.util.Optional
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
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
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.config.Coordinates
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.forecast.Freshness
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet
import nl.enadeag.carchargeplanner.engine.notify.Notifier
import nl.enadeag.carchargeplanner.engine.plan.GridAdvice
import nl.enadeag.carchargeplanner.engine.plan.TripAdvice
import nl.enadeag.carchargeplanner.engine.plan.TripGridCharge
import nl.enadeag.carchargeplanner.engine.plan.Window
import nl.enadeag.carchargeplanner.engine.store.FilePrivacy
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles
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

/**
 * The trip advice card, Adjust, and the day card and battery on a trip's days, on the dashboard on Robolectric;
 * 1 June 2026 is a Monday.
 */
@RunWith(AndroidJUnit4::class)
class TripAdviceScreenTest {

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

    @After
    fun stop() {
        scope.cancel()
    }

    private fun at(day: LocalDate, time: String) = day.atTime(LocalTime.parse(time)).atZone(lisbon).toInstant()

    /** From tomorrow 08:00 to Wednesday 18:00, 300 km: it needs a grid charge tonight, so it asks the owner to act. */
    private val longTrip = Trip(at(today.plusDays(1), "08:00"), at(today.plusDays(2), "18:00"), 300.0, false)

    /** On Thursday from 09:00 to 12:00, 40 km: the plan charges enough for it. */
    private val shortTrip = Trip(at(today.plusDays(3), "09:00"), at(today.plusDays(3), "12:00"), 40.0, false)

    private val tonight = TripGridCharge(Window(at(today, "21:00"), at(today, "23:00"), 16, 3.7, 0.0, 7.4),
        TripGridCharge.Start.PLANNED)

    private fun asks(trip: Trip = longTrip) = TripAdvice(trip, Percent(84.2), listOf(), Percent(40.0),
        Optional.of(tonight), Percent(85.0), 0.0, Percent(30.0))

    private fun enough(trip: Trip = shortTrip) = TripAdvice(trip, Percent(45.0), listOf(), Percent(60.0),
        Optional.empty(), Percent(60.0), 0.0, Percent(55.0))

    /**
     * Monday in Lisbon and three more days: a window today, none after; the grid advice for Thursday; the long trip's
     * advice, which ends tomorrow at 25%, and the short trip's.
     */
    private fun week(freshness: Freshness = Freshness.FRESH, advice: List<TripAdvice> = listOf(asks(), enough())) =
        Plans.planned(today.atTime(9, 0).atZone(lisbon),
            listOf(listOf(Plans.window(lisbon, today, 13, 15)), listOf(), listOf(), listOf()),
            gridAdvice = GridAdvice(today.plusDays(3), 2.0, 22.0, 6.0, Optional.empty(), Optional.empty()),
            source = Plans.source(today.atTime(8, 51).atZone(lisbon).toInstant(), freshness),
            tripAdvice = advice, endPct = { if (it == today.plusDays(1)) 25.0 else null })

    private lateinit var battery: BatteryEntry

    private fun show(answer: Outlook? = week(), on: EngineAccess = access,
        wrap: @Composable (@Composable () -> Unit) -> Unit = { it() }) {
        Settings.System.putString(compose.activity.contentResolver, Settings.System.TIME_12_24, "24")
        access.trips = listOf(longTrip, shortTrip)
        val outlook = HomeOutlook(on, scope, clock) { }
        battery = BatteryEntry(SavedStateHandle(), NumberText(Locale.ENGLISH), on, scope) { }
        val away = AwayEntry(SavedStateHandle(), on, scope, clock, outlook::awayChanged)
        val trips = TripEntry(SavedStateHandle(), NumberText(Locale.ENGLISH), on, scope, clock, outlook::tripsChanged)
        val notice = HomeNotice(saves, scope) { }
        compose.setContent {
            AppTheme { wrap { HomeScreen(outlook, notice, battery, away, trips, { lisbon }, noCard) { } } }
        }
        compose.waitForIdle()
        if (on === access && answer != null) {
            access.outlooks.last().complete(answer)
            compose.waitForIdle()
        }
    }

    private fun tab(name: String) {
        compose.onNode(hasContentDescription(name) and hasClickAction()).performClick()
        compose.waitForIdle()
    }

    private fun selectedTab(name: String) = compose.onNode(hasContentDescription(name) and
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertIsSelected()

    private val todaysPlan = "Charge to 85% from the grid. Start tonight at 21:00. Set 16 A (3.7 kW), and a charge " +
        "limit of 85% in the car or the charger."

    private val todaysAdvice = "Trip advice: Trip tomorrow at 08:00, 300 km there and back. $todaysPlan Assuming a " +
        "battery level of 40%."

    private val tomorrowsAdvice = "Trip advice: Trip Tuesday at 08:00, 300 km there and back. Charge to 85% from " +
        "the grid. Start Monday at 21:00. Set 16 A (3.7 kW), and a charge limit of 85% in the car or the charger. " +
        "Assuming a battery level of 40%."

    private val thursdaysAdvice = "Trip advice: Trip Thursday at 09:00, 40 km there and back. The plan charges " +
        "enough for it: the battery is at 60% when you leave. Assuming a battery level of 40%."

    private fun card(spoken: String) = compose.onNode(hasContentDescription(spoken), useUnmergedTree = true)

    private fun cards() = compose.onAllNodes(hasContentDescription(text(R.string.trip_advice_label), substring = true),
        useUnmergedTree = true).fetchSemanticsNodes().map { it.config[SemanticsProperties.ContentDescription].single() }

    private val adjust get() = compose.onNode(hasContentDescription(text(R.string.trip_advice_adjust_spoken)))

    private fun top(node: SemanticsNodeInteraction) = node.fetchSemanticsNode().positionInRoot.y

    private fun heading(words: String) = compose.onNode(isHeading() and hasText(words), useUnmergedTree = true)

    /** Gives the dialog's window the focus, as Android does once it shows it and Robolectric does not. */
    private fun dialogWindowFocused() {
        compose.runOnIdle {
            Shadow.extract<ShadowViewRootImpl>(ShadowDialog.getLatestDialog().window!!.decorView.parent)
                .callWindowFocusChanged(true)
        }
        compose.waitForIdle()
    }

    @Test
    fun todaysTabShowsTheAdviceThatAsksBelowTheDayCardAndAboveTheGridAdviceAndTheCharts() {
        show()

        assertThat(cards()).containsExactly(todaysAdvice)
        val card = card(todaysAdvice)
        assertThat(top(heading("Today"))).isLessThan(top(card))
        assertThat(top(card)).isLessThan(top(compose.onNodeWithText(text(R.string.advice_label))))
        assertThat(top(card)).isLessThan(top(heading(text(R.string.chart_heading))))
    }

    @Test
    fun theDepartureDaysTabShowsItsAdviceInWeekdaysAndADayWithoutADepartureNone() {
        show()

        tab("Tomorrow")
        assertThat(cards()).containsExactly(tomorrowsAdvice)
        assertThat(top(heading("Tomorrow"))).isLessThan(top(card(tomorrowsAdvice)))
        assertThat(top(card(tomorrowsAdvice))).isLessThan(top(heading(text(R.string.chart_heading))))
        tab("Wednesday")
        assertThat(cards()).isEmpty()
        tab("Thursday")
        assertThat(cards()).containsExactly(thursdaysAdvice)
    }

    @Test
    fun twoTripsLeavingTheSameDayEachHaveACardInTheEnginesOrder() {
        val second = Trip(at(today.plusDays(1), "19:00"), at(today.plusDays(1), "22:00"), 60.0, false)
        show(week(advice = listOf(asks(), enough(second))))

        tab("Tomorrow")

        assertThat(cards()).containsExactly(tomorrowsAdvice, "Trip advice: Trip Tuesday at 19:00, 60 km there and " +
            "back. The plan charges enough for it: the battery is at 60% when you leave. Assuming a battery level of " +
            "40%.")
    }

    @Test
    fun theCardIsOneHeadingThatSaysTheWholeAdviceWithAdjustApartAsAButton() {
        show()

        val node = card(todaysAdvice).fetchSemanticsNode()
        assertThat(node.config.contains(SemanticsProperties.Heading)).isTrue()
        assertThat(node.children).hasSize(4)
        assertThat(node.children.filter { !NOT_READ_APART.matches(it) }).`as`("the card's words read apart").isEmpty()
        compose.assertShownButNotReadApart(todaysPlan)
        compose.assertShownButNotReadApart("Assuming a battery level of 40%.")
        val button = adjust.performScrollTo().assertIsDisplayed().fetchSemanticsNode()
        assertThat(button.config[SemanticsProperties.Role]).isEqualTo(Role.Button)
        assertThat(button.config[SemanticsActions.OnClick].label).isEqualTo(text(R.string.trip_advice_adjust_hint))
        compose.onNodeWithText(text(R.string.trip_advice_adjust), useUnmergedTree = true).assertIsDisplayed()
        compose.assertEveryControlIsNamedAndLargeEnough()
    }

    @Test
    fun adjustOnALaterTabMovesToTodayOpensTheEntryAndOnCloseTheFocusIsOnUpdateBatteryLevel() {
        show()
        tab("Tomorrow")

        adjust.performScrollTo().performClick()
        compose.waitForIdle()

        selectedTab("Today")
        compose.onNode(isDialog()).assertExists()
        compose.onNode(isHeading() and hasText(text(R.string.battery_entry_title))).assertIsDisplayed()
        dialogWindowFocused()
        compose.onNodeWithText(text(R.string.action_cancel)).performClick()
        compose.waitForIdle()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.battery_update)).assertIsFocused()
    }

    @Test
    fun adjustOnTodaysTabOpensTheEntry() {
        show()

        adjust.performScrollTo().performClick()
        compose.waitForIdle()

        selectedTab("Today")
        compose.onNode(isDialog()).assertExists()
    }

    @Test
    fun aSecondAdjustLeavesOneEntry() {
        show()
        tab("Tomorrow")

        adjust.performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        adjust.performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertThat(compose.onAllNodes(isDialog()).fetchSemanticsNodes()).hasSize(1)
        assertThat(battery.open.value).isTrue()
    }

    @Test
    fun withoutAPlanThereIsNoTripAdvice() {
        show(answer = null)

        assertThat(cards()).isEmpty()
        compose.onAllNodes(hasText(text(R.string.trip_advice_adjust))).fetchSemanticsNodes().let {
            assertThat(it).isEmpty()
        }
    }

    @Test
    fun aStalePlanShowsItsAdviceUnderTheWarning() {
        show(week(Freshness.STALE))

        val warning = compose.onNodeWithText(text(R.string.stale_warning, "08:51"), useUnmergedTree = true)
        warning.assertIsDisplayed()
        assertThat(top(warning)).isLessThan(top(card(todaysAdvice)))
    }

    @Test
    @Config(qualifiers = "nl")
    fun theCardIsInDutch() {
        show()

        assertThat(cards()).containsExactly("Advies voor de rit: Rit morgen om 08:00, 300 km heen en terug. Laad tot " +
            "85% uit het net. Start het laden vanavond om 21:00. Stel 16 A in (3,7 kW), en een laadlimiet van 85% in " +
            "de auto of de laadpaal. Uitgaande van een accuniveau van 40%.")
        compose.onNode(hasContentDescription("Aanpassen: accuniveau")).performScrollTo().assertIsDisplayed()
        compose.assertShowsNoneOf(englishOnlyTexts())
    }

    @Test
    @Config(qualifiers = "nl-w320dp-h640dp")
    fun onA320DpPhoneInDutchAtTwiceTheFontSizeNothingIsCutAndAdjustIsLargeEnough() {
        show(wrap = { AtFontScaleTwo(it) })

        compose.assertEveryControlIsNamedAndLargeEnough()
        compose.assertScrollsToShow(hasText(Plans.CREDIT, substring = true))
        val words = compose.onNodeWithText("Aanpassen", useUnmergedTree = true).performScrollTo()
        val layout = mutableListOf<TextLayoutResult>()
        words.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layout)
        assertThat(layout.single().lineCount).isEqualTo(1)
        assertThat(layout.single().isLineEllipsized(0)).isFalse()
        assertThat(layout.single().didOverflowHeight).isFalse()
        compose.onNodeWithText("Uitgaande van een accuniveau van 40%.", useUnmergedTree = true).performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun theDepartureDaySaysNoWhyAndItsBatteryTheLevelAfterTheTrip() {
        show()

        tab("Tomorrow")

        compose.onNodeWithText(text(R.string.day_none_why)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.day_none_trip)).assertDoesNotExist()
        compose.onNode(hasContentDescription("Battery from 40% to 25% after the trip")).assertExists()
        compose.onNodeWithText("Battery 40 → 25% after the trip", useUnmergedTree = true).assertExists()
        compose.assertShownButNotReadApart("40 → 25% after the trip")
        compose.assertReadAsOne("Car battery 40% at the start of tomorrow, 40% after charging; minimum 20%, target " +
            "80%. After the trip the battery is at 25% at the end of the day.", Role.Image)
    }

    @Test
    fun adjustStaysOpenToUseWhileATripChangeRuns() {
        val gate = CompletableDeferred<Unit>()
        access.tripGate = gate
        try {
            show()
            tab("Wednesday")
            val choice = text(R.string.trip_choice_spoken, text(R.string.trip_not_with_this_car),
                text(R.string.trip_line_spoken, text(R.string.trip_days_spoken, "Tomorrow", "08:00", "Wed, Jun 3",
                    "18:00"), text(R.string.trip_distance, "300")))
            compose.onNode(hasContentDescription(choice)).performScrollTo().performClick()
            compose.waitForIdle()
            assertThat(access.tripChanges).hasSize(1)
            tab("Tomorrow")

            adjust.performScrollTo().assertIsEnabled().performClick()
            compose.waitForIdle()

            assertThat(gate.isCompleted).`as`("the trip change still runs").isFalse()
            selectedTab("Today")
            compose.onNode(isDialog()).assertExists()
        } finally {
            gate.complete(Unit)
        }
    }

    /** The colour the chip of the trip advice card on the page in view is drawn on, a little inside its start. */
    private fun chipGround(): Int {
        val width = compose.onRoot().fetchSemanticsNode().size.width
        val chip = compose.onAllNodes(hasText(text(R.string.trip_advice_label)), useUnmergedTree = true)
            .fetchSemanticsNodes().single { it.boundsInRoot.left >= 0 && it.boundsInRoot.left < width }.boundsInRoot
        return compose.screenshot().getPixel(chip.left.roundToInt() + 3, chip.center.y.roundToInt())
    }

    @Test
    fun theChipIsDrawnInTheColoursOfTheAdvicesSource() {
        show()
        card(todaysAdvice).performScrollTo()

        val grid = chipGround()
        tab("Wednesday")
        tab("Thursday")
        card(thursdaysAdvice).performScrollTo()
        val car = chipGround()

        assertThat(grid).`as`("a grid charge").isEqualTo(SourceColours.LIGHT.grid.container.toArgb())
        assertThat(car).`as`("enough").isEqualTo(SourceColours.LIGHT.car.container.toArgb())
    }

    @Test
    fun aDayAwayOnATripSaysSoAndNotThatTheCarNeedsNoCharging() {
        show()

        tab("Wednesday")

        compose.onNodeWithText(text(R.string.day_none_trip)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.day_none_why)).assertDoesNotExist()
        compose.onNodeWithText("Battery 40 → 40%", useUnmergedTree = true).assertExists()
    }

    @Test
    fun aTripMadeNotWithThisCarChangesTheAwayDayAtOnce() {
        show()
        tab("Wednesday")

        compose.onNode(hasContentDescription(text(R.string.trip_choice_spoken, text(R.string.trip_not_with_this_car),
            text(R.string.trip_line_spoken, text(R.string.trip_days_spoken, "Tomorrow", "08:00", "Wed, Jun 3",
                "18:00"), text(R.string.trip_distance, "300"))))).performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.day_none_why)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.day_none_trip)).assertDoesNotExist()
    }

    /** A cloudless Open-Meteo answer for four days in Lisbon, from today's midnight, each hour's end its time. */
    private fun sunnyAnswer(): String {
        val start = today.atStartOfDay(lisbon).toInstant()
        val hours = (1..96).map { start.plusSeconds(3600L * it) }
        val irradiance = hours.map { end ->
            val hour = end.minusSeconds(1800).atZone(lisbon).toLocalTime().toSecondOfDay() / 3600.0
            max(0.0, 800 * sin(PI * (hour - 6) / 14))
        }
        return """{"hourly":{"time":[${hours.joinToString(",") { it.epochSecond.toString() }}],""" +
            """"global_tilted_irradiance":[${irradiance.joinToString(",")}]}}"""
    }

    /** A trip saved through the app's own engine, planned by it on a forecast served here, shows its advice. */
    @Test
    fun aTripSavedThroughTheEngineShowsTheEnginesAdviceOnItsDepartureTab() {
        val folder = onDisk { Files.createDirectories(compose.activity.filesDir.toPath().resolve("engine-trips")) }
        val platform = testPlatform(folder.toFile(), File(folder.toFile(), "no_backup"))
        val raw = AppEngine.build(folder, { HttpGet.Reply(200, sunnyAnswer()) }, PrivateFiles(FilePrivacy.NONE),
            platform.apiKeys(), Notifier { }, clock)
        val draft = EngineConfig.setUpDraft(lisbon, ProviderId("open-meteo"))
        raw.saveConfig(draft.withSystem(draft.system().withLocation(Coordinates(38.7, -9.1), lisbon),
            draft.typical()))
        val engine = EngineOnDispatcher(raw, Dispatchers.IO)
        val trip = Trip(at(today.plusDays(1), "07:00"), at(today.plusDays(1), "21:00"), 450.0, false)
        runBlocking { engine.addTrip(trip) }

        show(on = engine)
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText(text(R.string.battery_heading))).fetchSemanticsNodes().isNotEmpty()
        }
        tab("Tomorrow")

        assertThat(cards()).hasSize(1)
        assertThat(cards().single()).startsWith("Trip advice: Trip Tuesday at 07:00, 450 km there and back. ")
        adjust.performScrollTo().assertIsDisplayed()
    }
}
