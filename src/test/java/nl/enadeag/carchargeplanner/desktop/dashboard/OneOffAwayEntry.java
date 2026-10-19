package nl.enadeag.carchargeplanner.desktop.dashboard;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;

/**
 * The open away entry of the first away card below a node, used as the owner uses it: by its controls. Call on the
 * JavaFX thread, once Add opened the entry.
 */
final class OneOffAwayEntry {

    private OneOffAwayEntry() {
    }

    /**
     * Chooses part of the day and types its times.
     *
     * @param root the dashboard, its window's scene root, or a page that holds the away card
     * @param start the start as typed, such as "10:00"
     * @param end the end as typed, such as "16:30"
     */
    static void enterTimes(Node root, String start, String end) {
        var entry = entry(root);
        var kinds = entry.lookup(".away-kinds").lookupAll(".radio-button").stream().map(RadioButton.class::cast)
                .toList();
        kinds.getLast().setSelected(true);
        var times = entry.lookupAll(".away-time").stream().map(TextField.class::cast).toList();
        times.getFirst().setText(start);
        times.getLast().setText(end);
    }

    /**
     * Presses Save.
     *
     * @param root the dashboard, its window's scene root, or a page that holds the away card
     */
    static void save(Node root) {
        entry(root).lookup(".away-buttons").lookupAll(".button").stream().map(Button.class::cast)
                .findFirst().orElseThrow().fire();
    }

    private static Node entry(Node root) {
        return root.lookup(".away-card").lookup(".away-editor");
    }
}
