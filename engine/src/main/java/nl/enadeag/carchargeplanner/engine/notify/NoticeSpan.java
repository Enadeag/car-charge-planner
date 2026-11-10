package nl.enadeag.carchargeplanner.engine.notify;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * The time in which a notice about something that starts may be told: from the notification lead time before its
 * start until a later moment, that moment left out.
 */
record NoticeSpan(Instant from, Instant until) {

    /**
     * @param until the moment from which the notice may no longer be told, such as the start itself
     */
    static NoticeSpan before(Instant start, Duration lead, Instant until) {
        return new NoticeSpan(start.minus(lead), until);
    }

    /**
     * @return true from {@code from} on, and before {@code until}
     */
    boolean contains(Instant moment) {
        return !moment.isBefore(from) && moment.isBefore(until);
    }

    /**
     * @return now while the span holds it, its start while that is still to come, or empty once the span is over or
     *     when it is empty
     */
    Optional<Instant> firstFrom(Instant now) {
        var first = now.isBefore(from) ? from : now;
        return contains(first) ? Optional.of(first) : Optional.empty();
    }
}
