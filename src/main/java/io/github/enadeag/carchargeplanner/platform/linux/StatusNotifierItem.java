package io.github.enadeag.carchargeplanner.platform.linux;

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

    /** A left click. */
    void Activate(int x, int y);

    /** A middle click. */
    void SecondaryActivate(int x, int y);

    /** Only for panels that cannot show our menu themselves; Plasma shows it from the Menu property. */
    void ContextMenu(int x, int y);

    void Scroll(int delta, String orientation);

    /** Plasma sends a Wayland activation token before Activate; JavaFX windows (XWayland) come up without it. */
    void ProvideXdgActivationToken(String token);

    /** One size of the icon: ARGB32 pixels in network byte order. */
    final class Pixmap extends Struct {
        @Position(0)
        public final int width;
        @Position(1)
        public final int height;
        @Position(2)
        public final byte[] argb;

        public Pixmap(int width, int height, byte[] argb) {
            this.width = width;
            this.height = height;
            this.argb = argb;
        }
    }

    final class ToolTip extends Struct {
        @Position(0)
        public final String iconName;
        @Position(1)
        public final List<Pixmap> icon;
        @Position(2)
        public final String title;
        @Position(3)
        public final String text;

        public ToolTip(String iconName, List<Pixmap> icon, String title, String text) {
            this.iconName = iconName;
            this.icon = icon;
            this.title = title;
            this.text = text;
        }
    }
}
