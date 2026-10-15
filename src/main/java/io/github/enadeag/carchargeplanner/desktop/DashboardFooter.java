package io.github.enadeag.carchargeplanner.desktop;

import atlantafx.base.theme.Styles;
import java.util.List;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/** The forecast's source and freshness, and the way to the away periods and to Settings. */
final class DashboardFooter extends HBox {

    private final Label forecast = Texts.paragraph("", "footer-text");

    private final Button away = new Button();
    private final Button settings = new Button();

    /**
     * Makes the footer, with an empty forecast text, and Away and Settings buttons that do nothing until
     * {@link #onAway(Runnable)} and {@link #onSettings(Runnable)}.
     *
     * @param messages the texts, in the owner's language
     */
    DashboardFooter(Messages messages) {
        HBox.setHgrow(forecast, Priority.ALWAYS);
        forecast.setMaxWidth(Double.MAX_VALUE);
        for (var button : List.of(away, settings)) {
            button.getStyleClass().add(Styles.SMALL);
            button.setMinWidth(USE_PREF_SIZE);
        }
        away.setText(messages.text(TextKey.FOOTER_AWAY));
        settings.setText(messages.text(TextKey.FOOTER_SETTINGS));
        getChildren().setAll(forecast, away, settings);
        getStyleClass().add("dashboard-footer");
    }

    /**
     * Shows the forecast's source and freshness.
     *
     * @param text such as "Forecast: Open-Meteo · 10:51"; never null
     */
    void show(String text) {
        forecast.setText(text);
    }

    /**
     * Lets the Settings button open Settings.
     *
     * @param action what the button does; called on the JavaFX thread
     */
    void onSettings(Runnable action) {
        settings.setOnAction(event -> action.run());
    }

    /** Takes the Away and Settings buttons away, leaving the forecast's text. */
    void hideButtons() {
        getChildren().setAll(forecast);
    }

    /**
     * Lets the Away button open the away periods.
     *
     * @param action what the button does; called on the JavaFX thread
     */
    void onAway(Runnable action) {
        away.setOnAction(event -> action.run());
    }
}
