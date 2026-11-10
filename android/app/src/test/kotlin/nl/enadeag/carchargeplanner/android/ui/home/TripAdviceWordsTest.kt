package nl.enadeag.carchargeplanner.android.ui.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.file.Files
import java.util.Locale
import java.util.Optional
import java.util.TimeZone
import nl.enadeag.carchargeplanner.android.home.Plans
import nl.enadeag.carchargeplanner.android.home.TripAdvices.BRUSSELS
import nl.enadeag.carchargeplanner.android.home.TripAdvices.SOLAR
import nl.enadeag.carchargeplanner.android.home.TripAdvices.TODAY
import nl.enadeag.carchargeplanner.android.home.TripAdvices.TOMORROW
import nl.enadeag.carchargeplanner.android.home.TripAdvices.advice
import nl.enadeag.carchargeplanner.android.home.TripAdvices.at
import nl.enadeag.carchargeplanner.android.home.TripAdvices.grid
import nl.enadeag.carchargeplanner.android.home.TripAdvices.level
import nl.enadeag.carchargeplanner.android.home.TripAdvices.model
import nl.enadeag.carchargeplanner.android.notify.LockScreen
import nl.enadeag.carchargeplanner.android.ui.notify.NoticeWords
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.dashboard.BatteryLevels
import nl.enadeag.carchargeplanner.engine.dashboard.ChargeLevels
import nl.enadeag.carchargeplanner.engine.dashboard.DashboardDay
import nl.enadeag.carchargeplanner.engine.dashboard.DayChart
import nl.enadeag.carchargeplanner.engine.dashboard.DayName
import nl.enadeag.carchargeplanner.engine.dashboard.NoWindowReason
import nl.enadeag.carchargeplanner.engine.dashboard.TripAdviceView
import nl.enadeag.carchargeplanner.engine.dashboard.TripPlan
import nl.enadeag.carchargeplanner.engine.notify.AnnouncedWindows
import nl.enadeag.carchargeplanner.engine.notify.Announcer
import nl.enadeag.carchargeplanner.engine.notify.Notice
import nl.enadeag.carchargeplanner.engine.notify.PlanLevel
import nl.enadeag.carchargeplanner.engine.notify.ToldTrips
import nl.enadeag.carchargeplanner.engine.notify.TripChargeStart
import nl.enadeag.carchargeplanner.engine.plan.TripAdvice
import nl.enadeag.carchargeplanner.engine.plan.TripGridCharge.Start
import nl.enadeag.carchargeplanner.engine.plan.Window
import nl.enadeag.carchargeplanner.engine.store.FilePrivacy
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The trip advice in words, as the desktop's `TripAdviceMapperTest` has them for the same advice, and the battery's
 * and the day card's words on a trip's day.
 */
@RunWith(AndroidJUnit4::class)
class TripAdviceWordsTest {

    private companion object {
        const val NBSP = ' '
    }

    private val resources = ApplicationProvider.getApplicationContext<Context>().resources
    private val zoneBefore: TimeZone = TimeZone.getDefault()

    @After
    fun zoneBack() {
        TimeZone.setDefault(zoneBefore)
    }

    private fun words(locale: Locale = Locale.ENGLISH, twentyFour: Boolean = true) =
        DashboardWords(resources, locale, twentyFour, BRUSSELS)

    private fun onToday(model: TripAdviceView, locale: Locale = Locale.ENGLISH, twentyFour: Boolean = true,
        assumed: Boolean = true) = words(locale, twentyFour).tripAdvice(model, DayName.Today(TODAY), level(assumed))

    private fun onTomorrow(model: TripAdviceView, locale: Locale = Locale.ENGLISH) =
        words(locale).tripAdvice(model, DayName.Tomorrow(TOMORROW), level())

    private fun span(from: String, to: String) = "$from$NBSP–$NBSP$to"

    @Test
    fun aTripChargedOnSolarPowerAndThenFromTheGridNamesBothPartsTheStartAndTheChargeLimit() {
        val model = model(advice(84.2, listOf(SOLAR), 60.0, grid(at(TODAY, 21, 0), Start.PLANNED), 84.2, 0.0))

        val today = onToday(model)
        val saturday = onTomorrow(model)

        assertThat(today.label).isEqualTo("Trip advice")
        assertThat(today.title).isEqualTo("Trip tomorrow at 08:15, 150 km there and back.")
        assertThat(today.plan).isEqualTo("Charge to 85%: on solar power today ${span("12:00", "15:00")}, then from " +
            "the grid. Start tonight at 21:00. Set 16 A (3.7 kW), and a charge limit of 85% in the car or the charger.")
        assertThat(today.level).isEqualTo("Assuming a battery level of 45%.")
        assertThat(saturday.title).isEqualTo("Trip Saturday at 08:15, 150 km there and back.")
        assertThat(saturday.plan).isEqualTo("Charge to 85%: on solar power Friday ${span("12:00", "15:00")}, then " +
            "from the grid. Start Friday at 21:00. Set 16 A (3.7 kW), and a charge limit of 85% in the car or the " +
            "charger.")
    }

    @Test
    @Config(qualifiers = "nl")
    fun anotherDaysTabNamesEveryDayByItsWeekdayInTheOwnersLanguage() {
        val tomorrowNight = Window(at(TOMORROW, 6, 0), at(TOMORROW, 7, 0), 6, 1.4, 0.3, 0.2)
        val model = model(advice(90.0, listOf(SOLAR, tomorrowNight), 60.0, grid(at(TODAY, 16, 0), Start.NOW), 90.0,
            0.0))
        val dutch = Locale.forLanguageTag("nl")

        val saturday = onTomorrow(model, dutch)
        val today = onToday(model, dutch)

        assertThat(saturday.title).isEqualTo("Rit zaterdag om 08:15, 150 km heen en terug.")
        assertThat(saturday.plan).startsWith("Laad tot 90%: op zonnestroom vrijdag ${span("12:00", "15:00")}, " +
            "zaterdag ${span("06:00", "07:00")}, daarna uit het net. Start het laden vrijdag om 16:00: ")
        assertThat(today.title).isEqualTo("Rit morgen om 08:15, 150 km heen en terug.")
        assertThat(today.plan).startsWith("Laad tot 90%: op zonnestroom vandaag ${span("12:00", "15:00")}, " +
            "morgen ${span("06:00", "07:00")}, daarna uit het net. Start het laden vandaag om 16:00: ")
    }

    @Test
    @Config(qualifiers = "nl")
    fun inDutchTheAdviceSpeaksOfRitZonnestroomUitHetNetAndLaadlimiet() {
        val model = model(advice(84.2, listOf(SOLAR), 60.0, grid(at(TODAY, 21, 0), Start.PLANNED), 84.2, 0.0))

        val today = onToday(model, Locale.forLanguageTag("nl"))

        assertThat(today.label).isEqualTo("Advies voor de rit")
        assertThat(today.title).isEqualTo("Rit morgen om 08:15, 150 km heen en terug.")
        assertThat(today.plan).isEqualTo("Laad tot 85%: op zonnestroom vandaag ${span("12:00", "15:00")}, daarna " +
            "uit het net. Start het laden vanavond om 21:00. Stel 16 A in (3,7 kW), en een laadlimiet van 85% in de " +
            "auto of de laadpaal.")
        assertThat(today.level).isEqualTo("Uitgaande van een accuniveau van 45%.")
        assertThat(today.spoken).isEqualTo(
            "Advies voor de rit: Rit morgen om 08:15, 150 km heen en terug. Laad tot 85%: op zonnestroom vandaag " +
                "12:00 tot 15:00, daarna uit het net. Start het laden vanavond om 21:00. Stel 16 A in (3,7 kW), " +
                "en een laadlimiet van 85% in de auto of de laadpaal. Uitgaande van een accuniveau van 45%.",
        )
    }

    @Test
    fun aScreenReaderReadsTheSolarWindowsWithToNotTheDash() {
        val later = Window(at(TODAY, 16, 0), at(TODAY, 17, 30), 6, 1.4, 2.0, 0.0)
        val solar = model(advice(93.6, listOf(SOLAR, later), 94.0, null, 94.0, 0.0))
        val solarThenGrid = model(advice(84.2, listOf(SOLAR), 60.0, grid(at(TODAY, 21, 0), Start.PLANNED), 84.2,
            0.0))

        assertThat(onToday(solar).spoken).isEqualTo("Trip advice: Trip tomorrow at 08:15, 150 km there and back. " +
            "Charge to 94% on solar power: today 12:00 to 15:00, 16:00 to 17:30. Assuming a battery level of 45%.")
        assertThat(onToday(solarThenGrid).spoken).isEqualTo("Trip advice: Trip tomorrow at 08:15, 150 km there and " +
            "back. Charge to 85%: on solar power today 12:00 to 15:00, then from the grid. Start tonight at 21:00. " +
            "Set 16 A (3.7 kW), and a charge limit of 85% in the car or the charger. Assuming a battery level of 45%.")
    }

    @Test
    @Config(qualifiers = "nl")
    fun aScreenReaderReadsTheSolarWindowsWithTotInDutch() {
        val dutch = Locale.forLanguageTag("nl")
        val solar = model(advice(93.6, listOf(SOLAR), 94.0, null, 94.0, 0.0))

        assertThat(onToday(solar, dutch).spoken).isEqualTo("Advies voor de rit: Rit morgen om 08:15, 150 km heen en " +
            "terug. Laad tot 94% op zonnestroom: vandaag 12:00 tot 15:00. Uitgaande van een accuniveau van 45%.")
    }

    @Test
    fun onA12HourClockAScreenReaderReadsTheSolarWindowsWithTo() {
        val solar = model(advice(93.6, listOf(SOLAR), 94.0, null, 94.0, 0.0))
        val solarThenGrid = model(advice(84.2, listOf(SOLAR), 60.0, grid(at(TODAY, 21, 0), Start.PLANNED), 84.2,
            0.0))
        val words = words(twentyFour = false)
        val noon = words.time(SOLAR.start())
        val three = words.time(SOLAR.end())

        assertThat(onToday(solar, twentyFour = false).spoken)
            .contains("Charge to 94% on solar power: today $noon to $three.").doesNotContain("–")
        assertThat(onToday(solarThenGrid, twentyFour = false).spoken)
            .contains("on solar power today $noon to $three, then from the grid.").doesNotContain("–")
    }

    @Test
    @Config(qualifiers = "nl")
    fun onA12HourClockInDutchTheTimesFollowItAndAScreenReaderSaysTot() {
        val dutch = Locale.forLanguageTag("nl")
        val model = model(advice(84.2, listOf(SOLAR), 60.0, grid(at(TODAY, 21, 0), Start.PLANNED), 84.2, 0.0))
        val words = words(dutch, twentyFour = false)
        val noon = words.time(SOLAR.start())
        val three = words.time(SOLAR.end())
        val nine = words.time(at(TODAY, 21, 0))

        val shown = onToday(model, dutch, twentyFour = false)

        assertThat(nine).doesNotContain("21")
        assertThat(shown.plan).startsWith("Laad tot 85%: op zonnestroom vandaag ${span(noon, three)}, daarna uit " +
            "het net. Start het laden vanavond om $nine.")
        assertThat(shown.spoken).contains("op zonnestroom vandaag $noon tot $three, daarna uit het net.")
            .doesNotContain("–")
    }

    @Test
    fun aStartTomorrowEveningOnTodaysTabIsTomorrowNotTonight() {
        val model = model(advice(90.0, listOf(), 45.0, grid(at(TOMORROW, 21, 0), Start.PLANNED), 90.0, 0.0))

        assertThat(onToday(model).plan).contains("Start tomorrow at 21:00.")
        assertThat(onTomorrow(model).plan).contains("Start Saturday at 21:00.")
    }

    @Test
    @Config(qualifiers = "nl")
    fun aStartTomorrowEveningOnTodaysTabIsMorgenNotVanavondInDutch() {
        val model = model(advice(90.0, listOf(), 45.0, grid(at(TOMORROW, 21, 0), Start.PLANNED), 90.0, 0.0))

        assertThat(onToday(model, Locale.forLanguageTag("nl")).plan).contains("Start het laden morgen om 21:00.")
    }

    @Test
    @Config(qualifiers = "nl")
    fun inDutchTheGridOnlyAdviceAndItsStartsHaveTheirOwnWording() {
        val dutch = Locale.forLanguageTag("nl")
        val cases = listOf(
            Triple(at(TOMORROW, 7, 0), Start.PLANNED, "Start het laden morgen om 07:00."),
            Triple(at(TODAY, 10, 20), Start.NOW, "Start het laden nu: er is weinig tijd tot het vertrek."),
            Triple(at(TODAY, 14, 30), Start.NOW, "Start het laden vandaag om 14:30: er is weinig tijd tot het " +
                "vertrek."),
            Triple(at(TODAY, 10, 20), Start.NOW_OUTSIDE_CHARGING_HOURS, "Start het laden nu, ook al is dat buiten " +
                "je laaduren: wachten laat te weinig tijd over."),
            Triple(at(TODAY, 23, 0), Start.NOW_OUTSIDE_CHARGING_HOURS, "Start het laden vanavond om 23:00, ook al " +
                "is dat buiten je laaduren: wachten laat te weinig tijd over."))

        cases.forEach { (start, why, sentence) ->
            val model = model(advice(90.0, listOf(), 45.0, grid(start, why), 90.0, 0.0))

            assertThat(onToday(model, dutch).plan).isEqualTo("Laad tot 90% uit het net. $sentence Stel 16 A in " +
                "(3,7 kW), en een laadlimiet van 90% in de auto of de laadpaal.")
        }
    }

    @Test
    @Config(qualifiers = "nl")
    fun inDutchAShortTripSolarOnlyAndEnoughHaveTheDesktopsWording() {
        val dutch = Locale.forLanguageTag("nl")
        val later = Window(at(TODAY, 16, 0), at(TODAY, 17, 30), 6, 1.4, 2.0, 0.0)
        val short = model(advice(100.0, listOf(SOLAR), 60.0, grid(at(TODAY, 21, 0), Start.PLANNED), 100.0, 40.2))
        val solar = model(advice(93.6, listOf(SOLAR, later), 94.0, null, 94.0, 0.0))
        val enough = model(advice(70.0, listOf(SOLAR), 74.5, null, 74.5, 0.0))

        assertThat(onToday(short, dutch).plan).endsWith("een laadlimiet van 100% in de auto of de laadpaal. " +
            "De accu komt 41 km tekort voor de rit: plan een laadstop.")
        assertThat(onToday(solar, dutch).plan).isEqualTo("Laad tot 94% op zonnestroom: vandaag " +
            "${span("12:00", "15:00")}, ${span("16:00", "17:30")}.")
        assertThat(onToday(enough, dutch).plan).isEqualTo("De planning laadt genoeg voor deze rit: bij vertrek " +
            "staat de accu op 75%.")
    }

    @Test
    fun aTripChargedFromTheGridAloneSaysSoAndNamesTheDayOfTheStart() {
        val model = model(advice(90.0, listOf(), 45.0, grid(at(TOMORROW, 7, 0), Start.PLANNED), 90.0, 0.0))

        assertThat(onToday(model).plan).isEqualTo("Charge to 90% from the grid. Start tomorrow at 07:00. " +
            "Set 16 A (3.7 kW), and a charge limit of 90% in the car or the charger.")
    }

    @Test
    fun aGridChargeThatCannotWaitHasItsOwnWording() {
        val cases = listOf(
            Triple(at(TODAY, 10, 20), Start.NOW, "Start now: little time is left before the departure."),
            Triple(at(TODAY, 14, 30), Start.NOW, "Start today at 14:30: little time is left before the departure."),
            Triple(at(TODAY, 10, 20), Start.NOW_OUTSIDE_CHARGING_HOURS, "Start now, although that is outside your " +
                "charging hours: waiting for them would leave too little time."),
            Triple(at(TODAY, 23, 0), Start.NOW_OUTSIDE_CHARGING_HOURS, "Start tonight at 23:00, although that is " +
                "outside your charging hours: waiting for them would leave too little time."))

        cases.forEach { (start, why, sentence) ->
            val model = model(advice(90.0, listOf(), 45.0, grid(start, why), 90.0, 0.0))

            assertThat(onToday(model).plan).isEqualTo("Charge to 90% from the grid. $sentence Set 16 A (3.7 kW), " +
                "and a charge limit of 90% in the car or the charger.")
        }
    }

    @Test
    fun aTripThatEvenAFullBatteryCannotMakeAsksForAChargingStopInWholeKmRoundedUp() {
        val model = model(advice(100.0, listOf(SOLAR), 60.0, grid(at(TODAY, 21, 0), Start.PLANNED), 100.0, 40.2))

        assertThat(onToday(model).plan)
            .startsWith("Charge to 100%: on solar power today ${span("12:00", "15:00")}, then from the grid.")
            .endsWith("a charge limit of 100% in the car or the charger. " +
                "The battery is 41 km short for the trip: plan a charging stop.")
    }

    @Test
    @Config(qualifiers = "nl")
    fun aTripTheCarCannotChargeForBeforeItLeavesSaysTheLevelItLeavesWithAndHowFarItFallsShort() {
        val model = model(advice(95.0, listOf(), 60.0, null, 60.0, 120.0))

        assertThat(onToday(model, Locale.forLanguageTag("nl")).plan).isEqualTo("Bij vertrek staat de accu op 60%. " +
            "De accu komt 120 km tekort voor de rit: plan een laadstop.")
    }

    @Test
    fun solarPowerAboveTheTargetNamesEachWindowWithItsDayOnce() {
        val later = Window(at(TODAY, 16, 0), at(TODAY, 17, 30), 6, 1.4, 2.0, 0.0)
        val tomorrow = Window(at(TOMORROW, 6, 30), at(TOMORROW, 7, 15), 6, 1.4, 0.6, 0.1)

        val model = model(advice(93.6, listOf(SOLAR, later, tomorrow), 94.0, null, 94.0, 0.0))

        assertThat(onToday(model).plan).isEqualTo("Charge to 94% on solar power: today ${span("12:00", "15:00")}, " +
            "${span("16:00", "17:30")}, tomorrow ${span("06:30", "07:15")}.")
    }

    @Test
    fun aTripThatNeedsNothingExtraSaysSoBrieflyWithItsLevelRoundedHalfUp() {
        val model = model(advice(70.0, listOf(SOLAR), 74.5, null, 74.5, 0.0))

        assertThat(onToday(model).plan).isEqualTo("The plan charges enough for it: the battery is at 75% when you " +
            "leave.")
    }

    @Test
    fun aLevelTheOwnerEnteredIsNotCalledAssumed() {
        val model = model(advice(70.0, listOf(), 75.0, null, 75.0, 0.0))

        assertThat(onToday(model, assumed = false).level).isEqualTo("Based on the battery level of 45% you entered.")
    }

    @Test
    @Config(qualifiers = "nl")
    fun aLevelTheOwnerEnteredIsNotCalledAssumedInDutch() {
        val model = model(advice(70.0, listOf(), 75.0, null, 75.0, 0.0))

        assertThat(onToday(model, Locale.forLanguageTag("nl"), assumed = false).level)
            .isEqualTo("Uitgaande van het accuniveau van 45% dat je invulde.")
    }

    @Test
    fun aScreenReaderReadsTheWholeAdvice() {
        val model = model(advice(70.0, listOf(), 75.0, null, 75.0, 0.0))

        assertThat(onToday(model).spoken).isEqualTo("Trip advice: Trip tomorrow at 08:15, 150 km there and back. " +
            "The plan charges enough for it: the battery is at 75% when you leave. Assuming a battery level of 45%.")
    }

    @Test
    fun tonightStartsAt18InTheHomesZoneWhateverThePhonesZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"))

        fun start(hour: Int, minute: Int): String {
            val model = model(advice(90.0, listOf(), 45.0, grid(at(TODAY, hour, minute), Start.PLANNED), 90.0, 0.0))
            return onToday(model).plan.substringAfter(". ").substringBefore(". ")
        }

        assertThat(start(17, 59)).isEqualTo("Start today at 17:59")
        assertThat(start(18, 0)).isEqualTo("Start tonight at 18:00")
        assertThat(start(23, 59)).isEqualTo("Start tonight at 23:59")
        assertThat(start(0, 0)).isEqualTo("Start now")
    }

    @Test
    fun aStartAfterMidnightInTheHomeIsTomorrowAndNeverTonight() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        val model = model(advice(90.0, listOf(), 45.0, grid(at(TOMORROW, 0, 30), Start.PLANNED), 90.0, 0.0))

        assertThat(onToday(model).plan).contains("Start tomorrow at 00:30.")
        assertThat(onTomorrow(model).plan).contains("Start Saturday at 00:30.")
    }

    @Test
    fun onA12HourClockTheTimesFollowItAndOnlyTheSpansDashIsKeptTogether() {
        val model = model(advice(93.6, listOf(SOLAR), 94.0, null, 94.0, 0.0))
        val words = words(twentyFour = false)
        val noon = words.time(SOLAR.start())
        val three = words.time(SOLAR.end())

        val shown = onToday(model, twentyFour = false)

        assertThat(noon).contains("12:00").containsIgnoringCase("PM")
        assertThat(shown.title).isEqualTo("Trip tomorrow at ${words.time(at(TOMORROW, 8, 15))}, 150 km there and " +
            "back.")
        assertThat(shown.plan).isEqualTo("Charge to 94% on solar power: today ${span(noon, three)}.")
    }

    @Test
    fun theAdvicesDayIsRelativeOnTodaysTabAndTheWeekdayElsewhereAlsoForTomorrow() {
        val words = words()

        assertThat(words.adviceDay(DayName.of(TOMORROW, TODAY), onToday = true)).isEqualTo("tomorrow")
        assertThat(words.adviceDay(DayName.of(TODAY, TODAY), onToday = true)).isEqualTo("today")
        assertThat(words.adviceDay(DayName.of(TODAY.plusDays(2), TODAY), onToday = true)).isEqualTo("Sunday")
        assertThat(words.adviceDay(DayName.of(TOMORROW, TODAY), onToday = false)).isEqualTo("Saturday")
        assertThat(words.adviceDay(DayName.of(TODAY, TODAY), onToday = false)).isEqualTo("Friday")
    }

    @Test
    @Config(qualifiers = "nl")
    fun inDutchTheWeekdayIsLowerCaseInTheMiddleOfASentence() {
        assertThat(words(Locale.forLanguageTag("nl")).weekday(TOMORROW)).isEqualTo("zaterdag")
    }

    /** A day without a charge window, not the grid advice's. */
    private fun day(name: DayName, levels: BatteryLevels, why: NoWindowReason = NoWindowReason.NOT_NEEDED,
        assumed: Percent? = null) =
        DashboardDay(name, Optional.empty(), listOf(), Optional.of(why), levels,
            Optional.ofNullable(assumed),
            DayChart(6.0, 20.0, 1.0, listOf(), 0.3, listOf(), Optional.empty(), listOf(), Optional.empty()))

    @Test
    fun onTheDayATripLeavesTheBatteryChipAndLineSayTheLevelAfterTheTrip() {
        val words = words()
        val charged = BatteryLevels(Percent(50.0), Percent(74.0), Optional.of(Percent(70.0)))
        val notCharged = BatteryLevels(Percent(50.0), Percent(50.4), Optional.of(Percent(30.0)))

        assertThat(words.figure(SourceFigure.Battery(charged))).isEqualTo("Battery 50 → 74%, 70% after the trip")
        assertThat(words.spokenFigure(SourceFigure.Battery(charged)))
            .isEqualTo("Battery from 50% to 74%, 70% after the trip")
        assertThat(words.figure(SourceFigure.Battery(notCharged))).isEqualTo("Battery 50 → 30% after the trip")
        assertThat(words.spokenFigure(SourceFigure.Battery(notCharged)))
            .isEqualTo("Battery 50%, 30% after the trip")
        assertThat(words.batteryLevels(charged)).isEqualTo("50 → 74%, 70% after the trip")
        assertThat(words.batteryLevels(notCharged)).isEqualTo("50 → 30% after the trip")
        assertThat(words.batteryLevels(BatteryLevels(Percent(50.0), Percent(74.0), Optional.empty()))).isEqualTo("50 → 74%")
    }

    @Test
    @Config(qualifiers = "nl")
    fun onTheDayATripLeavesTheBatteryChipAndLineSayTheLevelAfterTheTripInDutch() {
        val words = words(Locale.forLanguageTag("nl"))
        val charged = BatteryLevels(Percent(50.0), Percent(74.0), Optional.of(Percent(70.0)))
        val notCharged = BatteryLevels(Percent(50.0), Percent(50.0), Optional.of(Percent(30.0)))

        assertThat(words.figure(SourceFigure.Battery(charged))).isEqualTo("Accu 50 → 74%, 70% na de rit")
        assertThat(words.spokenFigure(SourceFigure.Battery(charged))).isEqualTo("Accu van 50% naar 74%, 70% na de rit")
        assertThat(words.figure(SourceFigure.Battery(notCharged))).isEqualTo("Accu 50 → 30% na de rit")
        assertThat(words.spokenFigure(SourceFigure.Battery(notCharged))).isEqualTo("Accu 50%, 30% na de rit")
        assertThat(words.batteryLevels(charged)).isEqualTo("50 → 74%, 70% na de rit")
        val tomorrow = day(DayName.Tomorrow(TOMORROW), charged)
        assertThat(words.battery(tomorrow, ChargeLevels(Percent(20.0), Percent(80.0)))).isEqualTo("Accu van de auto " +
            "50% aan het begin van morgen, na het laden 74%; minimum 20%, doel 80%. Na de rit staat de accu aan het " +
            "eind van de dag op 70%.")
    }

    @Test
    fun theBatteryBarsSentenceSaysTheLevelAfterTheTripLastOnlyOnTheDayATripLeaves() {
        val words = words()
        val levels = ChargeLevels(Percent(20.0), Percent(80.0))
        val today = day(DayName.Today(TODAY), BatteryLevels(Percent(50.0), Percent(74.0), Optional.of(Percent(70.0))),
            assumed = Percent(50.0))
        val plain = day(DayName.Tomorrow(TOMORROW), BatteryLevels(Percent(50.0), Percent(74.0), Optional.empty()))

        assertThat(words.battery(today, levels)).isEqualTo("Car battery 50% now, 74% after charging; minimum 20%, " +
            "target 80%. The battery level is assumed. Use Update battery level under it to enter the level the car " +
            "shows. After the trip the battery is at 70% at the end of the day.")
        assertThat(words.battery(plain, levels)).isEqualTo("Car battery 50% at the start of tomorrow, 74% after " +
            "charging; minimum 20%, target 80%.")
    }

    @Test
    fun aDaysNoteSaysWhyItHasNoWindowAsTheDashboardTellsIt() {
        val words = words()
        val levels = BatteryLevels(Percent(50.0), Percent(50.0), Optional.empty())

        assertThat(words.note(day(DayName.Tomorrow(TOMORROW), levels, NoWindowReason.AWAY)))
            .isEqualTo("The car is away on a trip, or there is too little solar power while it is home.")
        assertThat(words.note(day(DayName.Tomorrow(TOMORROW), levels, NoWindowReason.NOT_NEEDED)))
            .isEqualTo("The car needs no charging, or there is too little solar power for it.")
        assertThat(words.note(day(DayName.Tomorrow(TOMORROW), levels, NoWindowReason.DEPARTURE))).isEmpty()
    }

    @Test
    @Config(qualifiers = "nl")
    fun aDayAwayOnATripSaysSoInDutch() {
        val day = day(DayName.Tomorrow(TOMORROW), BatteryLevels(Percent(50.0), Percent(50.0), Optional.empty()), NoWindowReason.AWAY)

        assertThat(words(Locale.forLanguageTag("nl")).note(day))
            .isEqualTo("De auto is weg voor een rit, of er is te weinig zonnestroom terwijl hij thuis is.")
    }

    /** What the engine's notices tell about a trip's advice when its grid charge is due, at 20:55 for one at 21:00. */
    private fun chargeStartTold(advice: TripAdvice): TripChargeStart {
        val now = at(TODAY, 20, 55)
        val folder = Files.createTempDirectory("trip-notices")
        val files = PrivateFiles(FilePrivacy.NONE)
        val told = mutableListOf<Notice>()
        Announcer(AnnouncedWindows(folder.resolve("announced.json"), files),
            ToldTrips(folder.resolve("trip-notices.json"), files)) { told += it }
            .announceTripsDue(listOf(advice), PlanLevel(Percent(45.0), Optional.empty()), Plans.source(now), now,
                EngineConfig.setUpDraft(BRUSSELS, ProviderId("open-meteo")))
        return told.filterIsInstance<TripChargeStart>().single()
    }

    @Test
    fun theCardsChargeLimitIsTheNotificationsForTheSameAdvice() {
        listOf(84.2, 85 + 1e-9).forEach { needed ->
            val advice = advice(needed, listOf(), 45.0, grid(at(TODAY, 21, 0), Start.PLANNED), needed, 0.0)

            val notice = chargeStartTold(advice)
            val card = onToday(model(advice)).plan
            val body = NoticeWords(resources, Locale.ENGLISH, true).of(notice, LockScreen.WINDOW_SOON).body

            assertThat((model(advice).plan() as TripPlan.Grid).chargeLimitPct()).isEqualTo(notice.chargeLimitPct())
            assertThat(card).contains("a charge limit of 85% in the car")
            assertThat(body).contains("set the charge limit to 85%")
        }
    }
}
