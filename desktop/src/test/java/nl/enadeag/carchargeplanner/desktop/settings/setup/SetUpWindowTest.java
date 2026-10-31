package nl.enadeag.carchargeplanner.desktop.settings.setup;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.Fx;
import nl.enadeag.carchargeplanner.desktop.FxTest;
import nl.enadeag.carchargeplanner.desktop.Scenes;
import nl.enadeag.carchargeplanner.desktop.app.AppSettings;
import nl.enadeag.carchargeplanner.desktop.app.Appearance;
import nl.enadeag.carchargeplanner.desktop.app.Background;
import nl.enadeag.carchargeplanner.desktop.app.DisplayMode;
import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.desktop.settings.SettingsWindow;
import nl.enadeag.carchargeplanner.desktop.settings.about.AboutFacts;
import nl.enadeag.carchargeplanner.desktop.settings.page.Category;
import nl.enadeag.carchargeplanner.desktop.system.AppIcon;
import nl.enadeag.carchargeplanner.desktop.system.PosixFilePrivacy;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.LanguageChoice;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.config.ConfigFile;
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles;
import nl.enadeag.carchargeplanner.host.AppName;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javafx.application.ColorScheme;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Set-up in its window: it speaks the language and shows the appearance chosen on its App step at once, takes over
 * what Settings saves meanwhile, keeps everything entered when it is made again, and opens from Settings filled in.
 */
@FxTest
class SetUpWindowTest {

    private static final PrivateFiles FILES = new PrivateFiles(new PosixFilePrivacy());

    private static final Messages DUTCH = new Messages(Language.DUTCH);

    @TempDir
    Path folder;

    private final LanguageChoice language = new LanguageChoice(Language.ENGLISH);
    private final SimpleObjectProperty<Appearance> saved = new SimpleObjectProperty<>(Appearance.SYSTEM);
    private SetUpFixture setUp;
    private SetUpWindow window;

    @BeforeEach
    void make() throws Exception {
        setUp = new SetUpFixture(folder, Language.ENGLISH);
        window = setUp.window(language, saved);
    }

    @AfterEach
    void closeAll() throws Exception {
        Fx.run(() -> List.copyOf(Window.getWindows()).forEach(Window::hide));
    }

    /** On the JavaFX thread. */
    private static Stage shownStage() {
        return Window.getWindows().stream()
                .filter(shown -> shown.isShowing() && shown.getScene().getRoot() instanceof SetUpView)
                .map(Stage.class::cast)
                .findFirst().orElseThrow(() -> new AssertionError("set-up is not open"));
    }

    private static SetUpView view() throws Exception {
        return Fx.call(() -> (SetUpView) shownStage().getScene().getRoot());
    }

    private SetUpView open() throws Exception {
        window.open();
        Fx.settle();
        return view();
    }

    @Test
    void choosingDutchOnTheAppStepSpeaksItAtOnceAndKeepsWhatWasEntered() throws Exception {
        var before = open();
        Fx.run(() -> {
            before.next();
            setUp.choose(before, TextKey.FIELD_UNITS, Units.IMPERIAL);
            setUp.choose(before, TextKey.FIELD_LANGUAGE, Optional.of(Language.DUTCH));
        });
        Fx.settle();

        var after = view();
        assertThat(after).isNotSameAs(before);
        assertThat(Fx.call(after::step)).isEqualTo(SetUpStep.APP);
        assertThat(Fx.call(() -> shownStage().getTitle())).isEqualTo(DUTCH.text(TextKey.SET_UP_TITLE, AppName.TITLE));
        assertThat(Fx.call(() -> SetUpFixture.texts(after))).contains(DUTCH.text(TextKey.SET_UP_NEXT));
        assertThat(Fx.call(() -> after.draft().app().units())).isEqualTo(Units.IMPERIAL);
        assertThat(language.messages().language()).isEqualTo(Language.ENGLISH); // nothing is saved before Finish
    }

    @Test
    void aValueTypedBeforeTheLanguageChangesIsStillThere() throws Exception {
        var before = open();
        Fx.run(() -> before.show(SetUpStep.CAR));
        Fx.run(() -> setUp.type(before, TextKey.FIELD_CAR_BATTERY, "77"));

        setUp.appSettings.write(new AppSettings(Optional.of(Language.DUTCH), Units.METRIC, DisplayMode.AUTOMATIC,
                Appearance.SYSTEM));
        language.choose(Language.DUTCH);
        Fx.settle();

        var after = view();
        assertThat(Fx.call(after::step)).isEqualTo(SetUpStep.CAR);
        assertThat(Fx.call(() -> ((TextField) SetUpFixture.field(after, DUTCH.text(TextKey.FIELD_CAR_BATTERY)))
                .getText())).isEqualTo(DUTCH.number(77, 1));
    }

    @Test
    void choosingDarkOnTheAppStepShowsItAtOnce() throws Exception {
        var view = open();
        Fx.run(() -> {
            view.next();
            setUp.choose(view, TextKey.FIELD_APPEARANCE, Appearance.DARK);
        });

        assertThat(Fx.call(() -> shownStage().getScene().getRoot().getStyleClass())).contains("dark");
    }

    @Test
    void anAppearanceSavedInSettingsMeanwhileIsTakenOver() throws Exception {
        var view = open();
        Fx.run(view::next);
        setUp.appSettings.write(new AppSettings(Optional.empty(), Units.METRIC, DisplayMode.AUTOMATIC,
                Appearance.DARK));

        Fx.run(() -> saved.set(Appearance.DARK));

        assertThat(Fx.call(() -> shownStage().getScene().getRoot().getStyleClass())).contains("dark");
        var after = view();
        assertThat(Fx.call(() -> after.draft().app().appearance())).isEqualTo(Appearance.DARK);
        assertThat(Fx.call(after::step)).isEqualTo(SetUpStep.APP);
    }

    @Test
    void finishClosesTheWindowAndTellsTheApp() throws Exception {
        setUp.withExample();
        var view = open();

        setUp.preview(view);
        Fx.run(view::next);

        assertThat(setUp.finished).isEqualTo(1);
        assertThat(Fx.call(() -> Window.getWindows().stream().anyMatch(shown -> shown.isShowing()
                && shown.getScene().getRoot() instanceof SetUpView))).isFalse();
    }

    @Test
    void settingsRunsSetUpAgainFilledInWithWhatIsStored() throws Exception {
        setUp.withExample();
        var settings = Fx.call(() -> new SettingsWindow(setUp.stores(), new AboutFacts("1.2.3", setUp.configPath,
                setUp.appSettings.path(), folder.resolve("data"), folder.resolve("data").resolve("app.0.log"),
                setUp.keysFile()), setUp.opened::add, chosen -> { }, window::open,
                Scenes.theme(new SimpleObjectProperty<>(ColorScheme.LIGHT)), new AppIcon(), AppName.TITLE, language,
                new Background(Runnable::run, Runnable::run)));
        settings.open(Category.APP);
        Fx.settle();

        Fx.run(() -> setUp.button(Window.getWindows().stream()
                .filter(shown -> shown.isShowing() && !(shown.getScene().getRoot() instanceof SetUpView))
                .findFirst().orElseThrow().getScene().getRoot(), TextKey.RUN_SET_UP).fire());
        Fx.settle();
        Fx.settle();

        var view = view();
        Fx.run(() -> view.show(SetUpStep.LOCATION));
        var stored = new ConfigFile(setUp.configPath, FILES).read();
        assertThat(Fx.call(() -> ((TextField) setUp.field(view, TextKey.FIELD_LATITUDE)).getText()))
                .isEqualTo(setUp.messages.number(stored.system().location().latitude(), 4));
        assertThat(Fx.call(() -> view.draft().config())).isEqualTo(stored);
    }
}
