package io.github.enadeag.carchargeplanner.engine.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.regex.Pattern;

/**
 * The short, stable name of a forecast provider, such as {@code open-meteo}: lower case letters, digits and hyphens.
 * Not an enum, because providers may come from outside the app later. In JSON it is the plain text, also as an
 * object's key.
 *
 * @param value the id itself: lower case letters, digits and hyphens, starting with a letter or digit
 */
public record ProviderId(@JsonValue String value) {

    private static final Pattern FORM = Pattern.compile("[a-z0-9][a-z0-9-]*");

    /**
     * Checks the form of the id.
     *
     * @throws IllegalArgumentException when the value is not lower case letters, digits and hyphens
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public ProviderId {
        if (!isValid(value)) {
            throw new IllegalArgumentException(
                    "'" + value + "' is not a provider id: lower case letters, digits and hyphens, such as open-meteo");
        }
    }

    /**
     * Whether a text has the form of a provider id, for checks that give their own message.
     *
     * @param text the text to check; null is not an id
     * @return true when it is lower case letters, digits and hyphens, starting with a letter or digit
     */
    public static boolean isValid(String text) {
        return text != null && FORM.matcher(text).matches();
    }

    /**
     * The id itself, so that messages and names can show it as it is.
     *
     * @return the same text as {@link #value()}
     */
    @Override
    public String toString() {
        return value;
    }
}
