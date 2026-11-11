package nl.enadeag.carchargeplanner.engine.config;

import java.util.Optional;

/**
 * The name of the place the owner chose from the place search, kept with the home's coordinates: the result's label
 * as it was shown then, such as "Utrecht, Netherlands", in the language of that moment.
 *
 * @param text the name, without spaces around it; not blank and at most {@link #LONGEST} characters
 */
public record PlaceName(String text) {

    /** The most characters a place name has, counted as code points, so a letter outside the BMP counts once. */
    public static final int LONGEST = 100;

    /**
     * Trims the name and checks its length.
     *
     * @throws IllegalArgumentException when the name is blank or longer than {@link #LONGEST} characters once trimmed
     */
    public PlaceName {
        text = text.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("a place name must not be blank");
        }
        if (text.codePointCount(0, text.length()) > LONGEST) {
            throw new IllegalArgumentException("a place name must be at most " + LONGEST + " characters");
        }
    }

    /**
     * The name a text stands for, where a blank one means none, as in the settings file.
     *
     * @param text the name as written, possibly with spaces around it
     * @return the name, trimmed; empty when the text is blank
     * @throws IllegalArgumentException when the trimmed text is longer than {@link #LONGEST} characters
     */
    public static Optional<PlaceName> of(String text) {
        return text.isBlank() ? Optional.empty() : Optional.of(new PlaceName(text));
    }

    /**
     * The name of a place chosen from the search results, from the label the owner chose it by; a label longer than
     * {@link #LONGEST} characters is cut there, as a search result's label has no limit of its own.
     *
     * @param label the label as the results showed it, such as from
     *     {@link nl.enadeag.carchargeplanner.engine.forecast.Place#labelAmong(java.util.List)}
     * @return the name, trimmed and cut; empty when the label is blank
     */
    public static Optional<PlaceName> fromLabel(String label) {
        var trimmed = label.strip();
        if (trimmed.codePointCount(0, trimmed.length()) > LONGEST) {
            trimmed = trimmed.substring(0, trimmed.offsetByCodePoints(0, LONGEST));
        }
        return of(trimmed);
    }
}
