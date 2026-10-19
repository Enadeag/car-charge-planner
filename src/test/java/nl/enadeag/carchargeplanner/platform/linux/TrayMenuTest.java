package nl.enadeag.carchargeplanner.platform.linux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.desktop.system.Tray.MenuLabels;
import nl.enadeag.carchargeplanner.desktop.system.TrayMenuEntry;
import java.util.ArrayList;
import java.util.List;
import org.freedesktop.dbus.errors.InvalidMethodArgument;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The menu as the panel reads it: its entries in order, and what it answers for an entry it does not have. */
class TrayMenuTest {

    private final List<TrayMenuEntry> clicked = new ArrayList<>();
    private final TrayMenu menu = new TrayMenu(new MenuLabels("Open dashboard", "Settings…", "About…", "Quit"),
            entry -> () -> clicked.add(entry));

    @Test
    void aLineSetsQuitApartFromTheOtherEntries() {
        var root = menu.GetLayout(0, -1, List.of()).second;

        assertThat(root.children).extracting(child -> {
            var node = (DbusMenu.MenuNode) child.getValue();
            var label = node.properties.get("label");
            return label != null ? label.getValue() : node.properties.get("type").getValue();
        }).containsExactly("Open dashboard", "Settings…", "About…", "separator", "Quit");
    }

    @Test
    void aClickOnTheLineDoesNothing() {
        var root = menu.GetLayout(0, -1, List.of()).second;
        var line = ((DbusMenu.MenuNode) root.children.get(3).getValue()).id;

        menu.Event(line, "clicked", new Variant<>(0), new UInt32(0));
        menu.Event(4, "clicked", new Variant<>(0), new UInt32(0));

        assertThat(clicked).containsExactly(TrayMenuEntry.QUIT);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 6, 99})
    void anEntryThatDoesNotExistIsAnErrorForThePanelNotACrash(int id) {
        assertThatThrownBy(() -> menu.GetLayout(id, -1, List.of())).isInstanceOf(InvalidMethodArgument.class);
        assertThatThrownBy(() -> menu.GetProperty(id, "label")).isInstanceOf(InvalidMethodArgument.class);
    }

    @Test
    void aPropertyAnEntryDoesNotHaveIsAnErrorForThePanel() {
        assertThat(menu.GetProperty(4, "label").getValue()).isEqualTo("Quit");
        assertThatThrownBy(() -> menu.GetProperty(0, "label")).isInstanceOf(InvalidMethodArgument.class);
    }
}
