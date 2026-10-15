package io.github.enadeag.carchargeplanner.desktop;

import static io.github.enadeag.carchargeplanner.desktop.Texts.paragraph;

import javafx.scene.AccessibleRole;
import javafx.scene.layout.VBox;

/** Why there is no plan to show: still fetching, not set up, or no forecast. */
final class MessageCard extends VBox {

    /**
     * Makes the card.
     *
     * @param message what to say; an empty detail is left out
     */
    MessageCard(DashboardModel.Message message) {
        getChildren().add(paragraph(message.headline(), "message-headline"));
        if (!message.detail().isEmpty()) {
            getChildren().add(paragraph(message.detail(), "message-detail"));
        }
        getStyleClass().add("card");
        setAccessibleRole(AccessibleRole.TEXT);
        setAccessibleText(message.detail().isEmpty() ? message.headline() : message.headline() + ". " + message.detail());
        setFocusTraversable(true);
    }
}
