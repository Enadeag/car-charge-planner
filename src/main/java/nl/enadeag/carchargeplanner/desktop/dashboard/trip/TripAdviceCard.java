package nl.enadeag.carchargeplanner.desktop.dashboard.trip;

import static nl.enadeag.carchargeplanner.desktop.theme.Labels.paragraph;

import nl.enadeag.carchargeplanner.desktop.dashboard.parts.Chip;
import nl.enadeag.carchargeplanner.desktop.dashboard.parts.ChipModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.parts.Glyphs;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * A trip's advice, below the day's main card: the trip, what to charge and when, and the battery level it starts from,
 * with Adjust to enter the level the car shows. A screen reader reads the advice as one sentence.
 */
public final class TripAdviceCard extends VBox {

    private final Button adjust;

    /**
     * Makes the card, without Adjust until {@link #onAdjust(Runnable)}.
     *
     * @param advice the trip's advice; never null
     * @param messages the texts, in the owner's language
     */
    public TripAdviceCard(TripAdviceModel advice, Messages messages) {
        adjust = new Button(messages.text(TextKey.TRIP_ADVICE_ADJUST));
        adjust.getStyleClass().addAll("flat", "small");
        adjust.setMinWidth(USE_PREF_SIZE);
        var hint = messages.text(TextKey.TRIP_ADVICE_ADJUST_HINT);
        adjust.setTooltip(new Tooltip(hint));
        adjust.setAccessibleHelp(hint);
        adjust.setVisible(false);
        adjust.setManaged(false);
        var assumed = paragraph(advice.level(), "note");
        HBox.setHgrow(assumed, Priority.ALWAYS);
        var level = new HBox(assumed, adjust);
        level.getStyleClass().add("trip-advice-level");
        getChildren().setAll(
                new Chip(new ChipModel(advice.source(), advice.label())),
                paragraph(advice.title(), "advice-title"),
                paragraph(advice.plan(), "advice-detail"),
                level);
        getStyleClass().addAll("card", "advice-card", "trip-advice", Glyphs.styleClass(advice.source()));
        setAccessibleRole(AccessibleRole.TEXT);
        setAccessibleText(advice.spoken());
        setFocusTraversable(true);
    }

    /**
     * Shows Adjust, which lets the owner enter the battery level the car shows.
     *
     * @param action what Adjust does; called on the JavaFX thread
     */
    public void onAdjust(Runnable action) {
        adjust.setOnAction(_ -> action.run());
        adjust.setVisible(true);
        adjust.setManaged(true);
    }
}
