package nl.enadeag.carchargeplanner.android.home

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Optional
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.dashboard.ChargeLevels
import nl.enadeag.carchargeplanner.engine.dashboard.DashboardView
import nl.enadeag.carchargeplanner.engine.dashboard.DayName
import nl.enadeag.carchargeplanner.engine.dashboard.NoWindowReason
import nl.enadeag.carchargeplanner.engine.forecast.Freshness
import nl.enadeag.carchargeplanner.engine.forecast.ProviderRole
import nl.enadeag.carchargeplanner.engine.plan.GridAdvice
import nl.enadeag.carchargeplanner.engine.plan.TripAdvice
import nl.enadeag.carchargeplanner.engine.plan.TripGridCharge
import nl.enadeag.carchargeplanner.engine.plan.Window
import nl.enadeag.carchargeplanner.engine.trip.Trip
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class DashboardTest {

    private val amsterdam = ZoneId.of("Europe/Amsterdam")
    private val today = LocalDate.of(2026, 6, 1)
    private val madeAt = today.atTime(10, 0).atZone(amsterdam)

    private fun dashboard(away: List<OneOffAwayPeriod> = listOf(), gridAdvice: GridAdvice? = null) =
        Dashboard.of(Plans.planned(madeAt, listOf(listOf(), listOf(), listOf()), gridAdvice = gridAdvice), away)

    @Test
    fun eachDayListsTheTripsTheCarIsAwayForThatDayInTheEnginesOrder() {
        val overnight = Trip(today.atTime(20, 0).atZone(amsterdam).toInstant(),
            today.plusDays(1).atTime(9, 0).atZone(amsterdam).toInstant(), 200.0, false)
        val tomorrow = Trip(today.plusDays(1).atTime(10, 0).atZone(amsterdam).toInstant(),
            today.plusDays(1).atTime(18, 0).atZone(amsterdam).toInstant(), 80.0, true)
        val shown = Dashboard.of(Plans.planned(madeAt, listOf(listOf(), listOf(), listOf())), listOf(),
            listOf(overnight, tomorrow))

        assertThat(shown.days.map { day -> shown.tripsOn(day).map { it.trip } })
            .containsExactly(listOf(overnight), listOf(overnight, tomorrow), listOf())
    }

    @Test
    fun theSameDashboardWithTheStoredTripsKeepsThePlanAndTellsTheTripsByItsDay() {
        val before = dashboard()
        val trip = Trip(today.plusDays(1).atTime(10, 0).atZone(amsterdam).toInstant(),
            today.plusDays(1).atTime(18, 0).atZone(amsterdam).toInstant(), 80.0, true)

        val after = before.withTrips(listOf(trip))

        assertThat(after.trips).containsExactly(before.describe(trip))
        assertThat(after.trips.single().departureDay).isEqualTo(DayName.Tomorrow(today.plusDays(1)))
        assertThat(after.view).isEqualTo(before.planned.dashboard(listOf(), listOf(trip)))
        assertThat(after.copy(trips = listOf(), view = before.view)).isEqualTo(before)
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
    fun theFooterCreditsThePrimaryProviderWithTheTimeItsForecastWasFetched() {
        val fetched = madeAt.minusMinutes(20).toInstant()

        val footer = Footer.of(Plans.planned(madeAt, listOf(listOf()), source = Plans.source(fetched)))

        assertThat(footer).isEqualTo(Footer(Plans.CREDIT, Footer.Fallback.No, fetched, stale = false, busyUntil = null))
    }

    @Test
    fun aStaleForecastFromTheFallbackSaysForWhichProviderItStoodIn() {
        val fetched = madeAt.minusHours(3).toInstant()
        val source = Plans.source(fetched, Freshness.STALE, ProviderRole.FALLBACK, "Forecast.Solar")

        val footer = Footer.of(Plans.planned(madeAt, listOf(listOf()), source = source))

        assertThat(footer.fallback).isEqualTo(Footer.Fallback.For("Forecast.Solar"))
        assertThat(footer.stale).isTrue()
    }

    @Test
    fun aFallbackForAPrimaryWithoutANameSaysSo() {
        val source = Plans.source(madeAt.toInstant(), role = ProviderRole.FALLBACK, primaryName = "")

        assertThat(Footer.of(Plans.planned(madeAt, listOf(listOf()), source = source)).fallback)
            .isEqualTo(Footer.Fallback.ForUnknown)
    }

    @ParameterizedTest
    @CsvSource("-1, false", "0, false", "1, true")
    fun theProviderIsBusyOnlyWhileItsCoolDownLastsPastThePlan(minutesAfterThePlan: Long, busy: Boolean) {
        val until = madeAt.plusMinutes(minutesAfterThePlan).toInstant()
        val source = Plans.source(madeAt.toInstant(), busyUntil = until)

        assertThat(Footer.of(Plans.planned(madeAt, listOf(listOf()), source = source)).busyUntil)
            .isEqualTo(if (busy) until else null)
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
    fun theOneOffAwayPeriodsThatEndedAreLeftOutAndTheOthersListedByKind() {
        val ended = OneOffAwayPeriod.on(today, LocalTime.of(7, 0), LocalTime.of(10, 0), amsterdam)
        val running = OneOffAwayPeriod.on(today, LocalTime.of(9, 0), LocalTime.of(11, 0), amsterdam)
        val allDay = OneOffAwayPeriod.allDay(today.plusDays(1), amsterdam)
        val days = OneOffAwayPeriod(today.plusDays(2).atTime(18, 0).atZone(amsterdam).toInstant(),
            today.plusDays(4).atTime(9, 0).atZone(amsterdam).toInstant())

        assertThat(dashboard(listOf(ended, running, allDay, days)).away).containsExactly(
            ComingAway.PartOfDay(DayName.Today(today), running),
            ComingAway.AllDay(DayName.Tomorrow(today.plusDays(1)), allDay),
            ComingAway.Days(DayName.Other(today.plusDays(2)), DayName.Other(today.plusDays(4)), days))
        val listed = dashboard(listOf(days)).away.single() as ComingAway.Days
        assertThat(listed.start to listed.end).isEqualTo(days.start() to days.end())
    }

    @Test
    fun aPeriodIsDescribedFromTheDayThePlanWasMadeAlsoWhenItEnded() {
        val ended = OneOffAwayPeriod.on(today, LocalTime.of(7, 0), LocalTime.of(8, 0), amsterdam)
        val tomorrow = OneOffAwayPeriod.allDay(today.plusDays(1), amsterdam)
        val shown = dashboard()

        assertThat(shown.describe(ended)).isEqualTo(ComingAway.PartOfDay(DayName.Today(today), ended))
        assertThat((shown.describe(ended) as ComingAway.PartOfDay).let { it.start to it.end })
            .isEqualTo(ended.start() to ended.end())
        assertThat(shown.describe(tomorrow)).isEqualTo(ComingAway.AllDay(DayName.Tomorrow(today.plusDays(1)), tomorrow))
    }

    @ParameterizedTest
    @CsvSource("2026-03-29", "2026-10-25")
    fun aWholeShortOrLongDayIsAllDayAndTwentyFourHoursOfItAreNot(day: LocalDate) {
        val start = day.atStartOfDay(amsterdam).toInstant()
        val whole = OneOffAwayPeriod.allDay(day, amsterdam)
        val twentyFourHours = OneOffAwayPeriod(start, start.plusSeconds(24 * 3600))

        val listed = ComingAway.of(listOf(whole, twentyFourHours), amsterdam, start)

        assertThat(listed[0]).isEqualTo(ComingAway.AllDay(DayName.Today(day), whole))
        assertThat(listed[1]).isNotInstanceOf(ComingAway.AllDay::class.java)
    }

    @Test
    fun gridAdviceShowsOnTodaysTabAndNoOther() {
        val advice = GridAdvice(today.plusDays(1), 12.0, 8.0, 7.4, Optional.empty(), Optional.empty())
        val shown = dashboard(gridAdvice = advice)

        assertThat(shown.days.map { shown.adviceOn(it) }).containsExactly(shown.gridAdvice, null, null)
    }

    @Test
    fun aPlanWhoseFirstDayIsNotTodayShowsNoGridAdvice() {
        val advice = GridAdvice(today.plusDays(2), 12.0, 8.0, 7.4, Optional.empty(), Optional.empty())
        val shown = dashboard(gridAdvice = advice)
        val view = shown.view
        val later = shown.copy(view = DashboardView(view.madeAt(), view.zone(), view.days().drop(1), view.levels(),
            view.startLevel(), view.gridAdvice(), view.tripAdvice(), view.footer(), view.away(), view.trips()))

        assertThat(later.days.map { later.adviceOn(it) }).containsOnlyNulls()
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
        assertThat(ticked.days[1].noWindow()).contains(NoWindowReason.NOT_NEEDED)
        assertThat(removed.days[1].noWindow()).contains(NoWindowReason.NOT_NEEDED)
    }

    @Test
    fun theStoredAwayPeriodsKeepTheDaysAsTheStoredTripsMakeThem() {
        val away = trip(today.plusDays(1), "07:00", today.plusDays(1), "19:00")
        val shown = withTrips(listOf(away))
        val period = OneOffAwayPeriod(at(today.plusDays(2), "09:00"), at(today.plusDays(2), "12:00"))

        val changed = shown.withAway(listOf(period))

        assertThat(changed.view).isEqualTo(shown.planned.dashboard(listOf(period), listOf(away)))
        assertThat(changed.days[1].noWindow()).contains(NoWindowReason.AWAY)
        assertThat(changed.away).isEqualTo(ComingAway.of(listOf(period), amsterdam, madeAt.toInstant()))
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
