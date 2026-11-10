package nl.enadeag.carchargeplanner.engine.notify;

import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.plan.TripAdvice;
import nl.enadeag.carchargeplanner.engine.plan.TripGridCharge;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decides what the owner is told about the trips, each notice once per trip.
 *
 * <ul>
 *     <li>The start of a trip's grid charge, as a grid charge of the grid advice is announced: within the notification
 *     lead time before it starts; at once when it should start now, also outside the charging hours when the plan
 *     says so.</li>
 *     <li>The battery check, when a trip needs a grid charge or is {@linkplain #isTight tight} and the plan starts from
 *     a level the owner did not enter in the last {@link #RECENT_LEVEL}: within the charging hours, from their start
 *     on the day before the departure, or on the day of the grid charge when that is earlier. It is asked again when
 *     the trip needs {@link #MATERIAL_CHANGE_PCT} more from the grid than when it was asked.</li>
 *     <li>A trip that even the plan leaves short: within the charging hours, from {@link #EVENING} on the day before
 *     the departure.</li>
 * </ul>
 */
final class TripNotices {

    /** A level the owner entered this recently is taken as right: the owner is not asked to check it. */
    static final Duration RECENT_LEVEL = Duration.ofHours(12);
    /** A trip whose departure level is this close above what it needs, in percentage points, is tight. */
    static final double TIGHT_MARGIN_PCT = 5;
    /** How much more the trip needs from the grid, in percentage points, before the owner is asked again. */
    static final double MATERIAL_CHANGE_PCT = 10;
    /** When on the day before the departure the owner is told that a trip is short. */
    static final LocalTime EVENING = LocalTime.of(19, 0);
    /** How long after its departure what was told about a trip is kept. */
    static final Duration KEEP_AFTER_DEPARTURE = Duration.ofDays(1);

    private static final Logger LOG = LoggerFactory.getLogger(TripNotices.class);

    private final ToldTrips store;
    private final Notifier notifier;
    private final Map<Instant, ToldTrip> told = new LinkedHashMap<>();

    /** Makes the notices and reads what was told before the app was last stopped. */
    TripNotices(ToldTrips store, Notifier notifier) {
        this.store = store;
        this.notifier = notifier;
        store.load().forEach(trip -> told.put(trip.departure(), trip));
    }

    /**
     * @param advice the plan's advice per trip, in order of departure
     */
    void tellDue(List<TripAdvice> advice, PlanLevel level, Instant now, EngineConfig config) {
        boolean changed = false;
        for (var trip : advice) {
            var before = told.getOrDefault(trip.trip().departure(), ToldTrip.nothing(trip.trip().departure()));
            var after = tellDue(trip, before, level, now, config);
            if (!after.equals(before)) {
                told.put(after.departure(), after);
                changed = true;
            }
        }
        if (changed) {
            var keepFrom = now.minus(KEEP_AFTER_DEPARTURE);
            told.values().removeIf(trip -> trip.departure().isBefore(keepFrom));
            store.save(List.copyOf(told.values()));
        }
    }

    /** The notices about one trip that are due, told; what was told about it after them. */
    private ToldTrip tellDue(TripAdvice advice, ToldTrip before, PlanLevel level, Instant now, EngineConfig config) {
        var zone = config.system().timezone();
        var hours = config.planning().chargingHours();
        var trip = advice.trip();
        var result = before;
        double gridPct = advice.neededPct().value() - advice.solarLevelPct().value();
        if (askLevel(advice, level, now, config) && before.askedGridPct()
                .map(asked -> gridPct - asked >= MATERIAL_CHANGE_PCT).orElse(true)) {
            tell(new BatteryCheck(trip, level.levelPct(), zone), trip);
            result = new ToldTrip(result.departure(), result.chargeStartTold(), Optional.of(gridPct),
                    result.shortTold());
        }
        var grid = advice.grid();
        if (grid.isPresent() && !before.chargeStartTold()
                && chargeStartSpan(grid.get(), config.planning().notifyLead()).contains(now)) {
            tell(new TripChargeStart(trip, grid.get().window(), grid.get().start(), zone,
                    config.system().charger().phases(),
                    advice.chargeLimitPct()), trip);
            result = new ToldTrip(result.departure(), true, result.askedGridPct(), result.shortTold());
        }
        if (advice.kmShort() > 0 && !before.shortTold() && hours.allowStart(now, zone)
                && !now.isBefore(dayBefore(trip.departure(), zone).atTime(EVENING).atZone(zone).toInstant())) {
            tell(new TripShort(trip, advice.kmShort(), zone), trip);
            result = new ToldTrip(result.departure(), result.chargeStartTold(), result.askedGridPct(), true);
        }
        return result;
    }

    /** Whether the owner should check the battery level for a trip now, if not asked before. */
    private static boolean askLevel(TripAdvice advice, PlanLevel level, Instant now, EngineConfig config) {
        var zone = config.system().timezone();
        var hours = config.planning().chargingHours();
        var askFrom = dayBefore(advice.trip().departure(), zone);
        var gridDay = advice.grid().map(grid -> grid.window().start().atZone(zone).toLocalDate());
        if (gridDay.isPresent() && gridDay.get().isBefore(askFrom)) {
            askFrom = gridDay.get();
        }
        boolean enteredLately = level.enteredAt().map(at -> at.isAfter(now.minus(RECENT_LEVEL))).orElse(false);
        return isTight(advice, config.planning().targetPct()) && !enteredLately && hours.allowStart(now, zone)
                && !now.isBefore(askFrom.atTime(hours.start()).atZone(zone).toInstant());
    }

    /**
     * Whether a trip depends on the battery level being right: it needs a grid charge or is short, or the plan
     * leaves it less than {@link #TIGHT_MARGIN_PCT} above what it needs while solar power did not fill the battery to
     * the target. A battery charged to the target or above is not tight: more solar power would follow a lower level.
     */
    static boolean isTight(TripAdvice advice, Percent targetPct) {
        double departure = advice.departureLevelPct().value();
        return advice.grid().isPresent() || advice.kmShort() > 0
                || departure - advice.neededPct().value() < TIGHT_MARGIN_PCT && departure < targetPct.value();
    }

    /**
     * The first moment, from now on, at which the start of a trip's grid charge is due and not told yet. The battery
     * check and a short trip are left out: they wait for the rounds that come anyway.
     *
     * @return the earliest such moment, now when one is due now; empty when none is to come
     */
    Optional<Instant> nextChargeStart(List<TripAdvice> advice, Instant now, Duration lead) {
        return advice.stream()
                .filter(trip -> !told.getOrDefault(trip.trip().departure(), ToldTrip.nothing(trip.trip().departure()))
                        .chargeStartTold())
                .flatMap(trip -> trip.grid().stream())
                .flatMap(grid -> chargeStartSpan(grid, lead).firstFrom(now).stream())
                .min(Comparator.naturalOrder());
    }

    /**
     * When the start of a grid charge is due: one that starts as planned within the lead time before its start, one
     * that starts now from the lead time before its start until its end.
     */
    private static NoticeSpan chargeStartSpan(TripGridCharge grid, Duration lead) {
        var window = grid.window();
        return NoticeSpan.before(window.start(), lead,
                grid.start() == TripGridCharge.Start.PLANNED ? window.start() : window.end());
    }

    private static LocalDate dayBefore(Instant departure, ZoneId zone) {
        return departure.atZone(zone).toLocalDate().minusDays(1);
    }

    private void tell(Notice notice, Trip trip) {
        notifier.announce(notice);
        LOG.info("told a " + notice.getClass().getSimpleName() + " for a trip");
        // the owner's time only at DEBUG, which neither the release build on Android nor the desktop by default writes
        LOG.debug("that trip leaves at " + trip.departure().atZone(notice.zone()).toOffsetDateTime());
    }
}
