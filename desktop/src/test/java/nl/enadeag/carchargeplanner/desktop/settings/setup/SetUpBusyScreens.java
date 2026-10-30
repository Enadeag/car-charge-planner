package nl.enadeag.carchargeplanner.desktop.settings.setup;

import static nl.enadeag.carchargeplanner.desktop.settings.BusyScreens.laidOut;

import nl.enadeag.carchargeplanner.desktop.Fx;
import nl.enadeag.carchargeplanner.desktop.settings.BusyScreens;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.secrets.MemoryKeychain;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javafx.application.ColorScheme;
import javafx.scene.Node;

/**
 * Set-up while it waits: reading what is stored, saving, and after a read that failed; each screen is checked once it
 * is laid out. The slow work is held until the test lets it run.
 */
final class SetUpBusyScreens {

    private SetUpBusyScreens() {
    }

    static List<String> setUp(Path folder, Language language, ColorScheme scheme, Function<Node, List<String>> check)
            throws Exception {
        var held = new BusyScreens.Held();
        var setUp = new SetUpFixture(Files.createDirectories(folder.resolve("busy")), language, new MemoryKeychain(),
                held.background).withExample();
        try {
            var view = setUp.openReading(scheme);
            return Fx.call(() -> {
                var problems = new ArrayList<>(laidOut(view, check, setUp.messages.text(TextKey.SETTINGS_READING)));
                setUp.start(view);
                view.show(SetUpStep.PREVIEW);
                held.runAll();
                view.next();
                problems.addAll(laidOut(view, check, setUp.messages.text(TextKey.SETTINGS_SAVING)));
                var failure = setUp.messages.text(TextKey.UNEXPECTED_ERROR, "IllegalStateException");
                view.showUnreadable(failure);
                problems.addAll(laidOut(view, check, setUp.messages.text(TextKey.SETTINGS_READ_FAILED, failure)));
                return problems;
            });
        } finally {
            setUp.close();
        }
    }
}
