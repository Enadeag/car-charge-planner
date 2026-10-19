package nl.enadeag.carchargeplanner.desktop.dashboard.day;

import static nl.enadeag.carchargeplanner.desktop.theme.Labels.line;
import static nl.enadeag.carchargeplanner.desktop.theme.Labels.paragraph;

import nl.enadeag.carchargeplanner.desktop.dashboard.parts.Glyphs;
import nl.enadeag.carchargeplanner.desktop.dashboard.parts.SourceChip;
import javafx.scene.AccessibleRole;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/** A day's main card: its charge window and what to set, or why there is none. A screen reader reads it as one sentence. */
public final class MainCard extends VBox {

    /**
     * Makes the card for one day.
     *
     * @param day the day to show; its empty texts and lists are left out of the card
     */
    public MainCard(DayModel day) {
        getChildren().add(line(day.title() + " · " + day.heading(), "card-label"));
        if (!day.hero().isEmpty()) {
            getChildren().add(line(day.hero(), "hero"));
        }
        if (!day.facts().isEmpty()) {
            var facts = new FlowPane();
            day.facts().forEach(fact -> facts.getChildren().add(line(fact, "fact")));
            facts.getStyleClass().add("figures");
            getChildren().add(facts);
        }
        var chips = new FlowPane();
        day.chips().forEach(chip -> chips.getChildren().add(new SourceChip(chip)));
        chips.getStyleClass().add("chips");
        getChildren().add(chips);
        if (!day.note().isEmpty()) {
            getChildren().add(paragraph(day.note(), "note"));
        }
        getStyleClass().addAll("card", "main-card", Glyphs.styleClass(day.kind()));
        setAccessibleRole(AccessibleRole.TEXT);
        setAccessibleText(day.spoken());
        setFocusTraversable(true);
    }
}
