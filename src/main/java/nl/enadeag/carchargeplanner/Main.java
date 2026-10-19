package nl.enadeag.carchargeplanner;

import nl.enadeag.carchargeplanner.cli.CommandLine;
import nl.enadeag.carchargeplanner.cli.Commands;
import nl.enadeag.carchargeplanner.cli.ExitCode;
import nl.enadeag.carchargeplanner.desktop.app.AppSettingsFile;
import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.LanguageChoice;
import nl.enadeag.carchargeplanner.host.ConfigLocation;
import nl.enadeag.carchargeplanner.host.Host;
import nl.enadeag.carchargeplanner.host.InstanceLocation;
import nl.enadeag.carchargeplanner.instance.SingleInstance;
import nl.enadeag.carchargeplanner.logging.UncaughtLogging;
import nl.enadeag.carchargeplanner.platforms.PlatformParts;
import nl.enadeag.carchargeplanner.start.DesktopApp;
import java.time.Duration;
import java.util.Locale;

/**
 * Starts the desktop app or runs a command: the composition root, where the system is read once and the parts are
 * made and handed what they need.
 *
 * <p>Usage: {@code car-charge-planner [run | quit | test-notification | start-at-login on | start-at-login off
 * | install-launcher | uninstall-launcher] [--config=path/to/engine.json] [--background]}.
 */
public final class Main {

    /** The desktop app's own settings, beside the engine's config file in the app's folder. */
    private static final String APP_SETTINGS_FILE = "app.json";
    /** A running app that is still starting, or busy, may take a moment to listen or to confirm a command. */
    private static final Duration INSTANCE_TIMEOUT = Duration.ofSeconds(2);

    private Main() {
    }

    /**
     * Runs the app, or a command of its command line.
     *
     * @param args the command and options; see the class's usage. Options the app does not know are ignored.
     * @throws Exception when JavaFX cannot start or the dashboard cannot be made; the app cannot run then
     */
    public static void main(String[] args) throws Exception {
        new UncaughtLogging(System.getLogger(UncaughtLogging.class.getName())).install();
        CommandLine commandLine;
        try {
            commandLine = CommandLine.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println(CommandLine.USAGE);
            System.exit(ExitCode.USAGE.code());
            return;
        }
        var host = Host.current();
        var parts = PlatformParts.of(host);
        var defaultConfig = ConfigLocation.defaultPath(host.os(), host.environment(), host.home());
        var appSettingsFile = new AppSettingsFile(defaultConfig.resolveSibling(APP_SETTINGS_FILE),
                Units.ofCountry(Locale.getDefault(Locale.Category.FORMAT).getCountry()));
        // the mode is read once, as the app starts, and applies at the next start; language and appearance follow Settings
        var appSettings = appSettingsFile.read();
        var language = new LanguageChoice(Language.ofSystem(host.environment(), Locale.getDefault(Locale.Category.DISPLAY)));
        appSettings.language().ifPresentOrElse(language::choose, language::followSystem);
        var instance = new SingleInstance(InstanceLocation.folder(host.os(), host.environment(), host.home()),
                host.owner(), INSTANCE_TIMEOUT);

        if (commandLine.command() == CommandLine.Command.RUN) {
            new DesktopApp(commandLine, host, parts, instance, commandLine.config().orElse(defaultConfig),
                    appSettingsFile, appSettings, language).run();
            return;
        }
        var outcome = Commands.of(host, parts, instance, language.messages()).run(commandLine.command());
        outcome.print(System.out, System.err);
        System.exit(outcome.exit().code());
    }
}
