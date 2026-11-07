package nl.enadeag.carchargeplanner.android.home

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Optional
import kotlin.math.max
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.plan.TripAdvice
import nl.enadeag.carchargeplanner.engine.plan.TripGridCharge
import nl.enadeag.carchargeplanner.engine.plan.TripGridCharge.Start
import nl.enadeag.carchargeplanner.engine.plan.Window
import nl.enadeag.carchargeplanner.engine.trip.Trip
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/** The rules of the desktop's `TripAdviceMapperTest`, on its advice: what the plan does, its source and figures. */
class TripAdviceModelTest {

    companion object {
        val BRUSSELS: ZoneId = ZoneId.of("Europe/Brussels")

        /** A Friday. */
        val TODAY: LocalDate = LocalDate.of(2026, 7, 3)
        val TOMORROW: LocalDate = TODAY.plusDays(1)
        val MADE_AT: Instant = at(TODAY, 10, 20)

        /** Tomorrow from 08:15 to 19:00, 150 km. */
        val TRIP = Trip(at(TOMORROW, 8, 15), at(TOMORROW, 19, 0), 150.0, false)
        val SOLAR = Window(at(TODAY, 12, 0), at(TODAY, 15, 0), 8, 1.8, 5.2, 0.1)

        fun at(day: LocalDate, hour: Int, minute: Int): Instant = day.atTime(hour, minute).atZone(BRUSSELS).toInstant()

        /** A grid charge of 16 A on one phase, from a start to an hour later. */
        fun grid(start: Instant, why: Start): TripGridCharge =
            TripGridCharge(Window(start, start.plusSeconds(3600), 16, 3.7, 0.0, 3.7), why)

        fun advice(neededPct: Double, solar: List<Window>, solarPct: Double, grid: TripGridCharge?,
            departurePct: Double, kmShort: Double, trip: Trip = TRIP): TripAdvice =
            TripAdvice(trip, Percent(neededPct), solar, Percent(solarPct), Optional.ofNullable(grid),
                Percent(departurePct), kmShort, Percent(max(0.0, departurePct - 52)))

        /** Made at 10:20 today from an assumed battery level of 45%, with a target of 80%. */
        fun model(advice: TripAdvice, assumed: Boolean = true): TripAdviceModel =
            TripAdviceModel.of(advice, BRUSSELS, MADE_AT, Percent(80.0), Percent(45.0), assumed)

        @JvmStatic
        fun tripsThatNeedNothingExtra(): List<Arguments> = listOf(
            // the plan's own windows up to the target are enough
            Arguments.of(advice(70.0, listOf(SOLAR), 75.0, null, 75.0, 0.0)),
            // exactly the target is what the plan charges to anyway
            Arguments.of(advice(80.0, listOf(SOLAR), 80.0, null, 80.0, 0.0)),
            // above the target, but the battery holds it already
            Arguments.of(advice(88.0, listOf(), 91.0, null, 91.0, 0.0)),
        )

        @JvmStatic
        fun startsThatCannotWait(): List<Arguments> = listOf(
            Arguments.of(MADE_AT, Start.NOW, true),
            Arguments.of(at(TODAY, 14, 30), Start.NOW, false),
            Arguments.of(MADE_AT.minusSeconds(60), Start.NOW_OUTSIDE_CHARGING_HOURS, true),
            Arguments.of(at(TODAY, 23, 0), Start.NOW_OUTSIDE_CHARGING_HOURS, false),
        )
    }

    @Test
    fun aTripChargedOnSolarPowerAndThenFromTheGridHasBothPartsTheStartAndTheChargeLimitRoundedUp() {
        val start = grid(at(TODAY, 21, 0), Start.PLANNED)

        val model = model(advice(84.2, listOf(SOLAR), 60.0, start, 84.2, 0.0))

        assertThat(model).isEqualTo(TripAdviceModel(TRIP.departure(), TOMORROW, 150.0,
            TripAdviceModel.Plan.SolarThenGrid(Percent(85.0), listOf(TripAdviceModel.DayWindows(TODAY, listOf(SOLAR))),
                TripAdviceModel.GridStart(start.window(), Start.PLANNED, now = false)),
            EnergySource.GRID, null, Percent(45.0), true))
        assertThat(model.asksAction).isTrue()
    }

    @Test
    fun aTripChargedFromTheGridAloneHasNoSolarPart() {
        val model = model(advice(90.0, listOf(), 45.0, grid(at(TOMORROW, 7, 0), Start.PLANNED), 90.0, 0.0))

        assertThat(model.plan).isInstanceOf(TripAdviceModel.Plan.Grid::class.java)
        assertThat((model.plan as TripAdviceModel.Plan.Grid).limit).isEqualTo(Percent(90.0))
        assertThat(model.source).isEqualTo(EnergySource.GRID)
    }

    /** A start no later than the plan's moment is "now"; a later one is when the car is home or the hours begin. */
    @ParameterizedTest
    @MethodSource("startsThatCannotWait")
    fun aGridChargeStartsNowUpToTheMomentOfThePlan(start: Instant, why: Start, now: Boolean) {
        val model = model(advice(90.0, listOf(), 45.0, grid(start, why), 90.0, 0.0))

        assertThat((model.plan as TripAdviceModel.Plan.Grid).grid).isEqualTo(
            TripAdviceModel.GridStart(grid(start, why).window(), why, now))
    }

    @Test
    fun aTripThatEvenAFullBatteryCannotMakeIsShortByWholeKmRoundedUp() {
        val model = model(advice(100.0, listOf(SOLAR), 60.0, grid(at(TODAY, 21, 0), Start.PLANNED), 100.0, 40.2))

        assertThat(model.shortKm).isEqualTo(41)
        assertThat(model.source).isEqualTo(EnergySource.GRID)
        assertThat(model.asksAction).isTrue()
    }

    @Test
    fun aTripTheCarCannotChargeForBeforeItLeavesHasTheLevelItLeavesWithAndHowFarItFallsShort() {
        val model = model(advice(95.0, listOf(), 60.0, null, 60.0, 120.0))

        assertThat(model.plan).isEqualTo(TripAdviceModel.Plan.Leaves(Percent(60.0)))
        assertThat(model.shortKm).isEqualTo(120)
        assertThat(model.source).isEqualTo(EnergySource.GRID)
        assertThat(model.asksAction).isTrue()
    }

    @Test
    fun solarPowerAboveTheTargetHasItsWindowsByDayInOrder() {
        val later = Window(at(TODAY, 16, 0), at(TODAY, 17, 30), 6, 1.4, 2.0, 0.0)
        val tomorrow = Window(at(TOMORROW, 6, 30), at(TOMORROW, 7, 15), 6, 1.4, 0.6, 0.1)

        val model = model(advice(93.6, listOf(SOLAR, later, tomorrow), 94.0, null, 94.0, 0.0))

        assertThat(model.plan).isEqualTo(TripAdviceModel.Plan.Solar(Percent(94.0), listOf(
            TripAdviceModel.DayWindows(TODAY, listOf(SOLAR, later)),
            TripAdviceModel.DayWindows(TOMORROW, listOf(tomorrow)))))
        assertThat(model.source).isEqualTo(EnergySource.SOLAR)
        assertThat(model.asksAction).isTrue()
    }

    @ParameterizedTest
    @MethodSource("tripsThatNeedNothingExtra")
    fun aTripThatNeedsNothingExtraIsEnoughFromTheCar(advice: TripAdvice) {
        val model = model(advice)

        assertThat(model.plan).isEqualTo(TripAdviceModel.Plan.Enough(advice.departureLevelPct()))
        assertThat(model.source).isEqualTo(EnergySource.CAR)
        assertThat(model.asksAction).isFalse()
        assertThat(model.shortKm).isNull()
    }

    @Test
    fun aLevelOnlyAFractionAboveAWholePercentageIsNotRoundedUpToTheNext() {
        val model = model(advice(85 + 1e-9, listOf(), 45.0, grid(at(TODAY, 21, 0), Start.PLANNED), 85.0, 0.0))

        assertThat((model.plan as TripAdviceModel.Plan.Grid).limit).isEqualTo(Percent(85.0))
    }

    @Test
    fun aLevelTheOwnerEnteredIsNotAssumed() {
        val model = model(advice(70.0, listOf(), 75.0, null, 75.0, 0.0), assumed = false)

        assertThat(model.levelAssumed).isFalse()
        assertThat(model.level).isEqualTo(Percent(45.0))
    }

    @Test
    fun theAdviceShowsOnTheDayOfTheDepartureAndOnTodayOnlyWhenItAsksSomething() {
        val asks = model(advice(90.0, listOf(), 45.0, grid(at(TODAY, 21, 0), Start.PLANNED), 90.0, 0.0))
        val enough = model(advice(70.0, listOf(), 75.0, null, 75.0, 0.0))

        assertThat(asks.shownOn(TOMORROW, TODAY)).isTrue()
        assertThat(asks.shownOn(TODAY, TODAY)).isTrue()
        assertThat(asks.shownOn(TODAY.plusDays(2), TODAY)).isFalse()
        assertThat(enough.shownOn(TOMORROW, TODAY)).isTrue()
        assertThat(enough.shownOn(TODAY, TODAY)).isFalse()
    }

    @Test
    fun theDepartureDayIsTheHomesNotTheInstantsUtcDay() {
        // 00:30 in Brussels is still the day before in UTC
        val early = Trip(at(TOMORROW, 0, 30), at(TOMORROW, 6, 0), 40.0, false)

        val model = model(advice(70.0, listOf(), 75.0, null, 75.0, 0.0, trip = early))

        assertThat(model.departureDay).isEqualTo(TOMORROW)
    }
}
