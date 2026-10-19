package nl.enadeag.carchargeplanner.desktop.settings.page;

import nl.enadeag.carchargeplanner.desktop.settings.field.FieldRow;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Settings that belong together, under a title and a short explanation. */
public final class Card extends VBox {

    private final Label heading = new Label();

    /**
     * Makes the card.
     *
     * @param title the card's title, in the owner's language
     * @param explanation a sentence or two about the settings on it, in the owner's language; empty for none
     * @param content the rows and other nodes on the card, in order
     */
    public Card(String title, String explanation, Node... content) {
        heading.setText(title);
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

    /**
     * Changes the card's title.
     *
     * @param title the new title, in the owner's language
     */
    public void setTitle(String title) {
        heading.setText(title);
    }
}
