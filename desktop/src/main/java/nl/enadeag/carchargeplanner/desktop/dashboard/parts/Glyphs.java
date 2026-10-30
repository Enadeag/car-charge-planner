package nl.enadeag.carchargeplanner.desktop.dashboard.parts;

import java.util.Locale;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;

/** Small 16 px pictures beside figures and on the day tabs; dashboard.css colours them after their source. */
public final class Glyphs {

    private static final double SIZE = 16;

    private Glyphs() {
    }

    /**
     * The glyph of a source: a sun for solar energy, a bolt for the grid, a battery for the car.
     *
     * @param source the source to draw
     * @return a new 16 px glyph, coloured by dashboard.css
     */
    static Node of(EnergySource source) {
        return switch (source) {
            case SOLAR -> glyph("solar", sun());
            case GRID -> glyph("grid", bolt());
            case CAR -> glyph("car", battery());
        };
    }

    /**
     * The glyph on a day's tab: its source's, or a hollow circle for a day without a charge window.
     *
     * @param kind where the day's charge comes from
     * @return a new 16 px glyph, coloured by dashboard.css
     */
    public static Node of(DayKind kind) {
        return switch (kind) {
            case SOLAR -> of(EnergySource.SOLAR);
            case GRID -> of(EnergySource.GRID);
            case NONE -> glyph("none", ring());
        };
    }

    /**
     * A box with an arrow out of it, for the button that opens the popup as a window of its own.
     *
     * @return a new 16 px glyph, in the muted text colour
     */
    public static Node detach() {
        var box = outlined(new Rectangle(1.5, 3.5, 11, 11));
        box.setArcWidth(3);
        box.setArcHeight(3);
        return glyph("neutral", box, stroke(7, 9, 14.5, 1.5), stroke(9.5, 1.5, 14.5, 1.5), stroke(14.5, 1.5, 14.5, 6.5));
    }

    /**
     * The CSS class of a source or a kind of day.
     *
     * @param kind a {@link EnergySource} or a {@link DayKind}
     * @return its name in lower case: "solar", "grid", "car", "none"
     */
    public static String styleClass(Enum<?> kind) {
        return kind.name().toLowerCase(Locale.ROOT);
    }

    /** Every glyph takes the full 16 px square, so glyphs of different shapes line up. */
    private static Node glyph(String styleClass, Node... shapes) {
        var square = new Rectangle(SIZE, SIZE);
        square.getStyleClass().add("glyph-outline");
        var glyph = new Group(square);
        glyph.getChildren().addAll(shapes);
        glyph.getStyleClass().addAll("glyph", styleClass);
        return glyph;
    }

    private static Node[] sun() {
        var parts = new Node[9];
        var disc = new Circle(8, 8, 3.6);
        disc.getStyleClass().add("glyph-fill");
        parts[0] = disc;
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * i / 4;
            parts[i + 1] = stroke(8 + Math.cos(angle) * 5.6, 8 + Math.sin(angle) * 5.6,
                    8 + Math.cos(angle) * 7.6, 8 + Math.sin(angle) * 7.6);
        }
        return parts;
    }

    private static Node[] battery() {
        var body = outlined(new Rectangle(1, 4, 12, 8));
        body.setArcWidth(3);
        body.setArcHeight(3);
        var level = new Rectangle(3, 6, 6, 4);
        level.getStyleClass().add("glyph-fill");
        var tip = new Rectangle(13.5, 6.5, 1.8, 3);
        tip.getStyleClass().add("glyph-fill");
        return new Node[] {body, level, tip};
    }

    private static Node[] bolt() {
        var bolt = new Polygon(9, 1, 3, 9, 7.5, 9, 6, 15, 12.5, 6.5, 8, 6.5);
        bolt.getStyleClass().add("glyph-fill");
        return new Node[] {bolt};
    }

    /** A hollow circle: a day without a charge window. */
    private static Node[] ring() {
        return new Node[] {outlined(new Circle(8, 8, 4.5))};
    }

    private static Line stroke(double startX, double startY, double endX, double endY) {
        var line = new Line(startX, startY, endX, endY);
        line.setStrokeWidth(1.5);
        line.setStrokeLineCap(StrokeLineCap.ROUND);
        line.getStyleClass().add("glyph-stroke");
        return line;
    }

    private static <T extends Shape> T outlined(T shape) {
        shape.setStrokeWidth(1.5);
        shape.getStyleClass().addAll("glyph-stroke", "glyph-outline");
        return shape;
    }
}
