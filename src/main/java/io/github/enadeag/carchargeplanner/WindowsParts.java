package io.github.enadeag.carchargeplanner;

import io.github.enadeag.carchargeplanner.desktop.system.AppIcon;
import io.github.enadeag.carchargeplanner.desktop.system.StartAtLogin;
import io.github.enadeag.carchargeplanner.desktop.system.StatusIcon;
import io.github.enadeag.carchargeplanner.desktop.system.TaskbarHiding;
import io.github.enadeag.carchargeplanner.desktop.text.LanguageChoice;
import io.github.enadeag.carchargeplanner.engine.secrets.Keychain;
import io.github.enadeag.carchargeplanner.platform.windows.AwtTray;
import io.github.enadeag.carchargeplanner.platform.windows.CredentialManagerKeychain;
import io.github.enadeag.carchargeplanner.platform.windows.RegistryRunKey;
import io.github.enadeag.carchargeplanner.platform.windows.TaskbarShade;
import io.github.enadeag.carchargeplanner.platform.windows.WindowsStartAtLogin;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import javafx.application.ColorScheme;
import javafx.beans.value.ObservableValue;

/** Windows' parts: AWT's tray icon and its messages, the Credential Manager and the Run key. */
final class WindowsParts implements PlatformParts {

    private final Host host;

    /**
     * Makes the parts; nothing is shown yet.
     *
     * @param host the system and its user, for the launcher that start at login starts
     */
    WindowsParts(Host host) {
        this.host = host;
    }

    /** Windows shows notifications as the tray icon's messages. */
    @Override
    public TrayParts trayParts(ObservableValue<ColorScheme> colorScheme, AppIcon icon, LanguageChoice language) {
        var tray = new AwtTray(new StatusIcon(), new TaskbarShade(), colorScheme, AppName.TITLE);
        return new TrayParts(tray, tray, true);
    }

    @Override
    public Keychain keychain() {
        return new CredentialManagerKeychain(AppName.ID);
    }

    @Override
    public Optional<StartAtLogin> startAtLogin() {
        return host.launcher().map(program -> new WindowsStartAtLogin(new RegistryRunKey(RegistryRunKey.RUN),
                AppName.TITLE, program));
    }

    @Override
    public TaskbarHiding taskbarHiding() {
        return TaskbarHiding.OWNER_WINDOW;
    }

    @Override
    public void browse(URI link) throws IOException {
        Desktop.getDesktop().browse(link);
    }
}
