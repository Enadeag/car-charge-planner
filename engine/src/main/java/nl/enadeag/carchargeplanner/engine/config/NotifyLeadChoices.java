package nl.enadeag.carchargeplanner.engine.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The notification lead times a form offers: at the start, or 5, 10, 15, 30 or 60 minutes ahead. A lead that is none
 * of them, such as a stored 20 minutes, is offered beside them while it is the one chosen, so it shows as it is and is
 * never rounded to the nearest; once another is chosen, it is no longer offered.
 */
public final class NotifyLeadChoices {

    /** The leads always offered, shortest first: 0, 5, 10, 15, 30 and 60 minutes. */
    public static final List<Duration> STANDARD = List.of(Duration.ZERO, Duration.ofMinutes(5),
            Duration.ofMinutes(10), Duration.ofMinutes(15), Duration.ofMinutes(30), Duration.ofMinutes(60));

    private NotifyLeadChoices() {
    }

    /**
     * The leads to offer while one is chosen.
     *
     * @param chosen the lead chosen now, such as the stored one, in whole minutes, 0 or more
     * @return the {@link #STANDARD} leads, with {@code chosen} among them in order of length when it is none of them
     * @throws IllegalArgumentException when {@code chosen} is negative
     */
    public static List<Duration> offered(Duration chosen) {
        if (chosen.isNegative()) {
            throw new IllegalArgumentException("a lead must not be negative, not " + chosen);
        }
        if (STANDARD.contains(chosen)) {
            return STANDARD;
        }
        var offered = new ArrayList<>(STANDARD);
        offered.add(chosen);
        offered.sort(null);
        return List.copyOf(offered);
    }
}
