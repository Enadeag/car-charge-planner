package nl.enadeag.carchargeplanner.desktop.settings;

import nl.enadeag.carchargeplanner.desktop.Fx;
import nl.enadeag.carchargeplanner.desktop.Scenes;
import nl.enadeag.carchargeplanner.desktop.app.Background;
import nl.enadeag.carchargeplanner.desktop.settings.page.Category;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.secrets.MemoryKeychain;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javafx.application.ColorScheme;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.CheckBox;

/**
 * Settings while it waits: reading what is stored, saving, and after a read that failed; each screen is checked once
 * it is laid out. The slow work is held until the test lets it run. Set-up's are in {@code SetUpBusyScreens}.
 */
public final class BusyScreens {

    private BusyScreens() {
    }

    /** Work handed over and not yet run; its outcome is shown on the thread that runs it. */
    public static final class Held {
        private final List<Runnable> work = new ArrayList<>();
        /** Holds the work handed to it. */
        public final Background background = new Background(work::add, Runnable::run);

        /** Runs the work held so far. */
        public void runAll() {
            var now = List.copyOf(work);
            work.clear();
            now.forEach(Runnable::run);
        }
    }

    /** A keychain that breaks, so reading what is stored fails in a way nobody planned for. */
    private static final class Broken extends MemoryKeychain {
        @Override
        public Optional<String> read(String name) {
            throw new IllegalStateException("broken");
        }
    }

    static List<String> settings(Path folder, Language language, ColorScheme scheme,
            Function<Node, List<String>> check) throws Exception {
        var held = new Held();
        var settings = new SettingsFixture(Files.createDirectories(folder.resolve("busy")), language,
                SettingsFixture.realProviders(), new MemoryKeychain(), held.background);
        var found = new ArrayList<String>();
        try {
            var view = settings.open(true, scheme);
            found.addAll(Fx.call(() -> {
                var problems = new ArrayList<>(laidOut(view, check, settings.messages.text(TextKey.SETTINGS_READING)));
                held.runAll();
                view.show(Category.APP);
                ((CheckBox) SettingsFixture.field(view, settings.messages.text(TextKey.FIELD_START_AT_LOGIN)))
                        .setSelected(true);
                view.save(() -> { });
                problems.addAll(laidOut(view, check, settings.messages.text(TextKey.SETTINGS_SAVING)));
                held.runAll();
                return problems;
            }));
        } finally {
            settings.close();
        }
        var broken = new SettingsFixture(Files.createDirectories(folder.resolve("broken")), language,
                SettingsFixture.realProviders(), new Broken(), new Background(Runnable::run, Runnable::run));
        try {
            var view = broken.open(true, scheme);
            found.addAll(Fx.call(() -> laidOut(view, check, broken.messages.text(TextKey.SETTINGS_READ_FAILED,
                    broken.messages.text(TextKey.UNEXPECTED_ERROR, "IllegalStateException")))));
        } finally {
            broken.close();
        }
        return found;
    }

    /** The check's problems, and one more when the screen does not say what it should. */
    public static List<String> laidOut(Parent view, Function<Node, List<String>> check, String says) {
        view.applyCss();
        view.layout();
        var problems = new ArrayList<>(check.apply(view));
        if (!Scenes.visibleTexts(view).contains(says)) {
            problems.add("the screen does not say \"" + says + "\"");
        }
        return problems;
    }
}
