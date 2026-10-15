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
final class Scenes {

    private Scenes() {
    }

    static Stage show(Node node, ColorScheme scheme) {
        var scene = new Scene(new StackPane(node));
        new SystemTheme(new SimpleObjectProperty<>(scheme)).style(scene);
        var stage = new Stage();
        stage.setScene(scene);
        stage.show();
        return stage;
    }

    /** The node and everything below it, including what controls' skins add, such as a tab's label. */
    static List<Node> all(Node node) {
        var nodes = new ArrayList<Node>();
        nodes.add(node);
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> nodes.addAll(all(child)));
        }
        return nodes;
    }

    /** The texts a sighted user sees: every visible label with text, in layout order. */
    static List<String> visibleTexts(Node node) {
        return all(node).stream()
                .filter(Scenes::reallyVisible)
                .flatMap(child -> child instanceof Labeled labeled ? Stream.of(labeled.getText()) : Stream.empty())
                .filter(text -> text != null && !text.isEmpty())
                .toList();
    }

    static boolean reallyVisible(Node node) {
        for (var at = node; at != null; at = at.getParent()) {
            if (!at.isVisible()) {
                return false;
            }
        }
        return node.getScene() != null;
    }
}
