package nl.enadeag.carchargeplanner.desktop.dashboard.advice;

import static nl.enadeag.carchargeplanner.desktop.theme.Labels.paragraph;

import nl.enadeag.carchargeplanner.desktop.dashboard.parts.Chip;
import nl.enadeag.carchargeplanner.desktop.dashboard.parts.ChipModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.parts.EnergySource;
import javafx.scene.AccessibleRole;
import javafx.scene.layout.VBox;

/** Grid advice, below today's day card, when the sun will not keep the car above its minimum. */
public final class GridAdviceCard extends VBox {

    /**
     * Makes the card, read by a screen reader as one sentence.
     *
     * @param advice what to charge from the grid and why; never null
     */
    public GridAdviceCard(GridAdviceModel advice) {
        getChildren().setAll(
                new Chip(new ChipModel(EnergySource.GRID, advice.label())),
                paragraph(advice.title(), "advice-title"),
                paragraph(advice.detail(), "advice-detail"));
        getStyleClass().addAll("card", "advice-card", "grid");
        setAccessibleRole(AccessibleRole.TEXT);
        setAccessibleText(advice.spoken());
        setFocusTraversable(true);
    }
}
