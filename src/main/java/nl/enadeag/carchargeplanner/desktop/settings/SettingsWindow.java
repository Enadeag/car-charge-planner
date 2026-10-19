package nl.enadeag.carchargeplanner.desktop.settings;

import nl.enadeag.carchargeplanner.desktop.app.AppSettings;
import nl.enadeag.carchargeplanner.desktop.app.Background;
import nl.enadeag.carchargeplanner.desktop.settings.about.AboutFacts;
import nl.enadeag.carchargeplanner.desktop.settings.page.Category;
import nl.enadeag.carchargeplanner.desktop.settings.page.WindowSize;
import nl.enadeag.carchargeplanner.desktop.settings.store.SettingsStores;
import nl.enadeag.carchargeplanner.desktop.system.AppIcon;
import nl.enadeag.carchargeplanner.desktop.text.LanguageChoice;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.desktop.theme.WindowTheme;
import java.net.URI;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Settings in a window of its own, beside the dashboard; the tray and the dashboard keep working while it is open.
 * The window is made each time it opens, and reads what is stored in the background; it keeps the owner's edits while
 * it stays open. Closed, it is let go of entirely, so its pages take no memory while the app runs in the tray.
 *
 * <p>When the owner chooses another language, Settings is made again in it, on the category it showed. Saving is what
 * chooses a language, and after a Save nothing is left unsaved; should the language change while edits are unsaved,
 * Settings keeps them, and speaks the new language from the next time it opens.
 *
 * <p>Make it on the JavaFX thread; {@link #open(Category)} may be called from any thread.
 */
public final class SettingsWindow {

    private static final double WIDTH_EM = 60;
    private static final double HEIGHT_EM = 46;

    /** Makes the view in a language; the runnable closes the window it is shown in. */
    private final BiFunction<Messages, Runnable, SettingsView> views;
    private final LanguageChoice language;
    private final WindowTheme theme;
    private final AppIcon icon;
    // the fields below belong to the JavaFX thread; all are null while the window is closed
    private Stage stage;
    private SettingsView view;
    private Messages spoken;

    /**
     * Makes what opens the window; no window is made yet.
     *
     * @param stores where the settings are kept, read when the window opens
     * @param facts the version and the places of the files, for the About page
     * @param openLink opens a web page in the owner's browser; called on the JavaFX thread, so it should not block
     * @param saved gets the app settings as stored, on the JavaFX thread, after something was saved, so the app follows
     *     them and plans again; it should hand slow work on
     * @param runSetUp opens set-up once Settings closed
     * @param colorScheme light or dark for the windows, followed while the app runs: the owner's appearance over the
     *     system's ({@link nl.enadeag.carchargeplanner.desktop.app.Appearance#scheme}); never null
     * @param icon the app icon, for the title bar and the taskbar
     * @param language the language the texts are in, followed while the app runs; never null
     * @param background reads and writes the stores away from the JavaFX thread
     */
    public SettingsWindow(SettingsStores stores, AboutFacts facts, Consumer<URI> openLink,
            Consumer<AppSettings> saved, Runnable runSetUp, ObservableValue<ColorScheme> colorScheme,
            AppIcon icon, LanguageChoice language, Background background) {
        this.views = (messages, close) -> SettingsView.of(stores, facts, openLink, close, saved, runSetUp, background,
                messages);
        this.language = language;
        this.theme = new WindowTheme(colorScheme);
        this.icon = icon;
        // later, also on the JavaFX thread: a Save chooses the language while its button is still handling the click
        language.onChange(_ -> Platform.runLater(this::speak));
    }

    /**
     * Shows Settings on a category, in front. A window that is open already keeps the owner's edits; otherwise a new
     * one opens and reads what is stored.
     *
     * @param category the category to show
     */
    public void open(Category category) {
        Platform.runLater(() -> {
            if (stage == null) {
                make();
                view.reload();
            }
            view.show(category);
            stage.show();
            stage.setIconified(false);
            stage.toFront();
            stage.requestFocus();
        });
    }

    /** Makes the window and its view, in the language spoken now. */
    private void make() {
        var made = new Stage();
        stage = made;
        spoken = language.messages();
        view = views.apply(spoken, () -> close(made));
        var scene = new Scene(view);
        theme.style(scene);
        made.setScene(scene);
        made.setTitle(spoken.text(TextKey.SETTINGS_TITLE));
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
        theme.forget(closing.getScene());
        closing.close();
        stage = null;
        view = null;
        spoken = null;
    }

    /** Makes Settings again in the language spoken now, unless it is closed, speaks it or holds unsaved edits. */
    private void speak() {
        var now = language.messages();
        if (stage == null || now == spoken || view.isChanged()) {
            return;
        }
        spoken = now;
        var before = view;
        var shown = stage;
        view = views.apply(now, () -> close(shown));
        stage.getScene().setRoot(view);
        stage.setTitle(now.text(TextKey.SETTINGS_TITLE));
        view.continueFrom(before);
    }
}
