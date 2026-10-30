package nl.enadeag.carchargeplanner.engine.secrets;

import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Removes what the withdrawn calendar trips left on disk: the consents file in the data folder, and the calendar's
 * secret iCal address and the route service's API key among the API keys. Builds of {@code main} between v0.5.0 and
 * the removal wrote them; no released app did. The address gives whoever has it the whole calendar, so it should not
 * stay behind unused.
 */
public final class CalendarLeftovers {

    /** The consents file, in the data folder. */
    static final String CONSENT_FILE = "calendar-consents.json";
    /** The calendar's iCal address and the route service's API key, as they were kept. */
    static final Map<ProviderId, Set<String>> SECRETS = Map.of(
            new ProviderId("calendar"), Set.of("ical-link"),
            new ProviderId("geoapify"), Set.of("api-key"));

    private static final System.Logger LOG = System.getLogger(CalendarLeftovers.class.getName());

    private CalendarLeftovers() {
    }

    /**
     * Removes the leftovers that are there. One that is not there is passed over in silence; one that cannot be
     * removed is logged and left for the next start, and the rest is still removed. A locked keychain cannot be
     * checked, so a copy it may hold waits for a start at which it is unlocked; the secrets file is cleared all the
     * same. Never asks the owner anything, as it runs unasked. Blocks while the keychain answers: call it away from
     * the user interface's thread.
     *
     * @param keys where the API keys are kept
     * @param dataFolder the app's data folder, which held the consents file
     */
    public static void remove(ApiKeys keys, Path dataFolder) {
        var consents = dataFolder.resolve(CONSENT_FILE);
        try {
            if (PrivateFiles.remove(consents)) {
                LOG.log(Level.INFO, "removed " + consents + ", left by the withdrawn calendar trips");
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "could not remove " + consents + ", left by the withdrawn calendar trips: "
                    + e.getMessage());
        }
        try {
            if (keys.remove(SECRETS)) {
                LOG.log(Level.INFO, "removed the calendar's address and the route service's API key, left by the "
                        + "withdrawn calendar trips");
            }
        } catch (SecretsProblem e) {
            LOG.log(Level.WARNING, "could not check for the calendar's address and the route service's API key, left "
                    + "by the withdrawn calendar trips; tried again at the next start: " + e.getMessage());
        }
    }
}
