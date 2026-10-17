package io.github.enadeag.carchargeplanner.engine.forecast;

import java.util.regex.Pattern;

/** What the value of a provider's setting must look like, checked when it is entered and before it is sent. */
public enum SettingFormat {

    /** Any text. */
    TEXT,

    /** A server address a key may be sent to, as {@link ServerAddress#parse} accepts it. */
    SERVER_ADDRESS,

    /** Only the letters A to Z, in either case, and the digits 0 to 9, at most 64 of them, such as an API key. */
    LETTERS_AND_DIGITS;

    private static final Pattern LETTERS_DIGITS = Pattern.compile("[A-Za-z0-9]{1,64}");

    /**
     * Whether a value has this format. An empty value is left to the check on required settings.
     *
     * @param value the value as entered; white space around it is ignored, as the providers ignore it
     * @return true when the value is empty or has this format
     */
    public boolean accepts(String value) {
        var stripped = value.strip();
        if (stripped.isEmpty()) {
            return true;
        }
        return switch (this) {
            case TEXT -> true;
            case SERVER_ADDRESS -> ServerAddress.parse(stripped).isPresent();
            case LETTERS_AND_DIGITS -> LETTERS_DIGITS.matcher(stripped).matches();
        };
    }
}
