package nl.enadeag.carchargeplanner.android.ui.notify

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.Optional
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.config.Phases
import nl.enadeag.carchargeplanner.engine.notify.Announcement
import nl.enadeag.carchargeplanner.engine.notify.AnnouncementKind
import nl.enadeag.carchargeplanner.engine.notify.BatteryCheck
import nl.enadeag.carchargeplanner.engine.notify.TripChargeStart
import nl.enadeag.carchargeplanner.engine.notify.TripShort
import nl.enadeag.carchargeplanner.engine.plan.TripGridCharge
import nl.enadeag.carchargeplanner.engine.plan.Window
import nl.enadeag.carchargeplanner.engine.trip.Trip
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The notifications' words, from the app's real texts in English and Dutch; 7 June 2026 is a Sunday, in summer time in
 * Amsterdam (UTC+2).
 */
@RunWith(AndroidJUnit4::class)
class NoticeWordsTest {

    private val amsterdam = ZoneId.of("Europe/Amsterdam")

    /** 11:15 to 14:30 in Amsterdam, 10 A at 2.3 kW, 7.3 kWh of solar and 0.6 from the grid: 92% solar. */
    private val window = Window(Instant.parse("2026-06-07T09:15:00Z"), Instant.parse("2026-06-07T12:30:00Z"), 10,
        2.3, 7.3, 0.6)

    /** Sunday at 14:00 in Amsterdam. */
    private val trip = Trip(Instant.parse("2026-06-07T12:00:00Z"), Instant.parse("2026-06-07T20:00:00Z"), 240.0,
        false)

    /** 09:00 to 11:00 in Amsterdam, 16 A at 3.7 kW from the grid. */
    private val tripWindow = Window(Instant.parse("2026-06-07T07:00:00Z"), Instant.parse("2026-06-07T09:00:00Z"), 16,
        3.7, 0.0, 7.4)

    private fun words(language: String, twentyFour: Boolean = true): NoticeWords {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val locale = Locale.forLanguageTag(language)
        val resources = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(locale)
        }).resources
        return NoticeWords(resources, locale, twentyFour)
    }

    private fun solar() = Announcement(AnnouncementKind.SOLAR_WINDOW, window, amsterdam, Phases.ONE, Percent(30.0),
        Optional.empty())

    /** The text with the narrow spaces that some clocks put before AM and PM made plain ones. */
    private fun plain(text: String) = text.replace(' ', ' ').replace(' ', ' ')

    @Test
    fun aSolarWindowSaysItsTimesAsSpokenWhatToSetTheEnergyAndTheSolarShare() {
        assertThat(words("en").of(solar())).isEqualTo(NoticeText("Charge window 11:15 to 14:30",
            "Set the charge current to 10 A (2.3 kW) in your charger's app. About 7.9 kWh, 92% solar.",
            "Charge window soon"))
    }

    @Test
    fun inDutchASolarWindowHasTheGlossarysWordsAndADecimalComma() {
        assertThat(words("nl").of(solar())).isEqualTo(NoticeText("Laadvenster 11:15 tot 14:30",
            "Stel in de app van je laadpaal de laadstroom in op 10 A (2,3 kW). Ongeveer 7,9 kWh, 92% zonnestroom.",
            "Laadvenster binnenkort"))
    }

    @Test
    fun onA12HourClockTheTimesFollowIt() {
        val text = words("en", twentyFour = false).of(solar())

        assertThat(plain(text.title)).isEqualTo("Charge window 11:15 AM to 2:30 PM")
    }

    @Test
    fun aGridChargeOnThreePhasesSaysTheCurrentPerPhaseTheMinimumAndTheChargeLimit() {
        val grid = Announcement(AnnouncementKind.GRID_CHARGE, window, amsterdam, Phases.THREE, Percent(30.0),
            Optional.of(Percent(80.0)))

        assertThat(words("en").of(grid)).isEqualTo(NoticeText("Grid charge window 11:15 to 14:30",
            "Set the charge current to 10 A per phase (2.3 kW) in your charger's app. About 7.9 kWh, needed to stay " +
                "above 30%. Set the charge limit in the car or the charger to 80%.", "Charge window soon"))
        assertThat(words("nl").of(grid)).isEqualTo(NoticeText("Laden uit het net 11:15 tot 14:30",
            "Stel in de app van je laadpaal de laadstroom in op 10 A per fase (2,3 kW). Ongeveer 7,9 kWh, nodig om " +
                "boven 30% te blijven. Stel de laadlimiet in de auto of de laadpaal in op 80%.",
            "Laadvenster binnenkort"))
    }

    @Test
    fun aTripsPlannedGridChargeSaysWhenToStartForWhichTripAndTheChargeLimitThenWhatToSet() {
        val start = TripChargeStart(trip, tripWindow, TripGridCharge.Start.PLANNED, amsterdam, Phases.ONE,
            Percent(80.0))

        assertThat(words("en").of(start)).isEqualTo(NoticeText("Grid charge window 09:00 to 11:00",
            "Start charging at 09:00 for your trip on Sunday at 14:00, and set the charge limit to 80%. Set the " +
                "charge current to 16 A (3.7 kW) in your charger's app.", "Charge window soon"))
        assertThat(words("nl").of(start).body).isEqualTo("Start het laden om 09:00 voor je rit van zondag 14:00, en " +
            "zet de laadlimiet op 80%. Stel in de app van je laadpaal de laadstroom in op 16 A (3,7 kW).")
    }

    @Test
    fun aTripsGridChargeThatStartsNowSaysWhy() {
        val now = TripChargeStart(trip, tripWindow, TripGridCharge.Start.NOW, amsterdam, Phases.ONE, Percent(80.0))
        val outside = TripChargeStart(trip, tripWindow, TripGridCharge.Start.NOW_OUTSIDE_CHARGING_HOURS, amsterdam,
            Phases.ONE, Percent(80.0))
        val setting = "Set the charge current to 16 A (3.7 kW) in your charger's app."

        assertThat(words("en").of(now)).isEqualTo(NoticeText("Start charging now", "Start charging now for your " +
            "trip on Sunday at 14:00, and set the charge limit to 80%: little time is left before the departure. " +
            setting, "Start charging now"))
        assertThat(words("en").of(outside).body).isEqualTo("Start charging now for your trip on Sunday at 14:00, and " +
            "set the charge limit to 80%, although that is outside your charging hours: waiting for them would leave " +
            "too little time. $setting")
        assertThat(words("en").of(outside).publicTitle).isEqualTo("Start charging now")
        assertThat(words("nl").of(now).publicTitle).isEqualTo("Start nu met laden")
        assertThat(words("nl").of(outside).title).isEqualTo("Start nu met laden")
        assertThat(words("nl").of(outside).publicTitle).isEqualTo("Start nu met laden")
    }

    @Test
    fun aBatteryCheckAsksWhetherTheAssumedLevelIsRightAndShowsItsTitleOnTheLockScreen() {
        val check = BatteryCheck(trip, Percent(40.0), amsterdam)

        assertThat(words("en").of(check)).isEqualTo(NoticeText("Check the battery level", "Your trip on Sunday at " +
            "14:00 needs charging. Is the battery at about 40%? Tap to enter the level the car shows.",
            "Check the battery level"))
        assertThat(words("nl").of(check)).isEqualTo(NoticeText("Controleer het accuniveau", "Je rit van zondag " +
            "14:00 vraagt laden. Staat de accu op ongeveer 40%? Tik om het accuniveau in te vullen dat de auto " +
            "aangeeft.", "Controleer het accuniveau"))
        assertThat(words("en").enterLevel()).isEqualTo("Enter battery level")
        assertThat(words("nl").enterLevel()).isEqualTo("Accuniveau invullen")
    }

    @Test
    fun aShortTripSaysTheDistanceInWholeKmRoundedUp() {
        val short = TripShort(trip, 11.2, amsterdam)

        assertThat(words("en").of(short)).isEqualTo(NoticeText("Plan a charging stop", "For your trip on Sunday at " +
            "14:00 you are 12 km short: plan a charging stop.", "Plan a charging stop"))
        assertThat(words("nl").of(short).body)
            .isEqualTo("Voor je rit van zondag 14:00 kom je 12 km tekort: plan een laadstop.")
    }

    @Test
    fun theTripsWeekdayIsTheHomesNotTheMomentsInUtc() {
        // 23:30 in UTC on Saturday is Sunday 01:30 in Amsterdam
        val late = Trip(Instant.parse("2026-06-06T23:30:00Z"), Instant.parse("2026-06-07T08:00:00Z"), 100.0, false)

        assertThat(words("en").of(BatteryCheck(late, Percent(40.0), amsterdam)).body)
            .startsWith("Your trip on Sunday at 01:30 needs charging.")
    }

    @Test
    fun aWindowOverTheClocksGoingBackIsToldOnTheHomesClock() {
        // 25 October 2026: at 03:00 summer time the clocks go back to 02:00
        val overTheChange = Window(Instant.parse("2026-10-25T00:30:00Z"), Instant.parse("2026-10-25T02:30:00Z"), 6,
            1.4, 2.8, 0.0)
        val notice = Announcement(AnnouncementKind.SOLAR_WINDOW, overTheChange, amsterdam, Phases.ONE, Percent(30.0),
            Optional.empty())

        assertThat(Duration.between(overTheChange.start(), overTheChange.end())).isEqualTo(Duration.ofHours(2))
        assertThat(words("en").of(notice).title).isEqualTo("Charge window 02:30 to 03:30")
    }

    @Test
    fun theProblemsNotificationSaysTheForecastIsNotUpdatedAndWhereTheDetailsAre() {
        assertThat(words("en").problems()).isEqualTo(NoticeText("Forecast not updated",
            "The forecast could not be updated. Open the app for details.", "Forecast not updated"))
        assertThat(words("nl").problems()).isEqualTo(NoticeText("Voorspelling niet vernieuwd",
            "De voorspelling kon niet worden vernieuwd. Open de app voor details.", "Voorspelling niet vernieuwd"))
    }
}
