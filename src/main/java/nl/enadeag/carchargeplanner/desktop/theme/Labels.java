package nl.enadeag.carchargeplanner.desktop.theme;

import javafx.scene.control.Label;

/** Labels for the desktop's windows: the dashboard and Settings build their texts with these. */
public final class Labels {

    private Labels() {
    }

    /**
     * A label on one line.
     *
     * @param text the text; may be empty
     * @param styleClass the label's class in the shared stylesheet, such as "hero"
     * @return a new label
     */
    public static Label line(String text, String styleClass) {
        var label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    /**
     * A label that wraps onto more lines rather than cutting its text short.
     *
     * @param text the text; may be empty
     * @param styleClass the label's class in the shared stylesheet, such as "note"
     * @return a new label
     */
    public static Label paragraph(String text, String styleClass) {
        var label = line(text, styleClass);
        label.setWrapText(true);
        label.setMinHeight(Label.USE_PREF_SIZE);
        return label;
    }
}
