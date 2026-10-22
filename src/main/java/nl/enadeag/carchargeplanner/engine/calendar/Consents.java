package nl.enadeag.carchargeplanner.engine.calendar;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The calendar's consents the owner gave, each with its date and the version of its text. The calendar may be read
 * only while both count.
 *
 * @param given per consent, its record; a consent never given, or withdrawn, is left out
 */
public record Consents(Map<Consent, ConsentRecord> given) {

    /** No consent given. */
    public static final Consents NONE = new Consents(Map.of());

    /**
     * Checks that each record is filed under its own consent, and copies the map.
     *
     * @throws IllegalArgumentException when a record is filed under the other consent
     */
    public Consents {
        for (var entry : given.entrySet()) {
            if (entry.getValue().consent() != entry.getKey()) {
                throw new IllegalArgumentException("the record of " + entry.getValue().consent()
                        + " is filed under " + entry.getKey());
            }
        }
        given = Map.copyOf(given);
    }

    /**
     * The record of a consent, also one given to an older text.
     *
     * @param consent which consent
     * @return its record; empty when it was never given or was withdrawn
     */
    public Optional<ConsentRecord> record(Consent consent) {
        return Optional.ofNullable(given.get(consent));
    }

    /**
     * Whether a consent counts: given, to the text the app shows now.
     *
     * @param consent which consent
     * @return false when it was never given, was withdrawn, or was given to an older text
     */
    public boolean counts(Consent consent) {
        return record(consent).filter(ConsentRecord::isCurrent).isPresent();
    }

    /**
     * Whether the calendar may be read: both consents count.
     *
     * @return true when every consent counts
     */
    public boolean allCount() {
        for (var consent : Consent.values()) {
            if (!counts(consent)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The consents that count.
     *
     * @return those given to the text the app shows now; empty when none
     */
    public Set<Consent> counting() {
        var counting = EnumSet.noneOf(Consent.class);
        for (var consent : Consent.values()) {
            if (counts(consent)) {
                counting.add(consent);
            }
        }
        return Set.copyOf(counting);
    }

    /**
     * The consents after the owner chose which to give. A consent chosen that counts already keeps its record and
     * its date; one chosen that does not is given now, to the text the app shows now. A consent not chosen that
     * counts is withdrawn; one given to an older text, which counts for nothing, stays as it is.
     *
     * @param chosen the consents the owner gives
     * @param now the moment a consent given now is given at
     * @return the consents to keep; equal to these when the choice is what counts already
     */
    public Consents given(Set<Consent> chosen, Instant now) {
        var kept = new EnumMap<Consent, ConsentRecord>(Consent.class);
        for (var consent : Consent.values()) {
            var record = record(consent);
            if (chosen.contains(consent)) {
                kept.put(consent, counts(consent) ? record.orElseThrow() : ConsentRecord.givenNow(consent, now));
            } else if (record.isPresent() && !record.get().isCurrent()) {
                kept.put(consent, record.get());
            }
        }
        return new Consents(kept);
    }
}
