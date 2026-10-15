package io.github.enadeag.carchargeplanner;

import io.github.enadeag.carchargeplanner.desktop.AnnouncementTexts;
import io.github.enadeag.carchargeplanner.desktop.AppIcon;
import io.github.enadeag.carchargeplanner.desktop.AppSettings;
import io.github.enadeag.carchargeplanner.desktop.Appearance;
import io.github.enadeag.carchargeplanner.desktop.AppSettingsFile;
import io.github.enadeag.carchargeplanner.desktop.Dashboard;
import io.github.enadeag.carchargeplanner.desktop.DisplayMode;
import io.github.enadeag.carchargeplanner.desktop.Language;
import io.github.enadeag.carchargeplanner.desktop.LanguageChoice;
import io.github.enadeag.carchargeplanner.desktop.Messages;
import io.github.enadeag.carchargeplanner.desktop.Notifications;
import io.github.enadeag.carchargeplanner.desktop.StartAtLogin;
import io.github.enadeag.carchargeplanner.desktop.StatusIcon;
import io.github.enadeag.carchargeplanner.desktop.TaskbarHiding;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import io.github.enadeag.carchargeplanner.desktop.Tray;
import io.github.enadeag.carchargeplanner.desktop.TrayTexts;
import io.github.enadeag.carchargeplanner.desktop.Units;
import io.github.enadeag.carchargeplanner.desktop.settings.AboutFacts;
import io.github.enadeag.carchargeplanner.desktop.settings.Category;
import io.github.enadeag.carchargeplanner.desktop.settings.SettingsStores;
import io.github.enadeag.carchargeplanner.desktop.settings.SettingsWindow;
import io.github.enadeag.carchargeplanner.engine.Engine;
import io.github.enadeag.carchargeplanner.engine.away.AwayFile;
import io.github.enadeag.carchargeplanner.engine.battery.BatteryFile;
import io.github.enadeag.carchargeplanner.engine.config.ConfigFile;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastCache;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastService;
import io.github.enadeag.carchargeplanner.engine.forecast.JdkHttpGet;
import io.github.enadeag.carchargeplanner.engine.forecast.Providers;
import io.github.enadeag.carchargeplanner.engine.forecast.providers.ForecastSolar;
import io.github.enadeag.carchargeplanner.engine.forecast.providers.OpenMeteo;
import io.github.enadeag.carchargeplanner.engine.notify.AnnouncedWindows;
import io.github.enadeag.carchargeplanner.engine.notify.Announcer;
import io.github.enadeag.carchargeplanner.engine.notify.Notifier;
import io.github.enadeag.carchargeplanner.engine.secrets.ApiKeys;
import io.github.enadeag.carchargeplanner.engine.secrets.KeyStorage;
import io.github.enadeag.carchargeplanner.engine.secrets.Keychain;
import io.github.enadeag.carchargeplanner.engine.secrets.NoKeychain;
import io.github.enadeag.carchargeplanner.engine.secrets.SecretsFile;
import io.github.enadeag.carchargeplanner.instance.InstanceCommand;
import io.github.enadeag.carchargeplanner.instance.Reply;
import io.github.enadeag.carchargeplanner.instance.SingleInstance;
import io.github.enadeag.carchargeplanner.platform.linux.FreedesktopNotifications;
import io.github.enadeag.carchargeplanner.platform.linux.LinuxStartAtLogin;
import io.github.enadeag.carchargeplanner.platform.linux.SecretServiceKeychain;
import io.github.enadeag.carchargeplanner.platform.linux.StatusNotifierTray;
import io.github.enadeag.carchargeplanner.platform.windows.AwtTray;
import io.github.enadeag.carchargeplanner.platform.windows.CredentialManagerKeychain;
import io.github.enadeag.carchargeplanner.platform.windows.RegistryRunKey;
import io.github.enadeag.carchargeplanner.platform.windows.TaskbarShade;
import io.github.enadeag.carchargeplanner.platform.windows.WindowsStartAtLogin;
import java.awt.Desktop;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.UserPrincipal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;

/**
 * Starts the desktop app: the composition root, where every part is made and handed what it needs.
 *
 * <p>Usage: {@code car-charge-planner [run | quit | start-at-login on | start-at-login off]
 * [--config=path/to/engine.json] [--background]}.
 */
public final class Main {

    private static final System.Logger LOG = System.getLogger(Main.class.getName());
    /** Held here because java.util.logging forgets a logger's level once nothing refers to the logger. */
    private static final java.util.logging.Logger DBUS_LOG = java.util.logging.Logger.getLogger("org.freedesktop.dbus");
    private static final String TITLE = "Car charge planner";
    /** The app's name where other programs see it, such as on the keychain's entries. */
    private static final String APP_ID = "car-charge-planner";
    /** The desktop app's own settings, beside the engine's config file in the app's folder. */
    private static final String APP_SETTINGS_FILE = "app.json";
    /**
     * Often enough to announce a window within its notification lead time, and to keep "now" on the dashboard current.
     * The forecast cache answers in between, and a provider that could not be reached waits its own time before it is
     * asked again, so this is not how often a provider is asked.
     */
    private static final long REPLAN_MINUTES = 1;
    /** A running app that is still starting, or busy, may take a moment to confirm a command. */
    private static final Duration INSTANCE_TIMEOUT = Duration.ofSeconds(2);
    /** At login the panel may come up a moment after the app. */
    private static final Duration TRAY_WAIT_AT_LOGIN = Duration.ofSeconds(5);
    private static final Duration TRAY_RETRY = Duration.ofMillis(500);

    private Main() {
    }

    /**
     * Runs the app, or a command of its command line.
     *
     * @param args the command and options; see the class's usage. Options the app does not know are ignored.
     * @throws Exception when JavaFX cannot start or the dashboard cannot be made; the app cannot run then
     */
    public static void main(String[] args) throws Exception {
        CommandLine commandLine;
        try {
            commandLine = CommandLine.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println(CommandLine.USAGE);
            System.exit(2);
            return;
        }
        var appSettingsFile = new AppSettingsFile(defaultConfigPath().resolveSibling(APP_SETTINGS_FILE),
                Units.ofCountry(Locale.getDefault(Locale.Category.FORMAT).getCountry()));
        // the mode is read once, as the app starts, and applies at the next start; language and appearance follow Settings
        var appSettings = appSettingsFile.read();
        var language = new LanguageChoice(Language.ofSystem(System.getenv(), Locale.getDefault(Locale.Category.DISPLAY)));
        language.choose(appSettings.language());
        var messages = language.messages();
        switch (commandLine.command()) {
            case RUN -> run(commandLine, appSettingsFile, appSettings, language);
            case QUIT -> System.exit(quitRunningApp(messages));
            case START_AT_LOGIN_ON -> System.exit(switchStartAtLogin(true, messages));
            case START_AT_LOGIN_OFF -> System.exit(switchStartAtLogin(false, messages));
        }
    }

    /**
     * Starts the app: the dashboard, the tray icon (or a window without a tray), the notifications, the engine, and
     * planning every minute. When a copy runs already, it is asked to show its dashboard instead, and this one exits.
     */
    private static void run(CommandLine commandLine, AppSettingsFile appSettingsFile, AppSettings appSettings,
            LanguageChoice language) throws Exception {
        var mode = appSettings.mode();
        // set on the JavaFX thread only, as JavaFX's bindings on it are read there
        var appearance = new SimpleObjectProperty<>(appSettings.appearance());
        var commands = new CompletableFuture<Consumer<InstanceCommand>>();
        var instance = singleInstance();
        // commands that come in while the app is still starting wait until it can carry them out
        switch (instance.claim(command -> commands.thenAccept(handler -> handler.accept(command)))) {
            case HANDED_OVER -> {
                System.err.println(language.messages().text(TextKey.CLI_HANDED_OVER, TITLE));
                return;
            }
            case NO_ANSWER -> {
                System.err.println(language.messages().text(TextKey.CLI_NO_ANSWER, TITLE));
                System.exit(1);
            }
            case FIRST, UNAVAILABLE -> {
                // this copy runs; UNAVAILABLE has been logged
            }
        }

        DBUS_LOG.setLevel(java.util.logging.Level.WARNING); // dbus-java reports every connection at INFO
        var configPath = commandLine.config().orElseGet(Main::defaultConfigPath);
        var configFile = new ConfigFile(configPath);
        var clock = Clock.systemUTC();
        var secretsFile = new SecretsFile(configPath.resolveSibling("secrets.json"));
        var icon = new AppIcon();

        Platform.setImplicitExit(false);
        var colorSchemeFound = new CompletableFuture<ObservableValue<ColorScheme>>();
        var windowSchemeFound = new CompletableFuture<ObservableValue<ColorScheme>>();
        var dashboardMade = new CompletableFuture<Dashboard>();
        Platform.startup(() -> {
            // JavaFX hands out its preferences on its own thread only
            var colorScheme = Platform.getPreferences().colorSchemeProperty();
            colorSchemeFound.complete(colorScheme);
            // the windows follow the owner's appearance; the tray icon follows the panel, so the system's scheme
            var windowScheme = Appearance.scheme(appearance, colorScheme);
            windowSchemeFound.complete(windowScheme);
            dashboardMade.complete(new Dashboard(icon, windowScheme, Platform.getPreferences().reducedMotionProperty(),
                    clock, taskbarHiding(), language));
        });
        var dashboard = dashboardMade.get();
        var tray = tray(colorSchemeFound.get());
        var trayTexts = new TrayTexts(tray, language);
        // a click on a notification does not say where the tray icon is, and the popup would close again as soon as
        // the desktop gives the focus elsewhere: the window comes to the front and stays
        var notifications = notifications(tray, icon, language);
        notifications.listen(dashboard::openWindow);
        var texts = new AnnouncementTexts(language);
        var dataFolder = DataLocation.defaultFolder(System.getProperty("os.name"), System.getenv(),
                Path.of(System.getProperty("user.home")));
        var engine = engine(configFile, secretsFile, dataFolder, clock,
                announcement -> notifications.show(texts.notification(announcement)));
        if (engine.keyStorage() == KeyStorage.FILE) {
            LOG.log(Level.WARNING, "No usable system keychain: API keys are kept in " + secretsFile.path()
                    + ", readable only by you");
        }

        ScheduledExecutorService replanning = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform()
                .name("planner").daemon().factory());
        Runnable replan = () -> {
            try {
                var outlook = engine.plan();
                dashboard.show(outlook);
                trayTexts.show(outlook);
            } catch (RuntimeException e) {
                // an uncaught exception would stop the schedule for good
                LOG.log(Level.ERROR, "planning failed", e);
            }
        };
        var settingsMade = new CompletableFuture<SettingsWindow>();
        var facts = new AboutFacts(version(), configPath, appSettingsFile.path(), dataFolder, secretsFile.path());
        var stores = new SettingsStores(engine, appSettingsFile, startAtLogin());
        // a Save may have chosen another language or appearance: every part follows at once, before the plan is made
        // again; this runs on the JavaFX thread
        Runnable saved = () -> {
            var chosen = appSettingsFile.read();
            language.choose(chosen.language());
            appearance.set(chosen.appearance());
            replanning.execute(replan);
        };
        Platform.runLater(() -> settingsMade.complete(new SettingsWindow(stores, facts, Main::openLink, saved,
                Optional.empty(), windowSchemeFound.join(), icon, language)));
        var settings = settingsMade.get();
        dashboard.onSettings(() -> settings.open(Category.SOLAR));
        dashboard.onAway(() -> settings.open(Category.AWAY));

        Runnable quit = () -> {
            notifications.close();
            tray.remove();
            instance.close();
            Platform.exit();
            System.exit(0);
        };
        commands.complete(command -> {
            switch (command) {
                case SHOW -> dashboard.openWindow();
                case QUIT -> quit.run();
            }
        });
        var actions = new Tray.TrayActions(dashboard::popUp, dashboard::openWindow,
                () -> settings.open(Category.SOLAR), () -> settings.open(Category.ABOUT), quit);
        boolean windowMode = mode == DisplayMode.WINDOW;
        // Windows shows notifications through the tray icon, so in window mode it is there for them
        boolean inTray = (!windowMode || tray instanceof AwtTray)
                && showTray(tray, actions, commandLine.background() || mode == DisplayMode.TRAY);
        if (windowMode || !inTray) {
            dashboard.quitWhenClosed(quit);
            if (!commandLine.background()) { // started hidden, it shows itself when the app is started again
                dashboard.openWindow();
            }
        }
        // only now: on Windows the first announcement needs the tray icon to show it
        dashboard.onBatteryEntered(level -> replanning.execute(() -> {
            try {
                engine.setBatteryLevel(level);
            } catch (IOException e) {
                LOG.log(Level.WARNING, "could not save the battery level", e);
            }
            replan.run();
        }));
        replanning.scheduleWithFixedDelay(replan, 0, REPLAN_MINUTES, TimeUnit.MINUTES);
        LOG.log(Level.INFO, "planning with " + configFile.path());
    }

    /**
     * At login, or when the owner asked for a tray, the tray may not be there yet, so it is tried for a while; false
     * when it did not come, and the app shows a window instead.
     */
    private static boolean showTray(Tray tray, Tray.TrayActions actions, boolean wait) throws InterruptedException {
        var deadline = Instant.now().plus(wait ? TRAY_WAIT_AT_LOGIN : Duration.ZERO);
        while (true) {
            try {
                tray.show(actions);
                return true;
            } catch (Tray.TrayUnavailable e) {
                if (!Instant.now().isBefore(deadline)) {
                    LOG.log(Level.WARNING, e.getMessage() + "; showing the dashboard as a window instead",
                            e.getCause());
                    return false;
                }
                Thread.sleep(TRAY_RETRY);
            }
        }
    }

    /** The version Maven wrote into the build; "development" when the resource is missing, as in an IDE's build. */
    private static String version() {
        try (var in = Main.class.getResourceAsStream("version.properties")) {
            if (in == null) {
                return "development";
            }
            var properties = new Properties();
            properties.load(in);
            return properties.getProperty("version", "development");
        } catch (IOException e) {
            return "development";
        }
    }

    /** In the owner's browser, away from the JavaFX thread: the browser may take a moment to answer. */
    private static void openLink(URI link) {
        Thread.ofVirtual().start(() -> {
            try {
                if (System.getProperty("os.name").startsWith("Windows")) {
                    Desktop.getDesktop().browse(link);
                } else {
                    new ProcessBuilder("xdg-open", link.toString()).inheritIO().start();
                }
            } catch (IOException | UnsupportedOperationException e) {
                LOG.log(Level.WARNING, "could not open " + link + ": " + e.getMessage());
            }
        });
    }

    private static int quitRunningApp(Messages messages) {
        try {
            var reply = singleInstance().send(InstanceCommand.QUIT);
            if (reply == Reply.ANSWERED) {
                System.out.println(messages.text(TextKey.CLI_QUIT_CONFIRMED, TITLE));
                return 0;
            }
            if (reply == Reply.SILENT) {
                System.err.println(messages.text(TextKey.CLI_QUIT_SILENT, TITLE));
                return 1;
            }
        } catch (IOException e) {
            // no private folder for the socket, so no running copy can have made one there
        }
        System.out.println(messages.text(TextKey.CLI_NOT_RUNNING, TITLE));
        return 0;
    }

    private static int switchStartAtLogin(boolean on, Messages messages) {
        var startAtLogin = startAtLogin();
        if (startAtLogin.isEmpty()) {
            System.err.println(messages.text(TextKey.CLI_START_AT_LOGIN_NEEDS_INSTALL));
            return 1;
        }
        try {
            if (on) {
                startAtLogin.get().switchOn();
                System.out.println(messages.text(TextKey.CLI_START_AT_LOGIN_ON, TITLE));
            } else {
                startAtLogin.get().switchOff();
                System.out.println(messages.text(TextKey.CLI_START_AT_LOGIN_OFF, TITLE));
            }
            return 0;
        } catch (IOException e) {
            System.err.println(messages.text(TextKey.CLI_START_AT_LOGIN_FAILED, String.valueOf(e.getMessage())));
            return 1;
        }
    }

    /** Empty when the app does not run from jpackage's launcher, such as from Maven: there is nothing to start then. */
    private static Optional<StartAtLogin> startAtLogin() {
        var launcher = System.getProperty("jpackage.app-path");
        if (launcher == null) {
            return Optional.empty();
        }
        var program = Path.of(launcher).toAbsolutePath();
        if (System.getProperty("os.name").startsWith("Windows")) {
            return Optional.of(new WindowsStartAtLogin(new RegistryRunKey(RegistryRunKey.RUN), TITLE, program));
        }
        var autostart = ConfigLocation.systemFolder(System.getProperty("os.name"), System.getenv(),
                Path.of(System.getProperty("user.home"))).resolve("autostart");
        return Optional.of(new LinuxStartAtLogin(autostart, APP_ID + ".desktop", program, TITLE));
    }

    private static SingleInstance singleInstance() {
        var folder = InstanceLocation.folder(System.getProperty("os.name"), System.getenv(),
                Path.of(System.getProperty("java.io.tmpdir")), System.getProperty("user.name"));
        return new SingleInstance(folder, user(), INSTANCE_TIMEOUT);
    }

    /** Linux: the owner of the process's own entry in /proc is the user it runs as. Empty on Windows: see SingleInstance. */
    private static Optional<UserPrincipal> user() {
        if (!System.getProperty("os.name").startsWith("Linux")) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.getOwner(Path.of("/proc/self")));
        } catch (IOException e) {
            throw new IllegalStateException("cannot tell which user this app runs as", e);
        }
    }
    private static Engine engine(ConfigFile configFile, SecretsFile secretsFile, Path dataFolder, Clock clock,
            Notifier notifier) {
        var http = new JdkHttpGet(HttpClient.newHttpClient());
        var providers = new Providers(List.of(new OpenMeteo(http), new ForecastSolar(http)));
        var apiKeys = new ApiKeys(keychain(), secretsFile);
        var cache = new ForecastCache(dataFolder.resolve("forecasts"));
        var announcer = new Announcer(new AnnouncedWindows(dataFolder.resolve("announced.json")), notifier);
        return new Engine(configFile, new ForecastService(providers, cache, apiKeys), providers, apiKeys,
                new AwayFile(dataFolder.resolve("away.json")), new BatteryFile(dataFolder.resolve("battery.json")), clock,
                announcer);
    }

    private static Keychain keychain() {
        var os = System.getProperty("os.name");
        if (os.startsWith("Windows")) {
            return new CredentialManagerKeychain(APP_ID);
        }
        if (os.startsWith("Linux")) {
            return new SecretServiceKeychain(() -> DBusConnectionBuilder.forSessionBus().withShared(false).build(), APP_ID);
        }
        return new NoKeychain();
    }

    private static Path defaultConfigPath() {
        return ConfigLocation.defaultPath(System.getProperty("os.name"), System.getenv(),
                Path.of(System.getProperty("user.home")));
    }

    private static TaskbarHiding taskbarHiding() {
        return System.getProperty("os.name").startsWith("Windows") ? TaskbarHiding.OWNER_WINDOW : TaskbarHiding.NONE;
    }

    private static Tray tray(ObservableValue<ColorScheme> colorScheme) {
        if (System.getProperty("os.name").startsWith("Windows")) {
            return new AwtTray(new StatusIcon(), new TaskbarShade(), colorScheme, TITLE);
        }
        return new StatusNotifierTray(() -> DBusConnectionBuilder.forSessionBus().withShared(false).build(),
                new StatusIcon(), runtimeFolder("tray"), TITLE);
    }

    /** Windows shows notifications as the tray icon's messages; Linux has a notification server, also without a tray. */
    private static Notifications notifications(Tray tray, AppIcon icon, LanguageChoice language) {
        if (tray instanceof AwtTray awtTray) {
            return awtTray;
        }
        return new FreedesktopNotifications(() -> DBusConnectionBuilder.forSessionBus().withShared(false).build(),
                TITLE, APP_ID, icon, runtimeFolder("notify"), () -> language.messages().text(TextKey.OPEN_DASHBOARD));
    }

    /** Linux only: a private folder of this process's own; see {@link RuntimeFolder}. */
    private static Path runtimeFolder(String purpose) {
        try {
            return RuntimeFolder.make(purpose, System.getenv(), Path.of(System.getProperty("java.io.tmpdir")),
                    user().orElseThrow());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot make a private folder for the " + purpose + " icons", e);
        }
    }
}
