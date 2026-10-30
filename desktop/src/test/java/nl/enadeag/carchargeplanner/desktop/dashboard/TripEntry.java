package nl.enadeag.carchargeplanner.desktop.dashboard;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;

/**
 * The open trip entry of the first trip card below a node, used as the owner uses it: by its controls. Call on the
 * JavaFX thread, once Add trip opened the entry.
 */
final class TripEntry {

    private TripEntry() {
    }

    /**
     * Types the distance there and back.
     *
     * @param root the dashboard, its window's scene root, or a page that holds the trip card
     * @param distance the distance as typed, such as "120"
     */
    static void distance(Node root, String distance) {
        ((TextField) entry(root).lookup(".trip-distance")).setText(distance);
    }

    /**
     * Presses Save.
     *
     * @param root the dashboard, its window's scene root, or a page that holds the trip card
     */
    static void save(Node root) {
        entry(root).lookup(".trip-entry-buttons").lookupAll(".button").stream().map(Button.class::cast)
                .findFirst().orElseThrow().fire();
    }

    private static Node entry(Node root) {
        return root.lookup(".trip-card").lookup(".trip-editor");
    }
}
