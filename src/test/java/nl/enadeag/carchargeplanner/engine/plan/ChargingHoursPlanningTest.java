package nl.enadeag.carchargeplanner.engine.plan;

import static nl.enadeag.carchargeplanner.engine.plan.Forecasts.NIGHT_CHARGING;
import static nl.enadeag.carchargeplanner.engine.plan.Forecasts.PREFERENCES;
import static nl.enadeag.carchargeplanner.engine.plan.Forecasts.at;
import static nl.enadeag.carchargeplanner.engine.plan.Forecasts.hourly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import nl.enadeag.carchargeplanner.engine.config.Car;
import nl.enadeag.carchargeplanner.engine.config.ChargingHours;
import nl.enadeag.carchargeplanner.engine.config.HomeSystem;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.Phases;
import nl.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/**
 * Every grid charge starts within the charging hours, 07:00 to 22:00 unless the owner changes them, and may run on
 * after them. A trip's car has a 100 kWh battery, so a percent is a kWh; the charger gives 7.36 kW at 32 A.
 */
class ChargingHoursPlanningTest {

    private static final LocalDate THU = LocalDate.of(2026, 7, 2);
    private static final LocalDate FRI = THU.plusDays(1);
    private static final LocalDate SAT = FRI.plusDays(1);
    private static final Offset<Double> CLOSE = within(1e-6);
    /** Without other driving, so only the trips take energy. */
    private static final HomeSystem CAR = Forecasts.withCar(new Car(100, 18, 0), Phases.ONE);

    /** Days without sun from Thursday on. */
    private static List<ForecastStep> dark(int days) {
        var forecast = new ArrayList<ForecastStep>();
        for (int i = 0; i < days; i++) {
            forecast.addAll(hourly(THU.plusDays(i), Map.of()));
        }
        return forecast;
    }

    private static TripAdvice tripAdvice(PlanningPreferences preferences, double levelPct, Instant now, Trip trip) {
        var away = new AwayTimes(List.of(), List.of(), List.of(trip));
        return new Planner(CAR, preferences, away).planWithTrips(dark(3), new Percent(levelPct), now, List.of())
                .trips().getFirst();
    }

    private static GridAdvice gridAdvice(HomeSystem system, PlanningPreferences preferences,
            List<ForecastStep> forecast, double levelPct, Instant now, AwayTimes away) {
        var days = new Planner(system, preferences, away).plan(forecast, new Percent(levelPct), now, List.of());
        return new GridAdvisor(system, preferences, away).advise(days, forecast, now).orElseThrow();
    }

    private static Trip trip(LocalDate day, int departureHour, int departureMinute, double km) {
        return new Trip(at(day, departureHour, departureMinute), at(day, 18), km, false);
    }

    @Test
    void aTripsGridChargeThatWouldStartAtNightMovesToTheEveningBefore() {
        // 42.1 % short: 46.78 kWh, 6.36 h at 32 A, which would start at 00:30 to be done by 07:00
        var grid = tripAdvice(PREFERENCES, 50, at(FRI, 12), trip(SAT, 8, 0, 300)).grid().orElseThrow();
        assertThat(grid.start()).isEqualTo(TripGridCharge.Start.PLANNED);
        assertThat(grid.window().start()).isEqualTo(at(FRI, 22));
        assertThat(grid.window().end()).isBeforeOrEqualTo(at(SAT, 7));
    }

    @Test
    void chargingHoursThatSpanMidnightLetATripsGridChargeStartInTheNight() {
        var grid = tripAdvice(NIGHT_CHARGING, 50, at(FRI, 12), trip(SAT, 8, 0, 300)).grid().orElseThrow();
        assertThat(grid.start()).isEqualTo(TripGridCharge.Start.PLANNED);
        assertThat(grid.window().amps()).isEqualTo(32);
        assertThat(List.of(grid.window().start(), grid.window().end())).containsExactly(at(SAT, 0, 30), at(SAT, 6, 55));
    }

    @Test
    void chargingNowOutsideTheHoursWaitsForTheirStartWhenThatIsSoonEnough() {
        // 10.7 % short: 11.89 kWh, 1.62 h at 32 A; from 07:00 it is not done by 08:00, but it is by the departure
        var grid = tripAdvice(PREFERENCES, 40, at(FRI, 23), trip(SAT, 9, 0, 100)).grid().orElseThrow();
        assertThat(grid.start()).isEqualTo(TripGridCharge.Start.NOW);
        assertThat(List.of(grid.window().start(), grid.window().end())).containsExactly(at(SAT, 7), at(SAT, 8, 40));
        assertThat(grid.window().importKwh()).isCloseTo(10.7 / 0.9, CLOSE);
    }

    @Test
    void chargingNowOutsideTheHoursStartsAtOnceWhenWaitingWouldBeTooLateAndSaysSo() {
        // from 07:00 only an hour is left before the 08:00 departure, too little for 1.62 h
        var advice = tripAdvice(PREFERENCES, 40, at(FRI, 23), trip(SAT, 8, 0, 100));
        var grid = advice.grid().orElseThrow();
        assertThat(grid.start()).isEqualTo(TripGridCharge.Start.NOW_OUTSIDE_CHARGING_HOURS);
        assertThat(List.of(grid.window().start(), grid.window().end())).containsExactly(at(FRI, 23), at(SAT, 0, 40));
        assertThat(advice.kmShort()).isZero();
    }

    @Test
    void chargingNowOutsideTheHoursStartsAtOnceWhenTheCarIsAwayUntilTheyEnd() {
        var hours = PREFERENCES.withChargingHours(new ChargingHours(LocalTime.of(7, 0), LocalTime.of(7, 15)));
        var away = new AwayTimes(List.of(), List.of(new OneOffAwayPeriod(at(SAT, 6, 30), at(SAT, 7, 30))),
                List.of(trip(SAT, 11, 0, 100)));
        var advice = new Planner(CAR, hours, away).planWithTrips(dark(3), new Percent(40), at(FRI, 23), List.of())
                .trips().getFirst();
        var grid = advice.grid().orElseThrow();
        assertThat(grid.start()).isEqualTo(TripGridCharge.Start.NOW_OUTSIDE_CHARGING_HOURS);
        assertThat(grid.window().start()).isEqualTo(at(FRI, 23));
    }

    @Test
    void theGridAdviceStartsAtTheStartOfTheChargingHoursAndMayRunOnAfterThem() {
        var hours = NIGHT_CHARGING;
        // 40 % - 18 % = 22 %: 6.22 kWh, 4.51 h at 6 A from 00:00, the first quarter the hours allow on Friday
        var advice = gridAdvice(Forecasts.golden(), hours, hourly(FRI, Map.of()), 40, at(FRI, 0), AwayTimes.NONE);
        var window = advice.window().orElseThrow();
        assertThat(List.of(window.start(), window.end())).containsExactly(at(FRI, 0), at(FRI, 4, 35));

        var dayTime = gridAdvice(Forecasts.golden(), PREFERENCES, hourly(FRI, Map.of()), 40, at(FRI, 0),
                AwayTimes.NONE).window().orElseThrow();
        assertThat(List.of(dayTime.start(), dayTime.end())).containsExactly(at(FRI, 7), at(FRI, 11, 35));
    }

    @Test
    void aGridChargeMayStartExactlyAtTheEndOfTheChargingHours() {
        // 1.38 kWh: one hour at 6 A, and the car is home from 22:00
        var away = new AwayTimes(List.of(), List.of(new OneOffAwayPeriod(at(FRI, 0), at(FRI, 22))), List.of());
        double levelPct = 48 - 1.38 * 0.9 / 70 * 100;
        var window = gridAdvice(Forecasts.golden(), PREFERENCES, hourly(FRI, Map.of()), levelPct, at(FRI, 0), away)
                .window().orElseThrow();
        assertThat(List.of(window.start(), window.end())).containsExactly(at(FRI, 22), at(FRI, 23));
    }

    @Test
    void noGridChargeStartsAfterTheEndOfTheChargingHours() {
        var away = new AwayTimes(List.of(), List.of(new OneOffAwayPeriod(at(FRI, 0), at(FRI, 22, 15))), List.of());
        double levelPct = 48 - 1.38 * 0.9 / 70 * 100;
        var advice = gridAdvice(Forecasts.golden(), PREFERENCES, hourly(FRI, Map.of()), levelPct, at(FRI, 0), away);
        assertThat(advice.window()).isEmpty();
        assertThat(advice.chargeLimitPct()).isEmpty();
    }

    @Test
    void theChargeLimitIsTheLevelTheGridChargeBringsTheBatteryTo() {
        // 40 % at the start of the window, and 8 % from the grid
        var advice = gridAdvice(Forecasts.golden(), PREFERENCES, hourly(FRI, Map.of()), 40, at(FRI, 0),
                AwayTimes.NONE);
        assertThat(advice.chargeLimitPct()).contains(new Percent(48));
    }

    @Test
    void theChargeLimitStartsFromTheLevelOfTheDayOfTheWindow() {
        // 60 % - 18 % on Friday, 42 % - 18 % on Saturday: 6 % short, charged on Saturday from 07:00
        var forecast = new ArrayList<>(hourly(FRI, Map.of()));
        forecast.addAll(hourly(SAT, Map.of()));
        var advice = gridAdvice(Forecasts.golden(), PREFERENCES, forecast, 60, at(FRI, 0), AwayTimes.NONE);
        assertThat(advice.window().orElseThrow().start()).isEqualTo(at(SAT, 7));
        assertThat(advice.chargeLimitPct()).contains(new Percent(48));
    }

    @Test
    void aSolarWindowAfterTheGridChargeDoesNotCountForItsChargeLimit() {
        // the solar window from 14:00 brings 8.28 %, which leaves Friday 1.72 % short: charged from 07:00, before it
        var advice = gridAdvice(Forecasts.golden(), PREFERENCES, hourly(FRI, Map.of(14, 4.4, 15, 4.4)), 38,
                at(FRI, 0), AwayTimes.NONE);
        assertThat(advice.deficitPct()).isCloseTo(1.72, CLOSE);
        assertThat(advice.window().orElseThrow().start()).isEqualTo(at(FRI, 7));
        assertThat(advice.chargeLimitPct()).contains(new Percent(40));
    }

    @Test
    void theChargeLimitIsRoundedUpToAWholePercentage() {
        // 75 km take 19.29 %: from 40 % the day ends 9.29 % short, so the grid charge brings the battery to 49.29 %
        var system = Forecasts.withCar(new Car(70, 18, 75), Phases.ONE);
        var advice = gridAdvice(system, PREFERENCES, hourly(FRI, Map.of()), 40, at(FRI, 0), AwayTimes.NONE);
        assertThat(advice.deficitPct()).isCloseTo(30 - (40 - 75 * 0.18 / 70 * 100), CLOSE);
        assertThat(advice.chargeLimitPct()).contains(new Percent(50));
    }

    @Test
    void theChargeLimitIsAtMostAFullBattery() {
        // 500 km a day take 128.6 % of the battery: from 90 % the day ends 68.6 % short of the minimum
        var system = Forecasts.withCar(new Car(70, 18, 500), Phases.ONE);
        var advice = gridAdvice(system, PREFERENCES, hourly(FRI, Map.of()), 90, at(FRI, 0), AwayTimes.NONE);
        assertThat(advice.deficitPct()).isGreaterThan(10);
        assertThat(advice.chargeLimitPct()).contains(new Percent(100));
    }
}
