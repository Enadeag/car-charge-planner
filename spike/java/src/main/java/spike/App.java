package spike;

import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Questions 5 and 7: the dashboard as a tray app. Single instance over a Unix domain socket in a
 * per-user directory (a second launch asks the first to show its window and exits). Tray: our own
 * StatusNotifierItem on Linux, AWT SystemTray on Windows. Closing the window keeps the app in the tray.
 * Args: --hidden (start in the tray), --exit-when-shown (for start-up timing).
 */
public class App {
    static Stage stage;

    static void log(String s) {
        System.out.println(LocalTime.now().withNano(0) + " " + s);
        System.out.flush();
    }

    public static void main(String[] args) throws Exception {
        var socket = socketPath();
        if (handOff(socket)) {
            log("another instance is running; asked it to show its window");
            System.exit(0);
        }
        var argList = List.of(args);
        Platform.setImplicitExit(false);
        Platform.startup(() -> {
            boolean dark = Platform.getPreferences().getColorScheme() == ColorScheme.DARK;
            Dashboard.dark = dark;
            stage = new Stage();
            var scene = new Scene(Dashboard.build());
            Dashboard.applyTheme(scene, dark);
            Platform.getPreferences().colorSchemeProperty()
                    .addListener((o, a, b) -> Dashboard.applyTheme(scene, b == ColorScheme.DARK));
            stage.setScene(scene);
            stage.setTitle("Car charge planner");
            stage.getIcons().addAll(Dashboard.fxIcon(16), Dashboard.fxIcon(32), Dashboard.fxIcon(64), Dashboard.fxIcon(256));
            stage.setOnCloseRequest(e -> { e.consume(); stage.hide(); log("closed to tray"); });
            stage.setOnShown(e -> Platform.runLater(() -> {
                var started = ProcessHandle.current().info().startInstant().orElse(Instant.now());
                log("window shown " + Duration.between(started, Instant.now()).toMillis() + " ms after process start");
                if (argList.contains("--exit-when-shown")) System.exit(0);
            }));
            if (!argList.contains("--hidden")) stage.show();
        });
        listen(socket);
        if (System.getProperty("os.name").startsWith("Windows")) {
            awtTray();
        } else {
            Sni.start("Car charge planner", List.of("Open dashboard", "-", "Quit"), App::show,
                    id -> { if (id == 1) show(); if (id == 3) quit(socket); }, App::log);
        }
    }

    static void show() {
        Platform.runLater(() -> {
            stage.show();
            stage.setIconified(false);
            stage.toFront();
            stage.requestFocus();
            log("window shown on request");
        });
    }

    static void quit(Path socket) {
        try { Files.deleteIfExists(socket); } catch (IOException ignored) { }
        System.exit(0);
    }

    // ---- single instance ---------------------------------------------------------------------------

    static Path socketPath() throws IOException {
        Path dir;
        if (System.getProperty("os.name").startsWith("Windows")) {
            dir = Path.of(System.getenv("LOCALAPPDATA"), "car-charge-planner-spike");
        } else {
            var runtime = System.getenv("XDG_RUNTIME_DIR");
            dir = runtime != null ? Path.of(runtime, "car-charge-planner-spike")
                    : Path.of(System.getProperty("user.home"), ".cache", "car-charge-planner-spike");
        }
        Files.createDirectories(dir);
        return dir.resolve("instance.sock");
    }

    /** True when another instance answered and was told to show itself. */
    static boolean handOff(Path socket) {
        if (!Files.exists(socket)) return false;
        try (var ch = SocketChannel.open(UnixDomainSocketAddress.of(socket))) {
            ch.write(ByteBuffer.wrap("show\n".getBytes(StandardCharsets.UTF_8)));
            return true;
        } catch (IOException e) {
            log("stale socket (" + e.getMessage() + "), taking over");
            try { Files.deleteIfExists(socket); } catch (IOException ignored) { }
            return false;
        }
    }

    static void listen(Path socket) throws IOException {
        var server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        server.bind(UnixDomainSocketAddress.of(socket));
        log("listening on " + socket);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { Files.deleteIfExists(socket); } catch (IOException ignored) { }
        }));
        var t = new Thread(() -> {
            while (true) {
                try (var ch = server.accept()) {
                    var buf = ByteBuffer.allocate(64);
                    ch.read(buf);
                    var msg = new String(buf.array(), 0, buf.position(), StandardCharsets.UTF_8).trim();
                    log("instance message: " + msg);
                    if (msg.equals("show")) show();
                } catch (IOException e) {
                    log("accept failed: " + e);
                }
            }
        }, "single-instance");
        t.setDaemon(true);
        t.start();
    }

    // ---- Windows tray ------------------------------------------------------------------------------

    static void awtTray() throws Exception {
        var menu = new PopupMenu();
        var open = new MenuItem("Open dashboard");
        var quit = new MenuItem("Quit");
        menu.add(open);
        menu.addSeparator();
        menu.add(quit);
        var icon = new TrayIcon(Icons.icon(64, Icons.GREEN), "Car charge planner", menu);
        icon.setImageAutoSize(true);
        icon.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) show();
            }
        });
        open.addActionListener(e -> show());
        quit.addActionListener(e -> quit(socketPath0()));
        SystemTray.getSystemTray().add(icon);
        log("AWT tray icon added");
    }

    static Path socketPath0() {
        try { return socketPath(); } catch (IOException e) { throw new RuntimeException(e); }
    }
}
