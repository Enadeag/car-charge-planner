package nl.enadeag.carchargeplanner.android.home

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import nl.enadeag.carchargeplanner.engine.dashboard.DayName
import nl.enadeag.carchargeplanner.engine.trip.Trip
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ListedTripTest {

    private val amsterdam = ZoneId.of("Europe/Amsterdam")
    private val today = LocalDate.of(2026, 6, 1)
    private val madeAt = today.atTime(8, 0).atZone(amsterdam).toInstant()

    private fun at(day: LocalDate, time: String, zone: ZoneId = amsterdam): Instant =
        day.atTime(LocalTime.parse(time)).atZone(zone).toInstant()

    private fun trip(from: Instant, until: Instant) = Trip(from, until, 300.0, false)

    private fun listed(trip: Trip, zone: ZoneId = amsterdam) = ListedTrip.of(trip, zone, madeAt)

    @Test
    fun aTripBackTheDayItLeavesTakesThatDayAloneAndIsToldWithoutItsDays() {
        val listed = listed(trip(at(today, "07:30"), at(today, "18:00")))

        assertThat(listed.firstDay).isEqualTo(today)
        assertThat(listed.lastDay).isEqualTo(today)
        assertThat(listed.sameDay).isTrue()
        assertThat(listed.departureDay).isEqualTo(DayName.Today(today))
        assertThat(listed.returnDay).isEqualTo(DayName.Today(today))
    }

    @Test
    fun aTripOverSeveralDaysTakesEveryDayFromItsDepartureToItsReturn() {
        val listed = listed(trip(at(today.plusDays(1), "07:30"), at(today.plusDays(3), "00:30")))

        assertThat(listed.covers(today)).isFalse()
        assertThat((1L..3L).map { listed.covers(today.plusDays(it)) }).containsOnly(true)
        assertThat(listed.covers(today.plusDays(4))).isFalse()
        assertThat(listed.sameDay).isFalse()
        assertThat(listed.departureDay).isEqualTo(DayName.Tomorrow(today.plusDays(1)))
        assertThat(listed.returnDay).isEqualTo(DayName.Other(today.plusDays(3)))
    }

    @Test
    fun aTripBackAtMidnightTakesNoTimeOfTheDayItReturnsOnYetIsToldWithItsDays() {
        val listed = listed(trip(at(today, "18:00"), at(today.plusDays(1), "00:00")))

        assertThat(listed.lastDay).isEqualTo(today)
        assertThat(listed.covers(today)).isTrue()
        assertThat(listed.covers(today.plusDays(1))).isFalse()
        assertThat(listed.sameDay).isFalse()
        assertThat(listed.returnDay).isEqualTo(DayName.Tomorrow(today.plusDays(1)))
    }

    @Test
    fun aTripBackAMinuteAfterMidnightTakesTheDayItReturnsOn() {
        val listed = listed(trip(at(today, "18:00"), at(today.plusDays(1), "00:01")))

        assertThat(listed.lastDay).isEqualTo(today.plusDays(1))
    }

    @ParameterizedTest
    @CsvSource("Europe/Amsterdam, 2026-06-02", "America/New_York, 2026-06-01", "Pacific/Auckland, 2026-06-02")
    fun theDaysAreTheHomesZonesNotTheMomentsInUtc(zone: String, day: LocalDate) {
        // 23:30 UTC on 1 June
        val listed = listed(trip(Instant.parse("2026-06-01T23:30:00Z"), Instant.parse("2026-06-02T01:00:00Z")),
            ZoneId.of(zone))

        assertThat(listed.firstDay).isEqualTo(day)
    }

    @Test
    fun theTripsAreListedInTheOrderTheyCameAlsoOneThatReturnedAlready() {
        val back = trip(at(today, "05:00"), at(today, "07:00"))
        val coming = trip(at(today.plusDays(1), "09:00"), at(today.plusDays(1), "17:00"))

        assertThat(ListedTrip.of(listOf(back, coming), amsterdam, madeAt).map { it.trip }).containsExactly(back, coming)
    }

    @Test
    fun aLastDayBeforeTheFirstIsRefused() {
        assertThatThrownBy {
            ListedTrip(trip(at(today, "07:30"), at(today, "18:00")), today, today.minusDays(1), DayName.Today(today),
                DayName.Today(today), true)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
