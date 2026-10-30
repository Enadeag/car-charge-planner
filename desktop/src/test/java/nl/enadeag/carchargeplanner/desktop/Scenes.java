package nl.enadeag.carchargeplanner.desktop;

import nl.enadeag.carchargeplanner.desktop.system.TextSize;
import nl.enadeag.carchargeplanner.desktop.theme.WindowTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javafx.application.ColorScheme;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
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

    /** The windows' theme, following a scheme, with text of normal size. */
    public static WindowTheme theme(ObservableValue<ColorScheme> scheme) {
        return new WindowTheme(scheme, new SimpleObjectProperty<>(TextSize.NORMAL));
    }

    /** The windows' theme in a scheme, with the system's text this much larger. */
    public static WindowTheme theme(ColorScheme scheme, TextSize size) {
        return new WindowTheme(new SimpleObjectProperty<>(scheme), new SimpleObjectProperty<>(size));
    }

    public static Stage show(Node node, ColorScheme scheme) {
        var scene = new Scene(new StackPane(node));
        theme(new SimpleObjectProperty<>(scheme)).style(scene);
        var stage = new Stage();
        stage.setScene(scene);
        stage.show();
        return stage;
    }

    /**
     * Shows a node in a window of a size, which it has from the start: a window first shown as large as the node asks
     * would reach past the 1000 px headless screen.
     *
     * @param node what the window shows
     * @param scheme light or dark
     * @param width the window's width in pixels
     * @param height the window's height in pixels
     * @return the window, showing
     */
    public static Stage show(Node node, ColorScheme scheme, double width, double height) {
        var scene = new Scene(new StackPane(node), width, height);
        theme(new SimpleObjectProperty<>(scheme)).style(scene);
        var stage = new Stage();
        stage.setScene(scene);
        stage.show();
        return stage;
    }

    /** Shows a node with larger text, in a window no larger than the headless screen, at its top. */
    public static Stage show(Node node, ColorScheme scheme, TextScale scale) {
        var stage = stage(node, scheme, scale);
        stage.show();
        stage.setHeight(Math.min(stage.getHeight(), TextScale.SCREEN));
        stage.setWidth(Math.min(stage.getWidth(), TextScale.SCREEN));
        return stage;
    }

    /**
     * Lays a node out with larger text at a size, in a scene of its own that no window shows: a window as large as
     * the larger text asks for would not fit the headless screen, which draws only what is on it.
     */
    public static Scene layOut(Node node, ColorScheme scheme, TextScale scale, double width, double height) {
        var root = new StackPane(node);
        var scene = new Scene(root, width, height);
        theme(scheme, scale.size()).style(scene);
        root.applyCss();
        root.resize(width, height);
        root.layout();
        return scene;
    }

    private static Stage stage(Node node, ColorScheme scheme, TextScale scale) {
        var scene = new Scene(new StackPane(node));
        theme(scheme, scale.size()).style(scene);
        var stage = new Stage();
        stage.setScene(scene);
        stage.setY(0);
        return stage;
    }

    /**
     * Texts that overlap another text, as two labels drawn on top of each other: every pair of visible labelled
     * nodes whose places meet, other than one inside the other, and other than the chart's readout, which floats over
     * the chart on purpose.
     */
    public static List<String> overlapping(Node root) {
        var labels = all(root).stream()
                .filter(node -> node instanceof Labeled labeled && labeled.getText() != null
                        && !labeled.getText().isEmpty() && reallyVisible(node) && !inReadout(node))
                .map(Labeled.class::cast).toList();
        var problems = new ArrayList<String>();
        for (int i = 0; i < labels.size(); i++) {
            for (int j = i + 1; j < labels.size(); j++) {
                var a = labels.get(i);
                var b = labels.get(j);
                if (within(a, b) || within(b, a)) {
                    continue;
                }
                var first = shown(a);
                var second = shown(b);
                if (first.isEmpty() || second.isEmpty()) {
                    continue;
                }
                double width = Math.min(first.getMaxX(), second.getMaxX()) - Math.max(first.getMinX(), second.getMinX());
                double height = Math.min(first.getMaxY(), second.getMaxY()) - Math.max(first.getMinY(), second.getMinY());
                if (width > 1 && height > 1) {
                    problems.add("'%s' overlaps '%s'".formatted(a.getText(), b.getText()));
                }
            }
        }
        return problems;
    }

    /** The part of a node that shows: what a scroll pane's viewport, or another clip above it, leaves of it. */
    private static Bounds shown(Node node) {
        var bounds = node.localToScene(node.getLayoutBounds());
        for (var at = node.getParent(); at != null; at = at.getParent()) {
            if (at.getClip() != null) {
                var clip = at.localToScene(at.getClip().getBoundsInParent());
                double minX = Math.max(bounds.getMinX(), clip.getMinX());
                double minY = Math.max(bounds.getMinY(), clip.getMinY());
                double maxX = Math.min(bounds.getMaxX(), clip.getMaxX());
                double maxY = Math.min(bounds.getMaxY(), clip.getMaxY());
                bounds = maxX > minX && maxY > minY ? new BoundingBox(minX, minY, maxX - minX, maxY - minY)
                        : new BoundingBox(0, 0, -1, -1);
            }
        }
        return bounds;
    }

    private static boolean within(Node node, Node ancestor) {
        for (var at = node.getParent(); at != null; at = at.getParent()) {
            if (at == ancestor) {
                return true;
            }
        }
        return false;
    }

    private static boolean inReadout(Node node) {
        for (var at = node; at != null; at = at.getParent()) {
            if (at.getStyleClass().contains("chart-readout")) {
                return true;
            }
        }
        return false;
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
