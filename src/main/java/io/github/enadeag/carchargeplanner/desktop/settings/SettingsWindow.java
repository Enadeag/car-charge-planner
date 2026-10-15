package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.AppIcon;
import io.github.enadeag.carchargeplanner.desktop.Messages;
import io.github.enadeag.carchargeplanner.desktop.SystemTheme;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import java.net.URI;
import java.util.function.Consumer;
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
 * <p>Make it on the JavaFX thread; {@link #open(Category)} may be called from any thread.
 */
public final class SettingsWindow {

    private static final double WIDTH_EM = 56;
    private static final double HEIGHT_EM = 40;

    private final Stage stage = new Stage();
    private final SettingsView view;

    /**
     * Makes the window, hidden.
     *
     * @param stores where the settings are kept, read when the window opens
     * @param facts the version and the places of the files, for the About page
     * @param openLink opens a web page in the owner's browser; called on the JavaFX thread, so it should not block
     * @param saved called on the JavaFX thread after something was saved, so the plan is made again with it; it
     *     should hand the work on
     * @param colorScheme the system's light or dark, followed while the app runs
     *     ({@code Platform.getPreferences().colorSchemeProperty()}); never null
     * @param icon the app icon, for the title bar and the taskbar
     * @param messages the texts, in the owner's language
     */
    public SettingsWindow(SettingsStores stores, AboutFacts facts, Consumer<URI> openLink, Runnable saved,
            ObservableValue<ColorScheme> colorScheme, AppIcon icon, Messages messages) {
        view = SettingsView.of(stores, facts, openLink, stage::hide, saved, messages);
        var scene = new Scene(view);
        new SystemTheme(colorScheme).style(scene);
        stage.setScene(scene);
        stage.setTitle(messages.text(TextKey.SETTINGS_TITLE));
        stage.getIcons().setAll(icon.fxImages());
        double em = Font.getDefault().getSize();
        stage.setWidth(WIDTH_EM * em);
        stage.setHeight(HEIGHT_EM * em);
        stage.setOnCloseRequest(event -> {
            event.consume();
            view.cancel();
        });
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
                view.reload();
            }
            view.show(category);
            stage.show();
            stage.setIconified(false);
            stage.toFront();
            stage.requestFocus();
        });
    }
}
