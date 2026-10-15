package io.github.enadeag.carchargeplanner.desktop;

import static io.github.enadeag.carchargeplanner.desktop.Texts.paragraph;

import io.github.enadeag.carchargeplanner.desktop.Chip.Source;
import javafx.scene.AccessibleRole;
import javafx.scene.layout.VBox;

/** Grid advice, below today's main card, when the sun will not keep the car above its minimum. */
final class AdviceCard extends VBox {

    /**
     * Makes the card, read by a screen reader as one sentence.
     *
     * @param advice what to charge from the grid and why; never null
     */
    AdviceCard(GridAdviceModel advice) {
        getChildren().setAll(
                new SourceChip(new Chip(Source.GRID, "Grid advice")),
                paragraph(advice.title(), "advice-title"),
                paragraph(advice.detail(), "advice-detail"));
        getStyleClass().addAll("card", "advice-card", "grid");
        setAccessibleRole(AccessibleRole.TEXT);
        setAccessibleText(advice.spoken());
        setFocusTraversable(true);
    }
}
