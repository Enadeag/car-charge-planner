package nl.enadeag.carchargeplanner.engine.plan;

import static nl.enadeag.carchargeplanner.engine.plan.Forecasts.PREFERENCES;
import static nl.enadeag.carchargeplanner.engine.plan.Forecasts.TZ;
import static nl.enadeag.carchargeplanner.engine.plan.Forecasts.at;
import static nl.enadeag.carchargeplanner.engine.plan.Forecasts.hourly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import nl.enadeag.carchargeplanner.engine.config.Car;
import nl.enadeag.carchargeplanner.engine.config.HomeSystem;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.Phases;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/**
 * The trip comes first. The car has a 100 kWh battery, so a percent is a kWh, and uses 18 kWh per 100 km; with the
 * 15 % trip surcharge a trip takes 0.207 kWh per km, and 300 km take 62.1 %. The charger gives 7.36 kW at 32 A.
 */
class TripPlanningTest {

    private static final LocalDate THU = LocalDate.of(2026, 7, 2);
    private static final LocalDate FRI = THU.plusDays(1);
    private static final LocalDate SAT = FRI.plusDays(1);
    private static final LocalDate SUN = SAT.plusDays(1);
    private static final Offset<Double> CLOSE = within(1e-6);
    /** Without a daily distance, so only the trips take energy. */
    private static final HomeSystem CAR = system(0);
    /** 8 kW from 09:00 to 17:00: 6.29 kW usable, 27 A on one phase. */
    private static final Map<Integer, Double> SUNNY = Map.of(9, 8.0, 10, 8.0, 11, 8.0, 12, 8.0, 13, 8.0, 14, 8.0,
            15, 8.0, 16, 8.0);

    private static HomeSystem system(double kmPerDay) {
        return Forecasts.withCar(new Car(100, 18, kmPerDay), Phases.ONE);
    }

    private record Planned(Plan plan, Optional<GridAdvice> gridAdvice) {

        TripAdvice trip() {
            return plan.trips().get(0);
        }

        DayPlan day(LocalDate date) {
            return plan.days().stream().filter(day -> day.date().equals(date)).findFirst().orElseThrow();
        }

        Window grid() {
            return trip().grid().orElseThrow().window();
        }
    }

    private static Planned plan(HomeSystem system, List<ForecastStep> forecast, double levelPct, Instant now,
            AwayTimes away) {
        var plan = new Planner(system, PREFERENCES, away).planWithTrips(forecast, new Percent(levelPct), now, List.of());
        return new Planned(plan, new GridAdvisor(system, PREFERENCES, away).advise(plan, forecast, now));
    }

    private static Planned plan(HomeSystem system, List<ForecastStep> forecast, double levelPct, Instant now,
            Trip... trips) {
        return plan(system, forecast, levelPct, now, new AwayTimes(List.of(), List.of(), List.of(trips)));
    }

    private static Planned plan(List<ForecastStep> forecast, double levelPct, Instant now, Trip... trips) {
        return plan(CAR, forecast, levelPct, now, trips);
    }

    /** Days without sun from Thursday on. */
    private static List<ForecastStep> dark(int days) {
        var forecast = new ArrayList<ForecastStep>();
        for (int i = 0; i < days; i++) {
            forecast.addAll(hourly(THU.plusDays(i), Map.of()));
        }
        return forecast;
    }

    private static List<ForecastStep> days(List<Map<Integer, Double>> powerKw) {
        var forecast = new ArrayList<ForecastStep>();
        for (int i = 0; i < powerKw.size(); i++) {
            forecast.addAll(hourly(THU.plusDays(i), powerKw.get(i)));
        }
        return forecast;
    }

    /** The level each day charges up to, to a millionth of a point, so 92.1 matches whatever the sums leave behind. */
    private static List<Double> chargeTo(Planned planned) {
        return planned.plan().days().stream()
                .map(day -> Math.round(day.chargeToPct().value() * 1e6) / 1e6)
                .toList();
    }

    private static Trip trip(LocalDate day, int departureHour, int returnHour, double km) {
        return new Trip(at(day, departureHour), at(day, returnHour), km, false);
    }

    @Test
    void theBatteryHoldsTheTripsEnergyAndTheMinimumAtTheDepartureWithTheRestFromTheGridAsLateAsPossible() {
        var planned = plan(dark(3), 50, at(FRI, 0), trip(SAT, 8, 18, 300));
        var advice = planned.trip();
        // 30 % + 300 km at 0.207 kWh = 92.1 %; no sun, so 42.1 % from the grid: 46.78 kWh; the latest start in the
        // charging hours is 22:00 the evening before, and 23 A (5.29 kW, 8.84 h) is the lowest current done by 07:00
        assertThat(advice.neededPct().value()).isCloseTo(92.1, CLOSE);
        assertThat(advice.solarWindows()).isEmpty();
        assertThat(advice.solarLevelPct().value()).isCloseTo(50, CLOSE);
        assertThat(advice.grid().orElseThrow().start()).isEqualTo(TripGridCharge.Start.PLANNED);
        var grid = planned.grid();
        assertThat(grid.amps()).isEqualTo(23);
        assertThat(List.of(grid.start(), grid.end())).containsExactly(at(FRI, 22), at(SAT, 6, 55));
        assertThat(grid.importKwh()).isCloseTo(42.1 / 0.9, CLOSE);
        assertThat(advice.departureLevelPct().value()).isCloseTo(92.1, CLOSE);
        assertThat(advice.kmShort()).isZero();
        assertThat(advice.returnLevelPct().value()).isCloseTo(30, CLOSE);
    }

    @Test
    void thePlanGoesOnFromTheLevelLeftOnReturn() {
        var planned = plan(dark(4), 50, at(FRI, 0), trip(SAT, 8, 18, 300));
        var saturday = planned.day(SAT);
        // the grid charge starts on Friday evening, so it counts on Friday
        assertThat(planned.day(FRI).endLevelPct().value()).isCloseTo(92.1, CLOSE);
        assertThat(saturday.startLevelPct().value()).isCloseTo(92.1, CLOSE);
        assertThat(saturday.levelAfterChargingPct().value()).isCloseTo(92.1, CLOSE);
        assertThat(saturday.drivingPct()).isCloseTo(62.1, CLOSE);
        assertThat(saturday.endLevelPct().value()).isCloseTo(30, CLOSE);
        assertThat(planned.day(SUN).startLevelPct().value()).isCloseTo(30, CLOSE);
        assertThat(planned.gridAdvice()).isEmpty();
    }

    @Test
    void aRunningAnnouncedWindowIsNoTimeForTheGridCharge() {
        // the window began on Friday, so no day of the plan holds it, but the charger is busy until 07:00
        var away = new AwayTimes(List.of(), List.of(), List.of(trip(SAT, 8, 18, 100)));
        var forecast = dark(4);
        var free = new Planner(CAR, PREFERENCES, away)
                .planWithTrips(forecast, new Percent(40), at(SAT, 0, 30), List.of()).trips().get(0).grid().orElseThrow()
                .window();
        var announced = new Window(at(FRI, 22), at(SAT, 7), 6, 1.38, 0, 0);
        assertThat(free.start()).as("free").isBefore(announced.end());

        var grid = new Planner(CAR, PREFERENCES, away)
                .planWithTrips(forecast, new Percent(40), at(SAT, 0, 30), List.of(announced)).trips().get(0).grid()
                .orElseThrow().window();

        assertThat(grid.start()).isAfterOrEqualTo(announced.end());
    }

    @Test
    void aShortfallOfExactlyOnePercentIsNoShortfall() {
        // 20 kWh per 100 km without a surcharge: 105 km take 21 %, so the trip needs 30 + 21 = 51 % at 50 %: 1 % short
        var system = Forecasts.withCar(new Car(100, 20, 0, new Percent(0)), Phases.ONE);
        var advice = plan(system, dark(3), 50, at(FRI, 0), trip(SAT, 8, 18, 105)).trip();

        assertThat(advice.neededPct().value()).isEqualTo(51);
        assertThat(advice.grid()).isEmpty();
        assertThat(advice.kmShort()).isZero();
    }

    @Test
    void aShortfallOfMoreThanOnePercentGetsAGridCharge() {
        var system = Forecasts.withCar(new Car(100, 20, 0, new Percent(0)), Phases.ONE);
        var advice = plan(system, dark(3), 50, at(FRI, 0), trip(SAT, 8, 18, 106)).trip();

        assertThat(advice.neededPct().value()).isCloseTo(51.2, CLOSE);
        assertThat(advice.grid()).isPresent();
    }

    @Test
    void theTripSurchargeIsTheCars() {
        var system = Forecasts.withCar(new Car(100, 18, 0, new Percent(25)), Phases.ONE);
        var advice = plan(system, dark(3), 50, at(FRI, 0), trip(SAT, 8, 18, 300)).trip();
        // 300 km at 0.225 kWh: 67.5 %
        assertThat(advice.neededPct().value()).isCloseTo(97.5, CLOSE);
    }

    @Test
    void solarPowerBeforeTheDepartureComesFirstAndMayGoAboveTheTarget() {
        var planned = plan(days(List.of(Map.of(), SUNNY, Map.of())), 50, at(FRI, 0), trip(SAT, 8, 18, 300));
        var advice = planned.trip();
        assertThat(advice.grid()).isEmpty();
        assertThat(advice.solarWindows()).isEqualTo(planned.day(FRI).windows());
        assertThat(advice.solarWindows()).extracting(Window::start, Window::amps)
                .containsExactly(tuple(at(FRI, 9), 27));
        assertThat(advice.solarLevelPct().value()).isCloseTo(92.1, CLOSE);
        assertThat(planned.day(FRI).levelAfterChargingPct().value()).isCloseTo(92.1, CLOSE);
        assertThat(advice.departureLevelPct().value()).isCloseTo(92.1, CLOSE);
    }

    @Test
    void thePartAboveTheTargetComesFromTheLatestSunnyDay() {
        var planned = plan(days(List.of(SUNNY, SUNNY, Map.of())), 50, at(THU, 0), trip(SAT, 8, 18, 300));
        assertThat(planned.day(THU).levelAfterChargingPct().value()).isCloseTo(80, CLOSE);
        assertThat(planned.day(FRI).levelAfterChargingPct().value()).isCloseTo(92.1, CLOSE);
        assertThat(planned.trip().solarWindows()).hasSize(2);
    }

    @Test
    void aTripOfSeveralDaysTakesTheSunOfItsDepartureDayBeforeItLeaves() {
        var morning = Map.of(9, 8.0, 10, 8.0, 11, 8.0);
        var trip = new Trip(at(SAT, 14), at(SAT.plusDays(3), 18), 300, false);
        var planned = plan(days(List.of(Map.of(), morning, SUNNY, Map.of(), SUNNY)), 30, at(THU, 0), trip);
        assertThat(planned.day(SAT).levelAfterChargingPct().value()).isCloseTo(92.1, CLOSE);
        assertThat(planned.trip().departureLevelPct().value()).isCloseTo(92.1, CLOSE);
    }

    @Test
    void aTripThatNeedsLessThanTheTargetLeavesTheSolarPlanAsItWas() {
        var forecast = days(List.of(SUNNY, Map.of(), Map.of()));
        var without = new Planner(CAR, PREFERENCES, AwayTimes.NONE).plan(forecast, new Percent(50), at(THU, 0), List.of());
        var planned = plan(forecast, 50, at(THU, 0), trip(SAT, 8, 18, 100));
        // 30 % + 20.7 %: the Thursday's 80 % is enough
        assertThat(planned.trip().neededPct().value()).isCloseTo(50.7, CLOSE);
        assertThat(planned.trip().grid()).isEmpty();
        assertThat(planned.day(THU)).isEqualTo(without.get(0));
        assertThat(planned.trip().returnLevelPct().value()).isCloseTo(80 - 20.7, CLOSE);
    }

    @Test
    void whenTheSunIsNotEnoughTheGridChargeComesAfterTheLastSolarWindow() {
        // 4 kW from 10:00 to 12:00 leaves 2.89 kW: 13 A, which takes the 0.2 kWh the window may import
        var friday = Map.of(10, 4.0, 11, 4.0);
        var planned = plan(days(List.of(Map.of(), friday, Map.of())), 50, at(FRI, 0), trip(SAT, 8, 18, 300));
        var advice = planned.trip();
        assertThat(advice.solarWindows()).extracting(Window::start, Window::end, Window::amps)
                .containsExactly(tuple(at(FRI, 10), at(FRI, 12), 13));
        double solarPct = 50 + 2 * 2.89 * 0.9;
        assertThat(advice.solarLevelPct().value()).isCloseTo(solarPct, CLOSE);
        var grid = planned.grid();
        assertThat(grid.solarKwh() + grid.importKwh()).isCloseTo((92.1 - solarPct) / 0.9, CLOSE);
        // 41 kWh from 22:00, the latest start in the charging hours: 20 A (4.6 kW, 8.91 h) is done by 07:00
        assertThat(List.of(grid.start(), grid.end())).containsExactly(at(FRI, 22), at(SAT, 6, 55));
        assertThat(grid.amps()).isEqualTo(20);
        assertThat(advice.departureLevelPct().value()).isCloseTo(92.1, CLOSE);
    }

    @Test
    void theGridChargeDoesNotGoBeforeTheDayOfTheLastSolarWindow() {
        // Thursday's leftover sun would take less from the grid, but Friday's solar window would then charge less
        var thursday = Map.of(9, 1.6, 10, 1.6, 11, 1.6, 12, 1.6, 13, 1.6, 14, 1.6, 15, 1.6, 16, 1.6);
        var planned = plan(days(List.of(thursday, Map.of(10, 4.0, 11, 4.0), Map.of())), 50, at(THU, 0),
                trip(SAT, 8, 18, 300));
        var grid = planned.grid();
        assertThat(List.of(grid.start(), grid.end())).containsExactly(at(FRI, 22), at(SAT, 6, 55));
        assertThat(planned.trip().departureLevelPct().value()).isCloseTo(92.1, CLOSE);
    }

    @Test
    void theGridChargeUsesLeftoverSunBeforeTheNight() {
        // 1.6 kW leaves 0.85 kW, too little for a window, but a 6 A grid charge takes it
        var friday = Map.of(9, 1.6, 10, 1.6, 11, 1.6, 12, 1.6, 13, 1.6, 14, 1.6, 15, 1.6, 16, 1.6);
        var grid = plan(days(List.of(Map.of(), friday, Map.of())), 85, at(FRI, 0), trip(SAT, 8, 18, 300)).grid();
        // 7.1 % is 7.89 kWh: 5.72 h at 6 A, all in the leftover sun, which lasts until 17:00
        assertThat(grid.amps()).isEqualTo(6);
        assertThat(grid.start()).isEqualTo(at(FRI, 11, 15));
        assertThat(grid.solarKwh()).isCloseTo(0.85 * 7.1 / 0.9 / 1.38, CLOSE);
    }

    @Test
    void withTooLittleTimeLeftTheAdviceIsToChargeNow() {
        // 100 km: 50.7 %, 10.7 % short: 11.89 kWh, 1.62 h at 32 A, which cannot be done by 21:00
        var advice = plan(dark(2), 40, at(FRI, 20), trip(FRI, 22, 23, 100)).trip();
        var grid = advice.grid().orElseThrow();
        assertThat(grid.start()).isEqualTo(TripGridCharge.Start.NOW);
        assertThat(List.of(grid.window().start(), grid.window().end())).containsExactly(at(FRI, 20), at(FRI, 21, 40));
        assertThat(grid.window().amps()).isEqualTo(32);
        assertThat(advice.departureLevelPct().value()).isCloseTo(50.7, CLOSE);
        assertThat(advice.kmShort()).isZero();
    }

    @Test
    void chargingNowThatCannotFinishLeavesTheCarShort() {
        var advice = plan(dark(2), 50, at(FRI, 20), trip(FRI, 22, 23, 300)).trip();
        var window = advice.grid().orElseThrow().window();
        // two hours at 7.36 kW: 13.248 %, so 28.852 % or 139.4 km short
        assertThat(List.of(window.start(), window.end())).containsExactly(at(FRI, 20), at(FRI, 22));
        assertThat(advice.departureLevelPct().value()).isCloseTo(63.248, CLOSE);
        assertThat(advice.kmShort()).isCloseTo(28.852 / 0.207, CLOSE);
    }

    @Test
    void chargingNowStartsWhenTheCarIsBack() {
        var away = new AwayTimes(List.of(), List.of(new OneOffAwayPeriod(at(FRI, 19), at(FRI, 20, 30))),
                List.of(trip(FRI, 22, 23, 100)));
        var window = plan(CAR, dark(2), 45, at(FRI, 19), away).grid();
        assertThat(window.start()).isEqualTo(at(FRI, 20, 30));
    }

    @Test
    void aCarThatIsNeverHomeBeforeTheDepartureGetsNoGridChargeAndIsShort() {
        var away = new AwayTimes(List.of(), List.of(new OneOffAwayPeriod(at(FRI, 0), at(SAT, 8))),
                List.of(trip(SAT, 8, 18, 100)));
        var advice = plan(CAR, dark(3), 40, at(FRI, 0), away).trip();
        assertThat(advice.grid()).isEmpty();
        assertThat(advice.departureLevelPct().value()).isCloseTo(40, CLOSE);
        assertThat(advice.kmShort()).isCloseTo(10.7 / 0.207, CLOSE);
    }

    @Test
    void whenEvenAFullBatteryIsNotEnoughThePlanSaysHowManyKmAreMissing() {
        var planned = plan(dark(3), 50, at(FRI, 0), trip(SAT, 12, 18, 450));
        var advice = planned.trip();
        // 30 % + 93.15 % = 123.15 %: charged to 100 %, 23.15 kWh or 111.8 km short
        assertThat(advice.neededPct().value()).isCloseTo(100, CLOSE);
        assertThat(advice.departureLevelPct().value()).isCloseTo(100, CLOSE);
        assertThat(advice.kmShort()).isCloseTo(23.15 / 0.207, CLOSE);
        assertThat(advice.returnLevelPct().value()).isCloseTo(6.85, CLOSE);
        var grid = planned.grid();
        // a start at 07:00 is too late to be done by 11:00, so it starts at 22:00 the evening before
        assertThat(List.of(grid.start(), grid.end())).containsExactly(at(FRI, 22), at(SAT, 10, 45));
        assertThat(planned.gridAdvice()).isEmpty(); // the trip's own day is left to its advice
    }

    @Test
    void withChargingAtTheDestinationOnlyTheWayThereCounts() {
        var trip = new Trip(at(SAT, 8), at(SAT, 18), 300, true);
        var advice = plan(dark(3), 50, at(FRI, 0), trip).trip();
        // 150 km: 31.05 %
        assertThat(advice.neededPct().value()).isCloseTo(61.05, CLOSE);
        assertThat(advice.departureLevelPct().value()).isCloseTo(61.05, CLOSE);
        assertThat(advice.returnLevelPct().value()).isCloseTo(30, CLOSE);
    }

    @Test
    void otherDrivingStillCountsEveryDayAndTripsComeOnTop() {
        // 50 km a day is 9 %; a trip of two days needs both days' daily distance on top of its 41.4 %
        var trip = new Trip(at(SAT, 8), at(SUN, 18), 200, false);
        var planned = plan(system(50), dark(4), 95, at(FRI, 0), trip);
        assertThat(planned.trip().neededPct().value()).isCloseTo(30 + 41.4 + 18, CLOSE);
        // 95 % - 9 % on Friday, and the 3.4 % short from the grid in the evening
        assertThat(planned.day(FRI).endLevelPct().value()).isCloseTo(89.4, CLOSE);
        assertThat(planned.day(SAT).drivingPct()).isCloseTo(9 + 41.4, CLOSE);
        assertThat(planned.day(SUN).endLevelPct().value()).isCloseTo(30, CLOSE);
    }

    @Test
    void aReturnAtMidnightBelongsToTheDayBefore() {
        var trip = new Trip(at(SAT, 8), at(SUN, 0), 200, false);
        var advice = plan(system(50), dark(4), 95, at(FRI, 0), trip).trip();
        assertThat(advice.neededPct().value()).isCloseTo(30 + 41.4 + 9, CLOSE);
    }

    @Test
    void aDepartureAtMidnightIsChargedForOnTheDayBefore() {
        var planned = plan(dark(3), 50, at(FRI, 0), new Trip(at(SAT, 0), at(SAT, 18), 300, false));
        var grid = planned.grid();
        // done by 23:00: 17:30 to 23:55 would be too late
        assertThat(List.of(grid.start(), grid.end())).containsExactly(at(FRI, 16, 30), at(FRI, 22, 55));
        assertThat(planned.day(FRI).levelAfterChargingPct().value()).isCloseTo(92.1, CLOSE);
        assertThat(planned.day(FRI).endLevelPct().value()).isCloseTo(92.1, CLOSE);
        assertThat(planned.day(SAT).startLevelPct().value()).isCloseTo(92.1, CLOSE);
        assertThat(planned.day(SAT).endLevelPct().value()).isCloseTo(30, CLOSE);
    }

    @Test
    void aGridChargeAcrossTheRepeatedHourIsDoneAnHourBeforeTheDeparture() {
        var day = LocalDate.of(2026, 10, 25);
        var forecast = new ArrayList<>(hourly(day.minusDays(1), Map.of()));
        forecast.addAll(hourly(day, Map.of()));
        var grid = plan(forecast, 50, at(day.minusDays(1), 12), trip(day, 8, 18, 300)).grid();
        // 08:00 winter time is 07:00 UTC; from 22:00 summer time there are 10 real hours to 07:00 winter time, and
        // 21 A (4.83 kW) takes 9.68 of them, up to 06:45 winter time
        assertThat(grid.start()).isEqualTo(Instant.parse("2026-10-24T20:00:00Z"));
        assertThat(grid.amps()).isEqualTo(21);
        assertThat(grid.end()).isEqualTo(Instant.parse("2026-10-25T05:45:00Z"));
        assertThat(grid.end()).isEqualTo(at(day, 6, 45));
    }

    @Test
    void twoTripsOnFollowingDaysEachGetTheirOwnCharge() {
        var planned = plan(dark(4), 50, at(FRI, 0), trip(SAT, 8, 18, 300), trip(SUN, 8, 18, 300));
        var first = planned.plan().trips().get(0);
        var second = planned.plan().trips().get(1);
        assertThat(first.departureLevelPct().value()).isCloseTo(92.1, CLOSE);
        // from 30 % after Saturday's return to 92.1 %: 69 kWh, 9.38 h at 32 A, done by 07:00 on Sunday
        var grid = second.grid().orElseThrow().window();
        assertThat(List.of(grid.start(), grid.end())).containsExactly(at(SAT, 21, 30), at(SUN, 6, 55));
        assertThat(second.departureLevelPct().value()).isCloseTo(92.1, CLOSE);
        assertThat(planned.day(SAT).endLevelPct().value()).isCloseTo(92.1, CLOSE);
        assertThat(planned.day(SUN).endLevelPct().value()).isCloseTo(30, CLOSE);
    }

    @Test
    void withoutRoomBetweenTwoTripsTheFirstChargesForBoth() {
        // 150 km is 31.05 %; home for half an hour on Sunday, charging at once: 3.68 kWh or 3.312 %
        var first = new Trip(at(SAT, 8), at(SUN, 7, 30), 150, false);
        var second = new Trip(at(SUN, 8), at(SUN, 18), 150, false);
        var planned = plan(dark(4), 50, at(FRI, 0), first, second);
        var advice = planned.plan().trips();
        assertThat(advice.get(0).neededPct().value()).isCloseTo(61.05 + 31.05 - 3.312, CLOSE);
        assertThat(advice.get(0).departureLevelPct().value()).isCloseTo(61.05 + 31.05 - 3.312, CLOSE);
        assertThat(advice.get(0).grid().orElseThrow().window().end()).isBeforeOrEqualTo(at(SAT, 7));
        var charge = advice.get(1).grid().orElseThrow();
        assertThat(charge.start()).isEqualTo(TripGridCharge.Start.NOW);
        assertThat(List.of(charge.window().start(), charge.window().end())).containsExactly(at(SUN, 7, 30), at(SUN, 8));
        assertThat(advice.get(1).departureLevelPct().value()).isCloseTo(61.05, CLOSE);
        assertThat(advice.get(1).kmShort()).isZero();
    }

    @Test
    void withRoomBetweenTwoTripsEachChargesOnlyForItself() {
        var planned = plan(dark(4), 50, at(FRI, 0), trip(SAT, 8, 18, 150), trip(SUN, 8, 18, 150));
        assertThat(planned.plan().trips()).hasSize(2).allSatisfy(
                advice -> assertThat(advice.neededPct().value()).isCloseTo(61.05, CLOSE));
        assertThat(planned.plan().trips().get(1).grid().orElseThrow().start()).isEqualTo(TripGridCharge.Start.PLANNED);
    }

    @Test
    void threeTripsInARowAreChargedForBeforeTheFirst() {
        // back to back, 100 km each: 20.7 %, three times on top of the minimum
        var a = new Trip(at(FRI, 8), at(FRI, 20), 100, false);
        var b = new Trip(at(FRI, 20), at(SAT, 20), 100, false);
        var c = new Trip(at(SAT, 20), at(SUN, 18), 100, false);
        var advice = plan(dark(4), 50, at(THU, 0), a, b, c).plan().trips();
        var levels = List.of(92.1, 71.4, 50.7);
        for (int i = 0; i < 3; i++) {
            assertThat(advice.get(i).neededPct().value()).isCloseTo(levels.get(i), CLOSE);
            assertThat(advice.get(i).departureLevelPct().value()).isCloseTo(levels.get(i), CLOSE);
        }
        assertThat(advice.get(0).grid().orElseThrow().window().end()).isBeforeOrEqualTo(at(FRI, 7));
        assertThat(advice).allSatisfy(trip -> assertThat(trip.kmShort()).isZero());
    }

    @Test
    void whatCannotFitEvenAtAFullBatteryLeavesTheLaterTripShort() {
        var first = new Trip(at(SAT, 8), at(SUN, 8), 300, false);
        var second = new Trip(at(SUN, 8), at(SUN, 18), 300, false);
        var advice = plan(dark(4), 50, at(FRI, 0), first, second).plan().trips();
        // 30 % + 2 × 62.1 % does not fit: the first leaves full and makes it, the second leaves at 37.9 %
        assertThat(advice.get(0).departureLevelPct().value()).isCloseTo(100, CLOSE);
        assertThat(advice.get(0).kmShort()).isZero();
        assertThat(advice.get(1).departureLevelPct().value()).isCloseTo(37.9, CLOSE);
        assertThat(advice.get(1).kmShort()).isCloseTo((92.1 - 37.9) / 0.207, CLOSE);
    }

    @Test
    void aGridAdviceAfterATripIsPlacedAfterItsReturn() {
        // 70 % - 9 % reaches the trip's 59.7 % without the grid; Sunday then ends at 22.3 %, and the car is away all
        // Sunday, so the grid charge goes on Saturday, after the return
        var away = new AwayTimes(List.of(), List.of(OneOffAwayPeriod.allDay(SUN, TZ)), List.of(trip(SAT, 8, 14, 100)));
        var planned = plan(system(50), dark(4), 70, at(FRI, 0), away);
        assertThat(planned.trip().grid()).isEmpty();
        var advice = planned.gridAdvice().orElseThrow();
        assertThat(advice.day()).isEqualTo(SUN);
        assertThat(advice.window().orElseThrow().start()).isEqualTo(at(SAT, 14));
        // back with 40.3 %, and the 7.7 % Sunday falls short
        assertThat(planned.trip().returnLevelPct().value()).isCloseTo(40.3, CLOSE);
        assertThat(advice.chargeLimitPct()).contains(new Percent(48));
    }

    @Test
    void aDipBeforeATripStillGetsItsGridAdviceBesideTheTripsCharge() {
        var planned = plan(system(50), dark(4), 35, at(FRI, 0), trip(SUN, 8, 18, 100));
        var advice = planned.gridAdvice().orElseThrow();
        assertThat(advice.day()).isEqualTo(FRI);
        var window = advice.window().orElseThrow();
        var grid = planned.grid();
        assertThat(!window.end().isAfter(grid.start()) || !window.start().isBefore(grid.end()))
                .as("%s overlaps %s", window, grid).isTrue();
        assertThat(planned.trip().departureLevelPct().value()).isCloseTo(59.7, CLOSE);
    }

    @Test
    void aGridAdviceStaysClearOfATripsGridCharge() {
        var forecast = dark(3);
        var days = new Planner(system(50), PREFERENCES, AwayTimes.NONE).plan(forecast, new Percent(35), at(FRI, 0),
                List.of());
        var tripGrid = new Window(at(FRI, 7), at(FRI, 9), 32, 7.36, 0, 14.72);
        var trip = new TripAdvice(trip(SAT, 8, 18, 100), new Percent(59.7), List.of(), new Percent(17),
                Optional.of(new TripGridCharge(tripGrid, TripGridCharge.Start.PLANNED)), new Percent(59.7), 0, new Percent(39));
        var advice = new GridAdvisor(system(50), PREFERENCES, AwayTimes.NONE)
                .advise(new Plan(days, List.of(trip)), forecast, at(FRI, 0)).orElseThrow();
        // Friday ends at 26 %: 4.44 kWh, 3.22 h at 6 A, from the end of the trip's grid charge
        assertThat(advice.day()).isEqualTo(FRI);
        var window = advice.window().orElseThrow();
        assertThat(List.of(window.start(), window.end())).containsExactly(at(FRI, 9), at(FRI, 12, 15));
        // 35 % and the trip's 13.25 % before it, and its own 4 %
        assertThat(advice.chargeLimitPct()).contains(new Percent(53));
    }

    @Test
    void anEmptyForecastGivesAnEmptyPlan() {
        var plan = new Planner(CAR, PREFERENCES, new AwayTimes(List.of(), List.of(), List.of(trip(SAT, 8, 18, 300))))
                .planWithTrips(List.of(), new Percent(50), at(FRI, 0), List.of());
        assertThat(plan).isEqualTo(new Plan(List.of(), List.of()));
    }

    @Test
    void whenNoDayReachesWhatTheTripNeedsTheLatestDayThatGivesTheMostChargesAboveTheTarget() {
        // 250 km is 51.75 %, with Saturday's 9 % of daily distance 90.75 %; Friday's driving takes 9 % after its sun,
        // so neither sunny day is enough, and Thursday charges only to the target
        var planned = plan(system(50), days(List.of(SUNNY, SUNNY, Map.of(), Map.of())), 50, at(THU, 0),
                trip(SAT, 8, 18, 250));
        assertThat(planned.trip().neededPct().value()).isCloseTo(90.75, CLOSE);
        assertThat(planned.day(THU).levelAfterChargingPct().value()).isCloseTo(80, CLOSE);
        assertThat(planned.trip().solarLevelPct().value()).isCloseTo(81.75, CLOSE);
        // the 9 % from the grid goes in Friday's leftover sun after the solar window, and counts after the driving
        var grid = planned.grid();
        assertThat(grid.start().atZone(TZ).toLocalDate()).isEqualTo(FRI);
        assertThat(planned.day(FRI).levelAfterChargingPct().value()).isCloseTo(90.75, CLOSE);
        assertThat(planned.day(FRI).endLevelPct().value()).isCloseTo(90.75, CLOSE);
        assertThat(planned.trip().departureLevelPct().value()).isCloseTo(90.75, CLOSE);
    }

    @Test
    void aGridChargeAfterTheDaysDrivingCountsFromTheLevelTheDrivingLeaves() {
        // 30 % + 250 km (51.75 %) + Saturday's 9 % = 90.75 %; Friday drives 9 % from 50 % and the evening's grid charge
        // brings the 41 % to 90.75 %, never 50 % + 49.75 %
        var planned = plan(system(50), dark(3), 50, at(FRI, 0), trip(SAT, 8, 18, 250));
        var friday = planned.day(FRI);
        assertThat(planned.grid().start().atZone(TZ).toLocalDate()).isEqualTo(FRI);
        assertThat(friday.startLevelPct().value()).isCloseTo(50, CLOSE);
        assertThat(friday.levelAfterChargingPct().value()).isCloseTo(90.75, CLOSE);
        assertThat(friday.endLevelPct().value()).isCloseTo(90.75, CLOSE);
        assertThat(planned.day(SAT).startLevelPct().value()).isCloseTo(90.75, CLOSE);
    }

    @Test
    void theIssuesSaturdayShowsTheLevelTheTripLeavesWithNotAFullBattery() {
        // the 60 kWh car of 40 km a day (12 %): 150 km on Sunday take 51.75 %, so Sunday needs 30 + 51.75 + 12 =
        // 93.75 %; Saturday drives from 59 % to 47 % and Saturday evening's grid charge adds 46.75 %, where 59 % plus
        // that would pass 100 %
        var home = Forecasts.withCar(new Car(60, 18, 40), Phases.ONE);
        var forecast = new ArrayList<ForecastStep>();
        for (int i = 0; i < 3; i++) {
            forecast.addAll(hourly(SAT.plusDays(i), Map.of()));
        }
        var planned = plan(home, forecast, 59, at(SAT, 0), new Trip(at(SUN, 8, 30), at(SUN, 18), 150, false));
        var saturday = planned.day(SAT);
        assertThat(planned.grid().start().atZone(TZ).toLocalDate()).isEqualTo(SAT);
        assertThat(saturday.startLevelPct().value()).isCloseTo(59, CLOSE);
        assertThat(saturday.levelAfterChargingPct().value()).isCloseTo(93.75, CLOSE);
        assertThat(saturday.endLevelPct().value()).isCloseTo(93.75, CLOSE);
        assertThat(planned.day(SUN).startLevelPct().value()).isCloseTo(93.75, CLOSE);
        assertThat(planned.trip().departureLevelPct().value()).isCloseTo(93.75, CLOSE);
        assertThat(planned.trip().chargeLimitPct()).isEqualTo(new Percent(94));
    }

    @Test
    void theDayBetweenTwoTripsShowsTheLevelItsGridChargeReachesForTheSecond() {
        // Saturday needs 30 + 20.7 + 9 = 59.7 %, Monday 30 + 51.75 + 9 = 90.75 %; Sunday starts at the 30 % Saturday
        // leaves, drives to 21 % and Sunday evening's grid charge brings it to 90.75 %
        var planned = plan(system(50), dark(5), 50, at(FRI, 0), trip(SAT, 8, 18, 100),
                trip(SUN.plusDays(1), 8, 18, 250));
        var sunday = planned.day(SUN);
        assertThat(planned.plan().trips().get(1).grid().orElseThrow().window().start().atZone(TZ).toLocalDate())
                .isEqualTo(SUN);
        assertThat(sunday.startLevelPct().value()).isCloseTo(30, CLOSE);
        assertThat(sunday.levelAfterChargingPct().value()).isCloseTo(90.75, CLOSE);
        assertThat(sunday.endLevelPct().value()).isCloseTo(90.75, CLOSE);
        assertThat(planned.day(FRI).levelAfterChargingPct().value()).isCloseTo(59.7, CLOSE);
        assertThat(planned.day(SAT).levelAfterChargingPct().value()).isCloseTo(59.7, CLOSE);
    }

    @Test
    void onADepartureDayTheSunAfterTheReturnGivesTheLevelAfterCharging() {
        // 90 km take 18.63 %, so 50 % is enough for the 48.63 % the trip needs; the car comes back at 08:00 with
        // 31.37 % and the strong sun charges it to the 80 % target
        var strong = Map.of(9, 12.0, 10, 12.0, 11, 12.0, 12, 12.0, 13, 12.0, 14, 12.0, 15, 12.0, 16, 12.0);
        var planned = plan(days(List.of(Map.of(), Map.of(), strong)), 50, at(FRI, 0), trip(SAT, 5, 8, 90));
        var saturday = planned.day(SAT);
        assertThat(planned.trip().departureLevelPct().value()).isCloseTo(50, CLOSE);
        assertThat(planned.trip().returnLevelPct().value()).isCloseTo(31.37, CLOSE);
        assertThat(saturday.levelAfterChargingPct().value()).isCloseTo(80, CLOSE);
        assertThat(saturday.endLevelPct().value()).isCloseTo(80, CLOSE);
    }

    @Test
    void onADayWithTwoTripsTheLevelAfterChargingIsTheHighestBeforeEitherLeaves() {
        // each trip takes 20.7 % and needs 50.7 %; the first leaves with the 50 % it starts with, short by less than a
        // point, comes back with 29.3 %, and the sun charges it to 80 % before the second, which leaves 59.3 %
        var strong = Map.of(9, 12.0, 10, 12.0, 11, 12.0, 12, 12.0, 13, 12.0, 14, 12.0, 15, 12.0, 16, 12.0);
        var planned = plan(days(List.of(Map.of(), Map.of(), strong)), 50, at(FRI, 0), trip(SAT, 6, 8, 100),
                trip(SAT, 18, 20, 100));
        var saturday = planned.day(SAT);
        assertThat(planned.plan().trips()).extracting(advice -> advice.departureLevelPct().value())
                .containsExactly(50.0, 80.0);
        assertThat(saturday.levelAfterChargingPct().value()).isCloseTo(80, CLOSE);
        assertThat(saturday.endLevelPct().value()).isCloseTo(59.3, CLOSE);
    }

    @Test
    void aSmallGridChargeAfterTheDrivingLeavesTheSolarLevelAsTheLevelAfterCharging() {
        // the sun brings Friday to the 80 % target; 180 km (37.26 %) need 30 + 37.26 + 9 = 76.26 %, below the target,
        // so Friday drives to 71 % and its evening's grid charge adds 5.26 %, which ends the day under the 80 %
        var planned = plan(system(50), days(List.of(Map.of(), SUNNY, Map.of())), 50, at(FRI, 0),
                trip(SAT, 8, 18, 180));
        var friday = planned.day(FRI);
        assertThat(planned.grid().start().atZone(TZ).toLocalDate()).isEqualTo(FRI);
        assertThat(friday.levelAfterChargingPct().value()).isCloseTo(80, CLOSE);
        assertThat(friday.endLevelPct().value()).isCloseTo(76.26, CLOSE);
    }

    @Test
    void aDayWithoutChargingShowsItsStartAsTheLevelAfterCharging() {
        var planned = plan(system(50), dark(2), 50, at(FRI, 0));
        var friday = planned.day(FRI);
        assertThat(friday.levelAfterChargingPct().value()).isCloseTo(50, CLOSE);
        assertThat(friday.endLevelPct().value()).isCloseTo(41, CLOSE);
        assertThat(friday.drivingShortPct()).isZero();
    }

    @Test
    void aDayKeepsTheDailyDrivingTheBatteryCannotHold() {
        // 600 km a day take 108 %: Friday runs out 58 points short, and every day after it starts empty
        var planned = plan(system(600), dark(3), 50, at(FRI, 0));
        var friday = planned.day(FRI);
        assertThat(friday.levelAfterChargingPct().value()).isCloseTo(50, CLOSE);
        assertThat(friday.endLevelPct().value()).isZero();
        assertThat(friday.drivingPct()).isCloseTo(108, CLOSE);
        assertThat(friday.drivingShortPct()).isCloseTo(58, CLOSE);
        assertThat(planned.day(SAT).startLevelPct().value()).isZero();
        assertThat(planned.day(SAT).levelAfterChargingPct().value()).isZero();
        assertThat(planned.day(SAT).drivingShortPct()).isCloseTo(108, CLOSE);
        assertThat(planned.gridAdvice().orElseThrow().endLevelPct()).isCloseTo(-58, CLOSE);
    }

    @Test
    void aDayKeepsTheTripTheBatteryCannotHold() {
        // 600 km take 124.2 %, more than the full battery the trip leaves with
        var planned = plan(dark(3), 50, at(FRI, 0), trip(SAT, 8, 18, 600));
        var saturday = planned.day(SAT);
        assertThat(saturday.startLevelPct().value()).isCloseTo(100, CLOSE);
        assertThat(saturday.levelAfterChargingPct().value()).isCloseTo(100, CLOSE);
        assertThat(saturday.endLevelPct().value()).isZero();
        assertThat(saturday.drivingShortPct()).isCloseTo(24.2, CLOSE);
        assertThat(planned.day(FRI).drivingShortPct()).isZero();
    }

    @Test
    void fromMiddayOnlyTheRestOfTheDaysDrivingComesBeforeTheGridCharge() {
        // from 12:00 Friday drives 4.5 %, to 45.5 %, and the evening's grid charge adds 45.25 % for Saturday's 90.75 %
        var planned = plan(system(50), dark(3), 50, at(FRI, 12), trip(SAT, 8, 18, 250));
        var friday = planned.day(FRI);
        assertThat(friday.drivingPct()).isCloseTo(4.5, CLOSE);
        assertThat(friday.levelAfterChargingPct().value()).isCloseTo(90.75, CLOSE);
        assertThat(friday.endLevelPct().value()).isCloseTo(90.75, CLOSE);
    }

    @Test
    void anEarlierDaysGridChargeStopsAtAFullBattery() {
        // 260 km take 53.82 %, so Sunday needs 30 + 53.82 + 9 = 92.82 %; away from Saturday 06:00, the grid charge
        // runs on Friday evening, and from Friday's 41 % after its driving its 92.82 - 32 = 60.82 % (as Saturday drives
        // 9 % more) would reach 101.82 %: it stops at 100 %, and Sunday leaves with 91 %, under the 92.82 % it needs.
        // That shortfall is the problem of a need above 100 % over several days that #511 holds, not this test's point
        var away = new AwayTimes(List.of(), List.of(new OneOffAwayPeriod(at(SAT, 6), at(SUN, 7))),
                List.of(trip(SUN, 8, 18, 260)));
        var planned = plan(system(50), dark(4), 50, at(FRI, 0), away);
        var friday = planned.day(FRI);
        assertThat(planned.grid().start().atZone(TZ).toLocalDate()).isEqualTo(FRI);
        assertThat(friday.endLevelPct().value()).isEqualTo(100);
        assertThat(friday.levelAfterChargingPct().value()).isEqualTo(100);
        assertThat(planned.day(SAT).endLevelPct().value()).isCloseTo(91, CLOSE);
        assertThat(planned.trip().departureLevelPct().value()).isCloseTo(91, CLOSE);
    }

    @Test
    void aDepartureDayWithAGridChargeBeforeAndSunAfterTheTripShowsTheHighestOfBoth() {
        // 145 km take 30.015 %, so the trip needs 60.015 %: the 10.015 % from the grid runs on Saturday morning before
        // the 10:00 departure; the car is back at 11:00 with 30 % and the sun until 19:00 charges it to the 80 % target
        var afternoon = Map.of(11, 12.0, 12, 12.0, 13, 12.0, 14, 12.0, 15, 12.0, 16, 12.0, 17, 12.0, 18, 12.0);
        var planned = plan(days(List.of(Map.of(), Map.of(), afternoon)), 50, at(FRI, 0), trip(SAT, 10, 11, 145));
        var saturday = planned.day(SAT);
        assertThat(planned.grid().start().atZone(TZ).toLocalDate()).isEqualTo(SAT);
        assertThat(planned.trip().departureLevelPct().value()).isCloseTo(60.015, CLOSE);
        assertThat(planned.trip().returnLevelPct().value()).isCloseTo(30, CLOSE);
        assertThat(saturday.levelAfterChargingPct().value()).isCloseTo(80, CLOSE);
        assertThat(saturday.endLevelPct().value()).isCloseTo(80, CLOSE);
    }

    @Test
    void theGridAdvicePassesOverADayAnEveningGridChargeBringsBackAndWarnsForTheDayTheLevelDrops() {
        // 400 km a day take 72 %, so Saturday's 100 km trip (20.7 %) would need 30 + 20.7 + 72 = 122.7 %: the plan
        // charges for 100 %. Friday drives from 90 % to 18 % and the evening's grid charge brings it to 100 %, above
        // the minimum, so Friday gets no advice. Saturday leaves with 100 %, is back with 79.3 % and drives to 7.3 %,
        // and Sunday's 72 % leave it 64.7 points short: the advice is for Sunday, 30 + 64.7 = 94.7 % under the minimum
        var planned = plan(system(400), dark(4), 90, at(FRI, 0), trip(SAT, 8, 18, 100));
        var friday = planned.day(FRI);
        assertThat(planned.grid().start().atZone(TZ).toLocalDate()).isEqualTo(FRI);
        assertThat(friday.endLevelPct().value()).isEqualTo(100);
        assertThat(friday.drivingShortPct()).isZero();
        var advice = planned.gridAdvice().orElseThrow();
        assertThat(advice.day()).isEqualTo(SUN);
        assertThat(advice.endLevelPct()).isCloseTo(-64.7, CLOSE);
        assertThat(advice.deficitPct()).isCloseTo(94.7, CLOSE);
    }

    @Test
    void aDayTheCarRunsOutOnBeforeAnEveningGridChargeForATripGetsNoGridAdvice() {
        // This pins accepted behaviour that #511 may change. 400 km a day take 72 %, so Saturday's 100 km trip (20.7 %)
        // would need 30 + 20.7 + 72 = 122.7 %: the plan charges for 100 %. Friday's 72 % leave the 50 % it starts with
        // 22 points short, and the evening's grid charge then brings it to 100 %: the advice reads Friday's end as
        // 100 - 22 = 78 %, above the minimum, and gives Friday no advice. Saturday leaves with 100 %, is back with
        // 79.3 % and drives to 7.3 %, and Sunday's 72 % leave it 64.7 points short: the advice is for Sunday, with a
        // deficit of 30 + 64.7 = 94.7 %
        var planned = plan(system(400), dark(4), 50, at(FRI, 0), trip(SAT, 8, 18, 100));
        var friday = planned.day(FRI);
        assertThat(planned.grid().start().atZone(TZ).toLocalDate()).isEqualTo(FRI);
        assertThat(friday.endLevelPct().value()).isEqualTo(100);
        assertThat(friday.drivingShortPct()).isCloseTo(22, CLOSE);
        var advice = planned.gridAdvice().orElseThrow();
        assertThat(advice.day()).isEqualTo(SUN);
        assertThat(advice.endLevelPct()).isCloseTo(-64.7, CLOSE);
        assertThat(advice.deficitPct()).isCloseTo(94.7, CLOSE);
    }

    @Test
    void aDayTheCarRunsOutOnBeforeAnEveningGridChargeForATripKeepsASmallerGridAdvice() {
        // This pins accepted behaviour that #511 may change. 600 km a day take 108 %, so Saturday's 100 km trip would
        // need far more than 100 %: the plan charges for 100 %. Friday's 108 % leave the 20 % it starts with 88 points
        // short, and the evening's grid charge then brings it to 100 %: the advice reads Friday's end as 100 - 88 =
        // 12 %, so Friday keeps its advice, with a deficit of 30 - 12 = 18 % (20 kWh at the wall), though the car runs
        // out 88 points short of the day's driving
        var planned = plan(system(600), dark(4), 20, at(FRI, 0), trip(SAT, 8, 18, 100));
        var friday = planned.day(FRI);
        assertThat(planned.grid().start().atZone(TZ).toLocalDate()).isEqualTo(FRI);
        assertThat(friday.endLevelPct().value()).isEqualTo(100);
        assertThat(friday.drivingShortPct()).isCloseTo(88, CLOSE);
        var advice = planned.gridAdvice().orElseThrow();
        assertThat(advice.day()).isEqualTo(FRI);
        assertThat(advice.endLevelPct()).isCloseTo(12, CLOSE);
        assertThat(advice.deficitPct()).isCloseTo(18, CLOSE);
        assertThat(advice.energyKwh()).isCloseTo(20, CLOSE);
    }

    @Test
    void eachDayStartsAtTheLevelTheDayBeforeEnds() {
        var planned = plan(system(50), days(List.of(Map.of(), SUNNY, Map.of(), Map.of(), Map.of())), 50, at(FRI, 0),
                trip(SAT, 8, 18, 100), trip(SUN.plusDays(1), 8, 18, 250));
        var days = planned.plan().days();
        for (int i = 1; i < days.size(); i++) {
            assertThat(days.get(i).startLevelPct()).isEqualTo(days.get(i - 1).endLevelPct());
        }
    }

    @Test
    void afterTheReturnTheSunChargesToTheTargetAgain() {
        // 9.69 kW usable: 32 A, 53 % from 09:00 to 17:00 after the 08:00 return, which would reach 83 %
        var strong = Map.of(9, 12.0, 10, 12.0, 11, 12.0, 12, 12.0, 13, 12.0, 14, 12.0, 15, 12.0, 16, 12.0);
        var planned = plan(days(List.of(Map.of(), SUNNY, strong)), 50, at(FRI, 0), trip(SAT, 5, 8, 250));
        assertThat(planned.trip().neededPct().value()).isCloseTo(81.75, CLOSE);
        assertThat(planned.trip().returnLevelPct().value()).isCloseTo(30, CLOSE);
        assertThat(planned.day(SAT).endLevelPct().value()).isCloseTo(80, CLOSE);
    }

    @Test
    void solarPowerAfterTheTripDoesNotCountForIt() {
        var planned = plan(days(List.of(Map.of(), Map.of(), Map.of(), SUNNY)), 50, at(FRI, 0), trip(SAT, 8, 18, 300));
        assertThat(planned.trip().solarWindows()).isEmpty();
        assertThat(planned.day(SUN).windows()).isNotEmpty();
        var grid = planned.grid();
        assertThat(List.of(grid.start(), grid.end())).containsExactly(at(FRI, 22), at(SAT, 6, 55));
    }

    @Test
    void chargingNowTakesTheLeftoverSun() {
        // 0.85 kW of leftover sun is too little for a window; 11.89 kWh at 32 A take 1.62 h from 12:00
        var friday = Map.of(9, 1.6, 10, 1.6, 11, 1.6, 12, 1.6, 13, 1.6, 14, 1.6, 15, 1.6, 16, 1.6);
        var window = plan(days(List.of(Map.of(), friday)), 40, at(FRI, 12), trip(FRI, 14, 16, 100)).grid();
        double hours = 10.7 / 0.9 / 7.36;
        assertThat(window.start()).isEqualTo(at(FRI, 12));
        assertThat(window.solarKwh()).isCloseTo(0.85 * hours, CLOSE);
        assertThat(window.importKwh()).isCloseTo((7.36 - 0.85) * hours, CLOSE);
    }

    @Test
    void withoutATripEveryDayChargesUpToTheTargetAlsoWhenItStartsAboveIt() {
        var planned = plan(days(List.of(SUNNY, Map.of(), SUNNY)), 90, at(THU, 0));
        assertThat(chargeTo(planned)).containsExactly(80.0, 80.0, 80.0);
    }

    @Test
    void aTripThatNeedsLessThanTheTargetLeavesEveryDayChargingUpToTheTarget() {
        var planned = plan(days(List.of(SUNNY, Map.of(), Map.of())), 50, at(THU, 0), trip(SAT, 8, 18, 100));
        assertThat(chargeTo(planned)).containsExactly(80.0, 80.0, 80.0);
    }

    @Test
    void theDaysThatChargeAboveTheTargetForATripChargeUpToWhatItNeedsAndTheDayAfterItToTheTargetAgain() {
        // Friday's sun alone reaches the 92.1 %, so Thursday charges only to the target; on Saturday the part before
        // the departure charges up to 92.1 % and the part after it to the target
        var planned = plan(days(List.of(SUNNY, SUNNY, Map.of(), Map.of())), 50, at(THU, 0), trip(SAT, 8, 18, 300));
        assertThat(chargeTo(planned)).containsExactly(80.0, 92.1, 92.1, 80.0);
    }

    @Test
    void theDayATripsGridChargeStartsOnKeepsItsSolarChargingAtTheTargetThoughTheGridChargeBringsItHigher() {
        // no sun, so the trip charges above the target only from its departure day; the grid charge starts on Friday
        // evening and brings Friday's level after charging to 92.1 % without a window
        var planned = plan(dark(4), 50, at(FRI, 0), trip(SAT, 8, 18, 300));
        var friday = planned.day(FRI);
        assertThat(planned.grid().start().atZone(TZ).toLocalDate()).isEqualTo(FRI);
        assertThat(friday.windows()).isEmpty();
        assertThat(friday.levelAfterChargingPct().value()).isCloseTo(92.1, CLOSE);
        assertThat(friday.chargeToPct().value()).isCloseTo(80, CLOSE);
        assertThat(planned.day(SAT).chargeToPct().value()).isCloseTo(92.1, CLOSE);
        assertThat(planned.day(SUN).chargeToPct().value()).isCloseTo(80, CLOSE);
    }

    @Test
    void aTripThatChargesForTheNextOneChargesUpToWhatBothNeedFromItsDepartureDayOn() {
        // no sun, so Friday's solar charging stays at the target though the first trip's grid charge starts on it
        var first = new Trip(at(SAT, 8), at(SUN, 7, 30), 150, false);
        var second = new Trip(at(SUN, 8), at(SUN, 18), 150, false);
        var planned = plan(dark(4), 50, at(FRI, 0), first, second);
        double both = 61.05 + 31.05 - 3.312;
        assertThat(planned.grid().start().atZone(TZ).toLocalDate()).isEqualTo(FRI);
        assertThat(planned.day(FRI).chargeToPct().value()).isCloseTo(80, CLOSE);
        assertThat(planned.day(SAT).chargeToPct().value()).isCloseTo(both, CLOSE);
    }

    @Test
    void onlyTripsAheadWithinTheForecastGetAdvice() {
        var started = new Trip(at(FRI, 6), at(FRI, 18), 300, false);
        var afterTheForecast = trip(SUN, 8, 18, 300);
        var planned = plan(dark(3), 50, at(FRI, 7), started, afterTheForecast);
        assertThat(planned.plan().trips()).isEmpty();
    }
}
