package io.github.enadeag.carchargeplanner.desktop.settings;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.desktop.Contrast;
import io.github.enadeag.carchargeplanner.desktop.Fx;
import io.github.enadeag.carchargeplanner.desktop.Language;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import java.nio.file.Path;
import io.github.enadeag.carchargeplanner.engine.config.ConfigFile;
import io.github.enadeag.carchargeplanner.engine.config.TypicalValues;
import java.util.ArrayList;
import java.util.List;
import javafx.application.ColorScheme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * WCAG AA in Settings, light and dark: every category, with typical values tagged, a refused value, its marked
 * category and the question.
 */
class SettingsContrastTest {

    @TempDir
    Path folder;

    private SettingsFixture settings;

    @AfterEach
    void close() throws Exception {
        settings.close();
    }

    @ParameterizedTest
    @EnumSource(ColorScheme.class)
    void everyTextStandsOutEnough(ColorScheme scheme) throws Exception {
        settings = new SettingsFixture(folder, Language.ENGLISH, SettingsFixture.realProviders());
        var file = new ConfigFile(settings.configPath);
        var example = file.read();
        file.write(example.withSystem(example.system(),
                new TypicalValues(TypicalValues.SYSTEM_SETTINGS, List.of(TypicalValues.PANEL_SETTINGS))));
        var view = settings.open(true, scheme);

        var problems = Fx.call(() -> {
            settings.type(view, TextKey.FIELD_MINIMUM_CHARGE, "95");
            view.save();
            view.cancel();
            var found = new ArrayList<String>();
            for (var category : Category.values()) {
                view.show(category);
                view.applyCss();
                view.layout();
                found.addAll(Contrast.problemsIn(view));
            }
            return found;
        });

        assertThat(problems).isEmpty();
    }
}
