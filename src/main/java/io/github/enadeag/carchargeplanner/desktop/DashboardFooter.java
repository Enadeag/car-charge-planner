package io.github.enadeag.carchargeplanner.desktop;

import atlantafx.base.theme.Styles;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/** The forecast's source and freshness, and the way to Away, which is not built yet, and to Settings. */
final class DashboardFooter extends HBox {

    private final Label forecast = Texts.paragraph("", "footer-text");

    private final Messages messages;
    private final Button settings = new Button();

    /**
     * Makes the footer, with an empty forecast text, the Away button switched off, and a Settings button that does
     * nothing until {@link #onSettings(Runnable)}.
     *
     * @param messages the texts, in the owner's language
     */
    DashboardFooter(Messages messages) {
        this.messages = messages;
        HBox.setHgrow(forecast, Priority.ALWAYS);
        forecast.setMaxWidth(Double.MAX_VALUE);
        settings.setText(messages.text(TextKey.FOOTER_SETTINGS));
        settings.getStyleClass().add(Styles.SMALL);
        settings.setMinWidth(USE_PREF_SIZE);
        getChildren().setAll(forecast, notYet(TextKey.FOOTER_AWAY), settings);
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

    private Button notYet(TextKey nameKey) {
        var name = messages.text(nameKey);
        var button = new Button(messages.text(TextKey.FOOTER_SOON, name));
        button.getStyleClass().add(Styles.SMALL);
        button.setDisable(true);
        button.setMinWidth(USE_PREF_SIZE);
        button.setAccessibleText(messages.text(TextKey.FOOTER_NOT_YET, name));
        return button;
    }
}
