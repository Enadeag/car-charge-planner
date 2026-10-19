package nl.enadeag.carchargeplanner.desktop.settings.setup;

import nl.enadeag.carchargeplanner.desktop.app.AppSettings;
import nl.enadeag.carchargeplanner.desktop.app.Appearance;
import nl.enadeag.carchargeplanner.desktop.app.Background;
import nl.enadeag.carchargeplanner.desktop.settings.page.WindowSize;
import nl.enadeag.carchargeplanner.desktop.settings.store.SettingsStorage;
import nl.enadeag.carchargeplanner.desktop.settings.store.SettingsStores;
import nl.enadeag.carchargeplanner.desktop.system.AppIcon;
import nl.enadeag.carchargeplanner.desktop.text.LanguageChoice;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.desktop.theme.WindowTheme;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import java.net.URI;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Set-up in a window of its own: the first time, before there is a config, and again from Settings, filled in with
 * what is stored. Nothing is saved until Finish. The window is made each time set-up opens, and reads what is stored
 * in the background; closed, it is let go of entirely.
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

    private final SettingsStorage storage;
    /** Makes the view in a language; the runnable closes the window it is shown in. */
    private final BiFunction<Messages, Runnable, SetUpView> views;
    private final LanguageChoice language;
    private final EngineConfig firstRun;
    private final Looks looks;
    private final AppIcon icon;
    private final Background background;
    // the fields below belong to the JavaFX thread; stage and view are null while the window is closed, start until
    // what is stored was read
    private final ObjectProperty<Appearance> appearance = new SimpleObjectProperty<>(Appearance.SYSTEM);
    private Stage stage;
    private SettingsStorage.Stored start;
    private SetUpView view;
    private Messages spoken;

    /**
     * Makes what opens the window; no window is made yet.
     *
     * @param stores where the settings are kept, read each time set-up opens, and the engine that checks, plans and
     *     searches
     * @param keysFile the file the API keys are kept in when there is no usable keychain, to tell the owner
     * @param openLink opens a web page in the owner's browser; called on the JavaFX thread, so it should not block
     * @param finished gets the app settings Finish saved, on the JavaFX thread, after set-up closed, so the app follows
     *     them, plans with what was saved and shows the dashboard; it should hand slow work on
     * @param timezone the time zone the home is assumed to be in until the owner chooses a place, such as the
     *     computer's own
     * @param looks the system's colour scheme and reduced motion, and the owner's saved appearance, followed while the
     *     app runs
     * @param icon the app icon, for the title bar and the taskbar
     * @param language the language the app speaks, followed while the app runs; never null
     * @param background reads and writes the stores, and asks the providers, away from the JavaFX thread
     */
    public SetUpWindow(SettingsStores stores, Path keysFile, Consumer<URI> openLink, Consumer<AppSettings> finished,
            ZoneId timezone, Looks looks, AppIcon icon, LanguageChoice language, Background background) {
        this.storage = new SettingsStorage(stores);
        this.views = (messages, close) -> SetUpView.of(stores, keysFile, openLink, looks.reducedMotion(), close,
                finished, background, messages);
        this.language = language;
        this.firstRun = EngineConfig.setUpDraft(timezone, stores.engine().providers().getFirst().id());
        this.looks = looks;
        this.icon = icon;
        this.background = background;
        language.onChange(_ -> Platform.runLater(this::takeOverSaved));
        looks.saved().addListener((_, _, _) -> takeOverSaved());
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
     * no config yet; until that is read, it says so. A set-up that is open already stays as it is.
     */
    public void open() {
        Platform.runLater(() -> {
            if (stage == null) {
                make();
                var reading = stage;
                background.run("reading the settings for set-up", storage::read, read -> {
                    if (stage == reading) {
                        start = read;
                        show(SetUpDraft.of(read, firstRun), SetUpStep.WELCOME);
                    }
                }, failure -> {
                    if (stage == reading) {
                        view.showUnreadable(Background.describe(failure, spoken));
                    }
                });
            }
            stage.show();
            stage.setIconified(false);
            stage.toFront();
            stage.requestFocus();
        });
    }

    /** Makes the window and its view, in the language spoken now, saying that what is stored is being read. */
    private void make() {
        var made = new Stage();
        stage = made;
        start = null;
        appearance.set(Appearance.SYSTEM);
        spoken = language.messages();
        view = makeView(spoken, made);
        var scene = new Scene(view);
        var theme = new WindowTheme(Appearance.scheme(appearance, looks.system()));
        theme.style(scene);
        made.setScene(scene);
        made.setTitle(spoken.text(TextKey.SETUP_TITLE));
        made.getIcons().setAll(icon.fxImages());
        var size = WindowSize.on(WIDTH_EM, HEIGHT_EM, Font.getDefault().getSize(),
                Screen.getPrimary().getVisualBounds());
        made.setWidth(size.getWidth());
        made.setHeight(size.getHeight());
        made.setOnCloseRequest(event -> {
            event.consume();
            view.cancel();
        });
    }

    /** Closes the window and lets go of it; a view of a window closed before changes nothing. */
    private void close(Stage closing) {
        if (closing != stage) {
            return;
        }
        closing.close();
        stage = null;
        view = null;
        start = null;
        spoken = null;
    }

    /** A language or appearance saved in Settings while set-up is open takes the place of the one on its App step. */
    private void takeOverSaved() {
        if (stage == null || start == null) {
            return; // set-up reads what is stored when it opens, and when its reading is done
        }
        var shown = stage;
        background.run("reading the app settings for set-up", storage::readApp, app -> {
            if (stage == shown && start != null) {
                show(view.draft().withLanguageAndAppearanceOf(app), view.step());
            }
        }, failure -> { });
    }

    /** Shows a draft on a step, in the language and appearance it holds; made again when the language differs. */
    private void show(SetUpDraft draft, SetUpStep step) {
        appearance.set(draft.app().appearance());
        var now = draft.app().language().map(language::of).orElseGet(language::ofSystem);
        if (now.language() != spoken.language()) {
            spoken = now;
            view = makeView(now, stage);
            stage.getScene().setRoot(view);
            stage.setTitle(now.text(TextKey.SETUP_TITLE));
        }
        view.start(start, draft, step);
    }

    /** The owner's choices on the App step show at once; another language makes set-up again, a moment later. */
    private SetUpView makeView(Messages messages, Stage in) {
        var made = views.apply(messages, () -> close(in));
        made.onAppearanceChosen(appearance::set);
        made.onLanguageChosen(chosen -> {
            if (stage == in && start != null
                    && chosen.map(language::of).orElseGet(language::ofSystem).language() != spoken.language()) {
                Platform.runLater(() -> {
                    if (stage == in) {
                        show(view.draft(), view.step());
                    }
                });
            }
        });
        return made;
    }
}
