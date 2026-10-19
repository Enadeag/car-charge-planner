package nl.enadeag.carchargeplanner.engine.notify;

import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastSource;
import nl.enadeag.carchargeplanner.engine.forecast.Freshness;
import nl.enadeag.carchargeplanner.engine.plan.DayPlan;
import nl.enadeag.carchargeplanner.engine.plan.GridAdvice;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Decides when the owner is told about a charge window: once, within the notification lead time before it starts.
 * Each announcement is logged, with its kind and the window's start.
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

    private static final System.Logger LOG = System.getLogger(Announcer.class.getName());

    private final AnnouncedWindows store;
    private final Notifier notifier;
    private List<Window> announced;

    /**
     * Makes the announcer and reads the windows announced before the app was last stopped.
     *
     * @param store where the announced windows are kept between runs
     * @param notifier what tells the owner
     */
    public Announcer(AnnouncedWindows store, Notifier notifier) {
        this.store = store;
        this.notifier = notifier;
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
     * @param advice the grid advice, whose window, when it has one, is announced as a grid charge
     * @param source where the plan's forecast came from; announcements wait while it is stale or too old
     * @param now the current moment
     * @param config the system and preferences the plan was made for: the lead time, the time zone, the phases and
     *     the minimum charge level
     */
    public void announceDue(List<DayPlan> days, GridAdvice advice, ForecastSource source, Instant now,
            EngineConfig config) {
        var candidates = candidates(days, config);
        advice.window().ifPresent(window -> candidates.add(announcement(AnnouncementKind.GRID_CHARGE, window, config)));
        announce(candidates, source, now, config);
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
                LOG.log(Level.INFO, "announced a " + candidate.kind() + " starting at "
                        + window.start().atZone(candidate.zone()).toOffsetDateTime());
                rememberAndSave(window, now);
            }
        }
    }

    private static boolean fitToAnnounceOn(ForecastSource source, Instant now) {
        return source.freshness() != Freshness.STALE
                && Duration.between(source.fetchedAt(), now).compareTo(MAX_FORECAST_AGE) <= 0;
    }

    /** The plan's windows on the sun, as announcements. */
    private static List<Announcement> candidates(List<DayPlan> days, EngineConfig config) {
        var found = new ArrayList<Announcement>();
        days.forEach(day -> day.windows().forEach(window ->
                found.add(announcement(AnnouncementKind.CHARGE_WINDOW, window, config))));
        return found;
    }

    private static Announcement announcement(AnnouncementKind kind, Window window, EngineConfig config) {
        return new Announcement(kind, window, config.system().timezone(), config.system().charger().phases(),
                config.planning().minimumPct());
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
