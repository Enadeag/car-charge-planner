package nl.enadeag.carchargeplanner.desktop.dashboard.parts;

import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/** A {@link ChipModel} on screen: its source's glyph and its text, on its source's soft background. */
public final class Chip extends HBox {

    /**
     * Makes the chip; it never gets narrower than its text.
     *
     * @param chip the figure and its source
     */
    public Chip(ChipModel chip) {
        var label = new Label(chip.text());
        label.setMinWidth(USE_PREF_SIZE);
        getChildren().setAll(Glyphs.of(chip.source()), label);
        getStyleClass().addAll("chip", Glyphs.styleClass(chip.source()));
        setMinWidth(USE_PREF_SIZE);
    }
}
