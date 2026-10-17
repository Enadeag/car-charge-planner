package io.github.enadeag.carchargeplanner;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.desktop.Language;
import io.github.enadeag.carchargeplanner.desktop.Messages;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import io.github.enadeag.carchargeplanner.platform.linux.IsolatedSessionBus;
import io.github.enadeag.carchargeplanner.platform.linux.StatusNotifierWatcher;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The app as it is started: in a JVM of its own, with the example config, on a private D-Bus whose only panel is a
 * fake tray host, on JavaFX's headless platform, with folders of its own and no way to the network. It puts its icon
 * in the tray, and quits cleanly when the command line asks it to.
 */
@EnabledOnOs(OS.LINUX)
class DesktopAppSmokeTest {

    private static final long TIMEOUT_S = 60;

    @TempDir
    Path folder;

    private IsolatedSessionBus bus;
    private DBusConnection panel;
    private Path runtime;
    private final List<Process> started = new ArrayList<>();

    /** The panel's registry, which only takes note of the items that register. */
    private static final class Watcher implements StatusNotifierWatcher {
        final BlockingQueue<String> registered = new LinkedBlockingQueue<>();

        @Override
        public void RegisterStatusNotifierItem(String service) {
            registered.add(service);
        }

        @Override
        public String getObjectPath() {
            return OBJECT_PATH;
        }
    }

    @BeforeEach
    void startAnIsolatedSessionBusAndFolders() throws Exception {
        bus = IsolatedSessionBus.start(folder);
        panel = DBusConnectionBuilder.forAddress(bus.address()).withShared(false).build();
        // a socket's path has at most 108 bytes, so the runtime folder gets a short one
        runtime = Files.createTempDirectory(Path.of("/tmp"), "ccp",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        Files.createDirectories(folder.resolve("config"));
        Files.copy(Path.of("docs", "example-engine.json"), folder.resolve("config/engine.json"));
    }

    @AfterEach
    void stopEverything() throws IOException {
        started.forEach(Process::destroyForcibly);
        panel.disconnect();
        bus.stop();
        try (var left = Files.walk(runtime)) {
            left.sorted((one, other) -> other.compareTo(one)).forEach(path -> path.toFile().delete());
        }
    }

    /** The app's JVM, with nothing of the developer's desktop: no display, no session bus, no folders of theirs. */
    private Process start(String name, String... args) throws IOException {
        var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx256m", "--enable-native-access=ALL-UNNAMED",
                "-Dglass.platform=Headless", "-Dprism.order=sw", "-Djava.awt.headless=true",
                "-Duser.home=" + folder.resolve("home"),
                // a proxy that refuses every connection: the forecast providers are never reached
                "-Dhttp.proxyHost=127.0.0.1", "-Dhttp.proxyPort=9", "-Dhttps.proxyHost=127.0.0.1",
                "-Dhttps.proxyPort=9",
                "-cp", System.getProperty("java.class.path"), Main.class.getName()));
        command.addAll(List.of(args));
        var builder = new ProcessBuilder(command)
                .redirectOutput(folder.resolve(name + ".out").toFile())
                .redirectError(folder.resolve(name + ".err").toFile());
        var environment = builder.environment();
        environment.clear();
        environment.put("PATH", System.getenv().getOrDefault("PATH", "/usr/bin:/bin"));
        environment.put("LANGUAGE", "en");
        environment.put("DBUS_SESSION_BUS_ADDRESS", bus.address());
        environment.put("XDG_RUNTIME_DIR", runtime.toString());
        environment.put("XDG_CONFIG_HOME", folder.resolve("config-home").toString());
        environment.put("XDG_DATA_HOME", folder.resolve("data-home").toString());
        environment.put("XDG_CACHE_HOME", folder.resolve("cache-home").toString());
        var process = builder.start();
        started.add(process);
        return process;
    }

    private String read(String name, String stream) throws IOException {
        return Files.readString(folder.resolve(name + "." + stream), StandardCharsets.UTF_8);
    }

    @Test
    void theAppPutsItsIconInTheTrayAndQuitsCleanlyWhenAsked() throws Exception {
        var watcher = new Watcher();
        panel.exportObject(watcher);
        panel.requestBusName(StatusNotifierWatcher.BUS_NAME);

        var app = start("app", "--config=" + folder.resolve("config/engine.json"));
        var item = watcher.registered.poll(TIMEOUT_S, TimeUnit.SECONDS);
        assertThat(item).as("the tray item registered; the app said: %s", read("app", "err"))
                .startsWith("org.kde.StatusNotifierItem-" + app.pid());

        var quit = start("quit", "quit");
        assertThat(quit.waitFor(TIMEOUT_S, TimeUnit.SECONDS)).isTrue();
        assertThat(quit.exitValue()).as(read("quit", "err")).isZero();
        assertThat(read("quit", "out").strip())
                .isEqualTo(new Messages(Language.ENGLISH).text(TextKey.CLI_QUIT_CONFIRMED, AppName.TITLE));

        assertThat(app.waitFor(TIMEOUT_S, TimeUnit.SECONDS)).as("the app ended").isTrue();
        assertThat(app.exitValue()).as(read("app", "err")).isZero();
        assertThat(read("app", "err")).doesNotContain("Exception in thread");
        assertThat(Files.exists(runtime.resolve(AppName.ID).resolve("instance.sock")))
                .as("the single-instance socket is removed").isFalse();
    }
}
