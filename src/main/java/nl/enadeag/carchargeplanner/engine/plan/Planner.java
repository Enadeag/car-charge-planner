package nl.enadeag.carchargeplanner.engine.plan;

import nl.enadeag.carchargeplanner.engine.config.HomeSystem;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Plans the charge windows for each day of the forecast, outside the times the car is away, and makes sure the car can
 * make each trip: the trip comes first, then solar power, and the grid only for the shortfall.
 */
public final class Planner {

    /** A shortfall smaller than this, in percentage points, is forecast noise: no grid charge and no km short for it. */
    private static final double MIN_SHORTFALL_PCT = 1.0;
    /** A trip's grid charge is done this long before the departure, so an early departure still finds it done. */
    private static final Duration GRID_DONE_BEFORE_DEPARTURE = Duration.ofHours(1);
    private static final double EPS = 1e-9;

    private final HomeSystem system;
    private final PlanningPreferences preferences;
    private final AwayTimes away;
    private final ZoneId zone;
    private final WindowFinder finder;
    private final GridPlacements placements;

    /**
     * Makes a planner for one home, one set of preferences and the times the car is away.
     *
     * @param system the home and equipment, with the car's trip surcharge
     * @param preferences the owner's choices the plan follows: the minimum and target level, the margin on the surplus,
     *     the shortest window and the grid energy a window may take
     * @param away when the car is not at home, with the trips it plans for; no window is planned in that time,
     *     {@link AwayTimes#NONE} for none
     */
    public Planner(HomeSystem system, PlanningPreferences preferences, AwayTimes away) {
        this.system = system;
        this.preferences = preferences;
        this.away = away;
        this.zone = system.timezone();
        this.finder = new WindowFinder(system, preferences);
        this.placements = new GridPlacements(system, preferences);
    }

    /**
     * Day by day from today, assuming the owner follows the plan, the trips' grid charges included.
     *
     * @param forecast the forecast to plan on, in any order; empty gives an empty plan
     * @param levelPct the battery level now
     * @param now the current moment; nothing is planned before it
     * @param announced the windows the owner has been told about, possibly none
     * @return one plan for each day from today to the last day of the forecast, in date order
     * @see #planWithTrips(List, Percent, Instant, List)
     */
    public List<DayPlan> plan(List<ForecastStep> forecast, Percent levelPct, Instant now, List<Window> announced) {
        return planWithTrips(forecast, levelPct, now, announced).days();
    }

    /**
     * Day by day from today, assuming the owner follows the plan, and the advice for each trip.
     *
     * <p>Driving is taken off at the end of each day, so a day's charging starts from the level the day before left.
     * Today only loses the part of a day's driving that is still ahead. A trip's energy is taken off at its departure,
     * on top of the other driving.
     *
     * <p>At a departure the battery must hold the trip's energy, the other driving of the days the trip lasts, and the
     * minimum charge level. Solar power before the departure comes first, above the target level when the trip needs
     * it, from the latest days that give the most; only the shortfall comes from the grid, as late as possible and done
     * an hour before the departure, or at once when there is less time. When the time between two trips cannot charge
     * enough for the later one, the earlier one charges for both, up to a full battery; working back over the trips, so
     * a chain of them charges as early as it must. Only what cannot fit at all leaves the later trip short.
     *
     * <p>An announced window that is running now is kept as it was announced, with the same power and end, so the
     * advice does not change while the owner charges at the power they set. Only its remaining energy counts.
     *
     * @param forecast the forecast to plan on, in any order; empty gives an empty plan
     * @param levelPct the battery level now
     * @param now the current moment; nothing is planned before it
     * @param announced the windows the owner has been told about, possibly none
     * @return the days from today to the last day of the forecast, and the advice for each trip that departs after now
     *     and before the forecast ends
     */
    public Plan planWithTrips(List<ForecastStep> forecast, Percent levelPct, Instant now, List<Window> announced) {
        if (forecast.isEmpty()) {
            return new Plan(List.of(), List.of());
        }
        var running = announced.stream()
                .filter(window -> window.isRunningAt(now))
                .map(window -> window.remainingAfter(now))
                .toList();
        var input = new Input(forecast, levelPct.value(), now, running,
                WindowFinder.byDay(runsBeside(forecast, now, running), zone), stepsByDay(forecast), lastDay(forecast));
        var trips = tripsAhead(forecast, now);
        var forNextTrips = new double[trips.size()];
        var charges = settle(trips, forNextTrips, input);
        var projection = project(input, charges);
        // from the last trip back, so what a trip passes on to the one before it is known when that one is settled
        for (int i = trips.size() - 1; i > 0; i--) {
            double shortPct = charges.get(i).neededPct() - projection.departures().get(i).departurePct();
            // the trip before must leave with this much more than it does now, which may be more than it needs
            double forNextPct = projection.departures().get(i - 1).departurePct() + shortPct
                    - charges.get(i - 1).requiredPct();
            if (shortPct > MIN_SHORTFALL_PCT && forNextPct > 0) {
                forNextTrips[i - 1] = forNextPct;
                charges = settle(trips, forNextTrips, input);
                projection = project(input, charges);
            }
        }
        return new Plan(projection.days(), advice(projection, charges, now));
    }

    /**
     * The charging for each trip in turn, each one after the trips before it are settled.
     *
     * @param forNextTrips per trip, the part of the battery it also charges for the trips after it, as their own legs
     *     cannot
     */
    private List<TripCharge> settle(List<Trip> trips, double[] forNextTrips, Input input) {
        var charges = new ArrayList<TripCharge>();
        for (int i = 0; i < trips.size(); i++) {
            charges.add(chargeFor(trips.get(i), forNextTrips[i], charges, input));
        }
        return charges;
    }

    /** What every projection of one plan shares. */
    private record Input(List<ForecastStep> forecast, double levelPct, Instant now, List<Window> running,
            Map<LocalDate, List<SurplusRun>> runsByDay, Map<LocalDate, List<ForecastStep>> stepsByDay,
            LocalDate lastDay) {
    }

    /**
     * How the plan charges for one trip.
     *
     * @param trip the trip
     * @param tripPct the part of the battery the trip takes, there and back or only there
     * @param requiredPct the level the trip needs at its departure; above 100 when even a full battery is not enough
     * @param forNextTripsPct the part of the battery charged on top for the trips after it, whose legs cannot
     * @param boostFrom the first day whose solar power may charge above the target level for the trip
     * @param grid the grid charge, none or one
     */
    private record TripCharge(Trip trip, double tripPct, double requiredPct, double forNextTripsPct,
            LocalDate boostFrom, List<TripGridCharge> grid) {

        double neededPct() {
            return Math.min(100, requiredPct + forNextTripsPct);
        }
    }

    /** The levels around one departure in a projection. */
    private record Departure(double solarPct, double departurePct, double returnPct) {
    }

    /** The days of a projection, and its levels around each departure, in the order of the charges. */
    private record Projection(List<DayPlan> days, List<Departure> departures) {
    }

    /** The trips the plan can charge for: those that depart after now and before the forecast ends. */
    private List<Trip> tripsAhead(List<ForecastStep> forecast, Instant now) {
        var end = forecast.stream().map(ForecastStep::end).max(Comparator.naturalOrder()).orElseThrow();
        return away.trips().stream()
                .filter(trip -> trip.departure().isAfter(now) && trip.departure().isBefore(end))
                .sorted(Comparator.comparing(Trip::departure))
                .toList();
    }

    /**
     * The charging for one trip, after the trips before it are settled: first the days of solar power above the
     * target, then the grid charge for what they leave short.
     */
    private TripCharge chargeFor(Trip trip, double forNextTripsPct, List<TripCharge> before, Input input) {
        double tripPct = tripPct(trip);
        double requiredPct = preferences.minimumPct().value() + tripPct + otherDrivingPct(trip, input.now());
        var departureDay = trip.departure().atZone(zone).toLocalDate();
        var charge = new TripCharge(trip, tripPct, requiredPct, forNextTripsPct, departureDay, List.of());
        if (charge.neededPct() > preferences.targetPct().value()) {
            charge = new TripCharge(trip, tripPct, requiredPct, forNextTripsPct, latestBoost(charge, before, input),
                    List.of());
        }
        var departure = project(input, with(before, charge)).departures().getLast();
        double shortPct = charge.neededPct() - departure.solarPct();
        if (shortPct <= MIN_SHORTFALL_PCT) {
            return charge;
        }
        var grid = gridCharge(charge, before, input, shortPct);
        return new TripCharge(trip, tripPct, requiredPct, forNextTripsPct, charge.boostFrom(), grid);
    }

    /**
     * The latest first day of charging above the target that brings the most solar power by the departure, so the
     * battery stands full for as short as it can.
     */
    private LocalDate latestBoost(TripCharge charge, List<TripCharge> before, Input input) {
        var departureDay = charge.boostFrom();
        var firstDay = legStart(before, input.now()).atZone(zone).toLocalDate();
        var best = departureDay;
        double bestPct = Double.NEGATIVE_INFINITY;
        for (var day = departureDay; !day.isBefore(firstDay) && bestPct < charge.neededPct() - EPS;
                day = day.minusDays(1)) {
            var boosted = new TripCharge(charge.trip(), charge.tripPct(), charge.requiredPct(),
                    charge.forNextTripsPct(), day, List.of());
            double pct = project(input, with(before, boosted)).departures().getLast().solarPct();
            if (pct > bestPct + EPS) {
                best = day;
                bestPct = pct;
            }
        }
        return best;
    }

    /**
     * The grid charge for a trip's shortfall: the stretch that takes the least from the grid and starts the latest,
     * done an hour before the departure and not before the day of the last solar window, whose charging it does not
     * change; when no stretch is done in time, at the full current from now.
     */
    private List<TripGridCharge> gridCharge(TripCharge charge, List<TripCharge> before, Input input,
            double shortPct) {
        double energyKwh = shortPct / 100 * system.car().usableBatteryKwh() / system.charger().efficiency();
        var projection = project(input, with(before, charge));
        var legStart = legStart(before, input.now());
        var departure = charge.trip().departure();
        // the trips before this one charge before its leg starts, so their grid charges are out of the way already
        var unavailable = new ArrayList<>(away.during(input.forecast(), zone));
        input.running().forEach(window -> unavailable.add(new BlockedTime(window.start(), window.end())));
        projection.days().forEach(day -> day.windows()
                .forEach(window -> unavailable.add(new BlockedTime(window.start(), window.end()))));
        var quarters = placements.quarterHours(legStart.atZone(zone).toLocalDate(),
                departure.atZone(zone).toLocalDate(), input.forecast(), unavailable);
        var notBefore = projection.days().stream()
                .flatMap(day -> day.windows().stream())
                .filter(window -> window.end().isAfter(legStart) && window.start().isBefore(departure))
                .map(window -> window.start().atZone(zone).toLocalDate().atStartOfDay(zone).toInstant())
                .reduce(legStart, Hours::latest);
        var doneBy = departure.minus(GRID_DONE_BEFORE_DEPARTURE);
        var charger = system.charger();
        Window best = null;
        for (int amps = charger.minAmps(); amps <= charger.maxAmps(); amps++) {
            for (var candidate : placements.placements(quarters, energyKwh, amps, notBefore)) {
                if (!candidate.end().isAfter(doneBy) && (best == null || isBetterGrid(candidate, best))) {
                    best = candidate;
                }
            }
        }
        if (best != null) {
            return List.of(new TripGridCharge(best, false));
        }
        return placements.fromNow(quarters, energyKwh, charger.maxAmps(), legStart, departure)
                .map(window -> List.of(new TripGridCharge(window, true)))
                .orElse(List.of());
    }

    /** Less from the grid first, as earlier solar power counts first; then the later start. */
    private static boolean isBetterGrid(Window candidate, Window best) {
        double imported = Charging.roundKwh(candidate.importKwh());
        double bestImported = Charging.roundKwh(best.importKwh());
        return imported < bestImported || imported == bestImported && candidate.start().isAfter(best.start());
    }

    private static List<TripCharge> with(List<TripCharge> before, TripCharge charge) {
        var charges = new ArrayList<>(before);
        charges.add(charge);
        return charges;
    }

    /** From when the car can charge for the next trip: now, or the return of the trips before it. */
    private static Instant legStart(List<TripCharge> before, Instant now) {
        return before.stream().map(charge -> charge.trip().returnTime()).reduce(now, Hours::latest);
    }

    private List<TripAdvice> advice(Projection projection, List<TripCharge> charges, Instant now) {
        var windows = projection.days().stream().flatMap(day -> day.windows().stream()).toList();
        var advice = new ArrayList<TripAdvice>();
        for (int i = 0; i < charges.size(); i++) {
            var charge = charges.get(i);
            var departure = projection.departures().get(i);
            var legStart = legStart(charges.subList(0, i), now);
            var trip = charge.trip();
            var solar = windows.stream()
                    .filter(window -> window.end().isAfter(legStart) && window.start().isBefore(trip.departure()))
                    .toList();
            double shortPct = charge.requiredPct() - departure.departurePct();
            double kmShort = shortPct > MIN_SHORTFALL_PCT ? kmFor(shortPct) : 0;
            advice.add(new TripAdvice(trip, new Percent(charge.neededPct()), solar, new Percent(departure.solarPct()),
                    charge.grid().stream().findFirst(), new Percent(departure.departurePct()), kmShort,
                    new Percent(departure.returnPct())));
        }
        return advice;
    }

    /** Day by day, the trips' departures and grid charges included. */
    private Projection project(Input input, List<TripCharge> charges) {
        var days = new ArrayList<DayPlan>();
        var departures = new ArrayList<Departure>();
        double level = input.levelPct();
        for (var date = today(input.now()); !date.isAfter(input.lastDay()); date = date.plusDays(1)) {
            var day = planDay(date, level, input, charges, departures);
            days.add(day);
            level = day.endLevelPct().value();
        }
        return new Projection(days, departures);
    }

    /**
     * The runs outside the away time and the running windows; the surplus just before or after a running window belongs
     * to it, not to a second window.
     */
    private List<SurplusRun> runsBeside(List<ForecastStep> forecast, Instant now, List<Window> running) {
        var unavailable = new ArrayList<>(away.during(forecast, zone));
        running.forEach(window -> unavailable.add(new BlockedTime(window.start(), window.end())));
        return finder.runs(forecast, now, unavailable).stream()
                .filter(run -> running.stream().noneMatch(window -> touches(run, window)))
                .toList();
    }

    private static boolean touches(SurplusRun run, Window window) {
        return run.start().equals(window.end()) || run.end().equals(window.start());
    }

    private Map<LocalDate, List<ForecastStep>> stepsByDay(List<ForecastStep> forecast) {
        return forecast.stream()
                .sorted(Comparator.comparing(ForecastStep::start))
                .collect(Collectors.groupingBy(step -> step.start().atZone(zone).toLocalDate(), Collectors.toList()));
    }

    /**
     * One day, in parts cut at its departures. Each part charges on solar power up to the target level, or up to what
     * the next trip needs from its first day of charging above the target. A trip's grid charge on its departure day is
     * counted before the departure; one on an earlier day after that day's driving, as it runs in the evening or the
     * night.
     */
    private DayPlan planDay(LocalDate date, double startPct, Input input, List<TripCharge> charges,
            List<Departure> departures) {
        var dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
        var runs = input.runsByDay().getOrDefault(date, List.of());
        var windows = new ArrayList<Window>();
        double level = startPct;
        double removedPct = 0;
        double tripsPct = 0;
        double laterGridPct = 0;
        var partStart = date.atStartOfDay(zone).toInstant();
        for (var charge : charges) {
            if (gridDate(charge).filter(date::equals).isPresent()
                    && charge.trip().departure().atZone(zone).toLocalDate().isAfter(date)) {
                laterGridPct += gridPct(charge);
            }
            var departure = charge.trip().departure();
            if (!departure.atZone(zone).toLocalDate().equals(date)) {
                continue;
            }
            level = chargePart(date, partStart, departure, level, runs, input.running(), charges, windows);
            if (gridDate(charge).filter(date::equals).isPresent()) {
                level = Math.min(100, level + gridPct(charge));
            }
            double returned = Math.max(0, level - charge.tripPct());
            departures.add(new Departure(Math.max(0, level - gridPct(charge)), level, returned));
            removedPct += level - returned;
            tripsPct += charge.tripPct();
            level = returned;
            partStart = departure;
        }
        level = chargePart(date, partStart, dayEnd, level, runs, input.running(), charges, windows);

        double otherPct = otherDrivingPct(date, input.now());
        double after = Math.min(100, level + removedPct + laterGridPct);
        double end = Math.min(100, Math.max(0, level - otherPct) + laterGridPct);
        return new DayPlan(date, new Percent(startPct), windows, new Percent(after), new Percent(end),
                otherPct + tripsPct, input.stepsByDay().getOrDefault(date, List.of()));
    }

    /**
     * Charges on solar power in one part of a day; the windows go to {@code windows} in time order, the level after
     * comes back.
     */
    private double chargePart(LocalDate date, Instant from, Instant to, double levelPct, List<SurplusRun> dayRuns,
            List<Window> running, List<TripCharge> charges, List<Window> windows) {
        var kept = running.stream().filter(window -> within(window.start(), from, to)).toList();
        var runs = dayRuns.stream().filter(run -> within(run.start(), from, to)).toList();
        double capPct = capPct(date, to, charges);
        double neededKwh = Math.max(0, energyToReachKwh(capPct, levelPct) - solarKwh(kept));
        var chosen = new ArrayList<>(kept);
        chosen.addAll(finder.windows(runs, neededKwh));
        windows.addAll(chosen);
        double gainedPct = solarKwh(chosen) * system.charger().efficiency() / system.car().usableBatteryKwh() * 100;
        return Math.clamp(levelPct + gainedPct, levelPct, Math.max(levelPct, capPct));
    }

    private static boolean within(Instant moment, Instant from, Instant to) {
        return !moment.isBefore(from) && moment.isBefore(to);
    }

    /** The target level, or what the next trip needs on the days it may charge above the target. */
    private double capPct(LocalDate date, Instant partEnd, List<TripCharge> charges) {
        double targetPct = preferences.targetPct().value();
        return charges.stream()
                .filter(charge -> !charge.trip().departure().isBefore(partEnd))
                .findFirst()
                .filter(charge -> !date.isBefore(charge.boostFrom()))
                .map(charge -> Math.max(targetPct, charge.neededPct()))
                .orElse(targetPct);
    }

    private Optional<LocalDate> gridDate(TripCharge charge) {
        return charge.grid().stream().findFirst().map(grid -> grid.window().start().atZone(zone).toLocalDate());
    }

    private double gridPct(TripCharge charge) {
        return charge.grid().stream()
                .mapToDouble(grid -> grid.window().solarKwh() + grid.window().importKwh())
                .sum() * system.charger().efficiency() / system.car().usableBatteryKwh() * 100;
    }

    /**
     * The energy to bring the battery from a level to the target charge level.
     *
     * @param levelPct the battery level to start from
     * @return the wall-side energy in kWh, 0 when the level is at or above the target
     */
    double energyNeededKwh(Percent levelPct) {
        return energyToReachKwh(preferences.targetPct().value(), levelPct.value());
    }

    private double energyToReachKwh(double capPct, double levelPct) {
        double missingPct = Math.max(0, capPct - levelPct);
        double batteryKwh = missingPct / 100 * system.car().usableBatteryKwh();
        return batteryKwh / system.charger().efficiency();
    }

    /** The part of the battery a trip takes: only the way there when the owner can charge at the destination. */
    private double tripPct(Trip trip) {
        double km = trip.canChargeAtDestination() ? trip.distanceKm() / 2 : trip.distanceKm();
        return km * tripKwhPerKm() / system.car().usableBatteryKwh() * 100;
    }

    /** How many km of a trip a share of the battery holds. */
    private double kmFor(double pct) {
        return pct / 100 * system.car().usableBatteryKwh() / tripKwhPerKm();
    }

    private double tripKwhPerKm() {
        var car = system.car();
        return car.consumptionKwhPer100km() / 100 * (1 + car.tripSurcharge().value() / 100);
    }

    /** The other driving of the days a trip lasts, from the day of its departure to the day of its return. */
    private double otherDrivingPct(Trip trip, Instant now) {
        double pct = 0;
        for (var date = trip.departure().atZone(zone).toLocalDate(); TripAdvice.covers(trip, date, zone);
                date = date.plusDays(1)) {
            pct += otherDrivingPct(date, now);
        }
        return pct;
    }

    /** The other driving of a day; today only the share still to come. */
    private double otherDrivingPct(LocalDate date, Instant now) {
        return dailyDrivingPct() * (date.equals(today(now)) ? shareOfDayLeft(now) : 1);
    }

    private double dailyDrivingPct() {
        var car = system.car();
        return car.kmPerDay() * car.consumptionKwhPer100km() / 100 / car.usableBatteryKwh() * 100;
    }

    private static double solarKwh(List<Window> windows) {
        return windows.stream().mapToDouble(Window::solarKwh).sum();
    }

    private LocalDate today(Instant now) {
        return now.atZone(zone).toLocalDate();
    }

    /** A step that ends exactly at midnight belongs to the day before. */
    private LocalDate lastDay(List<ForecastStep> forecast) {
        var end = forecast.stream().map(ForecastStep::end).max(Comparator.naturalOrder()).orElseThrow();
        return end.minusNanos(1).atZone(zone).toLocalDate();
    }

    /** The share of today still to come: the driving already done is in the battery level. */
    private double shareOfDayLeft(Instant now) {
        var today = today(now);
        var start = today.atStartOfDay(zone).toInstant();
        var end = today.plusDays(1).atStartOfDay(zone).toInstant();
        return Math.clamp(Hours.between(now, end) / Hours.between(start, end), 0.0, 1.0);
    }
}
