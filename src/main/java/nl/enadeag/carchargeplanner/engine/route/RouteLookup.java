package nl.enadeag.carchargeplanner.engine.route;

import nl.enadeag.carchargeplanner.engine.config.Coordinates;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderText;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Looks up a calendar event's location with one route service: whether it is a physical place, and the drive to it
 * from home. An online location is never sent, not even its coordinates; coordinates the event holds come first, so
 * only the route is asked for; of several places the one closest to home is chosen; without a route, the drive is
 * estimated. After a failure the service is left alone for a while, and a service that starts failing, and one that
 * answers again, is logged once. Nothing logged or told holds the API key, a location text or coordinates.
 *
 * <p>One lookup at a time: the methods are synchronized, as the cool-down is shared.
 */
public final class RouteLookup {

    /** How much longer the road is taken to be than the straight line, when the service has no route. */
    public static final double DETOUR_FACTOR = 1.3;
    /** The average speed in km/h over the estimated road distance, when the service has no route. */
    public static final double ESTIMATE_SPEED_KMH = 60;
    /** A request that never got an answer did not use the service's allowance, so it is tried again soon. */
    static final Duration RETRY_AFTER_UNAVAILABLE = Duration.ofMinutes(5);

    private static final System.Logger LOG = System.getLogger(RouteLookup.class.getName());

    private final RouteService service;
    private final RouteServiceInfo info;
    private Instant retryAt;
    private Boolean answering;

    /**
     * Makes the lookup; nothing is asked yet.
     *
     * @param service the route service to ask
     */
    public RouteLookup(RouteService service) {
        this.service = service;
        this.info = service.info();
    }

    /**
     * Looks up one location; blocks while the service is asked, and never throws.
     *
     * @param destination the event's location text, and its coordinates when it holds them
     * @param home where the drive starts, and near which places are favoured
     * @param settings the service's settings with values, secret ones included; not changed
     * @param now the current moment, by which the wait after a failure is measured
     * @return the place and the drive, no place, or why there is no answer
     */
    public synchronized RouteOutcome find(Destination destination, Coordinates home, Map<String, String> settings,
            Instant now) {
        var text = destination.text().strip();
        if (OnlineLocations.isOnline(text) || text.isEmpty() && destination.coordinates().isEmpty()) {
            return new RouteOutcome.NotAPlace();
        }
        var missing = info.missingRequired(settings);
        if (!missing.isEmpty()) { // nothing is sent without what the service requires
            return failedAtOnce(String.join(", ", missing) + " is required", List.of());
        }
        if (retryAt != null && now.isBefore(retryAt)) {
            return new RouteOutcome.Failed("not asked again yet", Optional.of(retryAt));
        }
        var hidden = new ArrayList<>(info.secretValues(settings));
        hidden.add(destination.text());
        try {
            var outcome = lookUp(destination, home, settings);
            retryAt = null;
            recordAnswer();
            return outcome;
        } catch (RouteError.RateLimited e) {
            return failed(e.getMessage(), now.plus(e.retryAfter().orElse(info.cooldown())), hidden);
        } catch (RouteError.Unavailable e) {
            return failed(e.getMessage(), now.plus(RETRY_AFTER_UNAVAILABLE), hidden);
        } catch (RouteError.MissingSetting e) {
            return failedAtOnce(e.getMessage(), hidden);
        } catch (RouteError e) {
            return failed(e.getMessage(), now.plus(info.cooldown()), hidden); // it answered: that counted
        } catch (RuntimeException e) {
            // a bug in a service must not stop the app; its message may hold a secret, so only its kind is told
            var kind = e.getClass().getSimpleName();
            return failed("an unexpected error (" + kind + ")", now.plus(info.cooldown()), hidden);
        }
    }

    private RouteOutcome lookUp(Destination destination, Coordinates home, Map<String, String> settings)
            throws RouteError {
        var given = destination.coordinates();
        if (given.isPresent()) {
            return found(destination.text().strip(), given.get(), RouteOutcome.Match.GIVEN, home, settings);
        }
        var candidates = service.search(destination.text().strip(), home, settings);
        if (candidates.isEmpty()) {
            return new RouteOutcome.NotFound();
        }
        var closest = candidates.stream()
                .min(Comparator.comparingDouble(candidate -> GreatCircle.distanceKm(home, candidate.location())))
                .orElseThrow();
        var match = candidates.size() == 1 ? RouteOutcome.Match.ONLY : RouteOutcome.Match.CHOSEN;
        return found(closest.name(), closest.location(), match, home, settings);
    }

    private RouteOutcome found(String name, Coordinates place, RouteOutcome.Match match, Coordinates home,
            Map<String, String> settings) throws RouteError {
        return service.drive(home, place, settings)
                .map(drive -> new RouteOutcome.Found(name, drive, match, RouteOutcome.Measure.ROUTE))
                .orElseGet(() -> new RouteOutcome.Found(name, estimate(GreatCircle.distanceKm(home, place)), match,
                        RouteOutcome.Measure.ESTIMATE));
    }

    /** The straight line times the detour factor, at the estimate's speed, to the second. */
    private static Drive estimate(double straightKm) {
        double roadKm = straightKm * DETOUR_FACTOR;
        long seconds = Math.round(roadKm / ESTIMATE_SPEED_KMH * Duration.ofHours(1).toSeconds());
        return new Drive(roadKm, Duration.ofSeconds(seconds));
    }

    /** The service gave no answer, and is left alone until a moment. */
    private RouteOutcome failed(String given, Instant until, List<String> hidden) {
        retryAt = until;
        return new RouteOutcome.Failed(recordFailure(given, hidden), Optional.of(until));
    }

    /** Nothing was sent, so the service may be asked again at once. */
    private RouteOutcome failedAtOnce(String given, List<String> hidden) {
        retryAt = null;
        return new RouteOutcome.Failed(recordFailure(given, hidden), Optional.empty());
    }

    /**
     * Logs a failure when the service answered before or was not asked yet. A server can echo what it was sent, so
     * the reason loses every hidden value first.
     */
    private String recordFailure(String given, List<String> hidden) {
        var reason = ProviderText.redacted(given, hidden);
        if (answering == null || answering) {
            LOG.log(Level.WARNING, info.name() + " gives no places or routes: " + reason);
        }
        answering = false;
        return reason;
    }

    private void recordAnswer() {
        if (answering == null) {
            LOG.log(Level.INFO, info.name() + " gives places and routes");
        } else if (!answering) {
            LOG.log(Level.INFO, info.name() + " gives places and routes again");
        }
        answering = true;
    }
}
