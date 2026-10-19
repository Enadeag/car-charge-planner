package nl.enadeag.carchargeplanner.platform.linux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.freedesktop.dbus.errors.InvalidMethodArgument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** What the menu answers a panel that asks for an entry it does not have. */
class TrayMenuTest {

    private final TrayMenu menu = new TrayMenu(List.of("Open dashboard", "Quit"), List.of(() -> { }, () -> { }));

    @ParameterizedTest
    @ValueSource(ints = {-1, 3, 99})
    void anEntryThatDoesNotExistIsAnErrorForThePanelNotACrash(int id) {
        assertThatThrownBy(() -> menu.GetLayout(id, -1, List.of())).isInstanceOf(InvalidMethodArgument.class);
        assertThatThrownBy(() -> menu.GetProperty(id, "label")).isInstanceOf(InvalidMethodArgument.class);
    }

    @Test
    void aPropertyAnEntryDoesNotHaveIsAnErrorForThePanel() {
        assertThat(menu.GetProperty(2, "label").getValue()).isEqualTo("Quit");
        assertThatThrownBy(() -> menu.GetProperty(0, "label")).isInstanceOf(InvalidMethodArgument.class);
    }
}
