package nl.enadeag.carchargeplanner.android.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import java.util.Optional
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.home.AwayEntry
import nl.enadeag.carchargeplanner.android.home.BatteryEntry
import nl.enadeag.carchargeplanner.android.home.HomeNotice
import nl.enadeag.carchargeplanner.android.home.CardMemory
import nl.enadeag.carchargeplanner.android.home.HomeOutlook
import nl.enadeag.carchargeplanner.android.home.NotificationCard
import nl.enadeag.carchargeplanner.android.home.Plans
import nl.enadeag.carchargeplanner.android.home.TripEntry
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.store.Saves
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.android.ui.home.AxisLabels
import nl.enadeag.carchargeplanner.android.ui.home.HomeScreen
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.House
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.forecast.ForecastSource
import nl.enadeag.carchargeplanner.engine.forecast.Freshness
import nl.enadeag.carchargeplanner.engine.forecast.ProviderRole
import nl.enadeag.carchargeplanner.engine.plan.GridAdvice
import nl.enadeag.carchargeplanner.engine.plan.Window
import nl.enadeag.carchargeplanner.engine.secrets.KeyChanges
import nl.enadeag.carchargeplanner.engine.secrets.Secrets
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The dashboard on Robolectric, on plans made up here; 1 June 2026 is a Monday. */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    /** A clock that stands still until a test moves it. */
    private class TestClock(var now: Instant) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
    }

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val access = FakeEngineAccess(StartState.Ready(EngineConfig.setUpDraft(ZoneId.of("Europe/Amsterdam"),
        ProviderId("open-meteo"))))
    private val hasNoScrollAncestor = !hasAnyAncestor(hasScrollAction())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** A card that never shows, for the tests of the home's other parts. */
    private val noCard by lazy { allowedCard(scope) }
    private val amsterdam = ZoneId.of("Europe/Amsterdam")
    private val lisbon = ZoneId.of("Europe/Lisbon")
    private val today = LocalDate.of(2026, 6, 1)
    private val clock = TestClock(today.atTime(9, 0).atZone(lisbon).toInstant())
    private val title = isHeading() and hasText(text(R.string.app_name))

    @After
    fun stop() {
        scope.cancel()
    }

    private fun clock(hours: String) {
        Settings.System.putString(compose.activity.contentResolver, Settings.System.TIME_12_24, hours)
    }

    private val saves = Saves(access, scope)
    private var settingsOpened = 0
    private lateinit var focus: FocusManager

    /** The home's saved state, which the battery entry keeps its texts in. */
    private val handle = SavedStateHandle()
    private lateinit var battery: BatteryEntry
    private lateinit var away: AwayEntry
    private lateinit var shownOutlook: HomeOutlook

    /** A battery entry of its own, for a home a test sets up itself. */
    private fun entry() = BatteryEntry(SavedStateHandle(), NumberText(Locale.ENGLISH), access, scope) { }

    /** Changes to the away periods of their own, for a home a test sets up itself. */
    private fun awayEntry() = AwayEntry(SavedStateHandle(), access, scope, clock) { }

    /** Today's battery bar in words while its level is assumed, as in the plans here unless they say otherwise. */
    private fun todaysBattery(vararg levels: String) =
        text(R.string.battery_spoken_today, *levels) + " " + text(R.string.battery_assumed)

    /** What the notification card remembers, in memory. */
    private class Memory(var closed: Boolean = false, var refused: Boolean = false) : CardMemory {
        override suspend fun closed() = closed
        override suspend fun close() {
            closed = true
        }
        override suspend fun refused() = refused
        override suspend fun refuse() {
            refused = true
        }
    }

    private fun show(answer: Outlook?, phoneZone: ZoneId = amsterdam, fontScaleTwo: Boolean = false,
        kept: SavedStateHandle = handle, card: NotificationCard = noCard): HomeOutlook {
        val outlook = HomeOutlook(access, scope, clock) { }
        shownOutlook = outlook
        battery = BatteryEntry(kept, NumberText(Locale.ENGLISH), access, scope) { outlook.planAgain() }
        away = AwayEntry(kept, access, scope, clock, outlook::awayChanged)
        val trips = TripEntry(SavedStateHandle(), NumberText(Locale.ENGLISH), access, scope, clock) { }
        val notice = HomeNotice(saves, scope) { }
        compose.setContent {
            AppTheme {
                focus = LocalFocusManager.current
                if (fontScaleTwo) {
                    AtFontScaleTwo {
                        HomeScreen(outlook, notice, battery, away, trips, { phoneZone }, card) { settingsOpened++ }
                    }
                } else {
                    HomeScreen(outlook, notice, battery, away, trips, { phoneZone }, card) { settingsOpened++ }
                }
            }
        }
        compose.waitForIdle()
        answer?.let { answer(it) }
        return outlook
    }

    private fun answer(answer: Outlook) {
        access.outlooks.last().complete(answer)
        compose.waitForIdle()
    }

    private fun tab(name: String) = compose.onNode(hasContentDescription(name) and
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))

    private val page = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    /**
     * Monday in Lisbon, with two windows today, none on Tuesday and one on Wednesday; the minimum charge level is 20%
     * unless given.
     */
    private fun lisbonWeek(gridAdvice: GridAdvice? = null, freshness: Freshness = Freshness.FRESH,
        keyRefusedBy: List<ProviderId> = listOf(), minimum: Double = 20.0) = Plans.planned(
        today.atTime(9, 0).atZone(lisbon), listOf(
            listOf(Plans.window(lisbon, today, 13, 15), Plans.window(lisbon, today, 16, 17)),
            listOf(),
            listOf(Plans.window(lisbon, today.plusDays(2), 11, 12, importKwh = 0.3))),
        gridAdvice = gridAdvice, source = Plans.source(today.atTime(8, 51).atZone(lisbon).toInstant(), freshness,
            keyRefusedBy = keyRefusedBy), minimum = minimum)

    /** Sun from 07:00 to 18:00 on every day, at most 3.2 kW at noon. */
    private val sun = DoubleArray(24) { hour -> if (hour == 12) 3.2 else if (hour in 7..17) 2.0 else 0.0 }

    /** Monday in Lisbon in the sun, with two windows today and none on Tuesday. */
    private fun sunnyDays() = Plans.planned(today.atTime(9, 0).atZone(lisbon), listOf(
        listOf(Plans.window(lisbon, today, 13, 15), Plans.window(lisbon, today, 16, 17)), listOf()),
        forecast = { Plans.forecast(lisbon, it, *sun) })

    private fun axisLabels(): List<String> =
        compose.onNode(SemanticsMatcher.keyIsDefined(AxisLabels), useUnmergedTree = true).fetchSemanticsNode()
            .config[AxisLabels]

    @Test
    fun theTopAppBarNamesTheAppAndATabNamesEveryDayOfThePlanTodayFirst() {
        show(lisbonWeek())

        compose.onNode(title).assert(hasNoScrollAncestor).assertIsDisplayed()
        tab("Today").assert(hasText("Today")).assertIsSelected()
        tab("Tomorrow").assert(hasText("Tue")).assertIsNotSelected()
        tab("Wednesday").assert(hasText("Wed")).assertIsNotSelected()
    }

    @Test
    fun todaysCardShowsTheMainWindowWhatToSetTheSharesAndTheLaterWindowInTheHomesZone() {
        clock("24")

        show(lisbonWeek())

        compose.onNode(isHeading() and hasText("Today"), useUnmergedTree = true).assertIsDisplayed()
        // the chart's legend says "Charge window" too, but is not read apart
        compose.onNode(hasText(text(R.string.day_window)) and !NOT_READ_APART).assertIsDisplayed()
        compose.onNodeWithText("13:00 – 15:00").assert(hasContentDescription("13:00 to 15:00"))
        compose.onNodeWithText(text(R.string.day_set, 6, "1.4")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.day_energy, "2.8")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.day_solar_share, "100")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.day_battery, "40", "60"))
            .assert(hasContentDescription(text(R.string.day_battery_spoken, "40", "60")))
        compose.onNodeWithText(text(R.string.day_from_grid, "0.0")).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.day_later_windows, "16:00 – 17:00 at 6 A") + " " +
            text(R.string.day_battery_assumed, "40")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.home_zone, "Europe/Lisbon")).assertIsDisplayed()
    }

    @Test
    fun onA12HourClockTheTimesFollowItAndTheHomesZoneIsNotNamedWhenItIsThePhones() {
        clock("12")

        show(lisbonWeek(), phoneZone = lisbon)

        val shown = compose.shownTexts()
        assertThat(shown).noneMatch { it.contains("13:00") }.anyMatch { it.contains("1:00") }
        assertThat(shown).doesNotContain(text(R.string.home_zone, "Europe/Lisbon"))
    }

    @Test
    fun aTabsClickShowsItsDayAndASwipeMovesTheTab() {
        clock("24")
        show(lisbonWeek())

        tab("Tomorrow").performClick()
        compose.waitForIdle()
        tab("Tomorrow").assertIsSelected()
        compose.onNode(isHeading() and hasText("Tomorrow"), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.day_none)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.day_none_why)).assertIsDisplayed()

        compose.onNode(page).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        tab("Wednesday").assertIsSelected()
        compose.onNodeWithText("11:00 – 12:00").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.day_from_grid, "0.3")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.day_battery_assumed, "40"), substring = true).assertDoesNotExist()
    }

    @Test
    fun gridAdviceShowsOnTodaysTabOnlyAndItsDayWithoutAWindowSaysWhy() {
        clock("24")
        val window = Window(today.atTime(20, 0).atZone(lisbon).toInstant(),
            today.atTime(22, 0).atZone(lisbon).toInstant(), 16, 3.7, 0.0, 7.4)
        val advice = GridAdvice(today.plusDays(1), 12.0, 18.0, 7.4, Optional.of(window), Optional.of(Percent(60.0)))
        show(lisbonWeek(advice, minimum = 30.0))

        compose.onNodeWithText(text(R.string.advice_label)).performScrollTo().assert(isHeading())
        compose.onNodeWithText(text(R.string.advice_title_window, "7.4", "today", "20:00 – 22:00")).assertExists()
        compose.onNodeWithText(text(R.string.advice_ends_low, "tomorrow", "12", "30") + " " +
            text(R.string.advice_set, 16, "3.7", "60")).assertExists()

        tab("Tomorrow").performClick()
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.day_grid)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.day_grid_why)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.advice_label)).assertDoesNotExist()
    }

    @Test
    fun theComingOneOffAwayPeriodsAreListedAndTheEndedOnesLeftOut() {
        clock("24")
        access.away = listOf(OneOffAwayPeriod.on(today, LocalTime.of(6, 0), LocalTime.of(8, 0), lisbon),
            OneOffAwayPeriod.allDay(today.plusDays(1), lisbon),
            OneOffAwayPeriod.on(today.plusDays(1), LocalTime.of(10, 0), LocalTime.of(16, 30), lisbon))

        show(lisbonWeek())

        compose.onNodeWithText(text(R.string.one_off_heading)).performScrollTo().assert(isHeading())
        compose.onNodeWithText(text(R.string.one_off_all_day, "Tomorrow")).assertExists()
        compose.onNodeWithText(text(R.string.one_off_part, "Tomorrow", "10:00 – 16:30")).assertExists()
        compose.onNodeWithText(text(R.string.one_off_part, "Today", "06:00 – 08:00")).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.one_off_none)).assertDoesNotExist()
    }

    @Test
    fun withoutOneOffAwayPeriodsTheListSaysNoneAreEntered() {
        show(lisbonWeek())

        compose.onNodeWithText(text(R.string.one_off_none)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theFooterCreditsTheProviderInEnglishAndSaysWhenTheForecastWasFetched() {
        clock("24")
        show(lisbonWeek())

        val footer = "${Plans.CREDIT} · ${text(R.string.footer_updated, "08:51")}"
        compose.onNodeWithText(footer).performScrollTo().assertIsDisplayed()
        val shown = compose.onNodeWithText(footer).fetchSemanticsNode().config[SemanticsProperties.Text].single()
        assertThat(shown.spanStyles.single { it.start == 0 }.item.localeList?.first()?.language).isEqualTo("en")
        compose.onNodeWithText(text(R.string.stale_warning, "08:51")).assertDoesNotExist()
    }

    @Test
    fun aStaleForecastShowsAWarningWithItsWordsAboveThePlanWhichStaysInView() {
        clock("24")
        show(lisbonWeek(freshness = Freshness.STALE))

        compose.onNodeWithText(text(R.string.stale_warning, "08:51")).assertIsDisplayed()
        compose.onNodeWithText("13:00 – 15:00").assertIsDisplayed()
        compose.onNodeWithText("${Plans.CREDIT} · ${text(R.string.footer_stale, "08:51")}").assertExists()
    }

    @Test
    fun noForecastShowsItsEnglishDetailsAndTryAgainAsksAgain() {
        show(Outlook.NoForecast("Open-Meteo: no network"))

        compose.onNode(isHeading() and hasText(text(R.string.no_forecast))).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.no_forecast_why)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.details_in_english, "Open-Meteo: no network")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_try_again)).performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(access.outlooks).hasSize(2)
        compose.onNodeWithText(text(R.string.home_asking)).assertIsDisplayed()
    }

    @Test
    fun aForecastWithoutDaysSaysSoWithItsFooter() {
        clock("24")
        show(Plans.planned(today.atTime(9, 0).atZone(lisbon), listOf()))

        compose.onNodeWithText(text(R.string.no_forecast_no_days)).assertIsDisplayed()
        compose.onNodeWithText("${Plans.CREDIT} · ${text(R.string.footer_updated, "09:00")}").assertExists()
    }

    @Test
    fun refreshInTheTopAppBarKeepsThePlanInViewAndIsOffUntilTheNewOneComes() {
        show(lisbonWeek())
        val refresh = compose.onNodeWithContentDescription(text(R.string.home_refresh)).assert(hasNoScrollAncestor)

        refresh.performClick()
        compose.waitForIdle()

        assertThat(access.outlooks).hasSize(2)
        compose.onNodeWithText(text(R.string.day_set, 6, "1.4")).assertIsDisplayed()
        refresh.assertIsNotEnabled()
        answer(lisbonWeek())
        refresh.assertIsEnabled()
    }

    @Test
    fun aPullDownTheDayAsksForThePlanAgain() {
        show(lisbonWeek())

        compose.onNode(page).performTouchInput { swipeDown(startY = top + 10f, endY = bottom) }
        compose.waitForIdle()

        assertThat(access.outlooks).hasSize(2)
    }

    @Test
    fun comingBackToTheAppAsksAgainOnlyWhenThePlanIsOlderThanAQuarterOfAnHour() {
        show(lisbonWeek())

        clock.now = clock.now.plus(Duration.ofMinutes(15))
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        assertThat(access.outlooks).hasSize(1)

        clock.now = clock.now.plusSeconds(1)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        assertThat(access.outlooks).hasSize(2)
    }

    /** A window over the hour the clocks change, on the home's 23- or 25-hour day. */
    private fun overTheChange(day: LocalDate) {
        clock("24")
        val window = Plans.window(amsterdam, day, 1, 4)

        show(Plans.planned(day.atTime(0, 30).atZone(amsterdam), listOf(listOf(window))))

        compose.onNodeWithText("01:00 – 04:00").assertIsDisplayed()
        compose.onNode(isHeading() and hasText("Today"), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun onTheShortDayTheTimesAreTheHomesClockTimes() = overTheChange(LocalDate.of(2026, 3, 29))

    @Test
    fun onTheLongDayTheTimesAreTheHomesClockTimes() = overTheChange(LocalDate.of(2026, 10, 25))

    @Test
    fun settingsIsTheTopAppBarsActionAndOpensSettings() {
        show(lisbonWeek())

        compose.onNodeWithContentDescription(text(R.string.home_settings)).assert(hasNoScrollAncestor).performClick()

        assertThat(settingsOpened).isEqualTo(1)
    }

    @Test
    fun aSaveNoScreenShowedShowsWithItsEnglishDetailsInALiveRegionUntilClosed() {
        access.writeFails = true
        saves.update(KeyChanges.NONE) { it.withSystem(it.system().withHouseAndCar(House(0.9), it.system().car()),
            it.typical()) }
        show(lisbonWeek())
        compose.waitForIdle()

        assertThat(compose.liveRegionOf(text(R.string.settings_save_failed))).isEqualTo(LiveRegionMode.Assertive)
        compose.onNodeWithText(text(R.string.details_in_english, "No space left on device"), useUnmergedTree = true)
            .assertExists()
        // Close is a button of its own, outside the live region
        assertThat(compose.liveRegionOf(text(R.string.action_close))).isNull()
        // the title took the focus when the home opened; the focus is off it, so Close has to bring it back
        compose.runOnIdle { focus.clearFocus() }
        compose.onNode(title).assertIsNotFocused()
        compose.onNodeWithText(text(R.string.action_close)).performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.settings_save_failed)).assertDoesNotExist()
        compose.onNode(title).assertIsFocused()
    }

    @Test
    fun aSavedNoticeIsReadPolitely() {
        saves.update(KeyChanges.NONE) { it.withSystem(it.system().withHouseAndCar(House(0.9), it.system().car()),
            it.typical()) }
        show(lisbonWeek())
        compose.waitForIdle()

        assertThat(compose.liveRegionOf(text(R.string.settings_saved))).isEqualTo(LiveRegionMode.Polite)
    }

    @Test
    fun keysNotSavedOnTheHomeAreReadAtOnce() {
        access.keysFail = true
        saves.update(KeyChanges.entering(Secrets(mapOf(ProviderId("open-meteo") to mapOf("api_key" to "K1"))))) {
            it
        }
        show(lisbonWeek())
        compose.waitForIdle()

        assertThat(compose.liveRegionOf(text(R.string.settings_keys_not_saved))).isEqualTo(LiveRegionMode.Assertive)
    }

    @Test
    fun theHomesControlsAreNamedAndLargeEnoughAtTheNormalFontSize() {
        access.writeFails = true
        saves.update(KeyChanges.NONE) { it.withSystem(it.system().withHouseAndCar(House(0.9), it.system().car()),
            it.typical()) }
        show(lisbonWeek())
        compose.waitForIdle()

        compose.assertEveryControlIsNamedAndLargeEnough()
    }

    @Test
    fun withoutAPlanTheHomesControlsAreNamedAndLargeEnough() {
        show(Outlook.NoForecast("Open-Meteo: no network"))

        compose.assertEveryControlIsNamedAndLargeEnough()
    }

    @Test
    @Config(qualifiers = "nl")
    fun theHomeIsInDutch() {
        clock("24")
        access.away = listOf(OneOffAwayPeriod.allDay(today.plusDays(1), lisbon))
        show(lisbonWeek(GridAdvice(today.plusDays(1), 0.0, 20.0, 6.0, Optional.empty(), Optional.empty()),
            Freshness.STALE))

        tab("Vandaag").assertIsSelected()
        compose.assertReadAsOne(text(R.string.chart_spoken_no_solar, "Vandaag"), Role.Image)
        compose.assertReadAsOne(todaysBattery("40", "60", "20", "80"), Role.Image)
        compose.onNodeWithText(text(R.string.advice_runs_empty, "morgen", "20") + " " + text(R.string.advice_no_room))
            .assertExists()
        compose.assertShowsNoneOf(englishOnlyTexts())
    }

    @Test
    fun theDaysScrollAndTheControlsAreNamedAndLargeEnoughAtTwiceTheFontSize() {
        access.writeFails = true
        saves.update(KeyChanges.NONE) { it.withSystem(it.system().withHouseAndCar(House(0.9), it.system().car()),
            it.typical()) }
        show(lisbonWeek(freshness = Freshness.STALE), fontScaleTwo = true)

        compose.assertEveryControlIsNamedAndLargeEnough()
        compose.assertScrollsToShow(hasText(Plans.CREDIT, substring = true))
        tab("Wednesday").performScrollTo().performClick()
        compose.waitForIdle()
        compose.assertScrollsToShow(hasText(Plans.CREDIT, substring = true))
    }

    @Test
    @Config(qualifiers = "w480dp-h320dp-land")
    fun onASmallPhoneOnItsSideAtTwiceTheFontSizeTheDayKeepsRoomAndScrolls() {
        access.writeFails = true
        saves.update(KeyChanges.NONE) { it.withSystem(it.system().withHouseAndCar(House(0.9), it.system().car()),
            it.typical()) }
        show(lisbonWeek(freshness = Freshness.STALE), fontScaleTwo = true)

        compose.assertScrollsToShow(hasText(Plans.CREDIT, substring = true))
        compose.onNodeWithText(text(R.string.action_close)).performScrollTo().assertIsDisplayed()
        tab("Wednesday").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun withoutAPlanThePageScrollsAtTwiceTheFontSize() {
        show(Outlook.NoForecast("Open-Meteo: no network ".repeat(20)), fontScaleTwo = true)

        compose.assertScrolls()
        compose.assertEveryControlIsNamedAndLargeEnough()
    }

    @Test
    fun theTitleHasTheFocusWhenTheHomeOpens() {
        show(lisbonWeek())

        compose.onNode(title).assertIsFocused()
    }

    @Test
    fun refreshIsOffWhileTheFirstPlanIsMade() {
        show(null)

        compose.onNodeWithText(text(R.string.home_asking)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.home_refresh)).assertIsNotEnabled()
    }

    @Test
    fun aPullDownThePageWithoutAPlanAsksAgain() {
        show(Outlook.NoForecast("Open-Meteo: no network"))

        compose.onNode(page).performTouchInput { swipeDown(startY = top + 10f, endY = bottom) }
        compose.waitForIdle()

        assertThat(access.outlooks).hasSize(2)
    }

    @Test
    fun aPauseWithoutLeavingTheAppAsksNothingNewHoweverLong() {
        show(lisbonWeek())

        clock.now = clock.now.plus(Duration.ofMinutes(16))
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()

        assertThat(access.outlooks).hasSize(1)
    }

    @Test
    fun aStaleForecastsWarningIsReadPolitely() {
        clock("24")
        show(lisbonWeek(freshness = Freshness.STALE))

        assertThat(compose.liveRegionOf(text(R.string.stale_warning, "08:51"))).isEqualTo(LiveRegionMode.Polite)
    }

    @Test
    fun aRefusedKeyIsSaidByItsProviderAboveThePlanInAPoliteLiveRegion() {
        clock("24")
        val source = Plans.source(today.atTime(8, 51).atZone(lisbon).toInstant(),
            keyRefusedBy = listOf(ProviderId("open-meteo")))
        show(Plans.planned(today.atTime(9, 0).atZone(lisbon), listOf(listOf(Plans.window(lisbon, today, 13, 15))),
            source = source))

        val warning = text(R.string.key_refused_warning, "Open-Meteo")
        compose.onNodeWithText(warning).assertIsDisplayed()
        assertThat(compose.liveRegionOf(warning)).isEqualTo(LiveRegionMode.Polite)
        assertThat(top(warning)).isLessThan(todaysCardTop())
        compose.onNodeWithText("13:00 – 15:00").assertIsDisplayed()
    }

    @Test
    fun everyProviderThatRefusedItsKeyIsSaidInTheOrderAsked() {
        clock("24")
        val source = Plans.source(today.atTime(8, 51).atZone(lisbon).toInstant(),
            keyRefusedBy = listOf(ProviderId("forecast-solar"), ProviderId("open-meteo")))
        show(Plans.planned(today.atTime(9, 0).atZone(lisbon), listOf(listOf(Plans.window(lisbon, today, 13, 15))),
            source = source))

        val first = text(R.string.key_refused_warning, "Forecast.Solar")
        val second = text(R.string.key_refused_warning, "Open-Meteo")
        compose.onNodeWithText(first).assertIsDisplayed()
        compose.onNodeWithText(second).assertIsDisplayed()
        // one warning, a line per provider
        assertThat(lineTop(first)).isLessThan(lineTop(second))
    }

    @Test
    fun aStaleForecastWithARefusedKeyShowsBothWarningsTheStaleOneFirst() {
        clock("24")
        val source = Plans.source(today.atTime(8, 51).atZone(lisbon).toInstant(), freshness = Freshness.STALE,
            keyRefusedBy = listOf(ProviderId("open-meteo")))
        show(Plans.planned(today.atTime(9, 0).atZone(lisbon), listOf(listOf(Plans.window(lisbon, today, 13, 15))),
            source = source))

        val stale = text(R.string.stale_warning, "08:51")
        val refused = text(R.string.key_refused_warning, "Open-Meteo")
        compose.onNodeWithText(stale).assertIsDisplayed()
        compose.onNodeWithText(refused).assertIsDisplayed()
        assertThat(top(stale)).isLessThan(top(refused))
    }

    @Test
    fun withoutARefusedKeyThereIsNoSuchWarning() {
        show(lisbonWeek())

        compose.onNodeWithText(text(R.string.key_refused_warning, "Open-Meteo")).assertDoesNotExist()
    }

    @Test
    fun withoutAPlanARefusedKeyIsSaidAboveNoForecast() {
        show(Outlook.NoForecast("Open-Meteo: 401 Unauthorized", false, listOf(ProviderId("open-meteo"))))

        val warning = text(R.string.key_refused_warning, "Open-Meteo")
        compose.onNodeWithText(warning).assertIsDisplayed()
        assertThat(compose.liveRegionOf(warning)).isEqualTo(LiveRegionMode.Polite)
        assertThat(top(warning)).isLessThan(top(text(R.string.no_forecast)))
        compose.onNodeWithText(text(R.string.details_in_english, "Open-Meteo: 401 Unauthorized")).assertExists()
    }

    @Test
    fun aForecastWithoutDaysSaysARefusedKeyToo() {
        show(Plans.planned(today.atTime(9, 0).atZone(lisbon), listOf(), source = Plans.source(
            today.atTime(8, 51).atZone(lisbon).toInstant(), keyRefusedBy = listOf(ProviderId("open-meteo")))))

        compose.onNodeWithText(text(R.string.key_refused_warning, "Open-Meteo")).assertIsDisplayed()
    }

    @Test
    fun withoutAPlanOrARefusedKeyThereIsNoSuchWarning() {
        show(Outlook.NoForecast("Open-Meteo: no network"))

        compose.onNodeWithText(text(R.string.key_refused_warning, "Open-Meteo")).assertDoesNotExist()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)).assertDoesNotExist()
    }

    @Test
    fun onALaterDayTheRefusedKeyAndTheCardShowOnceEach() {
        clock("24")
        show(lisbonWeek(keyRefusedBy = listOf(ProviderId("open-meteo"))), card = card())

        tab("Wednesday").performClick()
        compose.waitForIdle()

        tab("Wednesday").assertIsSelected()
        compose.onAllNodesWithText(text(R.string.key_refused_warning, "Open-Meteo")).assertCountEquals(1)
        compose.onNodeWithText(text(R.string.key_refused_warning, "Open-Meteo")).assertIsDisplayed()
        compose.onAllNodesWithText(text(R.string.notification_card_title)).assertCountEquals(1)
        compose.onNodeWithText(text(R.string.notification_card_title)).assertIsDisplayed()
    }

    @Test
    fun halfWayBetweenTwoDaysTheRefusedKeyAndTheCardAreStillOneEach() {
        show(lisbonWeek(keyRefusedBy = listOf(ProviderId("open-meteo"))), card = card())
        compose.mainClock.autoAdvance = false

        compose.onNode(page).performTouchInput {
            down(center)
            moveBy(Offset(-width / 2f, 0f))
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()

        assertThat(compose.onAllNodes(page).fetchSemanticsNodes()).hasSize(2)
        compose.onAllNodesWithText(text(R.string.key_refused_warning, "Open-Meteo")).assertCountEquals(1)
        compose.onAllNodesWithText(text(R.string.notification_card_title)).assertCountEquals(1)
        compose.onAllNodes(page).onFirst().performTouchInput { up() }
        compose.mainClock.autoAdvance = true
    }

    /** The card for a phone whose notifications are not allowed. */
    private fun card(memory: Memory = Memory(), canAsk: Boolean = true) =
        NotificationCard(memory, { false }, canAsk, scope)

    private fun lineTop(shown: String): Float =
        compose.onNodeWithText(shown, useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y

    private fun top(shown: String): Float =
        compose.onNode(hasText(shown) and !NOT_READ_APART).fetchSemanticsNode().positionInRoot.y

    /** The top of today's card, whose heading is the day's name. */
    private fun todaysCardTop(): Float = compose.onNode(isHeading() and hasText("Today"), useUnmergedTree = true)
        .fetchSemanticsNode().positionInRoot.y

    @Test
    fun theNotificationCardSaysWhatItIsForAfterTheWarningsAndBeforeTheDay() {
        clock("24")
        show(lisbonWeek(freshness = Freshness.STALE), card = card())

        val title = text(R.string.notification_card_title)
        compose.onNode(hasText(title) and isHeading()).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.notification_card_body)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.notification_card_ask)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.notification_card_no_ask)).assertDoesNotExist()
        assertThat(top(title)).isGreaterThan(top(text(R.string.stale_warning, "08:51")))
            .isLessThan(todaysCardTop())
        assertThat(compose.liveRegionOf(title)).isNull()
        assertThat(compose.liveRegionOf(text(R.string.notification_card_body))).isNull()
    }

    @Test
    fun theNotificationCardShowsWithoutAPlanToo() {
        show(Outlook.NoForecast("Open-Meteo: no network"), card = card())

        compose.onNodeWithText(text(R.string.notification_card_title)).assertIsDisplayed()
        assertThat(top(text(R.string.notification_card_title))).isLessThan(top(text(R.string.no_forecast)))
    }

    // On Android 16: Android 17 answers a request at once when the permission is granted already, and Robolectric 4.17
    // counts every permission the app's manifest names as granted there, so the question is answered "allowed" before
    // the refusal below can be given (#321); try it on 37 again with each Robolectric update
    @Test
    @Config(sdk = [36])
    fun theNotificationCardsButtonAsksAndroidAndARefusalLeadsToTheSettings() {
        val memory = Memory()
        show(lisbonWeek(), card = card(memory))

        compose.onNodeWithText(text(R.string.notification_card_ask)).performScrollTo().performClick()
        compose.waitForIdle()
        val asked = shadowOf(compose.activity).lastRequestedPermission
        assertThat(asked.requestedPermissions).containsExactly(Manifest.permission.POST_NOTIFICATIONS)
        // Android's answer, as the activity hands it to the Activity Result API
        compose.runOnUiThread {
            compose.activity.activityResultRegistry.dispatchResult(asked.requestCode, Activity.RESULT_OK, Intent()
                .putExtra(RequestMultiplePermissions.EXTRA_PERMISSIONS, asked.requestedPermissions)
                .putExtra(RequestMultiplePermissions.EXTRA_PERMISSION_GRANT_RESULTS,
                    intArrayOf(PackageManager.PERMISSION_DENIED)))
        }
        compose.waitForIdle()

        assertThat(memory.refused).isTrue()
        compose.onNodeWithText(text(R.string.notification_card_ask)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.notification_card_no_ask)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.notification_card_open_settings)).assertIsDisplayed()
        // the button the focus was on is gone, so the card's title takes it
        compose.onNode(isHeading() and hasText(text(R.string.notification_card_title))).assertIsFocused()
    }

    @Test
    fun aCardThatLeadsToTheSettingsFromTheStartLeavesTheFocusWithTheHomesTitle() {
        show(lisbonWeek(), card = card(Memory(refused = true)))

        compose.onNode(title).assertIsFocused()
        compose.onNode(isHeading() and hasText(text(R.string.notification_card_title))).assertIsNotFocused()
    }

    @Test
    fun theNotificationCardIsReadTitleTextButtonAndCloseLast() {
        show(lisbonWeek(), card = card())

        val close = compose.onNode(hasContentDescription(text(R.string.action_close)) and hasClickAction())
            .fetchSemanticsNode()
        val group = generateSequence(close.parent) { it.parent }
            .first { it.config.getOrNull(SemanticsProperties.IsTraversalGroup) == true }
        val inGroup = hasAnyAncestor(SemanticsMatcher("in the card's group") { it.id == group.id })
        val read = listOf(isHeading() and hasText(text(R.string.notification_card_title)),
            hasText(text(R.string.notification_card_body)), hasText(text(R.string.notification_card_ask)) and
                hasClickAction()).map { compose.onNode(it and inGroup).fetchSemanticsNode() }
        // within the card's group the order is by this index first, then by place: Close, beside the title, is last
        read.forEach {
            assertThat(it.config.getOrNull(SemanticsProperties.TraversalIndex) ?: 0f)
                .isLessThan(close.config[SemanticsProperties.TraversalIndex])
        }
        assertThat(read.map { it.positionInRoot.y }).isSorted()
    }

    /** Shows the card at twice the font size and checks that its button's words break only where a space is. */
    private fun assertTheCardsButtonBreaksOnlyBetweenWords(memory: Memory, button: Int) {
        show(lisbonWeek(), fontScaleTwo = true, card = card(memory))
        val words = text(button)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(words, useUnmergedTree = true).performScrollTo().fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        val layout = layouts.single()
        val breaks = (0 until layout.lineCount - 1).map { layout.getLineEnd(it) }
        assertThat(breaks.filter { words[it - 1] != ' ' }).`as`("the breaks inside a word of \"%s\"", words).isEmpty()
        assertThat((0 until layout.lineCount).filter { layout.isLineEllipsized(it) })
            .`as`("the lines of \"%s\" that end in an ellipsis", words).isEmpty()
        assertThat(layout.didOverflowHeight).`as`("whether \"%s\" is cut off below", words).isFalse()
    }

    @Test
    @Config(qualifiers = "nl-w320dp-h640dp")
    fun onANarrowPhoneInDutchAtTwiceTheFontSizeTheAskButtonBreaksOnlyBetweenWords() =
        assertTheCardsButtonBreaksOnlyBetweenWords(Memory(), R.string.notification_card_ask)

    @Test
    @Config(qualifiers = "nl-w320dp-h640dp")
    fun onANarrowPhoneInDutchAtTwiceTheFontSizeTheSettingsButtonBreaksOnlyBetweenWords() =
        assertTheCardsButtonBreaksOnlyBetweenWords(Memory(refused = true), R.string.notification_card_open_settings)

    @Test
    @Config(qualifiers = "nl")
    fun theNotificationCardAndTheRefusedKeyAreInDutch() {
        show(lisbonWeek(keyRefusedBy = listOf(ProviderId("open-meteo"))), card = card())

        compose.onNode(isHeading() and hasText("Krijg een melding vóór elk laadvenster")).assertExists()
        compose.onNodeWithText("De app meldt het kort voordat een laadvenster begint, zodat je je laadpaal op tijd " +
            "kunt instellen. Dat kun je later wijzigen bij Meldingen in Instellingen.").assertExists()
        compose.onNode(hasText("Meldingen aanzetten") and hasClickAction()).assertExists()
        compose.onNode(hasContentDescription("Sluiten") and hasClickAction()).assertExists()
        compose.onNodeWithText("Open-Meteo weigert de API-sleutel. Controleer de sleutel bij Voorspelling in " +
            "Instellingen.").assertExists()
    }

    @Test
    @Config(qualifiers = "nl")
    fun afterARefusalTheNotificationCardIsInDutch() {
        show(lisbonWeek(), card = card(Memory(refused = true)))

        compose.onNodeWithText("Android vraagt het niet nog een keer. Zet meldingen daar aan.").assertExists()
        compose.onNode(hasText("Instellingen voor meldingen openen") and hasClickAction()).assertExists()
    }

    @Test
    fun afterARefusalTheNotificationCardsButtonOpensTheAppsNotificationSettings() {
        show(lisbonWeek(), card = card(Memory(refused = true)))

        compose.onNodeWithText(text(R.string.notification_card_open_settings)).performScrollTo().performClick()

        val started = shadowOf(compose.activity).nextStartedActivity
        assertThat(started.action).isEqualTo(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        assertThat(started.getStringExtra(Settings.EXTRA_APP_PACKAGE)).isEqualTo(compose.activity.packageName)
    }

    @Test
    fun whereAndroidCannotAskTheNotificationCardLeadsToTheSettingsAtOnce() {
        show(lisbonWeek(), card = card(canAsk = false))

        compose.onNodeWithText(text(R.string.notification_card_open_settings)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.notification_card_no_ask)).assertIsDisplayed()
    }

    @Test
    fun theNotificationCardsCloseTakesItAwayForGoodAndTheFocusToTheTitle() {
        val memory = Memory()
        show(lisbonWeek(), card = card(memory))
        compose.runOnIdle { focus.clearFocus() }

        compose.onNode(hasContentDescription(text(R.string.action_close)) and hasClickAction())
            .performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.notification_card_title)).assertDoesNotExist()
        compose.onNode(title).assertIsFocused()
        assertThat(memory.closed).isTrue()
    }

    @Test
    fun aClosedNotificationCardDoesNotShow() {
        show(lisbonWeek(), card = card(Memory(closed = true)))

        compose.onNodeWithText(text(R.string.notification_card_title)).assertDoesNotExist()
    }

    @Test
    fun theNotificationCardWrapsAndItsControlsAreNamedAndLargeEnoughAtTwiceTheFontSize() {
        show(lisbonWeek(), fontScaleTwo = true, card = card())

        compose.assertEveryControlIsNamedAndLargeEnough()
        compose.assertScrollsToShow(hasText(Plans.CREDIT, substring = true))
        compose.onNodeWithText(text(R.string.notification_card_body)).performScrollTo().assertIsDisplayed()
    }

    /** Monday's plan in Lisbon with today's forecast from this source. */
    private fun footerOf(source: (Instant) -> ForecastSource): String {
        clock("24")
        show(Plans.planned(today.atTime(9, 0).atZone(lisbon), listOf(listOf()),
            source = source(today.atTime(8, 51).atZone(lisbon).toInstant())))
        return compose.onNodeWithText(Plans.CREDIT, substring = true).performScrollTo()
            .fetchSemanticsNode().config[SemanticsProperties.Text].single().text
    }

    @Test
    fun theFooterNamesTheProviderTheFallbackStoodInForAndUntilWhenItIsBusy() {
        val busy = today.atTime(9, 30).atZone(lisbon).toInstant()

        val footer = footerOf { Plans.source(it, role = ProviderRole.FALLBACK, primaryName = "Forecast.Solar",
            busyUntil = busy) }

        assertThat(footer).isEqualTo(listOf(Plans.CREDIT, text(R.string.footer_fallback, "Forecast.Solar"),
            text(R.string.footer_updated, "08:51"), text(R.string.footer_busy, "09:30")).joinToString(" · "))
    }

    @Test
    fun theFooterOfAFallbackForAnUnnamedProviderSaysTheChosenOneWasUnavailable() {
        val footer = footerOf { Plans.source(it, role = ProviderRole.FALLBACK) }

        assertThat(footer).isEqualTo(listOf(Plans.CREDIT, text(R.string.footer_fallback_unknown),
            text(R.string.footer_updated, "08:51")).joinToString(" · "))
    }

    @Test
    fun awayPeriodsBeyondTomorrowAreNamedByTheirDateAndThoseOverDaysByBothEnds() {
        clock("24")
        access.away = listOf(OneOffAwayPeriod.allDay(today.plusDays(3), lisbon),
            OneOffAwayPeriod(today.plusDays(2).atTime(18, 0).atZone(lisbon).toInstant(),
                today.plusDays(4).atTime(9, 0).atZone(lisbon).toInstant()))

        show(lisbonWeek())

        compose.onNodeWithText(text(R.string.one_off_all_day, "Thu, Jun 4")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.one_off_days, "Wed, Jun 3", "18:00", "Fri, Jun 5", "09:00"))
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun gridAdviceForALaterDayNamesItsWeekday() {
        show(lisbonWeek(GridAdvice(today.plusDays(2), 12.0, 18.0, 7.4, Optional.empty(), Optional.empty()),
            minimum = 30.0))

        compose.onNodeWithText(text(R.string.advice_title_no_window, "7.4", "Wednesday")).performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText(text(R.string.advice_ends_low, "Wednesday", "12", "30") + " " +
            text(R.string.advice_no_room)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aDaysLaterWindowsAreListedInItsNote() {
        clock("24")
        show(Plans.planned(today.atTime(9, 0).atZone(lisbon), listOf(listOf(Plans.window(lisbon, today, 10, 11),
            Plans.window(lisbon, today, 13, 14), Plans.window(lisbon, today, 16, 17))), assumed = false))

        compose.onNodeWithText(text(R.string.day_later_windows,
            "13:00 – 14:00 at 6 A, 16:00 – 17:00 at 6 A")).assertIsDisplayed()
    }

    @Test
    fun aRefreshToFewerDaysOnALaterTabShowsADayThatIsThere() {
        clock("24")
        val home = show(lisbonWeek())
        tab("Wednesday").performClick()
        compose.waitForIdle()

        home.refresh()
        compose.waitForIdle()
        answer(Plans.planned(today.atTime(9, 0).atZone(lisbon), listOf(listOf(Plans.window(lisbon, today, 13, 15)))))

        tab("Today").assertIsSelected()
        compose.onNodeWithText("13:00 – 15:00").assertIsDisplayed()
    }

    @Test
    fun aDayKeepsWhereItWasScrolledWhileAnotherDayIsShown() {
        show(lisbonWeek(), fontScaleTwo = true)
        val footer = compose.onNodeWithText(Plans.CREDIT, substring = true)
        footer.performScrollTo().assertIsDisplayed()

        tab("Wednesday").performScrollTo().performClick()
        compose.waitForIdle()
        tab("Today").performScrollTo().performClick()
        compose.waitForIdle()

        footer.assertIsDisplayed()
    }

    private fun failedSave() {
        access.writeFails = true
        saves.update(KeyChanges.NONE) { it.withSystem(it.system().withHouseAndCar(House(0.9), it.system().car()),
            it.typical()) }
    }

    private fun warningWords() = text(R.string.stale_warning, "08:51")

    @Test
    fun theStaleWarningIsOnePolitePieceOfTextWithoutTheSignInIt() {
        clock("24")
        show(lisbonWeek(freshness = Freshness.STALE))

        val region = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))

        val config = region.fetchSemanticsNode().config
        assertThat(config[SemanticsProperties.Text].joinToString("") { it.text }).isEqualTo(warningWords())
        assertThat(config.getOrNull(SemanticsProperties.ContentDescription)).isNull()
    }

    @Test
    fun theNoticeAndTheStaleWarningFollowTheSelectedTabOnceEach() {
        clock("24")
        failedSave()
        show(lisbonWeek(freshness = Freshness.STALE))

        tab("Tomorrow").performClick()
        compose.waitForIdle()

        compose.onAllNodesWithText(text(R.string.settings_save_failed)).assertCountEquals(1)
        compose.onAllNodesWithText(text(R.string.action_close)).assertCountEquals(1)
        compose.onAllNodesWithText(warningWords()).assertCountEquals(1)
    }

    @Test
    fun halfWayBetweenTwoDaysTheNoticeAndTheStaleWarningAreStillOneEach() {
        clock("24")
        failedSave()
        show(lisbonWeek(freshness = Freshness.STALE))
        compose.mainClock.autoAdvance = false

        compose.onNode(page).performTouchInput {
            down(center)
            moveBy(Offset(-width / 2f, 0f))
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()

        // two days are in the pager now: the notice and the warning must still be in one of them only
        assertThat(compose.onAllNodes(page).fetchSemanticsNodes()).hasSize(2)
        compose.onAllNodesWithText(text(R.string.action_close)).assertCountEquals(1)
        compose.onAllNodesWithText(warningWords()).assertCountEquals(1)
        compose.onAllNodes(page).onFirst().performTouchInput { up() }
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun theSolarChartIsOneImageThatSaysTheDayInASentenceThatAlsoShowsUnderIt() {
        clock("24")
        show(sunnyDays())

        val said = listOf(text(R.string.chart_spoken, "Today", "3.2", "12:00", "0.3"),
            text(R.string.chart_spoken_windows, "13:00 to 15:00, 16:00 to 17:00"),
            text(R.string.chart_spoken_now, "09:00")).joinToString(" ")
        compose.assertReadAsOne(said, Role.Image).performScrollTo().assertIsDisplayed()
        compose.assertShownButNotReadApart(said)
        compose.onNode(isHeading() and hasText(text(R.string.chart_heading))).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theLegendNamesTheSolarCurveTheHouseAndTheChargeWindowsInWords() {
        show(sunnyDays())
        // on a day without a window, as today's card says "Charge window" too, to be read
        tab("Tomorrow").performClick()
        compose.waitForIdle()

        listOf(R.string.chart_legend_solar, R.string.chart_legend_house, R.string.chart_legend_window).forEach {
            compose.assertShownButNotReadApart(text(it))
        }
    }

    @Test
    fun onTodayTheLegendsThreeEntriesAreShownButNotReadApart() {
        show(sunnyDays())

        // today's own card says "Charge window" too, to be read: the legend's is the hidden one of the two
        listOf(R.string.chart_legend_solar, R.string.chart_legend_house, R.string.chart_legend_window).forEach {
            compose.onAllNodes(hasText(text(it)) and NOT_READ_APART, useUnmergedTree = true).assertCountEquals(1)
        }
        compose.onAllNodes(hasText(text(R.string.chart_legend_solar)) and !NOT_READ_APART, useUnmergedTree = true)
            .assertCountEquals(0)
        compose.onAllNodes(hasText(text(R.string.chart_legend_house)) and !NOT_READ_APART, useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun onAnotherDayTheChartSaysThatDayWithoutNow() {
        clock("24")
        show(sunnyDays())

        tab("Tomorrow").performClick()
        compose.waitForIdle()

        compose.assertReadAsOne(text(R.string.chart_spoken, "Tomorrow", "3.2", "12:00", "0.3"), Role.Image)
    }

    @Test
    fun aDayWithoutSunSaysItsChartHasNone() {
        show(lisbonWeek())

        compose.assertReadAsOne(text(R.string.chart_spoken_no_solar, "Today"), Role.Image)
    }

    @Test
    fun theTimeAxisMarksEveryThirdHourOnThePhones24HourClock() {
        clock("24")
        show(sunnyDays())

        assertThat(axisLabels()).containsExactly("06", "09", "12", "15", "18")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun theTimeAxisMarksFewerHoursAtTwiceTheFontSizeOnThePhones12HourClock() {
        clock("12")
        var twice by mutableStateOf(false)
        val outlook = HomeOutlook(access, scope, clock) { }
        val notice = HomeNotice(saves, scope) { }
        val battery = entry()
        val away = awayEntry()
        val trips = TripEntry(SavedStateHandle(), NumberText(Locale.ENGLISH), access, scope, clock) { }
        compose.setContent {
            AppTheme {
                if (twice) {
                    AtFontScaleTwo { HomeScreen(outlook, notice, battery, away, trips, { amsterdam }, noCard) { } }
                } else {
                    HomeScreen(outlook, notice, battery, away, trips, { amsterdam }, noCard) { }
                }
            }
        }
        compose.waitForIdle()
        answer(sunnyDays())
        val normal = axisLabels()

        twice = true
        compose.waitForIdle()
        val large = axisLabels()

        assertThat(normal).hasSize(5).allMatch { it.endsWith("AM") || it.endsWith("PM") }
        assertThat(large).hasSize(3).isSubsetOf(normal)
    }

    @Test
    fun theBatteryBarIsOneImageThatSaysTheLevelsWhichAlsoShowWithItsMarks() {
        show(lisbonWeek())

        compose.assertReadAsOne(todaysBattery("40", "60", "20", "80"), Role.Image)
            .performScrollTo().assertIsDisplayed()
        compose.onNode(isHeading() and hasText(text(R.string.battery_heading))).performScrollTo().assertIsDisplayed()
        compose.assertShownButNotReadApart(text(R.string.battery_levels, "40", "60"))
        compose.assertShownButNotReadApart(text(R.string.battery_minimum, "20"))
        compose.assertShownButNotReadApart(text(R.string.battery_target, "80"))
    }

    @Test
    fun onAnotherDayTheBatteryBarSaysTheLevelAtItsStart() {
        show(lisbonWeek())

        tab("Wednesday").performClick()
        compose.waitForIdle()

        compose.assertReadAsOne(text(R.string.battery_spoken_day, "40", "Wednesday", "50", "20", "80"), Role.Image)
        compose.assertShownButNotReadApart(text(R.string.battery_levels, "40", "50"))
    }

    @Test
    @Config(qualifiers = "w480dp-h320dp-land")
    fun onASmallPhoneOnItsSideAtTwiceTheFontSizeTheChartAndTheBatteryBarCanBeScrolledTo() {
        show(sunnyDays(), fontScaleTwo = true)

        compose.onNode(SemanticsMatcher.keyIsDefined(AxisLabels), useUnmergedTree = true).performScrollTo()
            .assertIsDisplayed()
        compose.onNode(hasContentDescription(todaysBattery("40", "60", "20", "80")),
            useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.assertShownButNotReadApart(text(R.string.battery_target, "80"))
        compose.assertScrollsToShow(hasText(Plans.CREDIT, substring = true))
    }

    private fun chart(): SemanticsNode =
        compose.onNode(SemanticsMatcher.keyIsDefined(AxisLabels), useUnmergedTree = true).fetchSemanticsNode()

    private fun saying(sentence: String): Rect =
        compose.onNode(hasContentDescription(sentence), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun shown(words: String): Rect =
        compose.onNodeWithText(words, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    /** The width of the cards' column in pixels: the day's page less its padding and the card's, 16 dp each. */
    private fun column(): Float {
        val page = compose.onNode(page and hasAnyDescendant(SemanticsMatcher.keyIsDefined(AxisLabels)),
            useUnmergedTree = true).fetchSemanticsNode()
        return page.size.width - 64 * page.layoutInfo.density.density
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun onANarrowColumnTheChartTakesItsWidthAndIsStill160DpHigh() {
        show(sunnyDays())

        // half of the column's 296 dp would be lower than 160 dp
        val chart = chart()
        assertThat(chart.size.width.toFloat()).isCloseTo(column(), within(1f))
        assertThat(chart.size.height / chart.layoutInfo.density.density).isCloseTo(160f, within(0.5f))
    }

    @Test
    @Config(qualifiers = "w900dp-h1200dp")
    fun onAWideColumnTheChartTakesItsWidthAndIsHalfAsHigh() {
        show(sunnyDays())

        val chart = chart()
        assertThat(chart.size.width.toFloat()).isCloseTo(column(), within(1f))
        assertThat(chart.size.height.toFloat()).isCloseTo(chart.size.width / 2f, within(1f))
    }

    @Test
    fun marksCloseTogetherHaveTheirWordsOnTwoLinesBothWithinTheBar() {
        show(Plans.planned(today.atTime(9, 0).atZone(lisbon), listOf(listOf()), minimum = 70.0, target = 75.0))
        compose.onNodeWithText(text(R.string.battery_target, "75"), useUnmergedTree = true).performScrollTo()

        val bar = saying(todaysBattery("40", "40", "70", "75"))
        val minimum = shown(text(R.string.battery_minimum, "70"))
        val target = shown(text(R.string.battery_target, "75"))
        assertThat(minimum.overlaps(target)).`as`("the marks' words %s and %s overlap", minimum, target).isFalse()
        listOf(minimum, target).forEach {
            assertThat(it.left).isGreaterThanOrEqualTo(bar.left - 0.5f)
            assertThat(it.right).isLessThanOrEqualTo(bar.right + 0.5f)
            assertThat(it.top).isGreaterThanOrEqualTo(bar.top - 0.5f)
            assertThat(it.bottom).isLessThanOrEqualTo(bar.bottom + 0.5f)
        }
    }

    @Test
    fun rightToLeftTheBatteryBarRunsLeftToRightWithEachMarksWordsUnderIt() {
        val outlook = HomeOutlook(access, scope, clock) { }
        val notice = HomeNotice(saves, scope) { }
        val battery = entry()
        val away = awayEntry()
        val trips = TripEntry(SavedStateHandle(), NumberText(Locale.ENGLISH), access, scope, clock) { }
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                AppTheme { HomeScreen(outlook, notice, battery, away, trips, { amsterdam }, noCard) { } }
            }
        }
        compose.waitForIdle()
        answer(lisbonWeek())
        compose.onNodeWithText(text(R.string.battery_target, "80"), useUnmergedTree = true).performScrollTo()

        val bar = saying(todaysBattery("40", "60", "20", "80"))
        assertThat(shown(text(R.string.battery_minimum, "20")).center.x)
            .isCloseTo(bar.left + bar.width * 0.2f, within(1f))
        assertThat(shown(text(R.string.battery_target, "80")).center.x)
            .isCloseTo(bar.left + bar.width * 0.8f, within(1f))
    }

    @Test
    fun rightToLeftTheSolarChartIsDrawnAsLeftToRightWithItsWordsWhereTheyWere() {
        clock("24")
        var rtl by mutableStateOf(false)
        val outlook = HomeOutlook(access, scope, clock) { }
        val notice = HomeNotice(saves, scope) { }
        val battery = entry()
        val away = awayEntry()
        val trips = TripEntry(SavedStateHandle(), NumberText(Locale.ENGLISH), access, scope, clock) { }
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides
                if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                AppTheme { HomeScreen(outlook, notice, battery, away, trips, { amsterdam }, noCard) { } }
            }
        }
        compose.waitForIdle()
        answer(sunnyDays())
        compose.onNode(SemanticsMatcher.keyIsDefined(AxisLabels), useUnmergedTree = true).performScrollTo()
        val leftToRight = compose.picture(chart().boundsInRoot)
        val leftToRightLabels = axisLabels()

        rtl = true
        compose.waitForIdle()
        compose.onNode(SemanticsMatcher.keyIsDefined(AxisLabels), useUnmergedTree = true).performScrollTo()

        assertThat(axisLabels()).isEqualTo(leftToRightLabels)
        assertThat(compose.picture(chart().boundsInRoot).sameAs(leftToRight))
            .`as`("the chart as drawn right to left").isTrue()
    }

    @Test
    fun onTomorrowTheBatteryBarSaysTomorrowInItsSentence() {
        show(lisbonWeek())

        tab("Tomorrow").performClick()
        compose.waitForIdle()

        compose.assertReadAsOne(text(R.string.battery_spoken_day, "40", "tomorrow", "40", "20", "80"), Role.Image)
    }

    @Test
    @Config(qualifiers = "nl")
    fun onTomorrowTheBatteryBarSaysTomorrowInItsSentenceInDutch() {
        show(lisbonWeek())

        tab("Morgen").performClick()
        compose.waitForIdle()

        compose.assertReadAsOne(text(R.string.battery_spoken_day, "40", "morgen", "40", "20", "80"), Role.Image)
    }

    @Test
    fun theSolarChartComesBeforeTheBatteryBar() {
        show(sunnyDays())

        val chart = compose.onNode(isHeading() and hasText(text(R.string.chart_heading))).fetchSemanticsNode()
        val battery = compose.onNode(isHeading() and hasText(text(R.string.battery_heading))).fetchSemanticsNode()
        // the positions, unlike the bounds, are not cut off where the page ends
        assertThat(chart.positionInRoot.y).isLessThan(battery.positionInRoot.y)
    }

    /** The colour on the card in its padding, left of its heading, once scrolled to, as the screen draws it. */
    private fun groundBeside(heading: Int): Color {
        val node = compose.onNode(isHeading() and hasText(text(heading))).performScrollTo().fetchSemanticsNode()
        val bounds = node.boundsInRoot
        return Color(compose.screenshot().getPixel((bounds.left - 8 * node.layoutInfo.density.density).roundToInt(),
            bounds.center.y.roundToInt()))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun inLightTheChartAndTheBatteryBarAreOnTheirOwnGroundNotTheWallpapers() {
        show(sunnyDays())

        assertThat(groundBeside(R.string.chart_heading)).isEqualTo(SourceColours.LIGHT.chart.ground)
        assertThat(groundBeside(R.string.battery_heading)).isEqualTo(SourceColours.LIGHT.chart.ground)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "night")
    fun inDarkTheChartAndTheBatteryBarAreOnTheirOwnGroundNotTheWallpapers() {
        show(sunnyDays())

        assertThat(groundBeside(R.string.chart_heading)).isEqualTo(SourceColours.DARK.chart.ground)
        assertThat(groundBeside(R.string.battery_heading)).isEqualTo(SourceColours.DARK.chart.ground)
    }
}
