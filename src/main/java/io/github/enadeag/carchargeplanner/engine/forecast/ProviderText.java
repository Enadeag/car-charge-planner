package io.github.enadeag.carchargeplanner.engine.forecast;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Text a provider's server wrote, such as the reason in an error answer, made safe to show and log: secret values
 * replaced, control and direction characters taken out, and cut short. A server can write anything there, including
 * a key it was sent, text that reverses what follows it, or a whole page.
 */
public final class ProviderText {

    /** The most characters of a provider's own text that are kept. */
    public static final int MAX_LENGTH = 200;

    /** What a secret value is replaced by. */
    public static final String REDACTED = "***";

    /** Control characters (\p{Cc}) and format characters such as direction overrides (\p{Cf}). */
    private static final Pattern INVISIBLE = Pattern.compile("[\\p{Cc}\\p{Cf}]+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private ProviderText() {
    }

    /**
     * A provider's own words for a message: "Open-Meteo says: ...".
     *
     * @param provider the provider's name
     * @param text what its server wrote; may be anything
     * @param secrets values that must not be shown, such as the keys the request was sent with; empty ones are
     *     ignored
     * @return the provider's name and its cleaned text
     */
    public static String quoted(String provider, String text, Collection<String> secrets) {
        return provider + " says: " + cleaned(text, secrets);
    }

    /**
     * A server's text made safe to show: control and format characters become spaces, secret values become
     * {@value #REDACTED}, runs of white space become one space, and it is cut to {@value #MAX_LENGTH} characters with
     * an ellipsis. Secrets are replaced before the text is cut, so no part of one is left at the end.
     *
     * @param text what the server wrote; may be anything
     * @param secrets values that must not be shown, white space around them ignored; empty ones are ignored
     * @return the cleaned text, possibly empty
     */
    public static String cleaned(String text, Collection<String> secrets) {
        var visible = redacted(INVISIBLE.matcher(text).replaceAll(" "), secrets);
        var plain = SPACES.matcher(visible).replaceAll(" ").strip();
        if (plain.codePointCount(0, plain.length()) <= MAX_LENGTH) {
            return plain;
        }
        return plain.substring(0, plain.offsetByCodePoints(0, MAX_LENGTH)).stripTrailing() + "…";
    }

    /**
     * Replaces every secret value in a text with {@value #REDACTED}, also in the encoded form a web address carries it
     * in, longest first, so a secret that holds another is replaced whole.
     *
     * @param text a text that will be shown or logged
     * @param secrets values that must not be shown, white space around them ignored; empty ones are ignored
     * @return the text without any of the secrets
     */
    public static String redacted(String text, Collection<String> secrets) {
        var result = text;
        var ordered = secrets.stream().map(String::strip).filter(secret -> !secret.isEmpty())
                .flatMap(secret -> Stream.of(secret, URLEncoder.encode(secret, StandardCharsets.UTF_8))).distinct()
                .sorted((a, b) -> Integer.compare(b.length(), a.length())).toList();
        for (var secret : ordered) {
            result = result.replace(secret, REDACTED);
        }
        return result;
    }
}
