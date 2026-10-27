package nl.enadeag.carchargeplanner.engine.route;

import nl.enadeag.carchargeplanner.engine.config.Coordinates;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An outside service that finds places and the drive to them: the contract every route service fulfils. What the app
 * does with its answers (the filter on online meetings, coordinates in the event first, the match closest to home, the
 * estimate without a route, the cool-down) lives in {@link RouteLookup}, so a service only speaks its own protocol.
 *
 * <p>A service reaches the network only through the {@code HttpGet} it is made with, checks its settings before it
 * sends anything, and never puts a secret setting, a location text or coordinates in an error message.
 */
public interface RouteService {

    /**
     * What the service tells about itself.
     *
     * @return the same information on every call
     */
    RouteServiceInfo info();

    /**
     * Finds the places a location text may mean; blocks while the service is asked.
     *
     * @param text the location text as the event has it, not blank; sent to the service
     * @param near the home, which the service may favour places near; sent to the service
     * @param settings the service's declared settings that have a value, secret ones included; not changed
     * @return the places found, in the service's order, each with valid coordinates; empty when nothing matched
     * @throws RouteError when the service gave no answer to use; the kind says why
     */
    List<Candidate> search(String text, Coordinates near, Map<String, String> settings) throws RouteError;

    /**
     * Finds the drive by car from one place to another; blocks while the service is asked.
     *
     * @param from where the drive starts, the home; sent to the service
     * @param to where the drive ends; sent to the service
     * @param settings the service's declared settings that have a value, secret ones included; not changed
     * @return the drive, or empty when the service answered that it has no route between the two
     * @throws RouteError when the service gave no answer to use; the kind says why
     */
    Optional<Drive> drive(Coordinates from, Coordinates to, Map<String, String> settings) throws RouteError;
}
