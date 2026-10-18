package io.github.enadeag.carchargeplanner.platform.linux;

import io.github.enadeag.carchargeplanner.desktop.system.AppIcon;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The app in the user's application menu: a desktop entry in {@code $XDG_DATA_HOME/applications} and the app icon in
 * {@code $XDG_DATA_HOME/icons/hicolor}, in every size of the icon theme specification the app has. No root is
 * needed, and the desktop's menu and notifications find the app by this entry.
 */
public final class LinuxMenuEntry {

    /** The icon's name in the icon theme, and the entry's file name without {@code .desktop}. */
    public static final String APP_ID = "car-charge-planner";

    private static final System.Logger LOG = System.getLogger(LinuxMenuEntry.class.getName());
    private static final String SVG_RESOURCE = "icons/" + APP_ID + ".svg";
    private static final int CACHE_TOOL_SECONDS = 10;

    private final Path dataHome;
    private final Path program;
    private final String name;
    private final Consumer<Path> cachesChanged;

    /**
     * Makes the keeper of the menu entry; nothing is read or written yet.
     *
     * @param dataHome the user's data folder, {@code $XDG_DATA_HOME}; the folders below it are made when missing
     * @param program the app's launcher, an absolute path; it is quoted in the entry, so it may hold spaces, but no
     *     line break
     * @param name the app's name that the menu shows
     * @param cachesChanged told the data folder after files were written or removed, so the desktop's caches can be
     *     refreshed, such as with {@link #refreshCaches(Path)}; it must not fail when it cannot
     */
    public LinuxMenuEntry(Path dataHome, Path program, String name, Consumer<Path> cachesChanged) {
        this.dataHome = dataHome;
        this.program = program;
        this.name = name;
        this.cachesChanged = cachesChanged;
    }

    /**
     * Writes the entry and the icons, replacing those of an earlier call: that is how the entry follows the app
     * when its folder was moved.
     *
     * @return every file written, the entry first
     * @throws IOException when a folder or file cannot be written; files written before it stay
     */
    public List<Path> install() throws IOException {
        var written = new ArrayList<Path>();
        write(entryFile(), entry().getBytes(StandardCharsets.UTF_8), written);
        write(scalableIcon(), resource(SVG_RESOURCE), written);
        for (var size : AppIcon.SIZES) {
            write(pngIcon(size), resource("icons/icon-" + size + ".png"), written);
        }
        cachesChanged.accept(dataHome);
        return List.copyOf(written);
    }

    /**
     * Removes exactly the files {@link #install()} writes; the folders stay, other programs use them too.
     *
     * @return the files that were there and are removed now; empty when the entry was not installed
     * @throws IOException when a file cannot be removed
     */
    public List<Path> uninstall() throws IOException {
        var removed = new ArrayList<Path>();
        for (var file : files()) {
            if (Files.deleteIfExists(file)) {
                removed.add(file);
            }
        }
        if (!removed.isEmpty()) {
            cachesChanged.accept(dataHome);
        }
        return List.copyOf(removed);
    }

    /**
     * Asks the desktop to read the entries and icons again, with {@code update-desktop-database} and
     * {@code gtk-update-icon-cache} when they are installed. Menus pick up new files on their own, so a missing tool
     * or a failure is only logged.
     *
     * @param dataHome the user's data folder, {@code $XDG_DATA_HOME}
     */
    public static void refreshCaches(Path dataHome) {
        run("update-desktop-database", dataHome.resolve("applications").toString());
        run("gtk-update-icon-cache", "--ignore-theme-index", "--force", dataHome.resolve("icons/hicolor").toString());
    }

    private static void run(String... command) {
        try {
            var process = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(CACHE_TOOL_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                LOG.log(Level.WARNING, command[0] + " did not finish");
            } else if (process.exitValue() != 0) {
                LOG.log(Level.DEBUG, command[0] + " exited with " + process.exitValue());
            }
        } catch (IOException e) {
            LOG.log(Level.DEBUG, command[0] + " is not available: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private List<Path> files() {
        var files = new ArrayList<Path>();
        files.add(entryFile());
        files.add(scalableIcon());
        AppIcon.SIZES.forEach(size -> files.add(pngIcon(size)));
        return files;
    }

    private Path entryFile() {
        return dataHome.resolve("applications").resolve(APP_ID + ".desktop");
    }

    private Path scalableIcon() {
        return dataHome.resolve("icons/hicolor/scalable/apps").resolve(APP_ID + ".svg");
    }

    private Path pngIcon(int size) {
        return dataHome.resolve("icons/hicolor").resolve(size + "x" + size).resolve("apps").resolve(APP_ID + ".png");
    }

    private String entry() {
        return String.format(Locale.ROOT, """
                [Desktop Entry]
                Type=Application
                Name=%s
                Exec=%s
                Icon=%s
                Terminal=false
                Categories=Utility;
                """, name, LinuxStartAtLogin.quoted(program.toString()), APP_ID);
    }

    private static byte[] resource(String path) throws IOException {
        try (InputStream in = AppIcon.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("the app has no resource " + path);
            }
            return in.readAllBytes();
        }
    }

    private static void write(Path file, byte[] content, List<Path> written) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, content);
        written.add(file);
    }
}
