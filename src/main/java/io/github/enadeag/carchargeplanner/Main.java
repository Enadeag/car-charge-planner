package io.github.enadeag.carchargeplanner;

import io.github.enadeag.carchargeplanner.desktop.AppIcon;
import io.github.enadeag.carchargeplanner.desktop.Dashboard;
import io.github.enadeag.carchargeplanner.desktop.Tray;
import io.github.enadeag.carchargeplanner.engine.Engine;
import io.github.enadeag.carchargeplanner.engine.config.ConfigFile;
import io.github.enadeag.carchargeplanner.engine.forecast.JdkHttpGet;
import io.github.enadeag.carchargeplanner.engine.forecast.OpenMeteo;
import io.github.enadeag.carchargeplanner.platform.linux.StatusNotifierTray;
import io.github.enadeag.carchargeplanner.platform.windows.AwtTray;
import java.lang.System.Logger.Level;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
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
    /** Open-Meteo updates its forecast about once an hour. */
    private static final long REPLAN_MINUTES = 60;

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        DBUS_LOG.setLevel(java.util.logging.Level.WARNING); // dbus-java reports every connection at INFO
        var configFile = new ConfigFile(configPath(args));
        var engine = new Engine(configFile, new OpenMeteo(new JdkHttpGet(HttpClient.newHttpClient())), Clock.systemUTC());
        var icon = new AppIcon();

        Platform.setImplicitExit(false);
        var dashboardMade = new CompletableFuture<Dashboard>();
        Platform.startup(() -> dashboardMade.complete(
                new Dashboard(icon, Platform.getPreferences().colorSchemeProperty(), FORECAST_SOURCE)));
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
            tray.show(new Tray.TrayActions(dashboard::open, quit));
        } catch (Tray.TrayUnavailable e) {
            LOG.log(Level.WARNING, e.getMessage() + "; showing the dashboard as a window instead", e.getCause());
            dashboard.quitWhenClosed(quit);
            dashboard.open();
        }
        LOG.log(Level.INFO, "planning with " + configFile.path());
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
