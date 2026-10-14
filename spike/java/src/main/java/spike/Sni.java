package spike;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.Tuple;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.Position;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

/**
 * A minimal StatusNotifierItem (KDE/freedesktop tray protocol) plus a com.canonical.dbusmenu context menu,
 * written directly on dbus-java with its pure-Java unix socket transport. No native code, no GTK.
 *
 * Usage: {@code var sni = Sni.start("Title", onActivate, menuItems, onMenuClick); sni.setDot(Color.GREEN);}
 */
public final class Sni {

    // ---- D-Bus interfaces --------------------------------------------------------------------------

    @DBusInterfaceName("org.kde.StatusNotifierItem")
    public interface StatusNotifierItem extends DBusInterface {
        void ContextMenu(int x, int y);
        void Activate(int x, int y);
        void SecondaryActivate(int x, int y);
        void Scroll(int delta, String orientation);
        /** KDE extension, called by Plasma before Activate on Wayland so the app may raise a window. */
        void ProvideXdgActivationToken(String token);

        class NewIcon extends DBusSignal {
            public NewIcon(String path) throws DBusException { super(path); }
        }
        class NewToolTip extends DBusSignal {
            public NewToolTip(String path) throws DBusException { super(path); }
        }
        class NewStatus extends DBusSignal {
            public final String status;
            public NewStatus(String path, String status) throws DBusException { super(path, status); this.status = status; }
        }
    }

    @DBusInterfaceName("org.kde.StatusNotifierWatcher")
    public interface StatusNotifierWatcher extends DBusInterface {
        void RegisterStatusNotifierItem(String service);
    }

    @DBusInterfaceName("com.canonical.dbusmenu")
    public interface DbusMenu extends DBusInterface {
        Pair<UInt32, MenuNode> GetLayout(int parentId, int recursionDepth, List<String> propertyNames);
        List<IdProps> GetGroupProperties(List<Integer> ids, List<String> propertyNames);
        Variant<?> GetProperty(int id, String name);
        void Event(int id, String eventId, Variant<?> data, UInt32 timestamp);
        List<Integer> EventGroup(List<MenuEvent> events);
        boolean AboutToShow(int id);
        Pair<List<Integer>, List<Integer>> AboutToShowGroup(List<Integer> ids);

        class LayoutUpdated extends DBusSignal {
            public final UInt32 revision;
            public final int parent;
            public LayoutUpdated(String path, UInt32 revision, int parent) throws DBusException {
                super(path, revision, parent);
                this.revision = revision;
                this.parent = parent;
            }
        }
    }

    public static final class Pair<A, B> extends Tuple {
        @Position(0) public final A a;
        @Position(1) public final B b;
        public Pair(A a, B b) { this.a = a; this.b = b; }
    }

    public static final class Pixmap extends Struct {
        @Position(0) public final int width;
        @Position(1) public final int height;
        @Position(2) public final byte[] argb;
        public Pixmap(int width, int height, byte[] argb) { this.width = width; this.height = height; this.argb = argb; }
    }

    public static final class ToolTip extends Struct {
        @Position(0) public final String iconName;
        @Position(1) public final List<Pixmap> icon;
        @Position(2) public final String title;
        @Position(3) public final String text;
        public ToolTip(String iconName, List<Pixmap> icon, String title, String text) {
            this.iconName = iconName; this.icon = icon; this.title = title; this.text = text;
        }
    }

    public static final class MenuNode extends Struct {
        @Position(0) public final int id;
        @Position(1) public final Map<String, Variant<?>> props;
        @Position(2) public final List<Variant<?>> children;
        public MenuNode(int id, Map<String, Variant<?>> props, List<Variant<?>> children) {
            this.id = id; this.props = props; this.children = children;
        }
    }

    public static final class IdProps extends Struct {
        @Position(0) public final int id;
        @Position(1) public final Map<String, Variant<?>> props;
        public IdProps(int id, Map<String, Variant<?>> props) { this.id = id; this.props = props; }
    }

    public static final class MenuEvent extends Struct {
        @Position(0) public final int id;
        @Position(1) public final String eventId;
        @Position(2) public final Variant<?> data;
        @Position(3) public final UInt32 timestamp;
        public MenuEvent(int id, String eventId, Variant<?> data, UInt32 timestamp) {
            this.id = id; this.eventId = eventId; this.data = data; this.timestamp = timestamp;
        }
    }

    // ---- implementation ----------------------------------------------------------------------------

    static final String ITEM_PATH = "/StatusNotifierItem";
    static final String MENU_PATH = "/MenuBar";

    final DBusConnection conn;
    final Item item = new Item();
    final Menu menu = new Menu();
    volatile String title;
    volatile Color dot;
    volatile List<String> labels;
    volatile String activationToken;
    Runnable onActivate;
    IntConsumer onMenuClick;
    Consumer<String> log;

    /** labels: menu entries; "-" is a separator. Menu item ids are index + 1. */
    public static Sni start(String title, List<String> labels, Runnable onActivate, IntConsumer onMenuClick,
                            Consumer<String> log) throws DBusException {
        var s = new Sni(title, labels, onActivate, onMenuClick, log);
        return s;
    }

    private Sni(String title, List<String> labels, Runnable onActivate, IntConsumer onMenuClick,
                Consumer<String> log) throws DBusException {
        this.title = title;
        this.labels = labels;
        this.onActivate = onActivate;
        this.onMenuClick = onMenuClick;
        this.log = log;
        conn = DBusConnectionBuilder.forSessionBus().build();
        conn.exportObject(ITEM_PATH, item);
        conn.exportObject(MENU_PATH, menu);
        String name = "org.kde.StatusNotifierItem-" + ProcessHandle.current().pid() + "-1";
        conn.requestBusName(name);
        var watcher = conn.getRemoteObject("org.kde.StatusNotifierWatcher", "/StatusNotifierWatcher",
                StatusNotifierWatcher.class);
        watcher.RegisterStatusNotifierItem(name);
        log.accept("registered " + name + " with the StatusNotifierWatcher (unique name " + conn.getUniqueName() + ")");
    }

    public void setDot(Color dot) {
        this.dot = dot;
        send(() -> new StatusNotifierItem.NewIcon(ITEM_PATH));
    }

    public void setTitle(String title) {
        this.title = title;
        send(() -> new StatusNotifierItem.NewToolTip(ITEM_PATH));
    }

    public void setLabels(List<String> labels) {
        this.labels = labels;
        revision++;
        send(() -> new DbusMenu.LayoutUpdated(MENU_PATH, new UInt32(revision), 0));
    }

    public String takeActivationToken() {
        var t = activationToken;
        activationToken = null;
        return t;
    }

    interface SignalFactory { DBusSignal make() throws DBusException; }

    void send(SignalFactory f) {
        try {
            conn.sendMessage(f.make());
        } catch (DBusException e) {
            log.accept("signal failed: " + e);
        }
    }

    static List<Pixmap> pixmaps(Color dot) {
        var list = new ArrayList<Pixmap>();
        for (int size : new int[] {16, 22, 24, 32, 48, 64}) {
            var img = Icons.icon(size, dot);
            var bytes = new byte[size * size * 4];
            int i = 0;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int p = img.getRGB(x, y);
                    bytes[i++] = (byte) (p >>> 24);
                    bytes[i++] = (byte) (p >>> 16);
                    bytes[i++] = (byte) (p >>> 8);
                    bytes[i++] = (byte) p;
                }
            }
            list.add(new Pixmap(size, size, bytes));
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    static <A> A v(Object value, String sig) {
        return (A) new Variant<>(value, sig);
    }

    final class Item implements StatusNotifierItem, Properties {
        @Override public String getObjectPath() { return ITEM_PATH; }

        @Override public void ContextMenu(int x, int y) { log.accept("SNI ContextMenu " + x + "," + y); }
        @Override public void Activate(int x, int y) {
            log.accept("SNI Activate (left click) at " + x + "," + y);
            onActivate.run();
        }
        @Override public void SecondaryActivate(int x, int y) { log.accept("SNI SecondaryActivate (middle click)"); }
        @Override public void Scroll(int delta, String orientation) { log.accept("SNI Scroll " + delta + " " + orientation); }
        @Override public void ProvideXdgActivationToken(String token) {
            log.accept("SNI ProvideXdgActivationToken " + token);
            activationToken = token;
        }

        Map<String, Variant<?>> all() {
            var m = new LinkedHashMap<String, Variant<?>>();
            m.put("Category", new Variant<>("ApplicationStatus"));
            m.put("Id", new Variant<>("car-charge-planner-spike"));
            m.put("Title", new Variant<>(title));
            m.put("Status", new Variant<>("Active"));
            m.put("WindowId", new Variant<>(0));
            m.put("IconThemePath", new Variant<>(""));
            m.put("IconName", new Variant<>(""));
            m.put("IconPixmap", new Variant<>(pixmaps(dot), "a(iiay)"));
            m.put("OverlayIconName", new Variant<>(""));
            m.put("OverlayIconPixmap", new Variant<>(List.of(), "a(iiay)"));
            m.put("AttentionIconName", new Variant<>(""));
            m.put("AttentionIconPixmap", new Variant<>(List.of(), "a(iiay)"));
            m.put("AttentionMovieName", new Variant<>(""));
            m.put("ToolTip", new Variant<>(new ToolTip("", List.of(), title, "Next charge window: today 11:30-15:00"),
                    "(sa(iiay)ss)"));
            m.put("ItemIsMenu", new Variant<>(false));
            m.put("Menu", new Variant<>(new DBusPath(MENU_PATH)));
            return m;
        }

        @Override public <A> A Get(String iface, String prop) {
            var value = all().get(prop);
            return (A) value;
        }
        @Override public <A> void Set(String iface, String prop, A value) { }
        @Override public Map<String, Variant<?>> GetAll(String iface) { return all(); }
    }

    volatile int revision = 1;

    final class Menu implements DbusMenu, Properties {
        @Override public String getObjectPath() { return MENU_PATH; }

        Map<String, Variant<?>> props(int id) {
            var m = new LinkedHashMap<String, Variant<?>>();
            if (id == 0) {
                m.put("children-display", new Variant<>("submenu"));
                return m;
            }
            String label = labels.get(id - 1);
            if (label.equals("-")) {
                m.put("type", new Variant<>("separator"));
            } else {
                m.put("label", new Variant<>(label));
                m.put("enabled", new Variant<>(!label.startsWith("Status:")));
            }
            m.put("visible", new Variant<>(true));
            return m;
        }

        MenuNode node(int id) {
            var children = new ArrayList<Variant<?>>();
            if (id == 0) {
                for (int i = 1; i <= labels.size(); i++) {
                    children.add(new Variant<>(node(i), "(ia{sv}av)"));
                }
            }
            return new MenuNode(id, props(id), children);
        }

        @Override public Pair<UInt32, MenuNode> GetLayout(int parentId, int depth, List<String> names) {
            log.accept("dbusmenu GetLayout parent=" + parentId);
            return new Pair<>(new UInt32(revision), node(parentId));
        }
        @Override public List<IdProps> GetGroupProperties(List<Integer> ids, List<String> names) {
            var out = new ArrayList<IdProps>();
            for (int id : ids) {
                if (id >= 0 && id <= labels.size()) out.add(new IdProps(id, props(id)));
            }
            return out;
        }
        @Override public Variant<?> GetProperty(int id, String name) { return props(id).get(name); }
        @Override public void Event(int id, String eventId, Variant<?> data, UInt32 timestamp) {
            log.accept("dbusmenu Event id=" + id + " " + eventId);
            if (eventId.equals("clicked")) onMenuClick.accept(id);
        }
        @Override public List<Integer> EventGroup(List<MenuEvent> events) {
            for (var e : events) Event(e.id, e.eventId, e.data, e.timestamp);
            return List.of();
        }
        @Override public boolean AboutToShow(int id) {
            log.accept("dbusmenu AboutToShow " + id);
            return false;
        }
        @Override public Pair<List<Integer>, List<Integer>> AboutToShowGroup(List<Integer> ids) {
            return new Pair<>(List.of(), List.of());
        }

        @Override public <A> A Get(String iface, String prop) {
            return switch (prop) {
                case "Version" -> v(new UInt32(3), "u");
                case "TextDirection" -> v("ltr", "s");
                case "Status" -> v("normal", "s");
                case "IconThemePath" -> v(List.of(), "as");
                default -> null;
            };
        }
        @Override public <A> void Set(String iface, String prop, A value) { }
        @Override public Map<String, Variant<?>> GetAll(String iface) {
            var m = new LinkedHashMap<String, Variant<?>>();
            m.put("Version", new Variant<>(new UInt32(3)));
            m.put("TextDirection", new Variant<>("ltr"));
            m.put("Status", new Variant<>("normal"));
            m.put("IconThemePath", new Variant<>(List.of(), "as"));
            return m;
        }
    }
}
