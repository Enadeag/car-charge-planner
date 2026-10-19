package nl.enadeag.carchargeplanner.desktop.settings;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.Fx;
import nl.enadeag.carchargeplanner.desktop.FxTest;
import nl.enadeag.carchargeplanner.desktop.Scenes;
import nl.enadeag.carchargeplanner.desktop.settings.page.Category;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.LanguageChoice;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Settings follows the language the owner saves, without a restart. */
@FxTest
class SettingsWindowLanguageTest {

    @TempDir
    Path folder;

    private SettingsFixture settings;
    private final LanguageChoice language = new LanguageChoice(Language.ENGLISH);
    private SettingsWindow window;

    @BeforeEach
    void make() throws Exception {
        settings = new SettingsFixture(folder, Language.ENGLISH);
        window = settings.window(language);
    }

    @AfterEach
    void closeAll() throws Exception {
        Fx.run(() -> List.copyOf(Window.getWindows()).forEach(Window::hide));
    }

    private static Stage stage() throws Exception {
        return Fx.call(() -> Window.getWindows().stream()
                .filter(shown -> shown.isShowing() && shown.getScene().getRoot() instanceof SettingsView)
                .map(Stage.class::cast)
                .findFirst().orElseThrow());
    }

    private static SettingsView view() throws Exception {
        return (SettingsView) stage().getScene().getRoot();
    }

    /** What the view shows once laid out, as the next frame draws it: a new root gets its skins in the next pulse. */
    private static List<String> texts(SettingsView view) throws Exception {
        return Fx.call(() -> {
            view.applyCss();
            view.layout();
            return Scenes.visibleTexts(view);
        });
    }

    private void open(Category category) throws Exception {
        window.open(category);
        Fx.settle();
    }

    @Test
    void savingDutchRelabelsSettingsAtOnceOnTheSamePage() throws Exception {
        open(Category.APP);
        var before = view();
        Fx.run(() -> settings.choose(before, TextKey.FIELD_LANGUAGE, Optional.of(Language.DUTCH)));

        Fx.run(() -> SettingsFixture.save(before));
        Fx.settle();

        var stage = stage();
        var after = view();
        assertThat(language.messages().language()).isEqualTo(Language.DUTCH);
        assertThat(Fx.call(stage::getTitle)).isEqualTo("Instellingen – Car charge planner");
        assertThat(Fx.call(after::shown)).isEqualTo(Category.APP);
        assertThat(texts(after)).contains("Instellingen", "Opslaan", "Opgeslagen.",
                "Taal");
        assertThat(Fx.call(after::isChanged)).isFalse();
        @SuppressWarnings("unchecked")
        var chosen = Fx.call(() -> ((ComboBox<Optional<Language>>) SettingsFixture.field(after, "Taal")).getValue());
        assertThat(chosen).contains(Language.DUTCH);
        assertThat(languageNames(after, "Taal")).containsExactly("Zoals het systeem", "English", "Nederlands");
    }

    /** What the language list offers, as the owner reads it. */
    @SuppressWarnings("unchecked")
    private static List<String> languageNames(SettingsView view, String field) throws Exception {
        return Fx.call(() -> {
            var box = (ComboBox<Optional<Language>>) SettingsFixture.field(view, field);
            return box.getItems().stream().map(box.getConverter()::toString).toList();
        });
    }

    @Test
    void theLanguageListNamesEachLanguageInItselfInEitherLanguage() throws Exception {
        var english = settings.open();
        assertThat(languageNames(english, "Language")).containsExactly("Follow the system", "English", "Nederlands");
        settings.close();

        var dutch = new SettingsFixture(Files.createDirectory(folder.resolve("nl")), Language.DUTCH).open();
        assertThat(languageNames(dutch, "Taal")).containsExactly("Zoals het systeem", "English", "Nederlands");
    }

    @Test
    void followingTheSystemAgainRelabelsSettingsInTheSystemsLanguage() throws Exception {
        language.choose(Language.DUTCH);
        open(Category.APP);
        var before = view();
        Fx.run(() -> {
            ((ComboBox<?>) SettingsFixture.field(before, "Taal")).getSelectionModel().selectFirst();
            SettingsFixture.save(before);
        });
        Fx.settle();

        assertThat(language.messages().language()).isEqualTo(Language.ENGLISH);
        var stage = stage();
        var after = view();
        assertThat(Fx.call(stage::getTitle)).isEqualTo("Settings – Car charge planner");
        assertThat(texts(after)).contains("Saved.", "Language");
    }

    @Test
    void anUnsavedEditStaysAndSettingsSpeaksTheNewLanguageWhenItOpensAgain() throws Exception {
        open(Category.PLANNING);
        var before = view();
        Fx.run(() -> settings.type(before, TextKey.FIELD_TARGET_CHARGE, "90"));

        language.choose(Language.DUTCH);
        Fx.settle();

        assertThat(view()).isSameAs(before);
        assertThat(Fx.call(before::isChanged)).isTrue();
        assertThat(Fx.call(() -> ((TextField) settings.field(before, TextKey.FIELD_TARGET_CHARGE)).getText()))
                .isEqualTo("90");

        var closed = stage();
        Fx.run(() -> {
            before.cancel();
            settings.button(before, TextKey.SETTINGS_DISCARD).fire();
        });
        open(Category.PLANNING);

        var stage = stage();
        var reopened = view();
        assertThat(stage).isNotSameAs(closed);
        assertThat(Fx.call(stage::getTitle)).isEqualTo("Instellingen – Car charge planner");
        assertThat(texts(reopened)).contains("Opslaan");
        assertThat(Fx.call(reopened::isChanged)).isFalse();
    }
}
