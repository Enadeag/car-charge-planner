package nl.enadeag.carchargeplanner.desktop;

import java.util.Optional;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;

/**
 * The name a control shows on screen: the label whose {@code labelFor} it is, its own text, the tooltip of a button
 * that shows only a glyph, or the choice a list shows. JavaFX keeps which label names a node only as the node's
 * {@code LABELED_BY} attribute. Call on the JavaFX thread.
 */
public final class VisibleNames {

    private VisibleNames() {
    }

    /**
     * The name a node shows.
     *
     * @param node a control, or the item inside one that has the focus, such as a tab's header
     * @return the name, stripped; empty when the node shows none
     */
    public static String of(Node node) {
        var label = labelOf(node).filter(Scenes::reallyVisible).map(Label::getText).orElse("");
        if (!label.isBlank()) {
            return label.strip();
        }
        if (node instanceof Labeled labeled && labeled.getText() != null && !labeled.getText().isBlank()) {
            return labeled.getText().strip();
        }
        if (node instanceof Labeled labeled && labeled.getTooltip() != null) {
            return labeled.getTooltip().getText().strip();
        }
        if (node instanceof ComboBox<?> box && box.getValue() != null) {
            return shown(box).strip();
        }
        if (!(node instanceof Control) && node instanceof Parent parent) {
            // a tab's header is no control: its name is the text it holds
            return Scenes.all(parent).stream().filter(Labeled.class::isInstance).map(Labeled.class::cast)
                    .map(Labeled::getText).filter(text -> text != null && !text.isBlank()).findFirst().orElse("")
                    .strip();
        }
        return "";
    }

    /**
     * What has the focus inside a node: the node, or the item inside it that has the focus, such as the selected tab
     * of a tab pane or the focused row of a list.
     */
    public static Node focusTarget(Node node) {
        var item = (Node) node.queryAccessibleAttribute(AccessibleAttribute.FOCUS_ITEM);
        return item == null ? node : item;
    }

    /**
     * The label whose {@code labelFor} is the node, or the control the node is part of, such as a spinner's field.
     *
     * @return the label, shown or not; empty when none names the node
     */
    public static Optional<Label> labelOf(Node node) {
        for (var at = node; at != null; at = at.getParent()) {
            if (at.queryAccessibleAttribute(AccessibleAttribute.LABELED_BY) instanceof Label label) {
                return Optional.of(label);
            }
            if (at instanceof Control && at != node) {
                break;
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private static <T> String shown(ComboBox<T> box) {
        return box.getConverter() == null ? String.valueOf(box.getValue())
                : box.getConverter().toString(box.getValue());
    }
}
