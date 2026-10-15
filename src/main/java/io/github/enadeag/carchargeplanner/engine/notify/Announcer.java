package io.github.enadeag.carchargeplanner.engine.notify;

import io.github.enadeag.carchargeplanner.engine.config.EngineConfig;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastSource;
import io.github.enadeag.carchargeplanner.engine.forecast.Freshness;
import io.github.enadeag.carchargeplanner.engine.plan.DayPlan;
import io.github.enadeag.carchargeplanner.engine.plan.GridAdvice;
import io.github.enadeag.carchargeplanner.engine.plan.Window;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Decides when the owner is told about a charge window: once, within the notification lead time before it starts.
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
     * @param advice the grid advice, whose window is announced as a grid charge; empty when there is none
     * @param forecast the forecast the plan was made on; announcements wait while it is stale or too old
     * @param now the current moment
     * @param config the system and preferences the plan was made for: the lead time, the time zone, the phases and
     *     the minimum charge level
     */
    public synchronized void announceDue(List<DayPlan> days, Optional<GridAdvice> advice, ForecastSource forecast,
            Instant now, EngineConfig config) {
        if (!usable(forecast, now)) {
            return;
        }
        var lead = Duration.ofMinutes(config.planning().notifyLeadMinutes());
        for (var candidate : candidates(days, advice, config)) {
            var window = candidate.window();
            if (now.isBefore(window.start()) && !window.start().isAfter(now.plus(lead)) && !wasAnnounced(window)) {
                notifier.announce(candidate);
                remember(window, now);
            }
        }
    }

    private static boolean usable(ForecastSource forecast, Instant now) {
        return forecast.freshness() != Freshness.STALE
                && Duration.between(forecast.fetchedAt(), now).compareTo(MAX_FORECAST_AGE) <= 0;
    }

    /** The plan's windows on the sun and the grid advice's window, as announcements in the order they start. */
    private static List<Announcement> candidates(List<DayPlan> days, Optional<GridAdvice> advice, EngineConfig config) {
        var zone = config.system().timezone();
        var phases = config.system().charger().phases();
        var minimumPct = config.planning().minimumPct();
        var found = new ArrayList<Announcement>();
        days.forEach(day -> day.windows().forEach(window ->
                found.add(new Announcement(AnnouncementKind.CHARGE_WINDOW, window, zone, phases, minimumPct))));
        advice.flatMap(GridAdvice::window).ifPresent(window ->
                found.add(new Announcement(AnnouncementKind.GRID_CHARGE, window, zone, phases, minimumPct)));
        found.sort(Comparator.comparing(announcement -> announcement.window().start()));
        return found;
    }

    private boolean wasAnnounced(Window window) {
        return announced.stream().anyMatch(told -> told.start().isBefore(window.end()) && window.start().isBefore(told.end()));
    }

    private void remember(Window window, Instant now) {
        var kept = new ArrayList<>(announced.stream()
                .filter(told -> told.end().isAfter(now.minus(KEEP_AFTER_END)))
                .toList());
        kept.add(window);
        announced = List.copyOf(kept);
        store.save(announced);
    }
}
