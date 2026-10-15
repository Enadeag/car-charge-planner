package io.github.enadeag.carchargeplanner.desktop;

import javafx.scene.control.Label;

/** Labels for the dashboard's cards. */
final class Texts {

    private Texts() {
    }

    static Label label(String text, String styleClass) {
        var label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    /** A label that wraps onto more lines rather than cutting its text short. */
    static Label paragraph(String text, String styleClass) {
        var label = label(text, styleClass);
        label.setWrapText(true);
        label.setMinHeight(Label.USE_PREF_SIZE);
        return label;
    }
}
