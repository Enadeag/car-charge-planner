package nl.enadeag.carchargeplanner.platform.windows;

import nl.enadeag.carchargeplanner.desktop.system.TextSize;
import nl.enadeag.carchargeplanner.desktop.system.TextSizeSetting;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.util.function.Consumer;

/**
 * Windows' Text size (Settings, Accessibility), as the user's registry keeps it: the number value
 * {@code TextScaleFactor}, from 100 to 225, missing until the user first changes it. Read through {@link Advapi32} and
 * followed by the registry's change notification, on a thread of its own that waits for an event. JavaFX's own
 * preferences do not include it, and it changes nothing that they do include.
 */
public final class RegistryTextSize implements TextSizeSetting {

    /** The key below HKEY_CURRENT_USER that holds the user's accessibility settings, the text size among them. */
    public static final String ACCESSIBILITY_KEY = "Software\\Microsoft\\Accessibility";
    /** The number value that holds the text size in percent. */
    static final String VALUE = "TextScaleFactor";

    private static final System.Logger LOG = System.getLogger(RegistryTextSize.class.getName());

    private final String key;

    /**
     * Makes the setting; nothing is read yet. Only for Windows.
     *
     * @param key the key's path below HKEY_CURRENT_USER: {@link #ACCESSIBILITY_KEY}, or another key for a test, which
     *     leaves the user's text size alone
     */
    public RegistryTextSize(String key) {
        this.key = key;
    }

    /**
     * Gives the size now, then follows it while the key exists. Without the key there is nothing to follow: a change
     * in Settings makes it, and the app follows from its next start.
     */
    @Override
    public void follow(Consumer<TextSize> changed) {
        new RegistryFollower<>(key, this::read, TextSize.NORMAL, "Windows' text size",
                "the windows keep the size they open with", Level.WARNING, "text-size").follow(changed);
    }

    /** The size now; normal when the value is missing or cannot be read. */
    private TextSize read() {
        try {
            var percent = Advapi32.readNumber(key, VALUE);
            return percent.isPresent() ? TextSize.clamped(percent.getAsInt()) : TextSize.NORMAL;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "cannot read Windows' text size; the text keeps its normal size", e);
            return TextSize.NORMAL;
        }
    }
}
