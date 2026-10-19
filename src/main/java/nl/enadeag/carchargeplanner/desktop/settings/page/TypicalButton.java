package nl.enadeag.carchargeplanner.desktop.settings.page;

import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;

/** The line at the bottom of a card with "I don't know: use typical values". */
public final class TypicalButton {

    private TypicalButton() {
    }

    /**
     * Makes the line with its button.
     *
     * @param action fills the card's fields with typical values and tags them, on the JavaFX thread
     * @param cardTitle the card's title, which a screen reader reads with the button: a page can have more than one
     * @param messages the texts, in the owner's language
     * @return the line, to put on a card
     */
    public static HBox line(Runnable action, String cardTitle, Messages messages) {
        var button = new Button(messages.text(TextKey.USE_TYPICAL));
        button.setAccessibleText(messages.text(TextKey.IN_GROUP, button.getText(), cardTitle));
        button.setOnAction(_ -> action.run());
        var line = new HBox(button);
        line.getStyleClass().add("card-buttons");
        return line;
    }
}
