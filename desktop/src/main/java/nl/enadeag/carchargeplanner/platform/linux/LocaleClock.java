package nl.enadeag.carchargeplanner.platform.linux;

import nl.enadeag.carchargeplanner.desktop.system.ClockFormat;
import nl.enadeag.carchargeplanner.desktop.system.ClockSetting;
import java.text.DateFormat;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * The clock of the time locale the app was started with, as POSIX programs find it in the environment: the first of
 * {@code LC_ALL}, {@code LC_TIME} and {@code LANG} that is set, such as {@code en_US.UTF-8} for a 12-hour clock and
 * {@code en_GB.UTF-8} or {@code nl_NL.UTF-8} for a 24-hour one. KDE Plasma sets it from the region's time format;
 * read once, as the app starts, since the environment of a running program does not change.
 */
public final class LocaleClock implements ClockSetting {

    private final ClockFormat clock;

    /**
     * Reads the clock from the environment.
     *
     * @param environment the environment variables; each may be missing
     */
    public LocaleClock(Map<String, String> environment) {
        this.clock = of(environment);
    }

    /** Gives the clock read at the start, once. */
    @Override
    public void follow(Consumer<ClockFormat> changed) {
        changed.accept(clock);
    }

    /**
     * The clock of the environment's time locale.
     *
     * @param environment the environment variables; each may be missing
     * @return the clock the locale's region writes times on; the language's own when none is set, for the plain C or
     *     POSIX locale, or for a language the runtime has no locale data of
     */
    static ClockFormat of(Map<String, String> environment) {
        var value = Stream.of("LC_ALL", "LC_TIME", "LANG").map(environment::get)
                .filter(set -> set != null && !set.isEmpty()).findFirst().orElse("");
        var name = value.split("[.@]", -1)[0];
        if (name.isEmpty() || name.equals("C") || name.equals("POSIX")) {
            return ClockFormat.LANGUAGE;
        }
        var parts = name.split("_", -1);
        var locale = parts.length > 1 ? Locale.of(parts[0], parts[1]) : Locale.of(parts[0]);
        // the packaged runtime has the locale data of the app's languages only; any other would read as the root's
        if (Arrays.stream(DateFormat.getAvailableLocales())
                .noneMatch(available -> available.getLanguage().equals(locale.getLanguage()))) {
            return ClockFormat.LANGUAGE;
        }
        // "j" asks for the hours the locale's region prefers
        var pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern("j", IsoChronology.INSTANCE, locale);
        return pattern.replaceAll("'[^']*'", "").matches(".*[hK].*") ? ClockFormat.TWELVE_HOUR
                : ClockFormat.TWENTY_FOUR_HOUR;
    }
}
