package nl.enadeag.carchargeplanner.engine.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import nl.enadeag.carchargeplanner.engine.Outlook;
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import nl.enadeag.carchargeplanner.engine.away.WeeklyAwayPeriod;
import nl.enadeag.carchargeplanner.engine.config.Car;
import nl.enadeag.carchargeplanner.engine.config.Charger;
import nl.enadeag.carchargeplanner.engine.config.Coordinates;
import nl.enadeag.carchargeplanner.engine.config.HomeSystem;
import nl.enadeag.carchargeplanner.engine.config.House;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.Phases;
import nl.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastSource;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import nl.enadeag.carchargeplanner.engine.forecast.Freshness;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderRole;
import nl.enadeag.carchargeplanner.engine.notify.AnnouncedWindows;
import nl.enadeag.carchargeplanner.engine.store.FilePrivacy;
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The planner, the grid advice and the dashboard at the ends of what the settings, the forecast and the stored files
 * can hold: nothing throws, every figure is finite and within what the charger can deliver, and a round ends in time.
 * The moments far away cannot reach the planner; their bounds are tested where they come in.
 */
class PlannerExtremesTest {

    /** Where milliseconds are expected; a limit that proves too tight on a slow runner is raised, not removed. */
    private static final Duration ROUND_LIMIT = Duration.ofSeconds(30);
    private static final double RELATIVE = 1e-9;
    private static final LocalTime NOW = LocalTime.of(7, 30);

    /** Each zone with the first day of its forecast: the next day is one of its odd days. */
    private enum Zone {
        /** The next day has 25 hours. */
        AMSTERDAM("Europe/Amsterdam", LocalDate.of(2026, 10, 24)),
        /** Half an hour off the hour, without daylight saving. */
        KOLKATA("Asia/Kolkata", LocalDate.of(2026, 7, 1)),
        /** The next day starts at 01:00: its midnight does not exist. */
        SANTIAGO("America/Santiago", LocalDate.of(2026, 9, 5));

        final ZoneId id;
        final LocalDate firstDay;

        Zone(String id, LocalDate firstDay) {
            this.id = ZoneId.of(id);
            this.firstDay = firstDay;
        }
    }

    /** The forecast's power in the daytime hours; the night is 0. */
    private enum Power implements HourlyPower {
        NONE, MIXED, CHARGER, LARGEST;

        /** Every size from the smallest positive number to the largest, one per daytime hour, in turn. */
        private static final double[] SIZES = {Double.MIN_VALUE, 1e-9, 0.5, 3, 96, 1e6, 1e300, Double.MAX_VALUE};

        @Override
        public double at(int hourOfDay) {
            if (hourOfDay < 6 || hourOfDay > 19) {
                return 0;
            }
            switch (this) {
                case NONE:
                    return 0;
                case MIXED:
                    return SIZES[hourOfDay % SIZES.length];
                case CHARGER:
                    return 96;
                case LARGEST:
                    return Double.MAX_VALUE;
                default:
                    throw new IllegalStateException(name());
            }
        }
    }

    private enum TripShape {
        NONE, LONGEST, FARTHEST, AT_MIDNIGHT, TWO_IN_A_ROW, OVERLAPPING, TWENTY, ORDINARY;

        /** The trips, from the forecast's first day; the next day may be one without a midnight. */
        List<Trip> trips(LocalDate first, ZoneId zone) {
            var second = first.plusDays(1);
            var third = first.plusDays(2);
            switch (this) {
                case NONE:
                    return List.of();
                case LONGEST:
                    return List.of(new Trip(at(second, 9, zone), at(second, 9, zone).plus(Trip.MAX_LENGTH), 300,
                            false));
                case FARTHEST:
                    return List.of(new Trip(at(second, 18, zone), at(third, 20, zone), Trip.MAX_DISTANCE_KM, false));
                case AT_MIDNIGHT:
                    return List.of(new Trip(second.atStartOfDay(zone).toInstant(),
                            third.atStartOfDay(zone).toInstant(), 500, false));
                case TWO_IN_A_ROW:
                    return List.of(new Trip(at(second, 8, zone), at(second, 12, zone), 200, false),
                            new Trip(at(second, 12, zone), at(second, 20, zone), 400, false));
                case OVERLAPPING:
                    return List.of(new Trip(at(second, 8, zone), at(third, 8, zone), 300, false),
                            new Trip(at(second, 20, zone), at(third, 12, zone), 600, true));
                case TWENTY:
                    return IntStream.range(0, 20)
                            .mapToObj(i -> at(first, 9, zone).plus(Duration.ofHours(3L * i)))
                            .map(departure -> new Trip(departure, departure.plus(Duration.ofHours(2)), 150, false))
                            .toList();
                case ORDINARY:
                    return List.of(new Trip(at(third, 7, zone), at(third, 19, zone), 250, false));
                default:
                    throw new IllegalStateException(name());
            }
        }
    }

    /** Everything one planning round is given, and a name to find it by. */
    private record Case(String name, Zone zone, HomeSystem system, PlanningPreferences preferences, HourlyPower power,
            TripShape shape, Percent reading, boolean announced) {

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Row {@code r} (0 to 31) takes each two-valued factor's value from the parity of the bits of {@code r} that its
     * mask selects, and a factor with more values from the parities of two or three masks read as a number (taken
     * modulo its count of values). The masks are chosen so that the masks of any two factors are linearly independent
     * over GF(2): every combination of the two factors' parities then comes up equally often over the 32 rows, so
     * every pair of values of any two factors is in at least one row. Check: no two two-valued masks are equal, none
     * lies in the span of a many-valued factor's masks, and two many-valued factors' masks together have full rank.
     */
    private static final int[] TWO_VALUED = {1, 2, 5, 8, 9, 10, 11, 14, 15, 20, 23, 24, 26, 27};
    private static final int[] SHAPE_MASKS = {22, 17, 4};
    private static final int[] POWER_MASKS = {25, 6};
    private static final int[] ZONE_MASKS = {30, 13};
    private static final int[] CURRENT_MASKS = {12, 16};

    static Stream<Case> corners() {
        return IntStream.range(0, 32).mapToObj(PlannerExtremesTest::corner);
    }

    private static Case corner(int r) {
        var bit = new boolean[TWO_VALUED.length];
        for (int i = 0; i < bit.length; i++) {
            bit[i] = Integer.bitCount(r & TWO_VALUED[i]) % 2 == 1;
        }
        var zone = Zone.values()[number(r, ZONE_MASKS) % 3];
        int[][] currents = {{1, 1}, {1, 80}, {80, 80}};
        var amps = currents[number(r, CURRENT_MASKS) % 3];
        var car = new Car(bit[0] ? 1000 : 1, bit[1] ? 200 : 1, bit[2] ? 1000 : 0, new Percent(bit[3] ? 100 : 0));
        var charger = new Charger(bit[6] ? Phases.THREE : Phases.ONE, amps[0], amps[1], bit[5] ? 400 : 100,
                bit[4] ? 1 : 0.01);
        var house = new House(bit[7] ? 1e308 : 0);
        var preferences = new PlanningPreferences(new Percent(bit[11] ? 99 : 0), new Percent(100),
                bit[8] ? 1 : Double.MIN_VALUE, bit[9] ? Double.MAX_VALUE : 0,
                Duration.ofMinutes(bit[10] ? Integer.MAX_VALUE : 1), Duration.ofMinutes(15));
        return new Case("corner " + r, zone, system(zone, house, car, charger), preferences,
                Power.values()[number(r, POWER_MASKS)], TripShape.values()[number(r, SHAPE_MASKS)],
                new Percent(bit[12] ? 100 : 0), bit[13]);
    }

    private static int number(int r, int[] masks) {
        int value = 0;
        for (int k = 0; k < masks.length; k++) {
            value |= (Integer.bitCount(r & masks[k]) % 2) << k;
        }
        return value;
    }

    /** The unbounded inputs drawn log-uniformly over their whole range, the bounded ones within their bounds. */
    static Stream<Case> sweep() {
        var random = new Random(408);
        return IntStream.range(0, 48).mapToObj(i -> {
            var zone = Zone.values()[random.nextInt(Zone.values().length)];
            var car = new Car(logUniform(random, 1, 1000), logUniform(random, 1, 200), random.nextInt(1001),
                    new Percent(random.nextInt(101)));
            int minAmps = 1 + random.nextInt(80);
            var charger = new Charger(random.nextBoolean() ? Phases.ONE : Phases.THREE, minAmps,
                    minAmps + random.nextInt(81 - minAmps), 100 + random.nextInt(301), logUniform(random, 0.01, 1));
            var house = new House(random.nextInt(4) == 0 ? 0 : logUniform(random, Double.MIN_VALUE, 1e308));
            int minimum = random.nextInt(100);
            var preferences = new PlanningPreferences(new Percent(minimum),
                    new Percent(minimum + 1 + random.nextInt(100 - minimum)),
                    logUniform(random, Double.MIN_VALUE, 1),
                    random.nextInt(4) == 0 ? 0 : logUniform(random, Double.MIN_VALUE, Double.MAX_VALUE),
                    Duration.ofMinutes((long) logUniform(random, 1, Integer.MAX_VALUE)), Duration.ofMinutes(15));
            var daytime = new double[24];
            for (int hour = 6; hour < 20; hour++) {
                daytime[hour] = random.nextInt(4) == 0 ? 0 : logUniform(random, Double.MIN_VALUE, Double.MAX_VALUE);
            }
            return new Case("sweep " + i, zone, system(zone, house, car, charger), preferences, hour -> daytime[hour],
                    TripShape.values()[random.nextInt(TripShape.values().length)],
                    new Percent(random.nextInt(101)), random.nextBoolean());
        });
    }

    private static double logUniform(Random random, double low, double high) {
        double exponent = Math.log(low) + random.nextDouble() * (Math.log(high) - Math.log(low));
        return Math.min(high, Math.max(low, Math.exp(exponent)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource({"corners", "sweep"})
    void aRoundAtTheExtremesEndsInTimeWithFiniteFigures(Case input) {
        var now = input.zone().firstDay.atTime(NOW).atZone(input.zone().id).toInstant();
        var announced = input.announced() ? List.of(runningWindow(input.system().charger(), now))
                : List.<Window>of();
        roundEndsInTimeWithFiniteFigures(input, announced);
    }

    /** The windows of the planner at the largest charger, kept in the file and read back, are all still used. */
    @Test
    void everyWindowTheAppCanWriteIsReadBackFromTheFile(@TempDir Path folder) {
        var zone = Zone.AMSTERDAM.id;
        var day = LocalDate.of(2026, 7, 1);
        var system = system(Zone.AMSTERDAM, new House(0), new Car(1000, 1, 0, new Percent(0)),
                new Charger(Phases.THREE, 1, 80, 400, 1));
        var placements = new GridPlacements(system, Forecasts.NIGHT_CHARGING);
        var quarters = placements.quarterHours(day, day, hourly(day, zone, hour -> hour >= 10 && hour < 14 ? 40 : 0),
                List.of());
        var from = day.atStartOfDay(zone).toInstant();
        var written = new ArrayList<Window>();
        for (int amps = 1; amps <= 80; amps++) {
            double powerKw = Charging.powerKw(amps, system.charger());
            // from a quarter of an hour's energy up to the energy of a day, and at the edge of the planner's tolerance
            for (double energyKwh : new double[] {1e-9, 0.01, powerKw / 4 * (1 - 1e-9), powerKw / 4,
                powerKw / 4 * (1 + 1e-9), powerKw / 4 * (1 + 4e-10), powerKw, powerKw * 1.9, 100, 700}) {
                written.addAll(placements.placements(quarters, energyKwh, amps, from));
                placements.now(quarters, energyKwh, amps, from, from.plus(Duration.ofHours(24)))
                        .ifPresent(grid -> written.add(grid.window()));
            }
        }
        var preferences = new PlanningPreferences(new Percent(0), new Percent(100), 1, 0, Duration.ofMinutes(1),
                Duration.ofMinutes(15));
        var plan = new Planner(system, preferences, AwayTimes.NONE).planWithTrips(
                hourly(day, zone, hour -> hour >= 10 && hour < 14 ? 1e6 : 0), new Percent(0), at(day, 8, zone),
                List.of());
        plan.days().forEach(planned -> written.addAll(planned.windows()));
        assertThat(written).hasSizeGreaterThan(1000).anySatisfy(window -> assertThat(window.powerKw()).isEqualTo(96));

        var announced = new AnnouncedWindows(folder.resolve("announced.json"), new PrivateFiles(FilePrivacy.NONE),
                Clock.systemUTC());
        announced.save(written);

        assertThat(announced.load()).containsExactlyElementsOf(written);
    }

    /** The most a stored window may hold, as it runs now: the largest power, and the energy that power gives. */
    @Test
    void aRunningAnnouncedWindowAtTheFilesLimitsIsPlannedAndShown(@TempDir Path folder) {
        var zone = Zone.AMSTERDAM.id;
        var now = Zone.AMSTERDAM.firstDay.atTime(NOW).atZone(zone).toInstant();
        var system = system(Zone.AMSTERDAM, new House(0), new Car(60, 18, 40, new Percent(0)),
                new Charger(Phases.THREE, 1, 80, 400, 1));
        var limits = new Window(now.minus(Duration.ofMinutes(30)), now.plus(Duration.ofMinutes(90)), 80, 96,
                96.0000009, 96.000001);
        var announced = new AnnouncedWindows(folder.resolve("announced.json"), new PrivateFiles(FilePrivacy.NONE),
                Clock.systemUTC());
        announced.save(List.of(limits));
        assertThat(announced.load()).containsExactly(limits);

        roundEndsInTimeWithFiniteFigures(new Case("limits", Zone.AMSTERDAM, system, Forecasts.PREFERENCES,
                Power.CHARGER, TripShape.ORDINARY, new Percent(50), false), announced.load());
    }

    private static void roundEndsInTimeWithFiniteFigures(Case input, List<Window> announced) {
        assertTimeoutPreemptively(ROUND_LIMIT, () -> {
            var zone = input.zone().id;
            var first = input.zone().firstDay;
            var now = first.atTime(NOW).atZone(zone).toInstant();
            var trips = input.shape().trips(first, zone);
            var third = first.plusDays(2);
            var oneOff = List.of(new OneOffAwayPeriod(at(third, 10, zone), at(third, 12, zone)));
            var weekly = List.of(new WeeklyAwayPeriod(first.plusDays(1).getDayOfWeek(), LocalTime.of(13, 0),
                    LocalTime.of(15, 0)));
            var away = new AwayTimes(weekly, oneOff, trips);
            var forecast = forecast(first, 3, zone, input.power());

            var plan = new Planner(input.system(), input.preferences(), away)
                    .planWithTrips(forecast, input.reading(), now, announced);
            var advice = new GridAdvisor(input.system(), input.preferences(), away).advise(plan, forecast, now);
            var planned = new Outlook.Planned(now, zone, plan.days(), input.reading(), false, advice, plan.trips(),
                    source(now), input.system().house().baseloadKw(), input.preferences().minimumPct(),
                    input.preferences().targetPct());
            planned.dashboard(oneOff, trips);
            planned.dashboard(List.of(), List.of());

            var charger = input.system().charger();
            for (var day : plan.days()) {
                assertThat(day.drivingPct()).as("%s: driving", day.date()).isFinite();
                assertThat(day.drivingShortPct()).as("%s: driving short", day.date()).isFinite();
                day.windows().forEach(window -> assertDeliverable(window, charger));
            }
            for (var trip : plan.trips()) {
                assertThat(trip.kmShort()).as("km short").isFinite().isGreaterThanOrEqualTo(0);
                trip.solarWindows().forEach(window -> assertDeliverable(window, charger));
                trip.grid().ifPresent(grid -> assertDeliverable(grid.window(), charger));
            }
            advice.ifPresent(given -> {
                assertThat(given.endLevelPct()).as("grid advice: end level").isFinite();
                assertThat(given.deficitPct()).as("grid advice: deficit").isFinite();
                assertThat(given.energyKwh()).as("grid advice: energy").isFinite();
                given.window().ifPresent(window -> assertDeliverable(window, charger));
            });
        });
    }

    @Test
    void aWindowAtTheLargestChargerTakesItsWholePower() {
        var zone = Zone.AMSTERDAM.id;
        var day = LocalDate.of(2026, 7, 1);
        var system = system(Zone.AMSTERDAM, new House(0), new Car(1000, 1, 0, new Percent(0)),
                new Charger(Phases.THREE, 1, 80, 400, 1));
        var preferences = new PlanningPreferences(new Percent(0), new Percent(100), 1, 0, Duration.ofMinutes(1),
                Duration.ofMinutes(15));
        var forecast = hourly(day, zone, hour -> hour >= 10 && hour < 14 ? 1e6 : 0);

        var days = new Planner(system, preferences, AwayTimes.NONE)
                .plan(forecast, new Percent(0), at(day, 8, zone), List.of());

        assertThat(days).singleElement().satisfies(planned -> {
            assertThat(planned.windows()).containsExactly(new Window(at(day, 10, zone), at(day, 14, zone), 80, 96, 384,
                    0));
            assertThat(planned.levelAfterChargingPct().value()).isCloseTo(38.4, within(1e-9));
        });
    }

    @Test
    void theGridAdviceForTheSmallestBatteryAndTheLongestDrivingIsFinite() {
        var zone = Zone.AMSTERDAM.id;
        var day = LocalDate.of(2026, 7, 1);
        var system = system(Zone.AMSTERDAM, new House(0), new Car(1, 200, 1000, new Percent(0)),
                new Charger(Phases.ONE, 1, 1, 100, 0.01));
        var preferences = new PlanningPreferences(new Percent(30), new Percent(80), 1, 0, Duration.ofMinutes(1),
                Duration.ofMinutes(15));
        var forecast = hourly(day, zone, hour -> 0);
        var now = day.atStartOfDay(zone).toInstant();

        var plan = new Planner(system, preferences, AwayTimes.NONE).planWithTrips(forecast, new Percent(50), now,
                List.of());
        var advice = new GridAdvisor(system, preferences, AwayTimes.NONE).advise(plan, forecast, now);

        // 1000 km at 2 kWh a km is 2000 kWh, 200,000 % of a 1 kWh battery; 50 % of it is there
        assertThat(advice).hasValueSatisfying(given -> {
            assertThat(given.endLevelPct()).isCloseTo(-199_950, within(1e-6));
            assertThat(given.deficitPct()).isCloseTo(199_980, within(1e-6));
            assertThat(given.energyKwh()).isCloseTo(199_980, within(1e-6));
            assertThat(given.window()).isEmpty();
        });
    }

    @Test
    void theFarthestTripWithTheSmallestBatteryIsShortByAllButWhatTheBatteryHolds() {
        var zone = Zone.AMSTERDAM.id;
        var day = LocalDate.of(2026, 7, 1);
        var system = system(Zone.AMSTERDAM, new House(0), new Car(1, 200, 0, new Percent(0)),
                new Charger(Phases.THREE, 1, 80, 400, 1));
        var trip = new Trip(at(day, 12, zone), at(day, 20, zone), Trip.MAX_DISTANCE_KM, false);
        var away = new AwayTimes(List.of(), List.of(), List.of(trip));

        var plan = new Planner(system, Forecasts.PREFERENCES, away)
                .planWithTrips(hourly(day, zone, hour -> 0), new Percent(100), at(day, 8, zone), List.of());

        // a full 1 kWh battery above the minimum of 30 % holds 0.7 kWh, 0.35 km at 2 kWh a km
        assertThat(plan.trips()).singleElement().satisfies(advice -> {
            assertThat(advice.kmShort()).isCloseTo(19_999.65, within(1e-6));
            assertThat(advice.departureLevelPct()).isEqualTo(new Percent(100));
            assertThat(advice.grid()).isEmpty();
        });
    }

    @Test
    void aHugeEnergyHasNoPlaceAndDoesNotThrow() {
        var zone = Zone.AMSTERDAM.id;
        var day = LocalDate.of(2026, 7, 1);
        var system = system(Zone.AMSTERDAM, new House(0), new Car(60, 18, 40, new Percent(0)),
                new Charger(Phases.ONE, 1, 80, 100, 1));
        var placements = new GridPlacements(system, Forecasts.NIGHT_CHARGING);
        var quarters = placements.quarterHours(day, day, hourly(day, zone, hour -> 96), List.of());
        var from = day.atStartOfDay(zone).toInstant();

        assertThat(placements.placements(quarters, 1, 80, from)).isNotEmpty();
        for (double energyKwh : new double[] {1e300, Double.MAX_VALUE}) {
            for (int amps : new int[] {1, 80}) {
                assertThat(placements.placements(quarters, energyKwh, amps, from)).as("%s kWh at %s A", energyKwh, amps)
                        .isEmpty();
            }
        }
    }

    @Test
    void aForecastBeyondTheHorizonIsPlannedUpToIt() {
        var zone = Zone.AMSTERDAM.id;
        var first = LocalDate.of(2026, 7, 1);
        var beyond = first.plusDays(16);
        var trip = new Trip(at(beyond, 9, zone), at(beyond, 17, zone), 300, false);
        var away = new AwayTimes(List.of(), List.of(), List.of(trip));
        var forecast = forecast(first, 20, zone, Power.CHARGER);

        var plan = new Planner(Forecasts.example(), Forecasts.PREFERENCES, away)
                .planWithTrips(forecast, new Percent(50), first.atTime(NOW).atZone(zone).toInstant(), List.of());

        assertThat(plan.days()).extracting(DayPlan::date)
                .containsExactlyElementsOf(IntStream.range(0, 16).mapToObj(first::plusDays).toList());
        assertThat(plan.trips()).isEmpty();
    }

    /** The horizon is the start of the seventeenth day: a trip leaving then is after it, one a minute before is not. */
    @Test
    void aTripLeavingAtTheHorizonGetsNoAdviceAndOneAMinuteEarlierDoes() {
        var zone = Zone.AMSTERDAM.id;
        var first = LocalDate.of(2026, 7, 1);
        var horizon = at(first.plusDays(16), 0, zone);
        var forecast = forecast(first, 20, zone, Power.CHARGER);
        var now = first.atTime(NOW).atZone(zone).toInstant();

        var atTheHorizon = new AwayTimes(List.of(), List.of(),
                List.of(new Trip(horizon, horizon.plus(Duration.ofHours(8)), 300, false)));
        var aMinuteEarlier = new AwayTimes(List.of(), List.of(), List.of(new Trip(horizon.minus(Duration.ofMinutes(1)),
                horizon.plus(Duration.ofHours(8)), 300, false)));

        assertThat(new Planner(Forecasts.example(), Forecasts.PREFERENCES, atTheHorizon)
                .planWithTrips(forecast, new Percent(50), now, List.of()).trips()).isEmpty();
        assertThat(new Planner(Forecasts.example(), Forecasts.PREFERENCES, aMinuteEarlier)
                .planWithTrips(forecast, new Percent(50), now, List.of()).trips()).hasSize(1);
    }

    @Test
    void aForecastThatEndsAtTheHorizonIsPlannedWhole() {
        var zone = Zone.AMSTERDAM.id;
        var first = LocalDate.of(2026, 7, 1);
        var forecast = forecast(first, 16, zone, Power.CHARGER);

        var days = new Planner(Forecasts.example(), Forecasts.PREFERENCES, AwayTimes.NONE)
                .plan(forecast, new Percent(50), first.atStartOfDay(zone).toInstant(), List.of());

        assertThat(days).hasSize(16);
        assertThat(days.get(15).forecast()).hasSize(24);
    }

    @Test
    void aClockPutBackToNineteenSeventyPlansNothingOfAForecastOfNow() {
        var zone = Zone.AMSTERDAM.id;
        var first = LocalDate.of(2026, 7, 1);
        var trip = new Trip(at(first.plusDays(1), 9, zone), at(first.plusDays(1), 17, zone), 300, false);
        var away = new AwayTimes(List.of(), List.of(), List.of(trip));
        var forecast = forecast(first, 3, zone, Power.NONE);

        var plan = assertTimeoutPreemptively(ROUND_LIMIT, () -> new Planner(Forecasts.example(), Forecasts.PREFERENCES,
                away).planWithTrips(forecast, new Percent(50), Instant.EPOCH, List.of()));

        assertThat(plan.days()).isEmpty();
        assertThat(plan.trips()).isEmpty();
    }

    @Test
    void aClockPutBackSoTheForecastStartsAtTheHorizonPlansNothing() {
        var zone = Zone.AMSTERDAM.id;
        var first = LocalDate.of(2026, 7, 1);
        var now = first.minusDays(Planner.MAX_DAYS).atStartOfDay(zone).toInstant();

        var plan = new Planner(Forecasts.example(), Forecasts.PREFERENCES, AwayTimes.NONE)
                .planWithTrips(forecast(first, 3, zone, Power.CHARGER), new Percent(50), now, List.of());

        assertThat(plan.days()).isEmpty();
    }


    private static HomeSystem system(Zone zone, House house, Car car, Charger charger) {
        return new HomeSystem(new Coordinates(0, 0), Optional.empty(), zone.id, List.of(Forecasts.PANELS), house, car,
                charger);
    }

    private static ForecastSource source(Instant now) {
        return new ForecastSource(new ProviderId("open-meteo"), "Open-Meteo", "", now, Freshness.FRESH,
                ProviderRole.PRIMARY, "", Optional.empty(), List.of());
    }

    /** A window that started before now, at the charger's highest current and power, half of it from the grid. */
    private static Window runningWindow(Charger charger, Instant now) {
        double powerKw = Charging.powerKw(charger.maxAmps(), charger);
        return new Window(now.minus(Duration.ofMinutes(30)), now.plus(Duration.ofMinutes(90)), charger.maxAmps(),
                powerKw, powerKw, powerKw);
    }

    /** Hourly steps in real time from the first day's start, through the given number of days. */
    private static List<ForecastStep> forecast(LocalDate first, int days, ZoneId zone, HourlyPower power) {
        var steps = new ArrayList<ForecastStep>();
        var end = first.plusDays(days).atStartOfDay(zone).toInstant();
        for (var at = first.atStartOfDay(zone).toInstant(); at.isBefore(end); at = at.plus(Duration.ofHours(1))) {
            steps.add(new ForecastStep(at, at.plus(Duration.ofHours(1)), power.at(at.atZone(zone).getHour())));
        }
        return steps;
    }

    private interface HourlyPower {
        double at(int hourOfDay);
    }

    private static List<ForecastStep> hourly(LocalDate day, ZoneId zone, HourlyPower power) {
        var steps = new ArrayList<ForecastStep>();
        var end = day.plusDays(1).atStartOfDay(zone).toInstant();
        for (var at = day.atStartOfDay(zone).toInstant(); at.isBefore(end); at = at.plus(Duration.ofHours(1))) {
            steps.add(new ForecastStep(at, at.plus(Duration.ofHours(1)), power.at(at.atZone(zone).getHour())));
        }
        return steps;
    }

    private static Instant at(LocalDate day, int hour, ZoneId zone) {
        return day.atTime(hour, 0).atZone(zone).toInstant();
    }

    /** Finite, not negative, at most the charger's highest power, and no more energy than that power over its time. */
    private static void assertDeliverable(Window window, Charger charger) {
        double highestKw = Charging.powerKw(charger.maxAmps(), charger);
        double capacityKwh = window.powerKw() * Hours.between(window.start(), window.end());
        assertThat(window.powerKw()).as("%s: power", window).isFinite().isBetween(0.0, highestKw * (1 + RELATIVE));
        assertThat(window.solarKwh()).as("%s: solar energy", window).isFinite().isGreaterThanOrEqualTo(0);
        assertThat(window.importKwh()).as("%s: grid energy", window).isFinite().isGreaterThanOrEqualTo(0);
        assertThat(window.energyKwh()).as("%s: energy within the power over its length", window)
                .isLessThanOrEqualTo(capacityKwh * (1 + RELATIVE) + RELATIVE);
    }
}
