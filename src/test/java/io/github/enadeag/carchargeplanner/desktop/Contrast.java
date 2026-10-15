package io.github.enadeag.carchargeplanner.desktop;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Shape;

/** WCAG 2 contrast arithmetic, and the check of a rendered scene against it. */
public final class Contrast {

    private static final double TEXT = 4.5;
    private static final double MARK = 3;

    private Contrast() {
    }

    public static double ratio(Color a, Color b) {
        double lighter = Math.max(luminance(a), luminance(b));
        double darker = Math.min(luminance(a), luminance(b));
        return (lighter + 0.05) / (darker + 0.05);
    }

    /** A colour with transparency as it shows over an opaque one. */
    public static Color over(Color top, Color bottom) {
        double alpha = top.getOpacity();
        return Color.color(
                top.getRed() * alpha + bottom.getRed() * (1 - alpha),
                top.getGreen() * alpha + bottom.getGreen() * (1 - alpha),
                top.getBlue() * alpha + bottom.getBlue() * (1 - alpha));
    }

    private static double luminance(Color colour) {
        return 0.2126 * linear(colour.getRed()) + 0.7152 * linear(colour.getGreen()) + 0.0722 * linear(colour.getBlue());
    }

    private static double linear(double channel) {
        return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }
    /**
     * WCAG AA below a node as it is rendered: every visible text 4.5:1 against what it sits on, every glyph and chart
     * mark 3:1. Controls that are switched off are exempt, as WCAG allows.
     */
    public static List<String> problemsIn(Node root) {
        var problems = new ArrayList<String>();
        for (var node : Scenes.all(root)) {
            if (!Scenes.reallyVisible(node) || node.isDisabled()) {
                continue;
            }
            switch (node) {
                case Labeled labeled when labeled.getText() != null && !labeled.getText().isEmpty() ->
                        check(problems, labeled.getText(), labeled.getTextFill(), background(node), TEXT);
                case Shape shape when shape.getStyleClass().contains("glyph-fill") ->
                        check(problems, "glyph in " + owner(node), shape.getFill(), background(node), MARK);
                case Shape shape when shape.getStyleClass().contains("glyph-stroke") ->
                        check(problems, "glyph in " + owner(node), shape.getStroke(), background(node), MARK);
                case Shape shape when shape.getStyleClass().contains("chart-fill") ->
                        check(problems, shape.getStyleClass() + " in " + owner(node), shape.getFill(), background(node), MARK);
                case Shape shape when shape.getStyleClass().contains("chart-stroke") ->
                        check(problems, shape.getStyleClass() + " in " + owner(node), shape.getStroke(), background(node), MARK);
                default -> { }
            }
        }
        return problems;
    }

    private static void check(List<String> problems, String what, Object paint, Color background, double needed) {
        var colour = (Color) paint;
        double ratio = Contrast.ratio(Contrast.over(colour, background), background);
        if (ratio < needed) {
            problems.add("%s: %.2f:1, %s on %s".formatted(what, ratio, colour, background));
        }
    }

    /**
     * The colour behind a node's text or mark: the backgrounds of the regions it lies in, and its own when it is a
     * control such as a button, painted over each other.
     */
    private static Color background(Node node) {
        var regions = new ArrayList<Region>();
        for (Node at = node instanceof Labeled ? node : node.getParent(); at != null; at = at.getParent()) {
            if (at instanceof Region region) {
                regions.addFirst(region);
            }
        }
        var colour = Color.WHITE;
        for (var region : regions) {
            if (region.getBackground() == null) {
                continue;
            }
            for (var fill : region.getBackground().getFills()) {
                if (fill.getFill() instanceof Color paint) {
                    colour = Contrast.over(paint, colour);
                }
            }
        }
        return colour;
    }

    private static String owner(Node node) {
        for (var at = node.getParent(); at != null; at = at.getParent()) {
            if (!at.getStyleClass().isEmpty() && !at.getStyleClass().contains("glyph")) {
                return at.getStyleClass().toString();
            }
        }
        return "?";
    }

}
