package nl.enadeag.carchargeplanner.desktop;

import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;

/**
 * The name a screen reader on Windows reads for a node, worked out as JavaFX's UI Automation bridge
 * ({@code com.sun.glass.ui.win.WinAccessible}) does: from the node's own text, except for text fields and combo boxes,
 * which are named only by the label whose {@code labelFor} they are; then from that label.
 */
public final class AccessibleNames {

    private AccessibleNames() {
    }

    public static String of(Node node) {
        var role = (AccessibleRole) node.queryAccessibleAttribute(AccessibleAttribute.ROLE);
        String name = switch (role) {
            case TEXT_FIELD, TEXT_AREA, COMBO_BOX -> null;
            case null -> null;
            default -> (String) node.queryAccessibleAttribute(AccessibleAttribute.TEXT);
        };
        if (name == null || name.isBlank()) {
            var label = (Node) node.queryAccessibleAttribute(AccessibleAttribute.LABELED_BY);
            if (label != null) {
                name = (String) label.queryAccessibleAttribute(AccessibleAttribute.TEXT);
            }
        }
        return name == null ? "" : name.strip();
    }

    /** The help a screen reader reads after the name, such as a problem with the value; empty for none. */
    public static String help(Node node) {
        var help = (String) node.queryAccessibleAttribute(AccessibleAttribute.HELP);
        return help == null ? "" : help.strip();
    }

    /**
     * What the screen reader reads when the focus is on a node: the node, or the item inside it that has the focus,
     * such as the selected tab of a tab pane or the focused row of a list.
     */
    public static Node focusTarget(Node node) {
        var item = (Node) node.queryAccessibleAttribute(AccessibleAttribute.FOCUS_ITEM);
        return item == null ? node : item;
    }
}
