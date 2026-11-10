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

final class DashboardFooter extends HBox {

    private final Label forecast = Labels.paragraph("", "footer-text");

    private final Button away = new Button();
    private final Button settings = new Button();

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
     * @param text such as "Weather data: Open-Meteo.com (CC BY 4.0) · updated 10:51"; may be empty, never null
     */
    void show(String text) {
        forecast.setText(text);
    }

    /**
     * @param action called on the JavaFX thread
     */
    void onSettings(Runnable action) {
        settings.setOnAction(_ -> action.run());
    }

    void hideButtons() {
        getChildren().setAll(forecast);
    }

    /**
     * @param action called on the JavaFX thread
     */
    void onWeeklyAway(Runnable action) {
        away.setOnAction(_ -> action.run());
    }
}
