package nl.enadeag.carchargeplanner.engine.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import nl.enadeag.carchargeplanner.engine.away.AwayPeriod;
import nl.enadeag.carchargeplanner.engine.away.RecurringAwayPeriod;
import nl.enadeag.carchargeplanner.engine.config.Car;
import nl.enadeag.carchargeplanner.engine.config.Charger;
import nl.enadeag.carchargeplanner.engine.config.Coordinates;
import nl.enadeag.carchargeplanner.engine.config.HomeSystem;
import nl.enadeag.carchargeplanner.engine.config.House;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.Phases;
import nl.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The rules every plan keeps, checked on random homes, forecasts and away times. Each case comes from its own seed, so
 * a failing seed can be run again on its own.
 */
class PlannerInvariantsTest {

    private static final double EPS = 1e-6;
    private static final LocalDate FIRST_DAY = LocalDate.of(2026, 7, 1);

    /** One random case: everything the planner and the advisor are given. */
    private record Case(HomeSystem system, PlanningPreferences preferences, AwayTimes away, List<ForecastStep> forecast,
            Percent soc, Instant now) {
    }

    static Stream<Long> seeds() {
        return LongStream.range(0, 300).boxed();
    }

    @ParameterizedTest(name = "seed {0}")
    @MethodSource("seeds")
    void everyPlanKeepsItsRules(long seed) {
        var input = randomCase(new Random(seed));
        var days = new Planner(input.system(), input.preferences(), input.away())
                .plan(input.forecast(), input.soc(), input.now(), List.of());
        var advice = new GridAdvisor(input.system(), input.preferences(), input.away())
                .advise(days, input.forecast(), input.now());

        var windows = days.stream().flatMap(day -> day.windows().stream()).toList();
        var awayTime = input.away().during(input.forecast(), input.system().timezone());
        for (var window : windows) {
            assertThat(covered(input.forecast(), window.start(), window.end())).as("%s is inside the forecast", window).isTrue();
            assertThat(window.start()).as("%s starts from now", window).isAfterOrEqualTo(input.now());
            assertThat(Duration.between(window.start(), window.end()))
                    .as("%s lasts the minimum", window)
                    .isGreaterThanOrEqualTo(input.preferences().minWindow());
            assertThat(window.solarKwh()).as("%s takes no more than the surplus", window)
                    .isLessThanOrEqualTo(surplusKwh(input, window.start(), window.end()) + EPS);
            assertThat(window.importKwh()).as("%s imports within the tolerance", window)
                    .isLessThanOrEqualTo(input.preferences().importToleranceKwh() + EPS);
            assertThat(awayTime).as("%s is outside the away time", window)
                    .noneMatch(time -> time.overlaps(window.start(), window.end()));
        }
        var sorted = windows.stream().sorted(Comparator.comparing(Window::start)).toList();
        for (int i = 1; i < sorted.size(); i++) {
            assertThat(sorted.get(i).start()).as("%s does not overlap the window before", sorted.get(i))
                    .isAfterOrEqualTo(sorted.get(i - 1).end());
        }

        double minimum = input.preferences().minimumPct().value();
        if (advice.isEmpty()) {
            assertThat(days).as("no advice: every day ends at most a point under the minimum")
                    .allSatisfy(day -> assertThat(day.socAfterChargingPct().value() - day.drivingPct())
                            .isGreaterThanOrEqualTo(minimum - 1 - EPS));
            return;
        }
        var given = advice.get();
        double addedPct = given.energyKwh() * input.system().charger().efficiency()
                / input.system().car().usableBatteryKwh() * 100;
        assertThat(given.socEndPct() + addedPct).as("the grid charge brings the day up to the minimum")
                .isCloseTo(minimum, within(EPS));
        given.window().ifPresent(window -> {
            assertThat(window.start()).isAfterOrEqualTo(input.now());
            assertThat(window.start().atZone(input.system().timezone()).toLocalDate()).isBeforeOrEqualTo(given.day());
            assertThat(covered(input.forecast(), window.start(), window.end())).as("%s is inside the forecast", window)
                    .isTrue();
            assertThat(window.solarKwh() + window.importKwh()).as("%s gives the energy", window)
                    .isCloseTo(given.energyKwh(), within(EPS));
            assertThat(awayTime).as("%s is outside the away time", window)
                    .noneMatch(time -> time.overlaps(window.start(), window.end()));
            assertThat(windows).as("%s is clear of the solar windows", window)
                    .noneMatch(solar -> solar.start().isBefore(window.end()) && window.start().isBefore(solar.end()));
        });
    }

    private static Case randomCase(Random random) {
        var zone = Forecasts.TZ;
        var phases = random.nextBoolean() ? Phases.ONE : Phases.THREE;
        int minAmps = 6 + random.nextInt(3);
        var system = new HomeSystem(new Coordinates(51.0, 5.0), zone, List.of(Forecasts.PANELS),
                new House(random.nextDouble() * 1.0), new Car(30 + random.nextInt(70), 14 + random.nextInt(10),
                        random.nextInt(120)),
                new Charger(phases, minAmps, minAmps + random.nextInt(27), 230, 0.8 + random.nextDouble() * 0.2));
        int minimum = 10 + random.nextInt(40);
        var preferences = new PlanningPreferences(new Percent(minimum), new Percent(minimum + 10 + random.nextInt(40)),
                0.5 + random.nextDouble() * 0.5, random.nextDouble() * 0.5,
                        Duration.ofMinutes(15 + random.nextInt(60)), Duration.ofMinutes(15));

        var start = FIRST_DAY.plusDays(random.nextInt(7));
        int days = 1 + random.nextInt(3);
        var step = Duration.ofMinutes(List.of(15, 30, 60).get(random.nextInt(3)));
        double peakKw = (0.3 + random.nextDouble() * 0.7) * (phases == Phases.THREE ? 16 : 8);
        var forecast = new ArrayList<ForecastStep>();
        var from = start.atStartOfDay(zone).toInstant();
        var end = start.plusDays(days).atStartOfDay(zone).toInstant();
        for (var at = from; at.isBefore(end); at = at.plus(step)) {
            if (random.nextInt(40) == 0) {
                continue; // a gap in the forecast
            }
            var local = at.atZone(zone);
            double hour = local.getHour() + local.getMinute() / 60.0;
            double sun = hour >= 6 && hour <= 20 ? Math.sin(Math.PI * (hour - 6) / 14) : 0;
            double cloud = 0.4 + random.nextDouble() * 0.6;
            forecast.add(new ForecastStep(at, at.plus(step), peakKw * sun * cloud));
        }
        if (forecast.isEmpty()) {
            forecast.add(new ForecastStep(from, from.plus(step), 0));
        }

        var recurring = new ArrayList<RecurringAwayPeriod>();
        if (random.nextBoolean()) {
            int fromHour = 5 + random.nextInt(8);
            recurring.add(new RecurringAwayPeriod(DayOfWeek.of(1 + random.nextInt(7)), LocalTime.of(fromHour, 0),
                    LocalTime.of(fromHour + 1 + random.nextInt(8), 30)));
        }
        var oneOff = new ArrayList<AwayPeriod>();
        if (random.nextBoolean()) {
            var awayFrom = from.plus(Duration.ofMinutes(5L * random.nextInt(12 * 24 * days)));
            oneOff.add(new AwayPeriod(awayFrom, awayFrom.plus(Duration.ofMinutes(5L + 5L * random.nextInt(12 * 12)))));
        }

        var now = from.plus(Duration.ofMinutes(random.nextInt(24 * 60)));
        return new Case(system, preferences, new AwayTimes(recurring, oneOff), List.copyOf(forecast),
                new Percent(random.nextInt(101)), now);
    }

    /** Whether forecast steps cover the whole time, without a gap. */
    private static boolean covered(List<ForecastStep> forecast, Instant from, Instant to) {
        var at = from;
        for (var step : forecast.stream().sorted(Comparator.comparing(ForecastStep::start)).toList()) {
            if (!step.start().isAfter(at) && step.end().isAfter(at)) {
                at = step.end();
            }
            if (!at.isBefore(to)) {
                return true;
            }
        }
        return false;
    }

    /** The usable surplus over a time, from the forecast steps. */
    private static double surplusKwh(Case input, Instant from, Instant to) {
        double kwh = 0;
        for (var step : input.forecast()) {
            var start = Hours.latest(step.start(), from);
            var end = Hours.earliest(step.end(), to);
            if (end.isAfter(start)) {
                kwh += Charging.usableKw(step, input.system(), input.preferences()) * Hours.between(start, end);
            }
        }
        return kwh;
    }
}
