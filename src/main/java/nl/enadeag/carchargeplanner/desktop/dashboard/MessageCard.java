package nl.enadeag.carchargeplanner.desktop.dashboard;

import static nl.enadeag.carchargeplanner.desktop.theme.Labels.paragraph;

import nl.enadeag.carchargeplanner.desktop.dashboard.outlook.DashboardModel;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Why there is no plan to show: still fetching, not set up, or no forecast; before the first set-up, a way into it. */
final class MessageCard extends VBox {

    /**
     * Makes the card.
     *
     * @param message what to say; an empty detail is left out
     * @param canOpenSetUp true when the dashboard can open set-up; the card offers it only then, when the message asks
     *     for set-up
     * @param setUp opens set-up
     * @param messages the texts, in the owner's language
     */
    MessageCard(DashboardModel.Message message, boolean canOpenSetUp, Runnable setUp, Messages messages) {
        var text = new VBox(paragraph(message.headline(), "message-headline"));
        if (!message.detail().isEmpty()) {
            text.getChildren().add(paragraph(message.detail(), "message-detail"));
        }
        text.setAccessibleRole(AccessibleRole.TEXT);
        text.setAccessibleText(message.detail().isEmpty() ? message.headline()
                : message.headline() + ". " + message.detail());
        text.setFocusTraversable(true);
        text.getStyleClass().add("message-text");
        getChildren().add(text);
        if (message.offersSetUp() && canOpenSetUp) {
            var button = new Button(messages.text(TextKey.START_SETUP));
            button.getStyleClass().add("accent");
            button.setOnAction(_ -> setUp.run());
            var line = new HBox(button);
            line.getStyleClass().add("message-actions");
            getChildren().add(line);
        }
        getStyleClass().add("card");
    }
}
