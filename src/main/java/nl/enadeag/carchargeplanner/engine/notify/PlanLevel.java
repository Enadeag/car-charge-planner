package nl.enadeag.carchargeplanner.engine.notify;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import java.time.Instant;
import java.util.Optional;

/**
 * The battery level a plan starts from, and whether the owner entered it.
 *
 * @param levelPct the battery level
 * @param enteredAt when the owner entered it; empty while it is the assumed battery level
 */
public record PlanLevel(Percent levelPct, Optional<Instant> enteredAt) {
}
