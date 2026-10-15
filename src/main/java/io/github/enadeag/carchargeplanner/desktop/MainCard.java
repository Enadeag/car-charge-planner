package io.github.enadeag.carchargeplanner.desktop;

import static io.github.enadeag.carchargeplanner.desktop.Texts.label;
import static io.github.enadeag.carchargeplanner.desktop.Texts.paragraph;

import javafx.scene.AccessibleRole;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/** A day's main card: its charge window and what to set, or why there is none. A screen reader reads it as one sentence. */
final class MainCard extends VBox {

    MainCard(DayModel day) {
        getChildren().add(label(day.title() + " · " + day.heading(), "card-label"));
        if (!day.hero().isEmpty()) {
            getChildren().add(label(day.hero(), "hero"));
        }
        if (!day.facts().isEmpty()) {
            var facts = new FlowPane();
            day.facts().forEach(fact -> facts.getChildren().add(label(fact, "fact")));
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
