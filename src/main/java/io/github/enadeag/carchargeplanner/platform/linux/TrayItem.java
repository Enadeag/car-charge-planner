package io.github.enadeag.carchargeplanner.platform.linux;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javafx.geometry.Point2D;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.Variant;

/** Our StatusNotifierItem as the panel sees it: its properties and what a click does. */
final class TrayItem implements StatusNotifierItem, Properties {

    static final String OBJECT_PATH = "/StatusNotifierItem";

    private final String id;
    private final String title;
    private final List<Pixmap> pixmaps;
    private final Consumer<Point2D> onActivate;

    TrayItem(String id, String title, List<BufferedImage> icons, Consumer<Point2D> onActivate) {
        this.id = id;
        this.title = title;
        this.pixmaps = icons.stream().map(TrayItem::pixmap).toList();
        this.onActivate = onActivate;
    }

    @Override
    public String getObjectPath() {
        return OBJECT_PATH;
    }

    @Override
    public void Activate(int x, int y) {
        onActivate.accept(new Point2D(x, y));
    }

    @Override
    public void SecondaryActivate(int x, int y) {
    }

    @Override
    public void ContextMenu(int x, int y) {
    }

    @Override
    public void Scroll(int delta, String orientation) {
    }

    @Override
    public void ProvideXdgActivationToken(String token) {
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A> A Get(String interfaceName, String propertyName) {
        return (A) GetAll(interfaceName).get(propertyName);
    }

    @Override
    public <A> void Set(String interfaceName, String propertyName, A value) {
    }

    // dbus-java does not answer org.freedesktop.DBus.Properties for us, and needs the signature of each empty list
    @Override
    public Map<String, Variant<?>> GetAll(String interfaceName) {
        var properties = new LinkedHashMap<String, Variant<?>>();
        properties.put("Category", new Variant<>("ApplicationStatus"));
        properties.put("Id", new Variant<>(id));
        properties.put("Title", new Variant<>(title));
        properties.put("Status", new Variant<>("Active"));
        properties.put("WindowId", new Variant<>(0));
        properties.put("IconName", new Variant<>(""));
        properties.put("IconPixmap", new Variant<>(pixmaps, "a(iiay)"));
        properties.put("OverlayIconName", new Variant<>(""));
        properties.put("OverlayIconPixmap", new Variant<>(List.of(), "a(iiay)"));
        properties.put("AttentionIconName", new Variant<>(""));
        properties.put("AttentionIconPixmap", new Variant<>(List.of(), "a(iiay)"));
        properties.put("AttentionMovieName", new Variant<>(""));
        properties.put("ToolTip", new Variant<>(new ToolTip("", List.of(), title, ""), "(sa(iiay)ss)"));
        properties.put("ItemIsMenu", new Variant<>(false));
        properties.put("Menu", new Variant<>(new DBusPath(TrayMenu.OBJECT_PATH)));
        return properties;
    }

    private static Pixmap pixmap(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        var bytes = new byte[width * height * 4];
        int i = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                bytes[i++] = (byte) (argb >>> 24);
                bytes[i++] = (byte) (argb >>> 16);
                bytes[i++] = (byte) (argb >>> 8);
                bytes[i++] = (byte) argb;
            }
        }
        return new Pixmap(width, height, bytes);
    }
}
