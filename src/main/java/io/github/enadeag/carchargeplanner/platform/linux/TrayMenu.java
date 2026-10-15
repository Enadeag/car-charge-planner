package io.github.enadeag.carchargeplanner.platform.linux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

/** The tray item's menu, served over the dbusmenu protocol so the panel draws it natively. Entry 0 is the root. */
final class TrayMenu implements DbusMenu, Properties {

    /** The object path the menu is served on. */
    static final String OBJECT_PATH = "/MenuBar";

    /**
     * One clickable entry of the menu.
     *
     * @param label the text of the entry
     * @param action what a click does
     */
    record Entry(String label, Runnable action) {
    }

    private static final UInt32 REVISION = new UInt32(1);
    private static final String NODE_SIGNATURE = "(ia{sv}av)";

    private final List<Entry> entries;

    /**
     * Makes the menu.
     *
     * @param entries the entries below the root, in the order shown; they are copied
     */
    TrayMenu(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    @Override
    public String getObjectPath() {
        return OBJECT_PATH;
    }

    @Override
    public Pair<UInt32, MenuNode> GetLayout(int parentId, int recursionDepth, List<String> propertyNames) {
        return new Pair<>(REVISION, node(parentId));
    }

    @Override
    public List<IdProperties> GetGroupProperties(List<Integer> ids, List<String> propertyNames) {
        return ids.stream().filter(this::exists).map(id -> new IdProperties(id, properties(id))).toList();
    }

    @Override
    public Variant<?> GetProperty(int id, String name) {
        return properties(id).get(name);
    }

    @Override
    public void Event(int id, String eventId, Variant<?> data, UInt32 timestamp) {
        if (eventId.equals("clicked") && id > 0 && exists(id)) {
            entries.get(id - 1).action().run();
        }
    }

    @Override
    public List<Integer> EventGroup(List<MenuEvent> events) {
        events.forEach(event -> Event(event.id, event.eventId, event.data, event.timestamp));
        return List.of();
    }

    @Override
    public boolean AboutToShow(int id) {
        return false;
    }

    @Override
    public Pair<List<Integer>, List<Integer>> AboutToShowGroup(List<Integer> ids) {
        return new Pair<>(List.of(), List.of());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A> A Get(String interfaceName, String propertyName) {
        return (A) GetAll(interfaceName).get(propertyName);
    }

    @Override
    public <A> void Set(String interfaceName, String propertyName, A value) {
    }

    @Override
    public Map<String, Variant<?>> GetAll(String interfaceName) {
        var properties = new LinkedHashMap<String, Variant<?>>();
        properties.put("Version", new Variant<>(new UInt32(3)));
        properties.put("TextDirection", new Variant<>("ltr"));
        properties.put("Status", new Variant<>("normal"));
        properties.put("IconThemePath", new Variant<>(List.of(), "as"));
        return properties;
    }

    private boolean exists(int id) {
        return id >= 0 && id <= entries.size();
    }

    private MenuNode node(int id) {
        var children = new ArrayList<Variant<?>>();
        if (id == 0) {
            for (int child = 1; child <= entries.size(); child++) {
                children.add(new Variant<>(node(child), NODE_SIGNATURE));
            }
        }
        return new MenuNode(id, properties(id), children);
    }

    private Map<String, Variant<?>> properties(int id) {
        var properties = new LinkedHashMap<String, Variant<?>>();
        if (id == 0) {
            properties.put("children-display", new Variant<>("submenu"));
        } else {
            properties.put("label", new Variant<>(entries.get(id - 1).label()));
            properties.put("enabled", new Variant<>(true));
            properties.put("visible", new Variant<>(true));
        }
        return properties;
    }
}
