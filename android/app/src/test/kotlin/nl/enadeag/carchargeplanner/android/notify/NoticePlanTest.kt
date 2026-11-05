package nl.enadeag.carchargeplanner.android.notify

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
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
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class NoticePlanTest {

    private val zone = ZoneId.of("Europe/Amsterdam")
    private val start = Instant.parse("2026-06-07T09:15:00Z")
    private val end = Instant.parse("2026-06-07T12:30:00Z")
    private val window = Window(start, end, 10, 2.3, 7.3, 0.6)
    private val departure = Instant.parse("2026-06-07T12:00:00Z")
    private val trip = Trip(departure, departure.plus(Duration.ofHours(8)), 240.0, false)

    private fun solar() = Announcement(AnnouncementKind.SOLAR_WINDOW, window, zone, Phases.ONE, Percent(30.0),
        Optional.empty())

    private fun grid() = Announcement(AnnouncementKind.GRID_CHARGE, window, zone, Phases.ONE, Percent(30.0),
        Optional.of(Percent(80.0)))

    @Test
    fun aSolarWindowIsTaggedByItsStartInTheChargeWindowsUntilItsEndAndAlertsOnce() {
        assertThat(NoticePlan.of(solar())).isEqualTo(NoticePlan("solar_window:${start.epochSecond}",
            NoticeChannel.CHARGE_WINDOWS, end, LockScreen.WINDOW_SOON, Tap.DASHBOARD, true))
    }

    @Test
    fun aGridChargeIsTaggedApartFromASolarWindowOfTheSameStart() {
        assertThat(NoticePlan.of(grid())).isEqualTo(NoticePlan("grid_charge:${start.epochSecond}",
            NoticeChannel.CHARGE_WINDOWS, end, LockScreen.WINDOW_SOON, Tap.DASHBOARD, true))
    }

    @ParameterizedTest
    @EnumSource(TripGridCharge.Start::class)
    fun aTripsGridChargeIsTaggedByTheDepartureAndStaysUntilTheWindowsEnd(how: TripGridCharge.Start) {
        val notice = TripChargeStart(trip, window, how, zone, Phases.THREE, Percent(80.0))

        assertThat(NoticePlan.of(notice)).isEqualTo(NoticePlan("trip_charge_start:${departure.epochSecond}",
            NoticeChannel.CHARGE_WINDOWS, end, LockScreen.WINDOW_SOON, Tap.DASHBOARD, true))
    }

    @Test
    fun aBatteryCheckOpensTheBatteryEntryStaysUntilTheDepartureAndAlertsAgainWhenAskedAgain() {
        assertThat(NoticePlan.of(BatteryCheck(trip, Percent(40.0), zone))).isEqualTo(
            NoticePlan("battery_check:${departure.epochSecond}", NoticeChannel.BATTERY_TRIP_CHECKS, departure,
                LockScreen.TITLE, Tap.BATTERY_ENTRY, false))
    }

    @Test
    fun aShortTripIsACheckThatOpensTheDashboardUntilTheDeparture() {
        assertThat(NoticePlan.of(TripShort(trip, 11.2, zone))).isEqualTo(
            NoticePlan("trip_short:${departure.epochSecond}", NoticeChannel.BATTERY_TRIP_CHECKS, departure,
                LockScreen.TITLE, Tap.DASHBOARD, true))
    }

    @Test
    fun onlyABatteryCheckAlertsAgainWhenPostedAgainUnderItsTag() {
        assertThat(NoticePlan.of(BatteryCheck(trip, Percent(40.0), zone)).alertOnce).isFalse()
        assertThat(NoticePlan.of(solar()).alertOnce).isTrue()
        assertThat(NoticePlan.of(TripShort(trip, 11.2, zone)).alertOnce).isTrue()
    }

    @Test
    fun aBatteryCheckAskedAgainForTheSameTripHasTheSameTagSoItReplacesTheFirst() {
        assertThat(NoticePlan.of(BatteryCheck(trip, Percent(70.0), zone)).tag)
            .isEqualTo(NoticePlan.of(BatteryCheck(trip, Percent(40.0), zone)).tag)
    }

    @Test
    fun aWindowThatMovedIsANotificationOfItsOwn() {
        val moved = Announcement(AnnouncementKind.SOLAR_WINDOW, Window(start.plusSeconds(900), end, 10, 2.3, 7.0, 0.0),
            zone, Phases.ONE, Percent(30.0), Optional.empty())

        assertThat(NoticePlan.of(moved).tag).isNotEqualTo(NoticePlan.of(solar()).tag)
    }

    @Test
    fun theKindAloneNamesANoticeInTheLog() {
        assertThat(NoticePlan.kindOf(BatteryCheck(trip, Percent(40.0), zone))).isEqualTo("battery_check")
        assertThat(NoticePlan.kindOf(grid())).isEqualTo("grid_charge")
    }

    @Test
    fun aNoticeStaysUntilItsTimeout() {
        val plan = NoticePlan.of(solar())

        assertThat(plan.stayFrom(start.minus(Duration.ofMinutes(15)))).isEqualTo(Duration.ofMinutes(210))
        assertThat(plan.stayFrom(end.minusMillis(1))).isEqualTo(Duration.ofMillis(1))
    }

    @Test
    fun aNoticeWhoseTimeoutCameIsNotPosted() {
        val plan = NoticePlan.of(solar())

        assertThat(plan.stayFrom(end)).isNull()
        assertThat(plan.stayFrom(end.plusSeconds(1))).isNull()
    }

    @Test
    fun theChannelsIdsAreFixedAndOnlyProblemsIsQuiet() {
        assertThat(NoticeChannel.entries.map { it.id to it.quiet }).containsExactly("charge_windows" to false,
            "battery_trip_checks" to false, "problems" to true)
    }
}
