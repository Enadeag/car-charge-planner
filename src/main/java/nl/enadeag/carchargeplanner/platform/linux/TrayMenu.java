package nl.enadeag.carchargeplanner.platform.linux;

import nl.enadeag.carchargeplanner.desktop.system.Tray.MenuLabels;
import nl.enadeag.carchargeplanner.desktop.system.TrayMenuEntry;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.freedesktop.dbus.errors.InvalidMethodArgument;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

/**
 * The tray item's menu, served over the dbusmenu protocol so the panel draws it natively: the entries of
 * {@link TrayMenuEntry}, with a separator line where it asks for one. Entry 0 is the root.
 */
final class TrayMenu implements DbusMenu, Properties {

    /** The object path the menu is served on. */
    static final String OBJECT_PATH = "/MenuBar";

    private static final String NODE_SIGNATURE = "(ia{sv}av)";

    /** The entries' ids are 1 and up, in the order of {@link TrayMenuEntry}; the separators' come after them. */
    private static final List<TrayMenuEntry> ENTRIES = List.of(TrayMenuEntry.values());

    private final Map<TrayMenuEntry, Runnable> actions = new EnumMap<>(TrayMenuEntry.class);
    /** The root's children, in the order the panel shows them: an entry's id, or a separator's. */
    private final List<Integer> shown = new ArrayList<>();
    /** Replaced as a whole with its revision, so the panel never reads labels of one revision with another's number. */
    private volatile Layout layout;

    /** The entries' labels as the panel reads them, and the layout's revision, which the panel compares. */
    private record Layout(MenuLabels labels, UInt32 revision) {
    }

    /**
     * Makes the menu of every {@link TrayMenuEntry}, with a separator before each one that is set apart.
     *
     * @param labels what the entries say
     * @param actions what a click on an entry does; asked once per entry, now
     */
    TrayMenu(MenuLabels labels, Function<TrayMenuEntry, Runnable> actions) {
        var separator = ENTRIES.size();
        for (var entry : ENTRIES) {
            if (entry.separated()) {
                shown.add(++separator);
            }
            shown.add(id(entry));
            this.actions.put(entry, actions.apply(entry));
        }
        this.layout = new Layout(labels, new UInt32(1));
    }

    /**
     * Gives the entries new texts, such as in another language; the panel reads them once it is sent
     * {@link DbusMenu.LayoutUpdated} with the revision returned.
     *
     * @param labels what the entries say from now on
     * @return the layout's new revision, one more than before
     */
    synchronized UInt32 relabel(MenuLabels labels) {
        layout = new Layout(labels, new UInt32(layout.revision().longValue() + 1));
        return layout.revision();
    }

    @Override
    public String getObjectPath() {
        return OBJECT_PATH;
    }

    /**
     * {@inheritDoc}
     *
     * @throws InvalidMethodArgument when there is no such entry, which the panel gets as an error
     */
    @Override
    public Pair<UInt32, MenuNode> GetLayout(int parentId, int recursionDepth, List<String> propertyNames) {
        requireEntry(parentId);
        var now = layout;
        return new Pair<>(now.revision(), node(now, parentId));
    }

    @Override
    public List<IdProperties> GetGroupProperties(List<Integer> ids, List<String> propertyNames) {
        var now = layout;
        return ids.stream().filter(this::exists).map(id -> new IdProperties(id, properties(now, id))).toList();
    }

    /**
     * {@inheritDoc}
     *
     * @throws InvalidMethodArgument when there is no such entry, or it has no such property, which the panel gets as
     *     an error
     */
    @Override
    public Variant<?> GetProperty(int id, String name) {
        requireEntry(id);
        var value = properties(layout, id).get(name);
        if (value == null) {
            throw new InvalidMethodArgument("menu entry " + id + " has no property " + name);
        }
        return value;
    }

    @Override
    public void Event(int id, String eventId, Variant<?> data, UInt32 timestamp) {
        if (eventId.equals("clicked")) {
            entry(id).ifPresent(entry -> actions.get(entry).run());
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>An event for an entry that does not exist is left out, and not named in the answer either: this always answers
     * none.
     */
    @Override
    public List<Integer> EventGroup(List<MenuEvent> events) {
        events.forEach(event -> Event(event.id, event.eventId, event.data, event.timestamp));
        return List.of();
    }

    @Override
    public boolean AboutToShow(int id) {
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The menu never changes before it shows, and an entry that does not exist is not named: this always answers
     * none and none.
     */
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
        return id == 0 || shown.contains(id);
    }

    private void requireEntry(int id) {
        if (!exists(id)) {
            throw new InvalidMethodArgument("there is no menu entry " + id);
        }
    }

    private static int id(TrayMenuEntry entry) {
        return entry.ordinal() + 1;
    }

    /** The entry an id stands for; empty for the root, a separator, or an id the menu does not have. */
    private static Optional<TrayMenuEntry> entry(int id) {
        return id >= 1 && id <= ENTRIES.size() ? Optional.of(ENTRIES.get(id - 1)) : Optional.empty();
    }

    private MenuNode node(Layout now, int id) {
        var children = new ArrayList<Variant<?>>();
        if (id == 0) {
            for (var child : shown) {
                children.add(new Variant<>(node(now, child), NODE_SIGNATURE));
            }
        }
        return new MenuNode(id, properties(now, id), children);
    }

    private static Map<String, Variant<?>> properties(Layout now, int id) {
        var properties = new LinkedHashMap<String, Variant<?>>();
        if (id == 0) {
            properties.put("children-display", new Variant<>("submenu"));
            return properties;
        }
        entry(id).ifPresentOrElse(entry -> {
            properties.put("label", new Variant<>(entry.label(now.labels())));
            properties.put("enabled", new Variant<>(true));
        }, () -> properties.put("type", new Variant<>("separator")));
        properties.put("visible", new Variant<>(true));
        return properties;
    }
}
