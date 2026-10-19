package nl.enadeag.carchargeplanner.platform.windows;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.system.Tray.MenuLabels;
import nl.enadeag.carchargeplanner.desktop.system.Tray.TrayActions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrayMenuEntryTest {

    @Test
    void theMenuOffersOpenSettingsAboutAndQuitInTheOwnersLanguage() {
        var labels = new MenuLabels("Dashboard openen", "Instellingen…", "Over…", "Afsluiten");

        assertThat(Arrays.stream(TrayMenuEntry.values()).map(entry -> entry.label(labels)))
                .containsExactly("Dashboard openen", "Instellingen…", "Over…", "Afsluiten");
    }

    @Test
    void eachEntryRunsItsOwnAction() {
        var ran = new ArrayList<String>();
        var actions = new TrayActions(where -> ran.add("click"), () -> ran.add("open"), () -> ran.add("settings"),
                () -> ran.add("about"), () -> ran.add("quit"));

        for (var entry : TrayMenuEntry.values()) {
            entry.action(actions).run();
        }

        assertThat(ran).containsExactly("open", "settings", "about", "quit");
    }

    @Test
    void onlyQuitIsSetApartByALine() {
        assertThat(Arrays.stream(TrayMenuEntry.values()).filter(TrayMenuEntry::separated).toList())
                .isEqualTo(List.of(TrayMenuEntry.QUIT));
    }
}
