package io.github.enadeag.carchargeplanner.desktop;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javafx.application.ColorScheme;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/** Shows nodes on the headless platform as the app does, and finds what they show. Call on the JavaFX thread. */
public final class Scenes {

    private Scenes() {
    }

    public static Stage show(Node node, ColorScheme scheme) {
        var scene = new Scene(new StackPane(node));
        new SystemTheme(new SimpleObjectProperty<>(scheme)).style(scene);
        var stage = new Stage();
        stage.setScene(scene);
        stage.show();
        return stage;
    }

    /** The node and everything below it, including what controls' skins add, such as a tab's label. */
    public static List<Node> all(Node node) {
        var nodes = new ArrayList<Node>();
        nodes.add(node);
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> nodes.addAll(all(child)));
        }
        return nodes;
    }

    /** The texts a sighted user sees: every visible label with text, in layout order. */
    public static List<String> visibleTexts(Node node) {
        return all(node).stream()
                .filter(Scenes::reallyVisible)
                .flatMap(child -> child instanceof Labeled labeled ? Stream.of(labeled.getText()) : Stream.empty())
                .filter(text -> text != null && !text.isEmpty())
                .toList();
    }

    public static boolean reallyVisible(Node node) {
        for (var at = node; at != null; at = at.getParent()) {
            if (!at.isVisible()) {
                return false;
            }
        }
        return node.getScene() != null;
    }

    /** Texts shortened with an ellipsis, or wider than a clip above them, as a tab clips its label. */
    public static List<String> cutOff(Node root) {
        return Scenes.all(root).stream()
                .filter(Scenes::reallyVisible)
                .flatMap(node -> node instanceof Labeled labeled && (labeled.isTextTruncated() || clipped(labeled))
                        ? Stream.of(labeled.getText()) : Stream.empty())
                .toList();
    }

    /** Only sideways: a day taller than the screen scrolls, which clips it from below on purpose. */
    private static boolean clipped(Labeled labeled) {
        var bounds = labeled.localToScene(labeled.getLayoutBounds());
        for (var at = labeled.getParent(); at != null; at = at.getParent()) {
            if (at.getClip() != null) {
                var clip = at.localToScene(at.getClip().getBoundsInParent());
                if (bounds.getMinX() < clip.getMinX() - 0.5 || bounds.getMaxX() > clip.getMaxX() + 0.5) {
                    return true;
                }
            }
        }
        return false;
    }
}
