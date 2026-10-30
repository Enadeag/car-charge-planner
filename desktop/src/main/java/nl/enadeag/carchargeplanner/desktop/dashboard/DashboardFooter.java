package nl.enadeag.carchargeplanner.desktop.dashboard;

import atlantafx.base.theme.Styles;
import nl.enadeag.carchargeplanner.desktop.theme.Labels;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import java.util.List;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/** The forecast's source and freshness, and the way to the weekly away periods and to Settings. */
final class DashboardFooter extends HBox {

    private final Label forecast = Labels.paragraph("", "footer-text");

    private final Button away = new Button();
    private final Button settings = new Button();

    /**
     * Makes the footer, with an empty forecast text, and Away and Settings buttons that do nothing until
     * {@link #onWeeklyAway(Runnable)} and {@link #onSettings(Runnable)}.
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
        var awayHint = messages.text(TextKey.FOOTER_AWAY_HINT);
        away.setTooltip(new Tooltip(awayHint));
        away.setAccessibleHelp(awayHint);
        settings.setText(messages.text(TextKey.FOOTER_SETTINGS));
        getChildren().setAll(forecast, away, settings);
        getStyleClass().add("dashboard-footer");
    }

    /**
     * Shows the forecast's source and freshness.
     *
     * @param text such as "Weather data: Open-Meteo.com (CC BY 4.0) · updated 10:51"; may be empty, never null
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
        settings.setOnAction(_ -> action.run());
    }

    /** Takes the Away and Settings buttons away, leaving the forecast's text. */
    void hideButtons() {
        getChildren().setAll(forecast);
    }

    /**
     * Lets the Away button open the weekly away periods.
     *
     * @param action what the button does; called on the JavaFX thread
     */
    void onWeeklyAway(Runnable action) {
        away.setOnAction(_ -> action.run());
    }
}
