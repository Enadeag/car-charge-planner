package nl.enadeag.carchargeplanner.desktop.dashboard;

import static nl.enadeag.carchargeplanner.desktop.theme.Labels.paragraph;

import nl.enadeag.carchargeplanner.desktop.dashboard.parts.Glyphs;
import java.util.List;
import javafx.scene.AccessibleRole;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The warning above the day or the message without a plan that providers refused their API key: a warning sign and a
 * line per provider. A screen reader reads it as one text, as it reads the cards below it.
 */
final class KeyRefusedCard extends HBox {

    /**
     * @param lines a line per provider, in the order they were asked; not empty
     */
    KeyRefusedCard(List<String> lines) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("a warning about refused API keys needs a line per provider");
        }
        var text = new VBox();
        lines.forEach(line -> text.getChildren().add(paragraph(line, "warning-text")));
        text.getStyleClass().add("warning-lines");
        HBox.setHgrow(text, Priority.ALWAYS);
        getChildren().setAll(Glyphs.warning(), text);
        getStyleClass().addAll("card", "warning-card");
        setAccessibleRole(AccessibleRole.TEXT);
        setAccessibleText(String.join(" ", lines));
        setFocusTraversable(true);
    }
}
