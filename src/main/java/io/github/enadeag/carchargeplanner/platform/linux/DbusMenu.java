package io.github.enadeag.carchargeplanner.platform.linux;

import java.util.List;
import java.util.Map;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.Tuple;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.MethodNoReply;
import org.freedesktop.dbus.annotations.Position;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

/** The menu protocol the panel uses to show a tray item's menu natively. */
@DBusInterfaceName("com.canonical.dbusmenu")
public interface DbusMenu extends DBusInterface {

    /**
     * The menu below an entry.
     *
     * @param parentId the entry to start from; 0 is the root
     * @param recursionDepth how many levels to include; -1 for all
     * @param propertyNames the properties to include; empty for all
     * @return the layout's revision and the entry with its children
     */
    Pair<UInt32, MenuNode> GetLayout(int parentId, int recursionDepth, List<String> propertyNames);

    /**
     * The properties of several entries.
     *
     * @param ids the numbers of the entries, as the layout gives them; 0 is the root
     * @param propertyNames the properties wanted; empty for all
     * @return the properties of each of the entries that exist
     */
    List<IdProperties> GetGroupProperties(List<Integer> ids, List<String> propertyNames);

    /**
     * One property of one entry.
     *
     * @param id the number of the entry, as the layout gives it; 0 is the root
     * @param name the property, such as "label"
     * @return the value of the property
     */
    Variant<?> GetProperty(int id, String name);

    /**
     * A click on an entry arrives as the event "clicked". The panel needs no answer.
     *
     * @param id the entry the event is for
     * @param eventId the kind of event, such as "clicked"
     * @param data extra data of the event; not used
     * @param timestamp when the event happened, as the panel counts it
     */
    @MethodNoReply
    void Event(int id, String eventId, Variant<?> data, UInt32 timestamp);

    /**
     * Several events at once.
     *
     * @param events the events in the order they happened, each as {@link #Event} takes it
     * @return the entries that do not exist, so that their events were not handled
     */
    List<Integer> EventGroup(List<MenuEvent> events);

    /**
     * The panel tells that it is about to show an entry's submenu.
     *
     * @param id the number of the entry whose submenu is about to show; 0 is the root
     * @return true when the layout has changed and must be fetched again
     */
    boolean AboutToShow(int id);

    /**
     * {@link #AboutToShow} for several entries.
     *
     * @param ids the numbers of the entries whose submenus are about to show
     * @return the entries whose layout needs an update, and the entries that do not exist
     */
    Pair<List<Integer>, List<Integer>> AboutToShowGroup(List<Integer> ids);

    /** The menu changed, such as its labels: the panel asks for the layout again. */
    class LayoutUpdated extends DBusSignal {
        /** The layout's new revision. */
        public final UInt32 revision;
        /** The entry below which the menu changed; 0 for the whole menu. */
        public final int parent;

        /**
         * Makes the signal.
         *
         * @param path the object path of the menu that changed, such as "/MenuBar"
         * @param revision the layout's new revision, as {@link DbusMenu#GetLayout} answers it from now on
         * @param parent the entry below which the menu changed; 0 for the whole menu
         * @throws DBusException when the path is not a valid object path
         */
        public LayoutUpdated(String path, UInt32 revision, int parent) throws DBusException {
            super(path, revision, parent);
            this.revision = revision;
            this.parent = parent;
        }
    }

    /**
     * Two values returned together.
     *
     * @param <A> the type of the first value
     * @param <B> the type of the second value
     */
    final class Pair<A, B> extends Tuple {
        /** The first value. */
        @Position(0)
        public final A first;
        /** The second value. */
        @Position(1)
        public final B second;

        /**
         * Pairs two values.
         *
         * @param first the first value
         * @param second the second value
         */
        public Pair(A first, B second) {
            this.first = first;
            this.second = second;
        }
    }

    /** An entry and its children; a child is a MenuNode inside a variant, as the protocol has it. */
    final class MenuNode extends Struct {
        /** The entry's number. */
        @Position(0)
        public final int id;
        /** The entry's properties by name. */
        @Position(1)
        public final Map<String, Variant<?>> properties;
        /** The entry's children, each a {@link MenuNode} in a variant. */
        @Position(2)
        public final List<Variant<?>> children;

        /**
         * Makes an entry.
         *
         * @param id the entry's number
         * @param properties the entry's properties by name
         * @param children the entry's children, each a {@link MenuNode} in a variant; empty for none
         */
        public MenuNode(int id, Map<String, Variant<?>> properties, List<Variant<?>> children) {
            this.id = id;
            this.properties = properties;
            this.children = children;
        }
    }

    /** The properties of one entry. */
    final class IdProperties extends Struct {
        /** The entry's number. */
        @Position(0)
        public final int id;
        /** The entry's properties by name. */
        @Position(1)
        public final Map<String, Variant<?>> properties;

        /**
         * Makes the properties of one entry.
         *
         * @param id the entry's number
         * @param properties the entry's properties by name
         */
        public IdProperties(int id, Map<String, Variant<?>> properties) {
            this.id = id;
            this.properties = properties;
        }
    }

    /** One event on one entry. */
    final class MenuEvent extends Struct {
        /** The entry the event is for. */
        @Position(0)
        public final int id;
        /** The kind of event, such as "clicked". */
        @Position(1)
        public final String eventId;
        /** Extra data of the event. */
        @Position(2)
        public final Variant<?> data;
        /** When the event happened, as the panel counts it. */
        @Position(3)
        public final UInt32 timestamp;

        /**
         * Makes an event.
         *
         * @param id the entry the event is for
         * @param eventId the kind of event, such as "clicked"
         * @param data extra data of the event
         * @param timestamp when the event happened, as the panel counts it
         */
        public MenuEvent(int id, String eventId, Variant<?> data, UInt32 timestamp) {
            this.id = id;
            this.eventId = eventId;
            this.data = data;
            this.timestamp = timestamp;
        }
    }
}
