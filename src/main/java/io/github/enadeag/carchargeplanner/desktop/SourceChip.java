package io.github.enadeag.carchargeplanner.desktop;

import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/** A {@link Chip} on screen: its source's glyph and its text, on its source's soft background. */
final class SourceChip extends HBox {

    SourceChip(Chip chip) {
        var label = new Label(chip.text());
        label.setMinWidth(USE_PREF_SIZE);
        getChildren().setAll(Glyphs.of(chip.source()), label);
        getStyleClass().addAll("chip", Glyphs.styleClass(chip.source()));
        setMinWidth(USE_PREF_SIZE);
    }
}
