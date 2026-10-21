package nl.enadeag.carchargeplanner.platforms;

import nl.enadeag.carchargeplanner.desktop.system.AppIcon;
import nl.enadeag.carchargeplanner.desktop.system.StartAtLogin;
import nl.enadeag.carchargeplanner.desktop.system.StatusIcon;
import nl.enadeag.carchargeplanner.desktop.system.TaskbarHiding;
import nl.enadeag.carchargeplanner.desktop.system.TextSize;
import nl.enadeag.carchargeplanner.desktop.system.TextSizeSetting;
import nl.enadeag.carchargeplanner.desktop.text.LanguageChoice;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.secrets.Keychain;
import nl.enadeag.carchargeplanner.engine.secrets.NoKeychain;
import nl.enadeag.carchargeplanner.host.AppName;
import nl.enadeag.carchargeplanner.host.ConfigLocation;
import nl.enadeag.carchargeplanner.host.Host;
import nl.enadeag.carchargeplanner.host.Os;
import nl.enadeag.carchargeplanner.host.RuntimeFolder;
import nl.enadeag.carchargeplanner.platform.linux.ApplicationMenuEntry;
import nl.enadeag.carchargeplanner.platform.linux.BusConnector;
import nl.enadeag.carchargeplanner.platform.linux.FreedesktopNotifications;
import nl.enadeag.carchargeplanner.platform.linux.LinuxStartAtLogin;
import nl.enadeag.carchargeplanner.platform.linux.PortalStartAtLogin;
import nl.enadeag.carchargeplanner.platform.linux.SecretServiceKeychain;
import nl.enadeag.carchargeplanner.platform.linux.StatusNotifierTray;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javafx.application.ColorScheme;
import javafx.beans.value.ObservableValue;

/**
 * The freedesktop.org parts, over the session bus: a StatusNotifierItem, the notification server, the Secret Service
 * and a desktop entry in the autostart folder, or in a Flatpak sandbox the Background portal. Also used on systems
 * that are neither Linux nor Windows, without a keychain there.
 */
final class FreedesktopParts implements PlatformParts {

    private final Host host;
    private final BusConnector sessionBus = BusConnector.sessionBus();

    /**
     * Makes the parts; nothing is connected yet.
     *
     * @param host the system and its user, for the folders and the private icon folders
     */
    FreedesktopParts(Host host) {
        this.host = host;
    }

    @Override
    public TrayParts trayParts(ObservableValue<ColorScheme> colorScheme, AppIcon icon, LanguageChoice language) {
        var tray = new StatusNotifierTray(sessionBus, new StatusIcon(), runtimeFolder("tray"), AppName.ID,
                AppName.TITLE, host.flatpak() ? StatusNotifierTray.ItemName.CONNECTION
                        : StatusNotifierTray.ItemName.PER_PROCESS);
        // a notification server is there also without a tray
        var notifications = new FreedesktopNotifications(sessionBus, AppName.TITLE, desktopEntry(), icon,
                runtimeFolder("notify"), () -> language.messages().text(TextKey.OPEN_DASHBOARD));
        return new TrayParts(tray, notifications, false);
    }

    @Override
    public Keychain keychain() {
        return host.os() == Os.LINUX ? new SecretServiceKeychain(sessionBus, AppName.ID) : new NoKeychain();
    }

    @Override
    public Optional<StartAtLogin> startAtLogin() {
        var config = ConfigLocation.systemFolder(host.os(), host.environment(), host.home());
        if (host.flatpak()) {
            // the Flatpak's command, which the portal starts inside the sandbox
            return host.launcher().map(_ -> new PortalStartAtLogin(sessionBus,
                    config.resolve(AppName.ID).resolve("start-at-login"),
                    List.of(AppName.ID, StartAtLogin.AT_LOGIN_FLAG), PortalStartAtLogin.ANSWER_WAIT));
        }
        return host.launcher().map(program -> new LinuxStartAtLogin(config.resolve("autostart"),
                AppName.ID + ".desktop", program, AppName.TITLE));
    }

    /**
     * Always normal: GNOME's text scaling factor and KDE's font DPI reach X11 programs as the screen's DPI, from which
     * JavaFX's GTK toolkit scales the whole window, text and all, as it does for display scaling. Larger text on top
     * of that would count the setting twice.
     */
    @Override
    public TextSizeSetting textSize() {
        return changed -> changed.accept(TextSize.NORMAL);
    }

    @Override
    public TaskbarHiding taskbarHiding() {
        return TaskbarHiding.NONE;
    }

    @Override
    public void browse(URI link) throws IOException {
        new ProcessBuilder("xdg-open", link.toString()).inheritIO().start();
    }

    /**
     * The menu entry the notifications are tied to: the Flatpak's in its sandbox, the one the app's .deb or .rpm
     * installed when the app runs from there, otherwise the one {@code install-launcher} writes.
     *
     * @return the entry's name without ".desktop"
     */
    String desktopEntry() {
        if (host.flatpak()) {
            return AppName.FLATPAK_ID;
        }
        return host.launcher().flatMap(program -> ApplicationMenuEntry.packageEntry(program, AppName.ID))
                .orElse(AppName.ID);
    }

    /** A private folder of this process's own; see {@link RuntimeFolder}. */
    private Path runtimeFolder(String purpose) {
        try {
            return RuntimeFolder.make(purpose, host.environment(), host.temporary(), host.owner());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot make a private folder for the " + purpose + " icons", e);
        }
    }
}
