package nl.enadeag.carchargeplanner.desktop.settings;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.Fx;
import nl.enadeag.carchargeplanner.desktop.FxTest;
import nl.enadeag.carchargeplanner.desktop.Scenes;
import nl.enadeag.carchargeplanner.desktop.settings.page.Category;
import nl.enadeag.carchargeplanner.desktop.settings.store.SaveOutcome;
import nl.enadeag.carchargeplanner.desktop.system.PosixFilePrivacy;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.LanguageChoice;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.config.ConfigFile;
import nl.enadeag.carchargeplanner.engine.secrets.MemoryKeychain;
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Settings over stores that take their time, as a keychain or the system's start at login can: the window opens at
 * once and says what it is waiting for, the JavaFX thread never waits with it, and a closed window is let go of. The
 * same for set-up is in {@code SlowSetUpStoresTest}.
 */
@FxTest
class SlowStoresTest {

    private static final PrivateFiles FILES = new PrivateFiles(new PosixFilePrivacy());

    @TempDir
    Path folder;

    private final LanguageChoice language = new LanguageChoice(Language.ENGLISH);
    private final SlowKeychain keychain = new SlowKeychain();

    @AfterEach
    void closeAll() throws Exception {
        keychain.letGo.countDown();
        Fx.run(() -> List.copyOf(Window.getWindows()).forEach(Window::hide));
    }

    /** The window whose root is of this kind, on the JavaFX thread. */
    private static Stage shown(Class<?> root) throws Exception {
        return Fx.call(() -> Window.getWindows().stream()
                .filter(window -> window.isShowing() && root.isInstance(window.getScene().getRoot()))
                .map(Stage.class::cast)
                .findFirst().orElseThrow(() -> new AssertionError("no window shows a " + root.getSimpleName())));
    }

    private static boolean anyShows(Class<?> root) throws Exception {
        return Fx.call(() -> Window.getWindows().stream()
                .anyMatch(window -> window.isShowing() && root.isInstance(window.getScene().getRoot())));
    }

    private static List<String> texts(Parent root) throws Exception {
        return Fx.call(() -> {
            root.applyCss();
            root.layout();
            return Scenes.visibleTexts(root);
        });
    }

    private static Node planningPage(SettingsView view) {
        return view.pages().stream().filter(page -> page.category() == Category.PLANNING).findFirst().orElseThrow()
                .view();
    }

    @Test
    void settingsOpensAtOnceAndSaysItIsReadingWhileTheKeychainTakesItsTime() throws Exception {
        var settings = new SettingsFixture(folder, Language.ENGLISH, List.of(), keychain,
                VirtualThreads.background());
        var window = settings.window(language);

        window.open(Category.PLANNING);
        Fx.settle(); // the JavaFX thread is free: this would time out if it waited for the keychain

        var view = (SettingsView) shown(SettingsView.class).getScene().getRoot();
        assertThat(Fx.call(view::isReading)).isTrue();
        assertThat(texts(view)).contains(settings.messages.text(TextKey.SETTINGS_READING));
        assertThat(Fx.call(() -> planningPage(view).isDisabled())).as("the page cannot be edited yet").isTrue();
        assertThat(Fx.call(() -> settings.button(view, TextKey.SETTINGS_SAVE).isDisabled())).isTrue();

        keychain.letGo.countDown();
        SettingsFixture.waitFor(() -> !view.isReading());

        var target = new ConfigFile(settings.configPath, FILES).read().planning().targetPct().value();
        assertThat(Fx.call(() -> ((TextField) settings.field(view, TextKey.FIELD_TARGET_CHARGE)).getText()))
                .isEqualTo(settings.messages.number(target, 0));
        assertThat(Fx.call(() -> planningPage(view).isDisabled())).isFalse();
        assertThat(texts(view)).doesNotContain(settings.messages.text(TextKey.SETTINGS_READING));
    }

    @Test
    void aSaveSaysItIsSavingAndASecondSaveMeanwhileDoesNothing() throws Exception {
        var settings = new SettingsFixture(folder, Language.ENGLISH, List.of(), new MemoryKeychain(),
                VirtualThreads.background());
        var window = settings.window(language);
        window.open(Category.APP);
        Fx.settle();
        var view = (SettingsView) shown(SettingsView.class).getScene().getRoot();
        SettingsFixture.waitFor(() -> !view.isReading());
        settings.login.slow = new CountDownLatch(1);

        Fx.run(() -> {
            ((CheckBox) SettingsFixture.field(view, settings.messages.text(TextKey.FIELD_START_AT_LOGIN)))
                    .setSelected(true);
            view.save(() -> { });
            view.save(() -> { });
        });

        assertThat(texts(view)).contains(settings.messages.text(TextKey.SETTINGS_SAVING));
        assertThat(Fx.call(() -> settings.button(view, TextKey.SETTINGS_SAVE).isDisabled())).isTrue();
        settings.login.slow.countDown();
        SettingsFixture.waitFor(() -> view.outcome().isPresent());

        assertThat(Fx.call(view::outcome)).containsInstanceOf(SaveOutcome.Saved.class);
        assertThat(settings.login.switches).hasValue(1);
        assertThat(settings.login.on).isTrue();
        assertThat(texts(view)).contains(settings.messages.text(TextKey.SETTINGS_SAVED));
    }

    @Test
    void aClosedSettingsWindowIsLetGoOfAndANewOneOpensNextTime() throws Exception {
        var settings = new SettingsFixture(folder, Language.ENGLISH);
        var window = settings.window(language);
        window.open(Category.SOLAR);
        Fx.settle();
        var closed = new WeakReference<>(shown(SettingsView.class));
        var closedView = new WeakReference<>((SettingsView) closed.get().getScene().getRoot());

        Fx.run(() -> closedView.get().cancel());
        assertThat(anyShows(SettingsView.class)).isFalse();
        window.open(Category.SOLAR);
        Fx.settle();

        assertThat(shown(SettingsView.class)).isNotSameAs(closed.get());
        for (int tries = 0; tries < 50 && closedView.get() != null; tries++) {
            System.gc();
            Fx.settle();
        }
        assertThat(closedView.get()).as("the closed window's view was collected").isNull();
        assertThat(closed.get()).as("the closed window was collected").isNull();
    }
}
