package io.github.enadeag.carchargeplanner;

import io.github.enadeag.carchargeplanner.desktop.AnnouncementTexts;
import io.github.enadeag.carchargeplanner.desktop.AppIcon;
import io.github.enadeag.carchargeplanner.desktop.AppSettings;
import io.github.enadeag.carchargeplanner.desktop.AppSettingsFile;
import io.github.enadeag.carchargeplanner.desktop.Appearance;
import io.github.enadeag.carchargeplanner.desktop.Background;
import io.github.enadeag.carchargeplanner.desktop.Dashboard;
import io.github.enadeag.carchargeplanner.desktop.DashboardActions;
import io.github.enadeag.carchargeplanner.desktop.LanguageChoice;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import io.github.enadeag.carchargeplanner.desktop.Tray;
import io.github.enadeag.carchargeplanner.desktop.TrayTexts;
import io.github.enadeag.carchargeplanner.desktop.settings.AboutFacts;
import io.github.enadeag.carchargeplanner.desktop.settings.Category;
import io.github.enadeag.carchargeplanner.desktop.settings.SetUpOffer;
import io.github.enadeag.carchargeplanner.desktop.settings.SetUpWindow;
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
import io.github.enadeag.carchargeplanner.engine.secrets.Keychain;
import io.github.enadeag.carchargeplanner.engine.secrets.SecretsFile;
import io.github.enadeag.carchargeplanner.engine.store.PrivateFiles;
import io.github.enadeag.carchargeplanner.instance.InstanceCommand;
import io.github.enadeag.carchargeplanner.instance.SingleInstance;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;

/**
 * The desktop app as it runs: makes its parts in the order they need each other, shows the tray icon or the window,
 * starts the engine, and closes everything again in one order, at Quit or when the JVM shuts down.
 */
final class DesktopApp {

    private static final System.Logger LOG = System.getLogger(DesktopApp.class.getName());
    /** Held here because java.util.logging forgets a logger's level once nothing refers to the logger. */
    private static final java.util.logging.Logger DBUS_LOG = java.util.logging.Logger.getLogger("org.freedesktop.dbus");

    private final CommandLine commandLine;
    private final Host host;
    private final PlatformParts parts;
    private final SingleInstance instance;
    private final Path configPath;
    private final AppSettingsFile appSettingsFile;
    private final AppSettings appSettings;
    private final LanguageChoice language;
    private final ClosingOrder closing = new ClosingOrder();
    private final InstanceClaim claim;
    /** Runs the windows' slow work, such as reading the keychain, away from the JavaFX thread. */
    private final Background background = Background.onVirtualThreads();
    // set on the JavaFX thread only, as JavaFX's bindings on it are read there
    private final SimpleObjectProperty<Appearance> appearance;

    /** JavaFX's preferences, which it hands out on its own thread only. */
    private record Looks(ObservableValue<ColorScheme> system, ObservableValue<ColorScheme> windows,
            ObservableValue<Boolean> reducedMotion) {
    }

    /**
     * Gets what the app starts with; nothing is made yet.
     *
     * @param commandLine what the app was started with
     * @param host the system and its user
     * @param parts the system's own parts
     * @param instance the single-instance guard, not claimed yet
     * @param configPath the engine's config file
     * @param appSettingsFile the desktop app's own settings file
     * @param appSettings the app settings as the app starts; the display mode is read only now
     * @param language the language the texts are in, followed while the app runs
     */
    DesktopApp(CommandLine commandLine, Host host, PlatformParts parts, SingleInstance instance, Path configPath,
            AppSettingsFile appSettingsFile, AppSettings appSettings, LanguageChoice language) {
        this.commandLine = commandLine;
        this.host = host;
        this.parts = parts;
        this.instance = instance;
        this.configPath = configPath;
        this.appSettingsFile = appSettingsFile;
        this.appSettings = appSettings;
        this.language = language;
        this.appearance = new SimpleObjectProperty<>(appSettings.appearance());
        this.claim = new InstanceClaim(instance, language.messages(), System.err, LOG);
    }

    /**
     * Starts the app: the tray icon (or a window without a tray), the notifications, the dashboard, and the engine,
     * which plans every minute. When a copy runs already, it is asked to show its dashboard instead, and this one
     * ends.
     *
     * @throws Exception when JavaFX cannot start or the dashboard cannot be made; the app cannot run then
     */
    void run() throws Exception {
        var commands = new CompletableFuture<Consumer<InstanceCommand>>();
        if (!claim(commands)) {
            return;
        }
        closing.add("the single-instance guard", instance::close);
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().name("closing").unstarted(closing::close));
        var dataFolder = DataLocation.defaultFolder(host.os(), host.environment(), host.home());
        // only now: a second copy that hands over to this one does not write to its log file
        startLog(dataFolder);
        claim.logProblem();
        DBUS_LOG.setLevel(java.util.logging.Level.WARNING); // dbus-java reports every connection at INFO
        var http = JdkHttpGet.newClient();
        closing.add("the HTTP client", http::close);
        var icon = new AppIcon();

        Platform.setImplicitExit(false);
        Platform.startup(() -> { });
        var looks = onFx(() -> {
            var system = Platform.getPreferences().colorSchemeProperty();
            // the windows follow the owner's appearance; the tray icon follows the panel, so the system's scheme
            return new Looks(system, Appearance.scheme(appearance, system),
                    Platform.getPreferences().reducedMotionProperty());
        });
        var trayParts = parts.trayParts(looks.system(), icon, language);
        var tray = trayParts.tray();
        var notifications = trayParts.notifications();
        closing.add("the tray icon", tray::remove);
        closing.add("the notifications", notifications::close);
        var trayTexts = new TrayTexts(tray, language);

        var secretsFile = new SecretsFile(SecretsLocation.moveFromOldPlace(host.os(), configPath, dataFolder));
        // folders made before the app made its folders private; a folder named with --config= stays as it is
        PrivateFiles.tighten(appSettingsFile.path().toAbsolutePath().getParent());
        var texts = new AnnouncementTexts(language);
        var keychain = parts.keychain();
        closing.add("the keychain", keychain::close);
        var engine = engine(http, keychain, secretsFile, dataFolder,
                announcement -> notifications.show(texts.notification(announcement)));
        closing.add("the engine", engine::close);
        var saving = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("dashboard-changes").factory());
        closing.add("the dashboard's saving", saving::close);
        var changes = new DashboardChanges(engine, new Background(saving, Platform::runLater));

        var dashboardMade = new CompletableFuture<Dashboard>();
        var stores = new SettingsStores(engine, appSettingsFile, parts.startAtLogin());
        // a Save may have chosen another language or appearance: every part follows at once; the engine plans again
        // by itself after a save of its config. This runs on the JavaFX thread.
        Consumer<AppSettings> saved = chosen -> {
            language.choose(chosen.language());
            appearance.set(chosen.appearance());
        };
        // after Finish the app plans with what set-up saved, in the language and appearance it chose; the dashboard
        // is made after set-up, as it opens set-up
        Consumer<AppSettings> setUpFinished = chosen -> {
            saved.accept(chosen);
            dashboardMade.join().openWindow();
        };
        var setUp = onFx(() -> new SetUpWindow(stores, secretsFile.path(), this::openLink, setUpFinished,
                ZoneId.systemDefault(), new SetUpWindow.Looks(looks.system(), appearance, looks.reducedMotion()), icon,
                language, background));
        var facts = new AboutFacts(version(), configPath, appSettingsFile.path(), dataFolder,
                AppLog.file(dataFolder), secretsFile.path());
        var settings = onFx(() -> new SettingsWindow(stores, facts, this::openLink, saved, Optional.of(setUp::open),
                looks.windows(), icon, language, background));
        var setUpOffer = new SetUpOffer(setUp::open, settings::open, !commandLine.background());
        var actions = new DashboardActions(changes::enterBatteryLevel, changes::addAwayPeriod,
                changes::removeAwayPeriod, () -> setUpOffer.openSettings(Category.AWAY),
                () -> setUpOffer.openSettings(Category.SOLAR), setUp::open, this::quit);
        var dashboard = onFx(() -> new Dashboard(icon, looks.windows(), looks.reducedMotion(), Clock.systemUTC(),
                parts.taskbarHiding(), language, actions));
        dashboardMade.complete(dashboard);
        // a click on a notification does not say where the tray icon is, and the popup would close again as soon as
        // the desktop gives the focus elsewhere: the window comes to the front and stays
        notifications.listen(dashboard::openWindow);
        commands.complete(command -> {
            switch (command) {
                case SHOW -> dashboard.openWindow();
                case QUIT -> quit();
            }
        });

        showTrayOrWindow(new StartupDecision(appSettings.mode(), commandLine.background(), trayParts.needTheTray()),
                tray, dashboard, new Tray.TrayActions(dashboard::popUp, dashboard::openWindow,
                        () -> setUpOffer.openSettings(Category.SOLAR), () -> settings.open(Category.ABOUT),
                        this::quit));
        // only now: on Windows the first announcement needs the tray icon to show it
        engine.start(outlook -> {
            dashboard.show(outlook, engine.awayPeriods());
            trayTexts.show(outlook);
            setUpOffer.show(outlook);
        });
        LOG.log(Level.INFO, "planning with " + configPath);
    }

    /**
     * Sends the log to the file in the data folder from now on, and to the terminal when there is one; the log is
     * closed last. A data folder made before the app made its folders private is made private now.
     */
    private void startLog(Path dataFolder) {
        var log = new AppLog(java.util.logging.Logger.getLogger(""),
                java.util.logging.Logger.getLogger(Main.class.getPackageName()));
        log.start(dataFolder, host.environment(), host.terminal(), ZoneId.systemDefault());
        closing.add("the log file", log::close);
        LOG.log(Level.INFO, AppName.TITLE + " " + version() + " starts");
        PrivateFiles.tighten(dataFolder);
    }

    /** Closes every part, in order, then ends the app. */
    private void quit() {
        closing.close();
        Platform.exit();
        System.exit(ExitCode.SUCCESS.code());
    }

    /**
     * Becomes the running copy; false when another copy runs and this one ends. Commands that come in while the app
     * is still starting wait until it can carry them out.
     */
    private boolean claim(CompletableFuture<Consumer<InstanceCommand>> commands) {
        var end = claim.claim(command -> commands.thenAccept(handler -> handler.accept(command)));
        if (end.isEmpty()) {
            return true;
        }
        if (end.get() != ExitCode.SUCCESS) {
            System.exit(end.get().code());
        }
        return false;
    }

    private void showTrayOrWindow(StartupDecision decision, Tray tray, Dashboard dashboard, Tray.TrayActions actions)
            throws InterruptedException {
        boolean trayShown = decision.triesTheTray()
                && new TrayStarter(Clock.systemUTC(), Thread::sleep).show(tray, actions, decision.trayWait());
        switch (decision.dashboard(trayShown)) {
            case POPUP -> {
                // the tray icon opens it
            }
            case WINDOW -> {
                dashboard.quitWhenClosed();
                dashboard.openWindow();
            }
            case HIDDEN_WINDOW -> dashboard.quitWhenClosed(); // it shows itself when the app is started again
        }
    }

    private Engine engine(HttpClient http, Keychain keychain, SecretsFile secretsFile, Path dataFolder,
            Notifier notifier) {
        var get = new JdkHttpGet(http);
        var providers = new Providers(List.of(new OpenMeteo(get), new ForecastSolar(get)));
        var apiKeys = new ApiKeys(keychain, secretsFile, Clock.systemUTC());
        var cache = new ForecastCache(dataFolder.resolve("forecasts"));
        var announcer = new Announcer(new AnnouncedWindows(dataFolder.resolve("announced.json")), notifier);
        return new Engine(new ConfigFile(configPath), new ForecastService(providers, cache, apiKeys), providers,
                apiKeys, new AwayFile(dataFolder.resolve("away.json")),
                new BatteryFile(dataFolder.resolve("battery.json")), Clock.systemUTC(), announcer,
                Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("planner").daemon().factory()));
    }

    /** In the owner's browser, away from the JavaFX thread: the browser may take a moment to answer. */
    private void openLink(URI link) {
        background.run("opening a link", () -> {
            try {
                parts.browse(link);
            } catch (IOException | UnsupportedOperationException e) {
                LOG.log(Level.WARNING, "could not open " + link + ": " + e.getMessage());
            }
            return null;
        }, done -> { }, failed -> { });
    }

    /** The version Maven wrote into the build; "development" when the resource is missing, as in an IDE's build. */
    private static String version() {
        try (var in = DesktopApp.class.getResourceAsStream("version.properties")) {
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

    /** Makes something on the JavaFX thread and waits for it; what it throws is thrown here. */
    private static <T> T onFx(Supplier<T> make) throws InterruptedException, ExecutionException {
        var made = new CompletableFuture<T>();
        Platform.runLater(() -> {
            try {
                made.complete(make.get());
            } catch (RuntimeException e) {
                made.completeExceptionally(e);
            }
        });
        return made.get();
    }
}
