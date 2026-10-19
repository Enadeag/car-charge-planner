package nl.enadeag.carchargeplanner.desktop.settings.setup;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.Fx;
import nl.enadeag.carchargeplanner.desktop.FxTest;
import nl.enadeag.carchargeplanner.desktop.Scenes;
import nl.enadeag.carchargeplanner.desktop.app.Appearance;
import nl.enadeag.carchargeplanner.desktop.settings.SettingsFixture;
import nl.enadeag.carchargeplanner.desktop.settings.SlowKeychain;
import nl.enadeag.carchargeplanner.desktop.settings.VirtualThreads;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.LanguageChoice;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.secrets.MemoryKeychain;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Parent;
import javafx.scene.control.CheckBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Set-up over stores that take their time, as a keychain or the system's start at login can: the window opens at once
 * and says what it is waiting for, the JavaFX thread never waits with it, and a closed window is let go of.
 */
@FxTest
class SlowSetUpStoresTest {

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
        return Fx.call(() -> anyShowsOnFx(root));
    }

    private static boolean anyShowsOnFx(Class<?> root) {
        return Window.getWindows().stream()
                .anyMatch(window -> window.isShowing() && root.isInstance(window.getScene().getRoot()));
    }

    private static List<String> texts(Parent root) throws Exception {
        return Fx.call(() -> {
            root.applyCss();
            root.layout();
            return Scenes.visibleTexts(root);
        });
    }

    @Test
    void setUpOpensAtOnceAndSaysItIsReadingWhileTheKeychainTakesItsTime() throws Exception {
        var setUp = new SetUpFixture(folder, Language.ENGLISH, keychain, VirtualThreads.background());
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
        var setUp = new SetUpFixture(folder, Language.ENGLISH, new MemoryKeychain(), VirtualThreads.background())
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
