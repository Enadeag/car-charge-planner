package nl.enadeag.carchargeplanner.platform.linux;

import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.Position;
import org.freedesktop.dbus.interfaces.DBusInterface;
import java.util.List;

/**
 * The tray icon protocol of KDE and freedesktop.org (the panel calls these methods on our item). The method names
 * are the protocol's own, so they start with a capital.
 */
@DBusInterfaceName("org.kde.StatusNotifierItem")
public interface StatusNotifierItem extends DBusInterface {

    /**
     * A left click.
     *
     * @param x the screen x position of the click
     * @param y the screen y position of the click
     */
    void Activate(int x, int y);

    /**
     * A middle click.
     *
     * @param x the screen x position of the click
     * @param y the screen y position of the click
     */
    void SecondaryActivate(int x, int y);

    /**
     * Only for panels that cannot show our menu themselves; Plasma shows it from the Menu property.
     *
     * @param x the screen x position of the click
     * @param y the screen y position of the click
     */
    void ContextMenu(int x, int y);

    /**
     * A turn of the mouse wheel over the icon.
     *
     * @param delta how far the wheel turned
     * @param orientation "vertical" or "horizontal"
     */
    void Scroll(int delta, String orientation);

    /**
     * Plasma sends a Wayland activation token before Activate; JavaFX windows (XWayland) come up without it.
     *
     * @param token the activation token
     */
    void ProvideXdgActivationToken(String token);

    /** One size of the icon: ARGB32 pixels in network byte order. */
    final class Pixmap extends Struct {
        /** The width in pixels. */
        @Position(0)
        public final int width;
        /** The height in pixels. */
        @Position(1)
        public final int height;
        /** The pixels, four bytes each: alpha, red, green, blue. */
        @Position(2)
        public final byte[] argb;

        /**
         * Makes one size of the icon.
         *
         * @param width the width in pixels
         * @param height the height in pixels
         * @param argb the pixels, four bytes each: alpha, red, green, blue
         */
        public Pixmap(int width, int height, byte[] argb) {
            this.width = width;
            this.height = height;
            this.argb = argb;
        }
    }

    /** The text shown when the pointer rests on the icon. */
    final class ToolTip extends Struct {
        /** The name of a themed icon to show beside the text; may be empty. */
        @Position(0)
        public final String iconName;
        /** The icon as pixels, used when there is no icon name; may be empty. */
        @Position(1)
        public final List<Pixmap> icon;
        /** The first line of the tooltip. */
        @Position(2)
        public final String title;
        /** The rest of the tooltip. */
        @Position(3)
        public final String text;

        /**
         * Makes a tooltip.
         *
         * @param iconName the name of a themed icon to show beside the text; may be empty
         * @param icon the icon as pixels, used when there is no icon name; may be empty
         * @param title the first line of the tooltip
         * @param text the rest of the tooltip
         */
        public ToolTip(String iconName, List<Pixmap> icon, String title, String text) {
            this.iconName = iconName;
            this.icon = icon;
            this.title = title;
            this.text = text;
        }
    }
}
