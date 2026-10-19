package nl.enadeag.carchargeplanner.start;

import nl.enadeag.carchargeplanner.desktop.system.AppIcon;
import nl.enadeag.carchargeplanner.desktop.system.Notifications;
import nl.enadeag.carchargeplanner.desktop.system.StartAtLogin;
import nl.enadeag.carchargeplanner.desktop.system.TaskbarHiding;
import nl.enadeag.carchargeplanner.desktop.system.Tray;
import nl.enadeag.carchargeplanner.desktop.text.LanguageChoice;
import nl.enadeag.carchargeplanner.engine.secrets.Keychain;
import nl.enadeag.carchargeplanner.host.Host;
import nl.enadeag.carchargeplanner.host.Os;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import javafx.application.ColorScheme;
import javafx.beans.value.ObservableValue;

/** The parts each operating system brings itself: the tray, notifications, keychain and start at login. */
public sealed interface PlatformParts permits LinuxParts, WindowsParts {

    /**
     * The parts of the system the app runs on.
     *
     * @param host the system and its user
     * @return Windows' parts on Windows, otherwise the freedesktop.org ones
     */
    static PlatformParts of(Host host) {
        return host.os() == Os.WINDOWS ? new WindowsParts(host) : new LinuxParts(host);
    }

    /**
     * The tray icon and the notifications, which on some systems are one and the same.
     *
     * @param tray the icon in the system's tray
     * @param notifications the system's notifications
     * @param needTheTray true when the notifications are the tray icon's messages, so the icon must be there for
     *     them even when the dashboard is a window
     */
    record TrayParts(Tray tray, Notifications notifications, boolean needTheTray) {
    }

    /**
     * Makes the tray icon and the notifications; nothing is shown yet.
     *
     * @param colorScheme the system's light or dark, which the panel follows; read on the JavaFX thread
     * @param icon the app icon, for the notifications
     * @param language the language the notifications' buttons speak, followed while the app runs
     * @return the tray and the notifications
     */
    TrayParts trayParts(ObservableValue<ColorScheme> colorScheme, AppIcon icon, LanguageChoice language);

    /**
     * The system's keychain for the API keys; the caller closes it when the app ends, as it may keep a connection.
     *
     * @return a new keychain client; one that refuses everything on a system without one, so the keys go to the
     *     private file
     */
    Keychain keychain();

    /**
     * The system's start at login.
     *
     * @return empty when the app does not run from jpackage's launcher: there is nothing to start then
     */
    Optional<StartAtLogin> startAtLogin();

    /**
     * How this system keeps the dashboard's popup out of its taskbar.
     *
     * @return the way for this system
     */
    TaskbarHiding taskbarHiding();

    /**
     * Opens a link in the owner's browser. Blocks while the browser is started.
     *
     * @param link the page to open
     * @throws IOException when no browser could be started
     */
    void browse(URI link) throws IOException;
}
