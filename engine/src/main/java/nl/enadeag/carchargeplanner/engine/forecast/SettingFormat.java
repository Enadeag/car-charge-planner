package nl.enadeag.carchargeplanner.engine.forecast;

import java.util.regex.Pattern;

/** What the value of a provider's setting must look like, checked when it is entered and before it is sent. */
public enum SettingFormat {

    /** Any text on one line: no control characters, such as a line break, which could end a header. */
    TEXT,

    /**
     * A server address a key may be sent to, as {@link ServerAddress#parse} accepts it under the {@link PlainHttp} of
     * the client that checks it: https, or plain http to this computer only where that allows it.
     */
    SERVER_ADDRESS,

    /** Only the letters A to Z, in either case, and the digits 0 to 9, at most 64 of them, such as an API key. */
    LETTERS_AND_DIGITS;

    private static final Pattern LETTERS_DIGITS = Pattern.compile("[A-Za-z0-9]{1,64}");

    /**
     * Whether a value as entered has this format. An empty value is left to the check on required settings. Only
     * {@link #SERVER_ADDRESS} reads the policy on plain http; it is asked of every format, so a client that checks
     * settings of any format always says where it allows plain http.
     *
     * @param value the value as entered; white space around it is ignored, as the providers ignore it
     * @param plainHttp where the checking client lets a server address use plain http, as it can connect; not null
     * @return true when the value is empty or has this format
     * @throws IllegalArgumentException when {@code plainHttp} is null
     */
    public boolean accepts(String value, PlainHttp plainHttp) {
        if (plainHttp == null) {
            throw new IllegalArgumentException("where plain http is allowed must be given");
        }
        return switch (this) {
            case TEXT -> acceptsText(value);
            case SERVER_ADDRESS -> value.isBlank() || ServerAddress.parse(value, plainHttp).isPresent();
            case LETTERS_AND_DIGITS -> acceptsLettersAndDigits(value);
        };
    }

    /**
     * Whether a value has the format {@link #TEXT}, for a provider that checks a value before it sends it.
     *
     * @param value the value; white space around it is ignored
     * @return true when the value is empty or has no control characters
     */
    public static boolean acceptsText(String value) {
        return value.strip().chars().noneMatch(Character::isISOControl);
    }

    /**
     * Whether a value has the format {@link #LETTERS_AND_DIGITS}, for a provider that checks a value before it sends
     * it.
     *
     * @param value the value; white space around it is ignored
     * @return true when the value is empty or has only letters A to Z and digits, at most 64
     */
    public static boolean acceptsLettersAndDigits(String value) {
        var stripped = value.strip();
        return stripped.isEmpty() || LETTERS_DIGITS.matcher(stripped).matches();
    }
}
