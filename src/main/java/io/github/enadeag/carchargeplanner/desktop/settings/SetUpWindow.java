package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.AppIcon;
import io.github.enadeag.carchargeplanner.desktop.Appearance;
import io.github.enadeag.carchargeplanner.desktop.LanguageChoice;
import io.github.enadeag.carchargeplanner.desktop.Messages;
import io.github.enadeag.carchargeplanner.desktop.SystemTheme;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import io.github.enadeag.carchargeplanner.engine.config.EngineConfig;
import java.net.URI;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;

/**
 * Set-up in a window of its own: the first time, before there is a config, and again from Settings, filled in with
 * what is stored. Nothing is saved until Finish.
 *
 * <p>Set-up speaks the language and shows the appearance chosen on its App step at once, before they are saved; when
 * the owner saves another language or appearance in Settings meanwhile, set-up takes them over. Either way set-up is
 * made again, on the same step, with everything entered so far.
 *
 * <p>Make it on the JavaFX thread; {@link #open()} may be called from any thread.
 */
public final class SetUpWindow {

    private static final double WIDTH_EM = 50;
    private static final double HEIGHT_EM = 46;

    private final Stage stage = new Stage();
    private final SettingsStorage storage;
    private final Function<Messages, SetUpView> views;
    private final LanguageChoice language;
    private final EngineConfig firstRun;
    // all belong to the JavaFX thread
    private final ObjectProperty<Appearance> appearance = new SimpleObjectProperty<>(Appearance.SYSTEM);
    private SetUpStart start;
    private SetUpView view;
    private Messages spoken;

    /**
     * Makes the window, hidden.
     *
     * @param stores where the settings are kept, read each time set-up opens, and the engine that checks, plans and
     *     searches
     * @param keysFile the file the API keys are kept in when there is no usable keychain, to tell the owner
     * @param openLink opens a web page in the owner's browser; called on the JavaFX thread, so it should not block
     * @param finished called on the JavaFX thread after Finish saved and set-up closed, so the app plans with what was
     *     saved and shows the dashboard; it should hand slow work on
     * @param timezone the time zone the home is assumed to be in until the owner chooses a place, such as the
     *     computer's own
     * @param looks the system's colour scheme and reduced motion, and the owner's saved appearance, followed while the
     *     app runs
     * @param icon the app icon, for the title bar and the taskbar
     * @param language the language the app speaks, followed while the app runs; never null
     */
    public SetUpWindow(SettingsStores stores, Path keysFile, Consumer<URI> openLink, Runnable finished,
            ZoneId timezone, Looks looks, AppIcon icon, LanguageChoice language) {
        this.storage = new SettingsStorage(stores);
        this.views = messages -> SetUpView.of(stores, keysFile, openLink, looks.reducedMotion(), stage::hide, finished,
                messages);
        this.language = language;
        this.firstRun = EngineConfig.firstRun(timezone, stores.engine().providers().getFirst().id());
        this.spoken = language.messages();
        this.view = made(spoken);
        var scene = new Scene(view);
        new SystemTheme(Appearance.scheme(appearance, looks.system())).style(scene);
        stage.setScene(scene);
        stage.setTitle(spoken.text(TextKey.SETUP_TITLE));
        stage.getIcons().setAll(icon.fxImages());
        double em = Font.getDefault().getSize();
        stage.setWidth(WIDTH_EM * em);
        stage.setHeight(HEIGHT_EM * em);
        stage.setOnCloseRequest(event -> {
            event.consume();
            view.cancel();
        });
        language.onChange(chosen -> Platform.runLater(this::takeOverSaved));
        looks.saved().addListener((property, before, now) -> takeOverSaved());
    }

    /**
     * The colours and motion the window follows.
     *
     * @param system the system's colour scheme ({@code Platform.getPreferences().colorSchemeProperty()}); never null
     * @param saved the appearance the owner saved, changed on the JavaFX thread; never null
     * @param reducedMotion true while the system asks for reduced motion
     *     ({@code Platform.getPreferences().reducedMotionProperty()}); never null
     */
    public record Looks(ObservableValue<ColorScheme> system, ObservableValue<Appearance> saved,
            ObservableValue<Boolean> reducedMotion) {
    }

    /**
     * Shows set-up in front, on its first step, filled in with what is stored, or with a first-run draft when there is
     * no config yet. A set-up that is open already stays as it is.
     */
    public void open() {
        Platform.runLater(() -> {
            if (!stage.isShowing()) {
                start = SetUpStart.read(storage);
                show(start.draft(firstRun), SetUpStep.WELCOME);
            }
            stage.show();
            stage.setIconified(false);
            stage.toFront();
            stage.requestFocus();
        });
    }

    /** A language or appearance saved in Settings while set-up is open takes the place of the one on its App step. */
    private void takeOverSaved() {
        if (stage.isShowing()) {
            show(view.draft().withLooksOf(storage.readApp()), view.step());
        }
    }

    /** Shows a draft on a step, in the language and appearance it holds; made again when the language differs. */
    private void show(SetUpDraft draft, SetUpStep step) {
        appearance.set(draft.app().appearance());
        var now = language.of(draft.app().language());
        if (now.language() != spoken.language()) {
            spoken = now;
            view = made(now);
            stage.getScene().setRoot(view);
            stage.setTitle(now.text(TextKey.SETUP_TITLE));
        }
        view.start(start, draft, step);
    }

    /** The owner's choices on the App step show at once; another language makes set-up again, a moment later. */
    private SetUpView made(Messages messages) {
        var made = views.apply(messages);
        made.onAppearanceChosen(appearance::set);
        made.onLanguageChosen(chosen -> {
            if (language.of(chosen).language() != spoken.language()) {
                Platform.runLater(() -> show(view.draft(), view.step()));
            }
        });
        return made;
    }
}
