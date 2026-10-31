package nl.enadeag.carchargeplanner.engine.notify;

import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastSource;
import nl.enadeag.carchargeplanner.engine.forecast.Freshness;
import nl.enadeag.carchargeplanner.engine.plan.DayPlan;
import nl.enadeag.carchargeplanner.engine.plan.GridAdvice;
import nl.enadeag.carchargeplanner.engine.plan.TripAdvice;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decides when the owner is told about a charge window: once, within the notification lead time before it starts;
 * and what the owner is told about the trips. Each announcement is logged, with its kind and the window's start, and
 * each notice about a trip with the trip's departure.
 *
 * <p>Only windows that have not started are announced. A window that overlaps one announced before counts as
 * announced, for a newer forecast can move a start by a few minutes. Nothing is announced while the forecast could
 * not be refreshed or is older than {@link #MAX_FORECAST_AGE}: the owner would act on a plan that may be wrong.
 */
public final class Announcer {

    /** The oldest forecast that announcements are made on. */
    static final Duration MAX_FORECAST_AGE = Duration.ofHours(6);
    /** How long after its end an announced window is kept, so that a window running past midnight is still known. */
    static final Duration KEEP_AFTER_END = Duration.ofDays(1);

    private static final Logger LOG = LoggerFactory.getLogger(Announcer.class);

    private final AnnouncedWindows store;
    private final Notifier notifier;
    private final TripNotices trips;
    private List<Window> announced;

    /**
     * Makes the announcer and reads the windows announced, and what was told about the trips, before the app was last
     * stopped.
     *
     * @param store where the announced windows are kept between runs
     * @param toldTrips where what the owner was told about each trip is kept between runs
     * @param notifier what tells the owner
     */
    public Announcer(AnnouncedWindows store, ToldTrips toldTrips, Notifier notifier) {
        this.store = store;
        this.notifier = notifier;
        this.trips = new TripNotices(toldTrips, notifier);
        this.announced = store.load();
    }

    /**
     * The windows the owner has been told about, for the planner, which keeps a running one as it was announced.
     *
     * @return the announced windows, possibly none; those that had ended a day before the latest announcement are
     *     left out
     */
    public synchronized List<Window> announced() {
        return announced;
    }

    /**
     * Tells the owner about each window of the plan that is due, and remembers it.
     *
     * @param days the plan's days, whose charge windows are announced
     * @param source where the plan's forecast came from; announcements wait while it is stale or too old
     * @param now the current moment
     * @param config the system and preferences the plan was made for: the lead time, the time zone, the phases and
     *     the minimum charge level
     */
    public void announceDue(List<DayPlan> days, ForecastSource source, Instant now, EngineConfig config) {
        announce(candidates(days, config), source, now, config);
    }

    /**
     * Tells the owner about each window of the plan that is due, the grid advice's among them, and remembers it.
     *
     * @param days the plan's days, whose charge windows are announced
     * @param advice the grid advice, whose window, when it has one, is announced as a grid charge with its charge
     *     limit
     * @param source where the plan's forecast came from; announcements wait while it is stale or too old
     * @param now the current moment
     * @param config the system and preferences the plan was made for: the lead time, the time zone, the phases and
     *     the minimum charge level
     */
    public void announceDue(List<DayPlan> days, GridAdvice advice, ForecastSource source, Instant now,
            EngineConfig config) {
        var candidates = candidates(days, config);
        advice.window().ifPresent(window -> candidates.add(new Announcement(AnnouncementKind.GRID_CHARGE, window,
                config.system().timezone(), config.system().charger().phases(), config.planning().minimumPct(),
                advice.chargeLimitPct())));
        announce(candidates, source, now, config);
    }

    /**
     * Tells the owner what is due about the trips: the battery check, the start of a trip's grid charge and a trip that
     * even the plan leaves short, each once per trip, within the charging hours unless the grid charge must start now
     * outside them. A trip's grid charge is not one of the
     * {@linkplain #announced() announced windows}, which the planner keeps as they were announced.
     *
     * @param advice the plan's advice per trip, in order of departure; possibly none. A trip the owner makes without
     *     this car has none.
     * @param level the battery level the plan starts from, and whether and when the owner entered it
     * @param source where the plan's forecast came from; nothing is told while it is stale or too old
     * @param now the current moment
     * @param config the system and preferences the plan was made for: the lead time, the charging hours, the target
     *     charge level, the time zone and the phases
     */
    public synchronized void announceTripsDue(List<TripAdvice> advice, PlanLevel level, ForecastSource source,
            Instant now, EngineConfig config) {
        if (fitToAnnounceOn(source, now)) {
            trips.tellDue(advice, level, now, config);
        }
    }

    /** Announces the candidates that are due, in the order they start. */
    private synchronized void announce(List<Announcement> candidates, ForecastSource source, Instant now,
            EngineConfig config) {
        if (!fitToAnnounceOn(source, now)) {
            return;
        }
        candidates.sort(Comparator.comparing(announcement -> announcement.window().start()));
        var lead = config.planning().notifyLead();
        for (var candidate : candidates) {
            var window = candidate.window();
            if (now.isBefore(window.start()) && !window.start().isAfter(now.plus(lead)) && !wasAnnounced(window)) {
                notifier.announce(candidate);
                LOG.info("announced a " + candidate.kind() + " starting at "
                        + window.start().atZone(candidate.zone()).toOffsetDateTime());
                rememberAndSave(window, now);
            }
        }
    }

    private static boolean fitToAnnounceOn(ForecastSource source, Instant now) {
        return source.freshness() != Freshness.STALE
                && Duration.between(source.fetchedAt(), now).compareTo(MAX_FORECAST_AGE) <= 0;
    }

    /** The plan's solar windows, as announcements. */
    private static List<Announcement> candidates(List<DayPlan> days, EngineConfig config) {
        var found = new ArrayList<Announcement>();
        days.forEach(day -> day.windows().forEach(window ->
                found.add(new Announcement(AnnouncementKind.SOLAR_WINDOW, window, config.system().timezone(),
                        config.system().charger().phases(), config.planning().minimumPct(), Optional.empty()))));
        return found;
    }

    private boolean wasAnnounced(Window window) {
        return announced.stream().anyMatch(told -> told.start().isBefore(window.end()) && window.start().isBefore(told.end()));
    }

    private void rememberAndSave(Window window, Instant now) {
        var kept = new ArrayList<>(announced.stream()
                .filter(told -> told.end().isAfter(now.minus(KEEP_AFTER_END)))
                .toList());
        kept.add(window);
        announced = List.copyOf(kept);
        store.save(announced);
    }
}
