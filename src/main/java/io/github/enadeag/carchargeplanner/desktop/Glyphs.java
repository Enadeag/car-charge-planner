package io.github.enadeag.carchargeplanner.desktop;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;

/** Small 16 px pictures beside the figures, in the colour of their source (set in dashboard.css). */
final class Glyphs {

    private Glyphs() {
    }

    static Node sun() {
        var sun = new Group();
        var disc = new Circle(8, 8, 3.6);
        disc.getStyleClass().add("glyph-fill");
        sun.getChildren().add(disc);
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * i / 4;
            var ray = new Line(8 + Math.cos(angle) * 5.6, 8 + Math.sin(angle) * 5.6,
                    8 + Math.cos(angle) * 7.6, 8 + Math.sin(angle) * 7.6);
            ray.setStrokeWidth(1.6);
            ray.setStrokeLineCap(StrokeLineCap.ROUND);
            ray.getStyleClass().add("glyph-stroke");
            sun.getChildren().add(ray);
        }
        return sun;
    }

    static Node battery() {
        var body = new Rectangle(1, 4, 12, 8);
        body.setArcWidth(3);
        body.setArcHeight(3);
        body.setStrokeWidth(1.5);
        body.getStyleClass().add("glyph-stroke");
        body.setStyle("-fx-fill: transparent;");
        var level = new Rectangle(3, 6, 6, 4);
        level.getStyleClass().add("glyph-fill");
        var tip = new Rectangle(13.5, 6.5, 1.8, 3);
        tip.getStyleClass().add("glyph-fill");
        return new Group(body, level, tip);
    }

    static Node bolt() {
        var bolt = new Polygon(9, 1, 3, 9, 7.5, 9, 6, 15, 12.5, 6.5, 8, 6.5);
        bolt.getStyleClass().add("glyph-fill");
        return bolt;
    }
}
