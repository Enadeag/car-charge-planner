package nl.enadeag.carchargeplanner.platform.windows;

import nl.enadeag.carchargeplanner.desktop.system.ClockFormat;
import nl.enadeag.carchargeplanner.desktop.system.ClockSetting;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.util.function.Consumer;

/**
 * Windows' short time format (Settings, Time and language, Region), as the user's registry keeps it: the text value
 * {@code sShortTime}, such as "HH:mm" or "h:mm tt", and the older {@code iTime}, "1" for a 24-hour clock and "0" for
 * a 12-hour one. Read through {@link Advapi32} and followed by the registry's change notification, as
 * {@link RegistryTextSize} follows the text size.
 */
public final class RegistryClock implements ClockSetting {

    /** The key below HKEY_CURRENT_USER that holds the user's regional formats, the time format among them. */
    public static final String INTERNATIONAL_KEY = "Control Panel\\International";
    /** The text value that holds the short time format, in Windows' own pattern letters. */
    static final String SHORT_TIME = "sShortTime";
    /** The text value that says whether the clock has 24 hours, read when the short time format says nothing. */
    static final String TIME = "iTime";

    private static final System.Logger LOG = System.getLogger(RegistryClock.class.getName());

    private final String key;

    /**
     * Makes the setting; nothing is read yet. Only for Windows.
     *
     * @param key the key's path below HKEY_CURRENT_USER: {@link #INTERNATIONAL_KEY}, or another key for a test, which
     *     leaves the user's time format alone
     */
    public RegistryClock(String key) {
        this.key = key;
    }

    /**
     * Gives the clock now, then follows it while the key exists; without the key, or when it cannot be read, the
     * language's own.
     */
    @Override
    public void follow(Consumer<ClockFormat> changed) {
        new RegistryFollower<>(key, this::read, ClockFormat.LANGUAGE, "Windows' time format",
                "the times stay on the clock they have", Level.DEBUG, "clock").follow(changed);
    }

    private ClockFormat read() {
        try {
            return of(Advapi32.readText(key, SHORT_TIME).orElse(""), Advapi32.readText(key, TIME).orElse(""));
        } catch (IOException e) {
            LOG.log(Level.DEBUG, "cannot read Windows' time format; the times are written the language's way", e);
            return ClockFormat.LANGUAGE;
        }
    }

    /**
     * The clock the values stand for.
     *
     * @param shortTime the short time format, such as "HH:mm" or "h:mm tt", where text between single quotes is
     *     written as it is; an empty text when missing
     * @param time "1" for a 24-hour clock and "0" for a 12-hour one; an empty text when missing
     * @return a 24-hour clock when the short time format has an {@code H}, a 12-hour one when it has an {@code h};
     *     otherwise what {@code iTime} says, and the language's own when that says nothing either
     */
    static ClockFormat of(String shortTime, String time) {
        var letters = shortTime.replaceAll("'[^']*'?", "");
        if (letters.contains("H")) {
            return ClockFormat.TWENTY_FOUR_HOUR;
        }
        if (letters.contains("h")) {
            return ClockFormat.TWELVE_HOUR;
        }
        return switch (time.strip()) {
            case "1" -> ClockFormat.TWENTY_FOUR_HOUR;
            case "0" -> ClockFormat.TWELVE_HOUR;
            default -> ClockFormat.LANGUAGE;
        };
    }
}
