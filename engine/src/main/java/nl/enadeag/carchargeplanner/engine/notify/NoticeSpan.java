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
     * How long after its start something whose start is fixed, such as a trip's planned grid charge, is still told when
     * the notification lead time is 0: a round or the phone's alarm may come late. A running solar window begins at
     * each round's moment, so it is told when a round first sees it.
     */
    static final Duration AT_START_GRACE = Duration.ofMinutes(10);

    /**
     * @param until the moment from which the notice may no longer be told, such as the window's end
     */
    static NoticeSpan before(Instant start, Duration lead, Instant until) {
        return new NoticeSpan(start.minus(lead), until);
    }

    /** A lead of 0 would leave no time before the start, so then the span runs from the start. */
    static NoticeSpan beforeStart(Instant start, Duration lead, Instant end) {
        if (lead.isZero()) {
            var graceEnd = start.plus(AT_START_GRACE);
            return new NoticeSpan(start, graceEnd.isBefore(end) ? graceEnd : end);
        }
        return before(start, lead, start);
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
