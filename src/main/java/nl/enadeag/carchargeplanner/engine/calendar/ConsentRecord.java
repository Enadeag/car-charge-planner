package nl.enadeag.carchargeplanner.engine.calendar;

import java.time.Instant;

/**
 * A consent the owner gave.
 *
 * @param consent which consent
 * @param givenAt when the owner gave it
 * @param textVersion the version of the text the owner agreed to, 1 or more
 */
public record ConsentRecord(Consent consent, Instant givenAt, int textVersion) {

    /**
     * Checks the values.
     *
     * @throws IllegalArgumentException when the consent or the moment is null, or the version is below 1
     */
    public ConsentRecord {
        if (consent == null || givenAt == null) {
            throw new IllegalArgumentException("a consent record needs the consent and the moment it was given");
        }
        if (textVersion < 1) {
            throw new IllegalArgumentException("a text version is 1 or more, not " + textVersion);
        }
    }

    /**
     * The consent given now to the text the app shows now.
     *
     * @param consent which consent
     * @param now the moment the owner gives it
     * @return a record with the consent's current text version
     */
    public static ConsentRecord givenNow(Consent consent, Instant now) {
        return new ConsentRecord(consent, now, consent.textVersion());
    }

    /**
     * Whether the owner agreed to the text the app shows now, so the consent counts.
     *
     * @return false for a consent given to an older text
     */
    public boolean isCurrent() {
        return textVersion == consent.textVersion();
    }
}
