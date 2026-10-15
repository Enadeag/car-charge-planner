package io.github.enadeag.carchargeplanner;

import io.github.enadeag.carchargeplanner.desktop.AppIcon;
import io.github.enadeag.carchargeplanner.desktop.Dashboard;
import io.github.enadeag.carchargeplanner.desktop.Tray;
import io.github.enadeag.carchargeplanner.engine.Engine;
import io.github.enadeag.carchargeplanner.engine.config.ConfigFile;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastCache;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastService;
import io.github.enadeag.carchargeplanner.engine.forecast.JdkHttpGet;
import io.github.enadeag.carchargeplanner.engine.forecast.Providers;
import io.github.enadeag.carchargeplanner.engine.forecast.providers.ForecastSolar;
import io.github.enadeag.carchargeplanner.engine.forecast.providers.OpenMeteo;
import io.github.enadeag.carchargeplanner.engine.secrets.ApiKeys;
import io.github.enadeag.carchargeplanner.engine.secrets.KeyStorage;
import io.github.enadeag.carchargeplanner.engine.secrets.Keychain;
import io.github.enadeag.carchargeplanner.engine.secrets.NoKeychain;
import io.github.enadeag.carchargeplanner.engine.secrets.SecretsFile;
import io.github.enadeag.carchargeplanner.platform.linux.SecretServiceKeychain;
import io.github.enadeag.carchargeplanner.platform.linux.StatusNotifierTray;
import io.github.enadeag.carchargeplanner.platform.windows.AwtTray;
import io.github.enadeag.carchargeplanner.platform.windows.CredentialManagerKeychain;
import java.lang.System.Logger.Level;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;

/**
 * Starts the desktop app: the composition root, where every part is made and handed what it needs.
 *
 * <p>Usage: {@code car-charge-planner [--config=path/to/engine.json]}.
 */
public final class Main {

    private static final System.Logger LOG = System.getLogger(Main.class.getName());
    /** Held here because java.util.logging forgets a logger's level once nothing refers to the logger. */
    private static final java.util.logging.Logger DBUS_LOG = java.util.logging.Logger.getLogger("org.freedesktop.dbus");
    private static final String TITLE = "Car charge planner";
    private static final String FORECAST_SOURCE = "Open-Meteo";
    /** The app's name where other programs see it, such as on the keychain's entries. */
    private static final String APP_ID = "car-charge-planner";
    /**
     * The forecast cache answers in between, so this is how soon a provider that could not be reached is tried again,
     * not how often it is asked.
     */
    private static final long REPLAN_MINUTES = 15;

    private Main() {
    }

    /**
     * Starts the app: the engine, the dashboard, planning every hour, and the tray icon, or a window without a tray.
     *
     * @param args {@code --config=path/to/engine.json} to read the system from that file instead of the usual place;
     *     other arguments are ignored
     * @throws Exception when JavaFX cannot start or the dashboard cannot be made; the app cannot run then
     */
    public static void main(String[] args) throws Exception {
        DBUS_LOG.setLevel(java.util.logging.Level.WARNING); // dbus-java reports every connection at INFO
        var configPath = configPath(args);
        var configFile = new ConfigFile(configPath);
        var clock = Clock.systemUTC();
        var secretsFile = new SecretsFile(configPath.resolveSibling("secrets.json"));
        var engine = engine(configFile, secretsFile, clock);
        if (engine.keyStorage() == KeyStorage.FILE) {
            LOG.log(Level.WARNING, "No usable system keychain: API keys are kept in " + secretsFile.path()
                    + ", readable only by you");
        }
        var icon = new AppIcon();

        Platform.setImplicitExit(false);
        var dashboardMade = new CompletableFuture<Dashboard>();
        Platform.startup(() -> dashboardMade.complete(
                new Dashboard(icon, Platform.getPreferences().colorSchemeProperty(), FORECAST_SOURCE, clock)));
        var dashboard = dashboardMade.get();

        ScheduledExecutorService replanning = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform()
                .name("planner").daemon().factory());
        replanning.scheduleWithFixedDelay(() -> {
            try {
                dashboard.show(engine.plan());
            } catch (RuntimeException e) {
                // an uncaught exception would stop the schedule for good
                LOG.log(Level.ERROR, "planning failed", e);
            }
        }, 0, REPLAN_MINUTES, TimeUnit.MINUTES);

        var tray = tray(icon);
        Runnable quit = () -> {
            tray.remove();
            Platform.exit();
            System.exit(0);
        };
        try {
            tray.show(new Tray.TrayActions(dashboard::popUp, dashboard::openWindow, quit));
        } catch (Tray.TrayUnavailable e) {
            LOG.log(Level.WARNING, e.getMessage() + "; showing the dashboard as a window instead", e.getCause());
            dashboard.quitWhenClosed(quit);
            dashboard.openWindow();
        }
        LOG.log(Level.INFO, "planning with " + configFile.path());
    }

    private static Engine engine(ConfigFile configFile, SecretsFile secretsFile, Clock clock) {
        var http = new JdkHttpGet(HttpClient.newHttpClient());
        var providers = new Providers(List.of(new OpenMeteo(http), new ForecastSolar(http)));
        var apiKeys = new ApiKeys(keychain(), secretsFile);
        var cache = new ForecastCache(DataLocation.defaultFolder(System.getProperty("os.name"), System.getenv(),
                Path.of(System.getProperty("user.home"))).resolve("forecasts"));
        return new Engine(configFile, new ForecastService(providers, cache, apiKeys), providers, apiKeys, clock);
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

    private static Path configPath(String[] args) {
        for (var arg : args) {
            if (arg.startsWith("--config=")) {
                return Path.of(arg.substring("--config=".length()));
            }
        }
        return ConfigLocation.defaultPath(System.getProperty("os.name"), System.getenv(),
                Path.of(System.getProperty("user.home")));
    }

    private static Tray tray(AppIcon icon) {
        if (System.getProperty("os.name").startsWith("Windows")) {
            return new AwtTray(icon, TITLE);
        }
        return new StatusNotifierTray(() -> DBusConnectionBuilder.forSessionBus().withShared(false).build(), icon, TITLE);
    }
}
