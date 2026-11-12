package nl.enadeag.carchargeplanner.android.home

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Optional
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.dashboard.ChargeLevels
import nl.enadeag.carchargeplanner.engine.dashboard.ComingAway
import nl.enadeag.carchargeplanner.engine.dashboard.DayName
import nl.enadeag.carchargeplanner.engine.dashboard.NoWindowReason
import nl.enadeag.carchargeplanner.engine.plan.GridAdvice
import nl.enadeag.carchargeplanner.engine.plan.TripAdvice
import nl.enadeag.carchargeplanner.engine.plan.TripGridCharge
import nl.enadeag.carchargeplanner.engine.plan.Window
import nl.enadeag.carchargeplanner.engine.trip.Trip
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class DashboardTest {

    private val amsterdam = ZoneId.of("Europe/Amsterdam")
    private val today = LocalDate.of(2026, 6, 1)
    private val madeAt = today.atTime(10, 0).atZone(amsterdam)

    private fun dashboard(away: List<OneOffAwayPeriod> = listOf(), gridAdvice: GridAdvice? = null) =
        Dashboard.of(Plans.planned(madeAt, listOf(listOf(), listOf(), listOf()), gridAdvice = gridAdvice), away)

    @Test
    fun theSameDashboardWithTheStoredTripsKeepsThePlanAndTellsTheTripsByItsDay() {
        val before = dashboard()
        val trip = Trip(today.plusDays(1).atTime(10, 0).atZone(amsterdam).toInstant(),
            today.plusDays(1).atTime(18, 0).atZone(amsterdam).toInstant(), 80.0, true)

        val after = before.withTrips(listOf(trip))

        assertThat(after.trips).containsExactly(before.describe(trip))
        assertThat(after.trips.single().departureDay()).isEqualTo(DayName.Tomorrow(today.plusDays(1)))
        assertThat(after.view).isEqualTo(before.planned.dashboard(listOf(), listOf(trip)))
        assertThat(after.copy(storedTrips = listOf())).isEqualTo(before)
    }

    @Test
    fun theAwayPeriodsTripsAndFooterAreThoseOfTheEnginesDashboard() {
        val period = OneOffAwayPeriod.allDay(today.plusDays(1), amsterdam)
        val trip = trip(today.plusDays(1), "07:00", today.plusDays(1), "19:00")
        val shown = Dashboard.of(Plans.planned(madeAt, listOf(listOf(), listOf(), listOf())), listOf(period),
            listOf(trip))

        assertThat(shown.away).isEqualTo(shown.view.away()).isNotEmpty()
        assertThat(shown.trips).isEqualTo(shown.view.trips()).isNotEmpty()
        assertThat(shown.footer).isEqualTo(shown.view.footer())
        assertThat(shown.days.map { shown.tripsOn(it) }).isEqualTo(shown.days.map { shown.view.tripsOn(it) })
        assertThat(shown.describe(period)).isEqualTo(shown.view.describe(period))
        assertThat(shown.describe(trip)).isEqualTo(shown.view.describe(trip))
    }

    @Test
    fun aPlanWithoutDaysIsNoDashboard() {
        assertThatThrownBy { Dashboard.of(Plans.planned(madeAt, listOf()), listOf()) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun theProvidersThatRefusedTheirKeyAreNamedInTheOrderTheyWereAskedAndNoneWithout() {
        val source = Plans.source(madeAt.toInstant(), keyRefusedBy = listOf(ProviderId("open-meteo"),
            ProviderId("forecast-solar")))
        val names = mapOf("open-meteo" to "Open-Meteo", "forecast-solar" to "Forecast.Solar")

        val refused = Dashboard.of(Plans.planned(madeAt, listOf(listOf()), source = source), listOf()) {
            names.getValue(it.value())
        }

        assertThat(refused.keyRefusedBy).containsExactly("Open-Meteo", "Forecast.Solar")
        assertThat(dashboard().keyRefusedBy).isEmpty()
    }

    @Test
    fun withoutTheirNamesTheProvidersThatRefusedTheirKeyAreNamedByTheirIds() {
        val source = Plans.source(madeAt.toInstant(), keyRefusedBy = listOf(ProviderId("open-meteo")))

        assertThat(Dashboard.of(Plans.planned(madeAt, listOf(listOf()), source = source), listOf()).keyRefusedBy)
            .containsExactly("open-meteo")
    }

    @Test
    fun theGridAdviceIsTheEnginesAndWithoutOneThereIsNone() {
        val advice = GridAdvice(today.plusDays(1), 12.0, 8.0, 7.4, Optional.empty(), Optional.empty())
        val shown = dashboard(gridAdvice = advice)

        assertThat(shown.gridAdvice).isEqualTo(shown.view.gridAdvice().orElseThrow())
        assertThat(shown.gridAdvice!!.day().date()).isEqualTo(today.plusDays(1))
        assertThat(shown.gridAdvice!!.energyKwh()).isEqualTo(7.4)
        assertThat(dashboard().gridAdvice).isNull()
    }

    @Test
    fun gridAdviceShowsOnTodaysTabAndNoOther() {
        val advice = GridAdvice(today.plusDays(1), 12.0, 8.0, 7.4, Optional.empty(), Optional.empty())
        val shown = dashboard(gridAdvice = advice)

        assertThat(shown.days.map { shown.adviceOn(it) }).containsExactly(shown.gridAdvice, null, null)
    }

    @Test
    fun theBatteryBarsMarkThePlansMinimumAndTargetChargeLevels() {
        assertThat(dashboard().levels).isEqualTo(ChargeLevels(Percent(20.0), Percent(80.0)))
    }

    private fun at(day: LocalDate, time: String) = day.atTime(LocalTime.parse(time)).atZone(amsterdam).toInstant()

    private fun trip(
        from: LocalDate, departure: String, to: LocalDate, back: String, notWithThisCar: Boolean = false,
        canChargeThere: Boolean = false,
    ) = Trip(at(from, departure), at(to, back), 100.0, canChargeThere, notWithThisCar)

    /** The plan's advice for a trip, which asks the owner to act when it needs a grid charge. */
    private fun advice(trip: Trip, asks: Boolean = false): TripAdvice {
        val grid = if (asks) {
            Optional.of(TripGridCharge(Window(trip.departure().minusSeconds(7200), trip.departure().minusSeconds(3600),
                16, 3.7, 0.0, 3.7), TripGridCharge.Start.PLANNED))
        } else {
            Optional.empty()
        }
        return TripAdvice(trip, Percent(60.0), listOf(), Percent(60.0), grid, Percent(70.0), 0.0, Percent(50.0))
    }

    /** Three days without a window, today's grid-advice day the one given, with these trips and their advice. */
    private fun withTrips(trips: List<Trip>, advice: List<TripAdvice> = listOf(), gridDay: LocalDate? = null,
        windowOn: LocalDate? = null): Dashboard {
        val days = (0L..2L).map { index ->
            val date = today.plusDays(index)
            if (date == windowOn) listOf(Plans.window(amsterdam, date, 11, 12)) else listOf()
        }
        val grid = gridDay?.let { GridAdvice(it, 12.0, 8.0, 4.2, Optional.empty(), Optional.empty()) }
        return Dashboard.of(Plans.planned(madeAt, days, gridAdvice = grid, tripAdvice = advice), listOf(), trips)
    }

    @Test
    fun theStoredTripsTellTheAwayDaysAtOnceWithoutANewPlan() {
        val away = trip(today.plusDays(1), "07:00", today.plusDays(1), "19:00")
        val shown = withTrips(listOf(away))

        val ticked = shown.withTrips(listOf(Trip(away.departure(), away.returnTime(), 100.0, false, true)))
        val removed = shown.withTrips(listOf())

        assertThat(shown.days[1].noWindow()).contains(NoWindowReason.AWAY)
        assertThat(ticked.days[1].noWindow()).contains(NoWindowReason.TOO_LITTLE_SOLAR)
        assertThat(removed.days[1].noWindow()).contains(NoWindowReason.TOO_LITTLE_SOLAR)
    }

    @Test
    fun theStoredAwayPeriodsKeepTheDaysAsTheStoredTripsMakeThem() {
        val away = trip(today.plusDays(1), "07:00", today.plusDays(1), "19:00")
        val shown = withTrips(listOf(away))
        val period = OneOffAwayPeriod(at(today.plusDays(2), "09:00"), at(today.plusDays(2), "12:00"))

        val changed = shown.withAway(listOf(period))

        assertThat(changed.view).isEqualTo(shown.planned.dashboard(listOf(period), listOf(away)))
        assertThat(changed.storedTrips).containsExactly(away)
        assertThat(changed.days[1].noWindow()).contains(NoWindowReason.AWAY)
        assertThat(changed.away).isEqualTo(listOf(ComingAway.describe(period, amsterdam, today)))
    }

    @Test
    fun aChangeOfTheTripsKeepsTheStoredAwayPeriodsAlsoOneThatEndedThisMorning() {
        val endedThisMorning = OneOffAwayPeriod(at(today, "07:00"), at(today, "09:00"))
        val coming = OneOffAwayPeriod(at(today.plusDays(2), "09:00"), at(today.plusDays(2), "12:00"))
        val trip = trip(today.plusDays(1), "07:00", today.plusDays(1), "19:00")
        val shown = dashboard(listOf(endedThisMorning, coming))

        val changed = shown.withTrips(listOf(trip))

        // Only this line guards note 1 of #404, the old withTrips bug: the view leaves ended periods out anyway
        assertThat(changed.storedAway).containsExactly(endedThisMorning, coming)
        assertThat(changed.view).isEqualTo(shown.planned.dashboard(listOf(endedThisMorning, coming), listOf(trip)))
    }

    @Test
    fun eachTripsAdviceShowsOnItsDepartureDayAndOnTodayWhenItAsksInTheEnginesOrder() {
        val tomorrow = trip(today.plusDays(1), "07:00", today.plusDays(1), "19:00")
        val later = trip(today.plusDays(1), "20:00", today.plusDays(1), "23:00")
        val dayAfter = trip(today.plusDays(2), "09:00", today.plusDays(2), "12:00")
        val leavingToday = trip(today, "15:00", today, "18:00")
        val advice = listOf(advice(leavingToday, asks = true), advice(tomorrow, asks = true), advice(later),
            advice(dayAfter, asks = true))

        val shown = withTrips(listOf(leavingToday, tomorrow, later, dayAfter), advice)

        assertThat(shown.days.map { day -> shown.tripAdviceOn(day).map { it.trip().departure() } }).containsExactly(
            listOf(leavingToday.departure(), tomorrow.departure(), dayAfter.departure()),
            listOf(tomorrow.departure(), later.departure()),
            listOf(dayAfter.departure()))
    }

    @Test
    fun aTripBeyondTheLastTabThatAsksShowsOnTodaysTab() {
        val far = trip(today.plusDays(5), "09:00", today.plusDays(5), "12:00")

        val shown = withTrips(listOf(far), listOf(advice(far, asks = true)))

        assertThat(shown.days.map { day -> shown.tripAdviceOn(day).size }).containsExactly(1, 0, 0)
    }
}
