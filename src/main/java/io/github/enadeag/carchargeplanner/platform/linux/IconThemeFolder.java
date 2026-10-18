package io.github.enadeag.carchargeplanner.platform.linux;

import io.github.enadeag.carchargeplanner.desktop.system.PanelShade;
import io.github.enadeag.carchargeplanner.desktop.system.StatusDot;
import io.github.enadeag.carchargeplanner.desktop.system.StatusIcon;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Locale;

/**
 * The tray icons as a small icon theme in a folder of their own, from which the panel draws them by name. KDE Plasma
 * gives an icon drawn this way its panel's text colour, light or dark, which it cannot do with pixels; GNOME's
 * AppIndicator extension shows it as it is, light, for GNOME's top bar is dark in both of its styles.
 */
final class IconThemeFolder {

    private static final System.Logger LOG = System.getLogger(IconThemeFolder.class.getName());

    private final Path root;
    /** KDE Plasma finds the icons only in a folder named "icons": it takes the name of the folder above for the app's. */
    private final Path icons;
    private boolean written;

    /**
     * Makes the theme; nothing is written until {@link #write(StatusIcon)}.
     *
     * @param root a folder of this theme's own that only this user may use; {@link #delete()} removes it with
     *     everything in it
     */
    IconThemeFolder(Path root) {
        this.root = root;
        this.icons = root.resolve("icons");
    }

    /**
     * Writes an SVG for every dot. When that fails, the tray goes on with pixels: {@link #iconName(StatusDot)} then
     * names no icon.
     *
     * @param icon draws the icons
     */
    void write(StatusIcon icon) {
        var apps = icons.resolve("hicolor/scalable/apps");
        try {
            Files.createDirectories(apps);
            for (var dot : StatusDot.values()) {
                // never through a link another user may have put there
                Files.writeString(apps.resolve(name(dot) + ".svg"), icon.svg(dot, PanelShade.DARK),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS);
            }
            written = true;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "cannot write the tray icons to " + root + "; the panel gets pixels instead", e);
        }
    }

    /**
     * Where the panel finds the icons, for the item's {@code IconThemePath}.
     *
     * @return the absolute path of the folder "icons" in the theme's own folder
     */
    String path() {
        return icons.toAbsolutePath().toString();
    }

    /**
     * The icon's name in this theme, for the item's {@code IconName}. Each dot has a name of its own, so a panel
     * that keeps icons by name never shows an old dot.
     *
     * @param dot the dot the icon shows
     * @return a name such as "car_charge_planner_tray_sun", or an empty name when the icons could not be written
     */
    String iconName(StatusDot dot) {
        return written ? name(dot) : "";
    }

    /**
     * Without dashes: an icon theme shortens a name it cannot find at its dashes, and Plasma would show the system
     * theme's "car" before it looks in our folder.
     */
    private static String name(StatusDot dot) {
        return "car_charge_planner_tray_" + dot.name().toLowerCase(Locale.ROOT);
    }

    /** Removes the folder and the icons in it; a folder that is gone already is fine. */
    void delete() {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "cannot remove the tray icons from " + root, e);
        }
    }
}
