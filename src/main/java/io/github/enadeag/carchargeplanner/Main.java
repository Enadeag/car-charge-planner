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
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import io.github.enadeag.carchargeplanner.desktop.Tray;
import io.github.enadeag.carchargeplanner.desktop.TrayTexts;
import io.github.enadeag.carchargeplanner.desktop.Units;
import io.github.enadeag.carchargeplanner.desktop.settings.AboutFacts;
import io.github.enadeag.carchargeplanner.desktop.settings.Category;
import io.github.enadeag.carchargeplanner.desktop.settings.SettingsStores;
import io.github.enadeag.carchargeplanner.desktop.settings.SetUpOffer;
import io.github.enadeag.carchargeplanner.desktop.settings.SetUpWindow;
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
import io.github.enadeag.carchargeplanner.engine.secrets.SecretsFile;
import io.github.enadeag.carchargeplanner.instance.InstanceCommand;
import io.github.enadeag.carchargeplanner.instance.SingleInstance;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;

/**
 * Starts the desktop app: the composition root, where every part is made and handed what it needs.
 *
 * <p>Usage: {@code car-charge-planner [run | quit | start-at-login on | start-at-login off | install-launcher
 * | uninstall-launcher] [--config=path/to/engine.json] [--background]}.
 */
public final class Main {

    private static final System.Logger LOG = System.getLogger(Main.class.getName());
    /** Held here because java.util.logging forgets a logger's level once nothing refers to the logger. */
    private static final java.util.logging.Logger DBUS_LOG = java.util.logging.Logger.getLogger("org.freedesktop.dbus");
    /** The desktop app's own settings, beside the engine's config file in the app's folder. */
    private static final String APP_SETTINGS_FILE = "app.json";
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
        language.choose(appSettings.language());
        var instance = new SingleInstance(InstanceLocation.folder(host.os(), host.environment(), host.temporary(),
                host.userName()), host.user(), INSTANCE_TIMEOUT);
        if (commandLine.command() == CommandLine.Command.RUN) {
            run(commandLine, host, parts, instance, defaultConfig, appSettingsFile, appSettings, language);
            return;
        }
        var outcome = Commands.of(host, parts, instance, language.messages()).run(commandLine.command());
        outcome.print(System.out, System.err);
        System.exit(outcome.exit().code());
    }

    /**
     * Starts the app: the dashboard, the tray icon (or a window without a tray), the notifications, and the engine,
     * which plans every minute. When a copy runs already, it is asked to show its dashboard instead, and this one exits.
     */
    private static void run(CommandLine commandLine, Host host, PlatformParts parts, SingleInstance instance,
            Path defaultConfig, AppSettingsFile appSettingsFile, AppSettings appSettings, LanguageChoice language)
            throws Exception {
        var mode = appSettings.mode();
        // set on the JavaFX thread only, as JavaFX's bindings on it are read there
        var appearance = new SimpleObjectProperty<>(appSettings.appearance());
        var commands = new CompletableFuture<Consumer<InstanceCommand>>();
        // commands that come in while the app is still starting wait until it can carry them out
        switch (instance.claim(command -> commands.thenAccept(handler -> handler.accept(command)))) {
            case HANDED_OVER -> {
                System.err.println(language.messages().text(TextKey.CLI_HANDED_OVER, AppName.TITLE));
                return;
            }
            case NO_ANSWER -> {
                System.err.println(language.messages().text(TextKey.CLI_NO_ANSWER, AppName.TITLE));
                System.exit(ExitCode.FAILURE.code());
            }
            case FIRST, UNAVAILABLE -> {
                // this copy runs; UNAVAILABLE has been logged
            }
        }

        DBUS_LOG.setLevel(java.util.logging.Level.WARNING); // dbus-java reports every connection at INFO
        var configPath = commandLine.config().orElse(defaultConfig);
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
                    clock, parts.taskbarHiding(), language));
        });
        var dashboard = dashboardMade.get();
        var trayParts = parts.trayParts(colorSchemeFound.get(), icon, language);
        var tray = trayParts.tray();
        var trayTexts = new TrayTexts(tray, language);
        // a click on a notification does not say where the tray icon is, and the popup would close again as soon as
        // the desktop gives the focus elsewhere: the window comes to the front and stays
        var notifications = trayParts.notifications();
        notifications.listen(dashboard::openWindow);
        var texts = new AnnouncementTexts(language);
        var dataFolder = DataLocation.defaultFolder(host.os(), host.environment(), host.home());
        var engine = engine(parts.keychain(), configFile, secretsFile, dataFolder, clock,
                announcement -> notifications.show(texts.notification(announcement)),
                Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("planner").daemon().factory()));
        if (engine.keyStorage() == KeyStorage.FILE) {
            LOG.log(Level.WARNING, "No usable system keychain: API keys are kept in " + secretsFile.path()
                    + ", readable only by you");
        }

        var setUpMade = new CompletableFuture<SetUpWindow>();
        var settingsMade = new CompletableFuture<SettingsWindow>();
        // the windows are made below, before the first plan
        var setUpOffer = new SetUpOffer(() -> setUpMade.join().open(), category -> settingsMade.join().open(category),
                !commandLine.background());
        var facts = new AboutFacts(version(), configPath, appSettingsFile.path(), dataFolder, secretsFile.path());
        var stores = new SettingsStores(engine, appSettingsFile, parts.startAtLogin());
        // a Save may have chosen another language or appearance: every part follows at once; the engine plans again
        // by itself after a save of its config. This runs on the JavaFX thread.
        Runnable saved = () -> {
            var chosen = appSettingsFile.read();
            language.choose(chosen.language());
            appearance.set(chosen.appearance());
        };
        // after Finish the app plans with what set-up saved, in the language and appearance it chose
        Runnable setUpFinished = () -> {
            saved.run();
            dashboard.openWindow();
        };
        Platform.runLater(() -> {
            var looks = new SetUpWindow.Looks(colorSchemeFound.join(), appearance,
                    Platform.getPreferences().reducedMotionProperty());
            setUpMade.complete(new SetUpWindow(stores, secretsFile.path(), link -> openLink(parts, link), setUpFinished,
                    ZoneId.systemDefault(), looks, icon, language));
        });
        var setUp = setUpMade.get();
        Platform.runLater(() -> settingsMade.complete(new SettingsWindow(stores, facts, link -> openLink(parts, link), saved,
                Optional.of(setUp::open), windowSchemeFound.join(), icon, language)));
        var settings = settingsMade.get();
        dashboard.onSettings(() -> setUpOffer.openSettings(Category.SOLAR));
        dashboard.onAway(() -> setUpOffer.openSettings(Category.AWAY));
        dashboard.onSetUp(setUp::open);

        Runnable quit = () -> {
            engine.close();
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
                () -> setUpOffer.openSettings(Category.SOLAR), () -> settings.open(Category.ABOUT), quit);
        boolean windowMode = mode == DisplayMode.WINDOW;
        // Windows shows notifications through the tray icon, so in window mode it is there for them
        boolean inTray = (!windowMode || trayParts.needTheTray())
                && showTray(tray, actions, commandLine.background() || mode == DisplayMode.TRAY);
        if (windowMode || !inTray) {
            dashboard.quitWhenClosed(quit);
            if (!commandLine.background()) { // started hidden, it shows itself when the app is started again
                dashboard.openWindow();
            }
        }
        var changes = new DashboardChanges(engine,
                Executors.newSingleThreadExecutor(Thread.ofVirtual().name("dashboard-changes").factory()));
        dashboard.onBatteryEntered(changes::enterBatteryLevel);
        dashboard.onAwayChange(changes::addAwayPeriod, changes::removeAwayPeriod);
        // only now: on Windows the first announcement needs the tray icon to show it
        engine.start(outlook -> {
            dashboard.show(outlook, engine.awayPeriods());
            trayTexts.show(outlook);
            setUpOffer.show(outlook);
        });
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
    private static void openLink(PlatformParts parts, URI link) {
        Thread.ofVirtual().start(() -> {
            try {
                parts.browse(link);
            } catch (IOException | UnsupportedOperationException e) {
                LOG.log(Level.WARNING, "could not open " + link + ": " + e.getMessage());
            }
        });
    }

    private static Engine engine(Keychain keychain, ConfigFile configFile, SecretsFile secretsFile, Path dataFolder, Clock clock,
            Notifier notifier, ScheduledExecutorService planner) {
        var http = new JdkHttpGet(HttpClient.newHttpClient());
        var providers = new Providers(List.of(new OpenMeteo(http), new ForecastSolar(http)));
        var apiKeys = new ApiKeys(keychain, secretsFile);
        var cache = new ForecastCache(dataFolder.resolve("forecasts"));
        var announcer = new Announcer(new AnnouncedWindows(dataFolder.resolve("announced.json")), notifier);
        return new Engine(configFile, new ForecastService(providers, cache, apiKeys), providers, apiKeys,
                new AwayFile(dataFolder.resolve("away.json")), new BatteryFile(dataFolder.resolve("battery.json")), clock,
                announcer, planner);
    }
}
