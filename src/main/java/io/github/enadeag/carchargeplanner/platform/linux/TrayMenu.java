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

    private static final String NODE_SIGNATURE = "(ia{sv}av)";

    private final List<Runnable> actions;
    /** Replaced as a whole with its revision, so the panel never reads labels of one revision with another's number. */
    private volatile Layout layout;

    /** The entries' labels as the panel reads them, and the layout's revision, which the panel compares. */
    private record Layout(List<String> labels, UInt32 revision) {
    }

    /**
     * Makes the menu.
     *
     * @param labels the texts of the entries below the root, in the order shown; they are copied
     * @param actions what a click on each entry does, in the same order as the labels and as many; they are copied
     */
    TrayMenu(List<String> labels, List<Runnable> actions) {
        if (labels.size() != actions.size()) {
            throw new IllegalArgumentException("each entry needs a label and an action");
        }
        this.actions = List.copyOf(actions);
        this.layout = new Layout(List.copyOf(labels), new UInt32(1));
    }

    /**
     * Gives the entries new texts, such as in another language; the panel reads them once it is sent
     * {@link DbusMenu.LayoutUpdated} with the revision returned.
     *
     * @param labels the new texts, in the order of the entries and as many
     * @return the layout's new revision, one more than before
     */
    synchronized UInt32 relabel(List<String> labels) {
        if (labels.size() != actions.size()) {
            throw new IllegalArgumentException("each entry needs a label");
        }
        layout = new Layout(List.copyOf(labels), new UInt32(layout.revision().longValue() + 1));
        return layout.revision();
    }

    @Override
    public String getObjectPath() {
        return OBJECT_PATH;
    }

    @Override
    public Pair<UInt32, MenuNode> GetLayout(int parentId, int recursionDepth, List<String> propertyNames) {
        var now = layout;
        return new Pair<>(now.revision(), node(now, parentId));
    }

    @Override
    public List<IdProperties> GetGroupProperties(List<Integer> ids, List<String> propertyNames) {
        var now = layout;
        return ids.stream().filter(this::exists).map(id -> new IdProperties(id, properties(now, id))).toList();
    }

    @Override
    public Variant<?> GetProperty(int id, String name) {
        return properties(layout, id).get(name);
    }

    @Override
    public void Event(int id, String eventId, Variant<?> data, UInt32 timestamp) {
        if (eventId.equals("clicked") && id > 0 && exists(id)) {
            actions.get(id - 1).run();
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
        return id >= 0 && id <= actions.size();
    }

    private MenuNode node(Layout now, int id) {
        var children = new ArrayList<Variant<?>>();
        if (id == 0) {
            for (int child = 1; child <= actions.size(); child++) {
                children.add(new Variant<>(node(now, child), NODE_SIGNATURE));
            }
        }
        return new MenuNode(id, properties(now, id), children);
    }

    private static Map<String, Variant<?>> properties(Layout now, int id) {
        var properties = new LinkedHashMap<String, Variant<?>>();
        if (id == 0) {
            properties.put("children-display", new Variant<>("submenu"));
        } else {
            properties.put("label", new Variant<>(now.labels().get(id - 1)));
            properties.put("enabled", new Variant<>(true));
            properties.put("visible", new Variant<>(true));
        }
        return properties;
    }
}
