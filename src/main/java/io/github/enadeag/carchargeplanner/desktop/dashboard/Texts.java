package io.github.enadeag.carchargeplanner.desktop.dashboard;

import javafx.scene.control.Label;

/** Labels for the dashboard's cards. */
final class Texts {

    private Texts() {
    }

    /**
     * A label on one line.
     *
     * @param text the text; may be empty
     * @param styleClass the label's class in dashboard.css, such as "hero"
     * @return a new label
     */
    static Label label(String text, String styleClass) {
        var label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    /**
     * A label that wraps onto more lines rather than cutting its text short.
     *
     * @param text the text; may be empty
     * @param styleClass the label's class in dashboard.css, such as "note"
     * @return a new label
     */
    static Label paragraph(String text, String styleClass) {
        var label = label(text, styleClass);
        label.setWrapText(true);
        label.setMinHeight(Label.USE_PREF_SIZE);
        return label;
    }
}
