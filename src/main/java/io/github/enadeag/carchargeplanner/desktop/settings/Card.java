package io.github.enadeag.carchargeplanner.desktop.settings;

import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Settings that belong together, under a title and a short explanation. */
final class Card extends VBox {

    /**
     * Makes the card.
     *
     * @param title the card's title, in the owner's language
     * @param explanation a sentence or two about the settings on it, in the owner's language; empty for none
     * @param content the rows and other nodes on the card, in order
     */
    Card(String title, String explanation, Node... content) {
        var heading = new Label(title);
        heading.getStyleClass().add("card-title");
        heading.setWrapText(true);
        heading.setAccessibleRole(AccessibleRole.TEXT);
        getChildren().add(heading);
        if (!explanation.isEmpty()) {
            getChildren().add(FieldRow.paragraph(explanation, "note"));
        }
        getChildren().addAll(content);
        getStyleClass().addAll("card", "settings-card");
    }
}
