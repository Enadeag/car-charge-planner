package io.github.enadeag.carchargeplanner.desktop.settings;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.desktop.Fx;
import io.github.enadeag.carchargeplanner.desktop.Language;
import io.github.enadeag.carchargeplanner.desktop.Scenes;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import io.github.enadeag.carchargeplanner.desktop.TextScale;
import io.github.enadeag.carchargeplanner.engine.config.ConfigFile;
import io.github.enadeag.carchargeplanner.engine.config.PanelArray;
import io.github.enadeag.carchargeplanner.engine.config.TypicalValues;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * In every language Settings shows each text whole and on its own, at the size it opens with and with the system's
 * text at 150 and 200 %: on every category, with every value
 * tagged as typical and two panel arrays, with a problem next to a field, with the question before discarding, and
 * without a config file.
 */
class SettingsLanguagesTest {

    @TempDir
    Path folder;

    private SettingsFixture settings;

    @AfterEach
    void close() throws Exception {
        settings.close();
    }

    private static List<String> everyCategory(SettingsView view) {
        var found = new ArrayList<String>();
        for (var category : Category.values()) {
            view.show(category);
            view.applyCss();
            view.layout();
            found.addAll(Scenes.cutOff(view));
            found.addAll(Scenes.overlapping(view));
        }
        return found;
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void noTextIsCutOff(Language language) throws Exception {
        settings = new SettingsFixture(folder, language, SettingsFixture.realProviders());
        var file = new ConfigFile(settings.configPath);
        var example = file.read();
        file.write(example.withSystem(example.system().withPanels(List.of(PanelArray.TYPICAL, PanelArray.TYPICAL)),
                new TypicalValues(TypicalValues.SYSTEM_SETTINGS, List.of(TypicalValues.PANEL_SETTINGS))));
        var view = settings.open(false);

        var problems = Fx.call(() -> {
            var found = everyCategory(view);
            settings.type(view, TextKey.FIELD_MINIMUM_CHARGE, "95");
            view.save();
            view.cancel();
            found.addAll(everyCategory(view));
            return found;
        });

        assertThat(problems).isEmpty();
    }

    static Stream<Arguments> languagesAndScales() {
        return Stream.of(Language.values()).flatMap(language -> Stream.of(TextScale.values())
                .map(scale -> Arguments.of(language, scale)));
    }

    /** As the owner's system makes the text larger, at 150 and 200 %, in a window as much larger as the screen allows. */
    @ParameterizedTest
    @MethodSource("languagesAndScales")
    void withLargerTextNoTextIsCutOffOrOverlapsEither(Language language, TextScale scale) throws Exception {
        settings = new SettingsFixture(folder, language, SettingsFixture.realProviders());
        var file = new ConfigFile(settings.configPath);
        var example = file.read();
        file.write(example.withSystem(example.system().withPanels(List.of(PanelArray.TYPICAL, PanelArray.TYPICAL)),
                new TypicalValues(TypicalValues.SYSTEM_SETTINGS, List.of(TypicalValues.PANEL_SETTINGS))));
        var view = settings.open(scale);

        var problems = Fx.call(() -> {
            var found = everyCategory(view);
            settings.type(view, TextKey.FIELD_MINIMUM_CHARGE, "95");
            view.save();
            view.cancel();
            found.addAll(everyCategory(view));
            return found;
        });

        assertThat(problems).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void noTextIsCutOffWithoutAConfigFile(Language language) throws Exception {
        settings = new SettingsFixture(folder, language);
        Files.delete(settings.configPath);
        var view = settings.open();

        assertThat(Fx.call(() -> everyCategory(view))).isEmpty();
    }
}
