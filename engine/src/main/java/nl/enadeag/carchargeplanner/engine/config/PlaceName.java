package nl.enadeag.carchargeplanner.engine.config;

import java.util.Optional;

/**
 * The name of the place the owner chose from the place search, kept with the home's coordinates: the result's label
 * as it was shown then, such as "Utrecht, Netherlands", in the language of that moment.
 *
 * <p>A name holds no control character (line breaks and tabs among them), no line or paragraph separator and no
 * character that changes the text's direction, as such a character would break the line it is shown on or turn the
 * text around it; a zero-width joiner or non-joiner is allowed, as names in Persian and Indic scripts need them.
 *
 * @param text the name, without spaces around it; not blank, at most {@link #LONGEST} characters, and without a
 *     control, separator or direction character
 */
public record PlaceName(String text) {

    /** The most characters a place name has, counted as code points, so a letter outside the BMP counts once. */
    public static final int LONGEST = 100;

    /**
     * Trims the name and checks its length and characters.
     *
     * @throws IllegalArgumentException when the name is blank or longer than {@link #LONGEST} characters once trimmed,
     *     or holds a control, separator or direction character
     */
    public PlaceName {
        text = text.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("a place name must not be blank");
        }
        if (text.codePointCount(0, text.length()) > LONGEST) {
            throw new IllegalArgumentException("a place name must be at most " + LONGEST + " characters");
        }
        if (text.codePoints().anyMatch(PlaceName::isRefused)) {
            throw new IllegalArgumentException("a place name must not hold a control, line break or direction"
                    + " character");
        }
    }

    /**
     * The name a text stands for, where a blank one means none, as in the settings file.
     *
     * @param text the name as written, possibly with spaces around it
     * @return the name, trimmed; empty when the text is blank
     * @throws IllegalArgumentException when the trimmed text is longer than {@link #LONGEST} characters, or holds a
     *     control, separator or direction character
     */
    public static Optional<PlaceName> of(String text) {
        return text.isBlank() ? Optional.empty() : Optional.of(new PlaceName(text));
    }

    /**
     * The name of a place chosen from the search results, from the label the owner chose it by: each control,
     * separator or direction character becomes a space, and a label longer than {@link #LONGEST} characters is cut
     * there, as a search result's label has no rules of its own.
     *
     * @param label the label as the results showed it, such as from
     *     {@link nl.enadeag.carchargeplanner.engine.forecast.Place#labelAmong(java.util.List)}
     * @return the name, trimmed and cut; empty when the label is blank once those characters are spaces
     */
    public static Optional<PlaceName> fromLabel(String label) {
        var plain = new StringBuilder(label.length());
        label.codePoints().forEach(c -> plain.appendCodePoint(isRefused(c) ? ' ' : c));
        var trimmed = plain.toString().strip();
        if (trimmed.codePointCount(0, trimmed.length()) > LONGEST) {
            trimmed = trimmed.substring(0, trimmed.offsetByCodePoints(0, LONGEST));
        }
        return of(trimmed);
    }

    private static boolean isRefused(int c) {
        return Character.getType(c) == Character.CONTROL
                || c == ' ' || c == ' '
                || c == '‎' || c == '‏' || c == '؜'
                || c >= '‪' && c <= '‮'
                || c >= '⁦' && c <= '⁩';
    }
}
