package io.github.enadeag.carchargeplanner.platform.linux;

import java.util.List;
import java.util.Map;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.Tuple;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.MethodNoReply;
import org.freedesktop.dbus.annotations.Position;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

/** The menu protocol the panel uses to show a tray item's menu natively. */
@DBusInterfaceName("com.canonical.dbusmenu")
public interface DbusMenu extends DBusInterface {

    Pair<UInt32, MenuNode> GetLayout(int parentId, int recursionDepth, List<String> propertyNames);

    List<IdProperties> GetGroupProperties(List<Integer> ids, List<String> propertyNames);

    Variant<?> GetProperty(int id, String name);

    /** A click on an entry arrives as the event "clicked". The panel needs no answer. */
    @MethodNoReply
    void Event(int id, String eventId, Variant<?> data, UInt32 timestamp);

    List<Integer> EventGroup(List<MenuEvent> events);

    boolean AboutToShow(int id);

    Pair<List<Integer>, List<Integer>> AboutToShowGroup(List<Integer> ids);

    final class Pair<A, B> extends Tuple {
        @Position(0)
        public final A first;
        @Position(1)
        public final B second;

        public Pair(A first, B second) {
            this.first = first;
            this.second = second;
        }
    }

    /** An entry and its children; a child is a MenuNode inside a variant, as the protocol has it. */
    final class MenuNode extends Struct {
        @Position(0)
        public final int id;
        @Position(1)
        public final Map<String, Variant<?>> properties;
        @Position(2)
        public final List<Variant<?>> children;

        public MenuNode(int id, Map<String, Variant<?>> properties, List<Variant<?>> children) {
            this.id = id;
            this.properties = properties;
            this.children = children;
        }
    }

    final class IdProperties extends Struct {
        @Position(0)
        public final int id;
        @Position(1)
        public final Map<String, Variant<?>> properties;

        public IdProperties(int id, Map<String, Variant<?>> properties) {
            this.id = id;
            this.properties = properties;
        }
    }

    final class MenuEvent extends Struct {
        @Position(0)
        public final int id;
        @Position(1)
        public final String eventId;
        @Position(2)
        public final Variant<?> data;
        @Position(3)
        public final UInt32 timestamp;

        public MenuEvent(int id, String eventId, Variant<?> data, UInt32 timestamp) {
            this.id = id;
            this.eventId = eventId;
            this.data = data;
            this.timestamp = timestamp;
        }
    }
}
