package nl.enadeag.carchargeplanner.start;

import nl.enadeag.carchargeplanner.cli.CommandLine;
import nl.enadeag.carchargeplanner.cli.ExitCode;
import nl.enadeag.carchargeplanner.desktop.app.AppSettings;
import nl.enadeag.carchargeplanner.desktop.app.AppSettingsFile;
import nl.enadeag.carchargeplanner.desktop.app.Appearance;
import nl.enadeag.carchargeplanner.desktop.app.Background;
import nl.enadeag.carchargeplanner.desktop.dashboard.Dashboard;
import nl.enadeag.carchargeplanner.desktop.dashboard.DashboardActions;
import nl.enadeag.carchargeplanner.desktop.dashboard.tray.TrayPresenter;
import nl.enadeag.carchargeplanner.desktop.settings.SettingsWindow;
import nl.enadeag.carchargeplanner.desktop.settings.about.AboutFacts;
import nl.enadeag.carchargeplanner.desktop.settings.page.Category;
import nl.enadeag.carchargeplanner.desktop.settings.setup.SetUpOffer;
import nl.enadeag.carchargeplanner.desktop.settings.setup.SetUpWindow;
import nl.enadeag.carchargeplanner.desktop.settings.store.SettingsStores;
import nl.enadeag.carchargeplanner.desktop.system.AppIcon;
import nl.enadeag.carchargeplanner.desktop.system.Notifications;
import nl.enadeag.carchargeplanner.desktop.system.Tray;
import nl.enadeag.carchargeplanner.desktop.text.AnnouncementTexts;
import nl.enadeag.carchargeplanner.desktop.text.LanguageChoice;
import nl.enadeag.carchargeplanner.engine.Engine;
import nl.enadeag.carchargeplanner.engine.away.AwayFile;
import nl.enadeag.carchargeplanner.engine.battery.BatteryFile;
import nl.enadeag.carchargeplanner.engine.config.ConfigFile;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastCache;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastService;
import nl.enadeag.carchargeplanner.engine.forecast.JdkHttpGet;
import nl.enadeag.carchargeplanner.engine.forecast.Providers;
import nl.enadeag.carchargeplanner.engine.forecast.providers.ForecastSolar;
import nl.enadeag.carchargeplanner.engine.forecast.providers.OpenMeteo;
import nl.enadeag.carchargeplanner.engine.notify.AnnouncedWindows;
import nl.enadeag.carchargeplanner.engine.notify.Announcer;
import nl.enadeag.carchargeplanner.engine.notify.Notifier;
import nl.enadeag.carchargeplanner.engine.secrets.ApiKeys;
import nl.enadeag.carchargeplanner.engine.secrets.Keychain;
import nl.enadeag.carchargeplanner.engine.secrets.SecretsFile;
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles;
import nl.enadeag.carchargeplanner.host.AppName;
import nl.enadeag.carchargeplanner.host.DataLocation;
import nl.enadeag.carchargeplanner.host.Host;
import nl.enadeag.carchargeplanner.host.SecretsLocation;
import nl.enadeag.carchargeplanner.instance.InstanceCommand;
import nl.enadeag.carchargeplanner.instance.SingleInstance;
import nl.enadeag.carchargeplanner.logging.AppLog;
import nl.enadeag.carchargeplanner.platforms.PlatformParts;
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
public final class DesktopApp {

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
    private final ClaimReport claimReport;
    /** Runs the windows' slow work, such as reading the keychain, away from the JavaFX thread. */
    private final Background background =
            new Background(task -> Thread.ofVirtual().name("background").start(task), Platform::runLater);
    // set on the JavaFX thread only, as JavaFX's bindings on it are read there
    private final SimpleObjectProperty<Appearance> appearance;

    /** The windows the app makes once, and what opens set-up or Settings for the owner. */
    private record Windows(Dashboard dashboard, SettingsWindow settings, SetUpOffer setUpOffer) {
    }

    /** JavaFX's preferences, which it hands out on its own thread only. */
    private record FxPreferences(ObservableValue<ColorScheme> system, ObservableValue<ColorScheme> windows,
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
    public DesktopApp(CommandLine commandLine, Host host, PlatformParts parts, SingleInstance instance, Path configPath,
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
        this.claimReport = new ClaimReport(instance, language.messages(), System.err, LOG);
    }

    /**
     * Starts the app: the tray icon (or a window without a tray), the notifications, the dashboard, and the engine,
     * which plans every minute. When a copy runs already, it is asked to show its dashboard instead, and this one
     * ends.
     *
     * @throws Exception when JavaFX cannot start or the dashboard cannot be made; the app cannot run then
     */
    public void run() throws Exception {
        var commands = new CompletableFuture<Consumer<InstanceCommand>>();
        var end = claim(commands);
        if (end.isPresent()) {
            if (end.get() != ExitCode.SUCCESS) {
                System.exit(end.get().code());
            }
            return;
        }
        closing.add("the single-instance guard", instance::close);
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().name("closing").unstarted(closing::close));
        var dataFolder = DataLocation.defaultFolder(host.os(), host.environment(), host.home());
        // only now: a second copy that hands over to this one does not write to its log file
        startLog(dataFolder);
        // a data folder made before the app made its folders private is made private now
        PrivateFiles.tighten(dataFolder);
        claimReport.logProblem();
        DBUS_LOG.setLevel(java.util.logging.Level.WARNING); // dbus-java reports every connection at INFO
        var http = JdkHttpGet.newClient();
        closing.add("the HTTP client", http::close);
        var icon = new AppIcon();

        Platform.setImplicitExit(false);
        Platform.startup(() -> { });
        var preferences = makeOnFx(() -> {
            var system = Platform.getPreferences().colorSchemeProperty();
            // the windows follow the owner's appearance; the tray icon follows the panel, so the system's scheme
            return new FxPreferences(system, Appearance.scheme(appearance, system),
                    Platform.getPreferences().reducedMotionProperty());
        });
        var trayParts = parts.trayParts(preferences.system(), icon, language);
        var tray = trayParts.tray();
        var notifications = trayParts.notifications();
        closing.add("the tray icon", tray::remove);
        closing.add("the notifications", notifications::close);
        var trayPresenter = new TrayPresenter(tray, language);

        var secretsFile = new SecretsFile(SecretsLocation.moveFromOldPlace(host.os(), configPath, dataFolder));
        // folders made before the app made its folders private; a folder named with --config= stays as it is
        PrivateFiles.tighten(appSettingsFile.path().toAbsolutePath().getParent());
        var texts = new AnnouncementTexts(language);
        var keychain = parts.keychain();
        closing.add("the keychain", keychain::close);
        var engine = engine(http, keychain, secretsFile, dataFolder,
                announcement -> notifications.send(texts.notification(announcement)));
        closing.add("the engine", engine::stop);
        var saving = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("dashboard-changes").factory());
        closing.add("the dashboard's saving", saving::close);
        var changes = new DashboardChanges(engine, new Background(saving, Platform::runLater));

        var windows = makeWindows(engine, changes, secretsFile.path(), dataFolder, icon, preferences);
        var dashboard = windows.dashboard();
        // a click on a notification does not say where the tray icon is, and the popup would close again as soon as
        // the desktop gives the focus elsewhere: the window comes to the front and stays
        notifications.onClick(dashboard::openWindow);
        commands.complete(commandHandler(dashboard, notifications, texts));

        var decision = new StartupDecision(appSettings.mode(), commandLine.atLogin(),
                trayParts.notificationsNeedTheTray());
        showTrayOrWindow(decision, tray, dashboard, new Tray.Actions(dashboard::trayIconClicked, dashboard::openWindow,
                        () -> windows.setUpOffer().openSettings(Category.SOLAR),
                        () -> windows.settings().open(Category.ABOUT), this::quit));
        // only now: on Windows the first announcement needs the tray icon to show it
        engine.start(outlook -> {
            dashboard.show(outlook, engine.awayPeriods());
            trayPresenter.show(outlook);
            windows.setUpOffer().follow(outlook);
        });
        LOG.log(Level.INFO, "planning with " + configPath);
    }

    /**
     * Makes set-up, Settings and the dashboard on the JavaFX thread, each able to open the others. None is shown yet.
     */
    private Windows makeWindows(Engine engine, DashboardChanges changes, Path secretsPath, Path dataFolder,
            AppIcon icon, FxPreferences preferences) throws InterruptedException, ExecutionException {
        var dashboardMade = new CompletableFuture<Dashboard>();
        var stores = new SettingsStores(engine, appSettingsFile, parts.startAtLogin());
        // a Save may have chosen another language or appearance: every part follows at once; the engine plans again
        // by itself after a save of its config. This runs on the JavaFX thread.
        Consumer<AppSettings> saved = chosen -> {
            chosen.language().ifPresentOrElse(language::choose, language::followSystem);
            appearance.set(chosen.appearance());
        };
        // after Finish the app plans with what set-up saved, in the language and appearance it chose; the dashboard
        // is made after set-up, as it opens set-up
        Consumer<AppSettings> setUpFinished = chosen -> {
            saved.accept(chosen);
            dashboardMade.join().openWindow();
        };
        var setUp = makeOnFx(() -> new SetUpWindow(stores, secretsPath, this::openLink, setUpFinished,
                ZoneId.systemDefault(), new SetUpWindow.Looks(preferences.system(), appearance,
                preferences.reducedMotion()), icon, language, background));
        var facts = new AboutFacts(version(), configPath, appSettingsFile.path(), dataFolder,
                AppLog.file(dataFolder), secretsPath);
        var settings = makeOnFx(() -> new SettingsWindow(stores, facts, this::openLink, saved, setUp::open,
                preferences.windows(), icon, language, background));
        var setUpOffer = new SetUpOffer(setUp::open, settings::open, !commandLine.atLogin());
        var actions = new DashboardActions(changes::setBatteryLevel, changes::addAwayPeriod,
                changes::removeAwayPeriod, () -> setUpOffer.openSettings(Category.AWAY),
                () -> setUpOffer.openSettings(Category.SOLAR), setUp::open, this::quit);
        var dashboard = makeOnFx(() -> new Dashboard(icon, preferences.windows(), preferences.reducedMotion(),
                Clock.systemUTC(), parts.taskbarHiding(), language, actions));
        dashboardMade.complete(dashboard);
        return new Windows(dashboard, settings, setUpOffer);
    }

    /** What a command of a later start, or of the command line, does in this running app. */
    private Consumer<InstanceCommand> commandHandler(Dashboard dashboard, Notifications notifications,
            AnnouncementTexts texts) {
        return command -> {
            switch (command) {
                case SHOW -> dashboard.openWindow();
                case QUIT -> quit();
                // not through the engine's announcer: nothing is recorded as announced and the plan stays as it is
                case TEST_NOTIFICATION -> notifications.send(texts.testNotification(AppName.TITLE));
            }
        };
    }

    /**
     * Sends the log to the file in the data folder from now on, and to the terminal when there is one; the log closes
     * after every part made later, so their closing is logged.
     */
    private void startLog(Path dataFolder) {
        var log = new AppLog(java.util.logging.Logger.getLogger(""),
                java.util.logging.Logger.getLogger(AppLog.APP_LOGGER_NAME));
        log.start(dataFolder, host.environment(), host.terminal(), ZoneId.systemDefault());
        closing.add("the log file", log::close);
        LOG.log(Level.INFO, AppName.TITLE + " " + version() + " starts");
    }

    /** Closes every part, in order, then ends the app. */
    private void quit() {
        closing.close();
        Platform.exit();
        System.exit(ExitCode.SUCCESS.code());
    }

    /**
     * Becomes the running copy, or not. Commands that come in while the app is still starting wait until it can carry
     * them out.
     *
     * @return empty when this copy runs; else the exit code this one ends with
     */
    private Optional<ExitCode> claim(CompletableFuture<Consumer<InstanceCommand>> commands) {
        return claimReport.claim(command -> commands.thenAccept(handler -> handler.accept(command)));
    }

    private void showTrayOrWindow(StartupDecision decision, Tray tray, Dashboard dashboard, Tray.Actions actions)
            throws InterruptedException {
        boolean trayShown = decision.triesTheTray()
                && new TrayStarter(Clock.systemUTC(), Thread::sleep).tryAdd(tray, actions, decision.trayWait());
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
        } catch (IOException _) {
            return "development";
        }
    }

    /** Makes something on the JavaFX thread and waits for it; what it throws is thrown here. */
    private static <T> T makeOnFx(Supplier<T> make) throws InterruptedException, ExecutionException {
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
