package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.AppIcon;
import io.github.enadeag.carchargeplanner.desktop.LanguageChoice;
import io.github.enadeag.carchargeplanner.desktop.Messages;
import io.github.enadeag.carchargeplanner.desktop.SystemTheme;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import java.net.URI;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;

/**
 * Settings in a window of its own, beside the dashboard; the tray and the dashboard keep working while it is open.
 * It reads what is stored each time it opens, and keeps the owner's edits while it stays open.
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

    private final Stage stage = new Stage();
    private final Function<Messages, SettingsView> views;
    private final LanguageChoice language;
    // both belong to the JavaFX thread
    private SettingsView view;
    private Messages spoken;

    /**
     * Makes the window, hidden.
     *
     * @param stores where the settings are kept, read when the window opens
     * @param facts the version and the places of the files, for the About page
     * @param openLink opens a web page in the owner's browser; called on the JavaFX thread, so it should not block
     * @param saved called on the JavaFX thread after something was saved, so the plan is made again with it; it
     *     should hand the work on
     * @param runSetUp opens set-up once Settings closed; empty while the app has no set-up
     * @param colorScheme light or dark for the windows, followed while the app runs: the owner's appearance over the
     *     system's ({@link io.github.enadeag.carchargeplanner.desktop.Appearance#scheme}); never null
     * @param icon the app icon, for the title bar and the taskbar
     * @param language the language the texts are in, followed while the app runs; never null
     */
    public SettingsWindow(SettingsStores stores, AboutFacts facts, Consumer<URI> openLink, Runnable saved,
            Optional<Runnable> runSetUp, ObservableValue<ColorScheme> colorScheme, AppIcon icon,
            LanguageChoice language) {
        this.views = messages -> SettingsView.of(stores, facts, openLink, stage::hide, saved, runSetUp, messages);
        this.language = language;
        this.spoken = language.messages();
        this.view = views.apply(spoken);
        var scene = new Scene(view);
        new SystemTheme(colorScheme).style(scene);
        stage.setScene(scene);
        stage.setTitle(spoken.text(TextKey.SETTINGS_TITLE));
        stage.getIcons().setAll(icon.fxImages());
        double em = Font.getDefault().getSize();
        stage.setWidth(WIDTH_EM * em);
        stage.setHeight(HEIGHT_EM * em);
        stage.setOnCloseRequest(event -> {
            event.consume();
            view.cancel();
        });
        // later, also on the JavaFX thread: a Save chooses the language while its button is still handling the click
        language.onChange(chosen -> Platform.runLater(this::speak));
    }

    /**
     * Shows Settings on a category, in front. A window that is open already keeps the owner's edits; one that was
     * closed reads what is stored again.
     *
     * @param category the category to show
     */
    public void open(Category category) {
        Platform.runLater(() -> {
            if (!stage.isShowing()) {
                speak();
                view.reload();
            }
            view.show(category);
            stage.show();
            stage.setIconified(false);
            stage.toFront();
            stage.requestFocus();
        });
    }

    /** Makes Settings again in the language spoken now, unless it speaks it or holds edits that are not saved. */
    private void speak() {
        var now = language.messages();
        if (now == spoken || (stage.isShowing() && view.isChanged())) {
            return;
        }
        spoken = now;
        var before = view;
        view = views.apply(now);
        stage.getScene().setRoot(view);
        stage.setTitle(now.text(TextKey.SETTINGS_TITLE));
        if (stage.isShowing()) {
            view.continueFrom(before);
        }
    }
}
