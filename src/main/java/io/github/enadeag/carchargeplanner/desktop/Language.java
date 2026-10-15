package io.github.enadeag.carchargeplanner.desktop;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** A language the app's texts are written in; each has a complete resource bundle of its own. */
public enum Language {
    /** English, written the British way: a decimal point and a 24-hour clock. */
    ENGLISH("en", Locale.UK),
    /** Dutch: a decimal comma and a 24-hour clock. */
    DUTCH("nl", Locale.of("nl", "NL"));

    private final String code;
    private final Locale locale;

    Language(String code, Locale locale) {
        this.code = code;
        this.locale = locale;
    }

    /**
     * The language's ISO 639 code, as the app settings and the bundles' file names write it.
     *
     * @return such as "nl"
     */
    public String code() {
        return code;
    }

    /**
     * Where numbers, times and day names are written the way the language writes them.
     *
     * @return the locale the language's texts and their numbers, times and day names are made in
     */
    public Locale locale() {
        return locale;
    }

    /**
     * The language with this code.
     *
     * @param code an ISO 639 code such as "nl", in any case; may be empty or unknown
     * @return the language, or empty when the app has none with the code
     */
    public static Optional<Language> ofCode(String code) {
        return Arrays.stream(values()).filter(language -> language.code.equalsIgnoreCase(code)).findFirst();
    }

    /**
     * The system's language, the way gettext programs find it on Linux: the plain C or POSIX locale means English;
     * otherwise the first language the app has from the {@code LANGUAGE} list, then the system's locale.
     *
     * @param environment the environment variables; {@code LANGUAGE}, {@code LC_ALL}, {@code LC_MESSAGES} and
     *     {@code LANG} are read, each may be missing (Windows has none of them)
     * @param systemLocale the language Java found for the user's interface,
     *     {@code Locale.getDefault(Locale.Category.DISPLAY)}
     * @return the first of those the app has; English when it has none of them
     */
    public static Language ofSystem(Map<String, String> environment, Locale systemLocale) {
        var effective = Stream.of("LC_ALL", "LC_MESSAGES", "LANG").map(environment::get)
                .filter(value -> value != null && !value.isEmpty()).findFirst().orElse("");
        if (effective.equals("C") || effective.startsWith("C.") || effective.equals("POSIX")) {
            return ENGLISH;
        }
        // desktops such as KDE Plasma put the chosen languages in LANGUAGE, which Java itself does not read
        var listed = Arrays.stream(environment.getOrDefault("LANGUAGE", "").split(":"))
                .map(entry -> entry.split("[_.@-]")[0]);
        return Stream.concat(listed, Stream.of(systemLocale.getLanguage()))
                .flatMap(code -> ofCode(code).stream())
                .findFirst()
                .orElse(ENGLISH);
    }
}
