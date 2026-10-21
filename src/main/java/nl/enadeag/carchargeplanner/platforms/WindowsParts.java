package nl.enadeag.carchargeplanner.platforms;

import nl.enadeag.carchargeplanner.desktop.system.AppIcon;
import nl.enadeag.carchargeplanner.desktop.system.StartAtLogin;
import nl.enadeag.carchargeplanner.desktop.system.StatusIcon;
import nl.enadeag.carchargeplanner.desktop.system.TaskbarHiding;
import nl.enadeag.carchargeplanner.desktop.system.TextSizeSetting;
import nl.enadeag.carchargeplanner.desktop.text.LanguageChoice;
import nl.enadeag.carchargeplanner.engine.secrets.Keychain;
import nl.enadeag.carchargeplanner.host.AppName;
import nl.enadeag.carchargeplanner.host.Host;
import nl.enadeag.carchargeplanner.platform.windows.AwtTray;
import nl.enadeag.carchargeplanner.platform.windows.CredentialManagerKeychain;
import nl.enadeag.carchargeplanner.platform.windows.RegistryRunKey;
import nl.enadeag.carchargeplanner.platform.windows.RegistryTextSize;
import nl.enadeag.carchargeplanner.platform.windows.TaskbarShade;
import nl.enadeag.carchargeplanner.platform.windows.WindowsStartAtLogin;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import javafx.application.ColorScheme;
import javafx.beans.value.ObservableValue;

/** Windows' parts: AWT's tray icon and its messages, the Credential Manager, the Run key and the text size. */
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
        return host.launcher().map(program -> new WindowsStartAtLogin(new RegistryRunKey(RegistryRunKey.RUN_KEY_PATH),
                AppName.TITLE, program));
    }

    @Override
    public TextSizeSetting textSize() {
        return new RegistryTextSize(RegistryTextSize.ACCESSIBILITY_KEY);
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
