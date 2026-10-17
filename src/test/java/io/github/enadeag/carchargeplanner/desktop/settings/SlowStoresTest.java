package io.github.enadeag.carchargeplanner.desktop.settings;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.desktop.Appearance;
import io.github.enadeag.carchargeplanner.desktop.Background;
import io.github.enadeag.carchargeplanner.desktop.Fx;
import io.github.enadeag.carchargeplanner.desktop.FxTest;
import io.github.enadeag.carchargeplanner.desktop.Language;
import io.github.enadeag.carchargeplanner.desktop.LanguageChoice;
import io.github.enadeag.carchargeplanner.desktop.Scenes;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import io.github.enadeag.carchargeplanner.engine.config.ConfigFile;
import io.github.enadeag.carchargeplanner.engine.secrets.KeychainException;
import io.github.enadeag.carchargeplanner.engine.secrets.MemoryKeychain;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.beans.property.SimpleObjectProperty;
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
 * Settings and set-up over stores that take their time, as a keychain or the system's start at login can: the windows
 * open at once and say what they are waiting for, the JavaFX thread never waits with them, and a closed window is let
 * go of.
 */
@FxTest
class SlowStoresTest {

    @TempDir
    Path folder;

    private final LanguageChoice language = new LanguageChoice(Language.ENGLISH);
    private final SlowKeychain keychain = new SlowKeychain();

    /** A keychain whose reads wait until the test lets them go, as a Secret Service that takes its time. */
    private static final class SlowKeychain extends MemoryKeychain {
        final CountDownLatch letGo = new CountDownLatch(1);

        @Override
        public Optional<String> read(String name) throws KeychainException {
            try {
                if (!letGo.await(10, TimeUnit.SECONDS)) {
                    throw new KeychainException(KeychainException.Kind.UNAVAILABLE, "the test never let the read go");
                }
            } catch (InterruptedException e) {
                throw new KeychainException(KeychainException.Kind.UNAVAILABLE, "interrupted", e);
            }
            return super.read(name);
        }
    }

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
                Background.onVirtualThreads());
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

        var target = new ConfigFile(settings.configPath).read().planning().targetPct().value();
        assertThat(Fx.call(() -> ((TextField) settings.field(view, TextKey.FIELD_TARGET_CHARGE)).getText()))
                .isEqualTo(settings.messages.number(target, 0));
        assertThat(Fx.call(() -> planningPage(view).isDisabled())).isFalse();
        assertThat(texts(view)).doesNotContain(settings.messages.text(TextKey.SETTINGS_READING));
    }

    @Test
    void aSaveSaysItIsSavingAndASecondSaveMeanwhileDoesNothing() throws Exception {
        var settings = new SettingsFixture(folder, Language.ENGLISH, List.of(), new MemoryKeychain(),
                Background.onVirtualThreads());
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
    void setUpOpensAtOnceAndSaysItIsReadingWhileTheKeychainTakesItsTime() throws Exception {
        var setUp = new SetUpFixture(folder, Language.ENGLISH, keychain, Background.onVirtualThreads());
        var window = setUp.window(language, new SimpleObjectProperty<>(Appearance.SYSTEM));

        window.open();
        Fx.settle();

        var view = (SetUpView) shown(SetUpView.class).getScene().getRoot();
        assertThat(Fx.call(view::isReading)).isTrue();
        assertThat(texts(view)).contains(setUp.messages.text(TextKey.SETTINGS_READING));
        assertThat(Fx.call(() -> setUp.button(view, TextKey.SETUP_NEXT).isDisabled())).isTrue();
        Fx.run(view::next);

        keychain.letGo.countDown();
        SettingsFixture.waitFor(() -> !view.isReading());

        assertThat(Fx.call(view::step)).isEqualTo(SetUpStep.WELCOME);
        assertThat(Fx.call(() -> setUp.button(view, TextKey.SETUP_NEXT).isDisabled())).isFalse();
        assertThat(texts(view)).doesNotContain(setUp.messages.text(TextKey.SETTINGS_READING));
    }

    @Test
    void finishSaysItIsSavingAndClosesOnceItIsSaved() throws Exception {
        var setUp = new SetUpFixture(folder, Language.ENGLISH, new MemoryKeychain(), Background.onVirtualThreads())
                .withExample();
        var window = setUp.window(language, new SimpleObjectProperty<>(Appearance.SYSTEM));
        window.open();
        Fx.settle();
        var view = (SetUpView) shown(SetUpView.class).getScene().getRoot();
        SettingsFixture.waitFor(() -> !view.isReading());
        Fx.run(() -> {
            view.show(SetUpStep.APP);
            ((CheckBox) SetUpFixture.field(view, setUp.messages.text(TextKey.FIELD_START_AT_LOGIN))).setSelected(true);
        });
        setUp.preview(view);
        setUp.login.slow = new CountDownLatch(1);

        Fx.run(view::next);
        Fx.run(view::next);

        assertThat(texts(view)).contains(setUp.messages.text(TextKey.SETTINGS_SAVING));
        assertThat(setUp.finished).isZero();
        assertThat(anyShows(SetUpView.class)).isTrue();
        setUp.login.slow.countDown();
        SettingsFixture.waitFor(() -> !anyShowsOnFx(SetUpView.class));

        assertThat(setUp.finished).isEqualTo(1);
        assertThat(setUp.login.switches).hasValue(1);
        assertThat(setUp.login.on).isTrue();
    }

    private static boolean anyShowsOnFx(Class<?> root) {
        return Window.getWindows().stream()
                .anyMatch(window -> window.isShowing() && root.isInstance(window.getScene().getRoot()));
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

    @Test
    void aClosedSetUpWindowIsLetGoOfAndANewOneOpensNextTime() throws Exception {
        var setUp = new SetUpFixture(folder, Language.ENGLISH);
        var window = setUp.window(language, new SimpleObjectProperty<>(Appearance.SYSTEM));
        window.open();
        Fx.settle();
        var closedView = new WeakReference<>((SetUpView) shown(SetUpView.class).getScene().getRoot());

        Fx.run(() -> closedView.get().cancel());
        assertThat(anyShows(SetUpView.class)).isFalse();
        window.open();
        Fx.settle();

        assertThat(shown(SetUpView.class).getScene().getRoot()).isNotSameAs(closedView.get());
        for (int tries = 0; tries < 50 && closedView.get() != null; tries++) {
            System.gc();
            Fx.settle();
        }
        assertThat(closedView.get()).as("the closed window's view was collected").isNull();
    }
}
