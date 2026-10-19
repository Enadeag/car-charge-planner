package nl.enadeag.carchargeplanner.desktop.dashboard;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import javafx.geometry.Dimension2D;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;

/**
 * Where the tray popup goes: against the panel that holds the tray icon, whichever screen edge that panel is on,
 * centred on the icon along it. All in JavaFX's screen coordinates, which already account for 150 and 200 % scaling.
 */
final class PopupPlacement {

    /** Between the popup and the panel, the icon or the screen's edge. */
    static final double MARGIN = 8;

    /** A screen edge. */
    enum Edge {
        /** The top edge. */
        TOP,
        /** The bottom edge. */
        BOTTOM,
        /** The left edge. */
        LEFT,
        /** The right edge. */
        RIGHT
    }

    private PopupPlacement() {
    }

    /**
     * The screen edge of the panel the icon is in. A panel that reserves space (the Windows taskbar, a Plasma panel)
     * lies outside {@code available}, so an icon outside it names the edge. A panel that reserves nothing (an
     * auto-hidden taskbar) leaves the icon inside; then the nearest edge wins.
     *
     * @param icon where the tray icon is, in screen coordinates
     * @param available the screen's free area, without the panels that reserve space
     * @return the edge the panel is on
     */
    static Edge panelEdge(Point2D icon, Rectangle2D available) {
        return Arrays.stream(Edge.values())
                .max(Comparator.comparingDouble(edge -> beyond(edge, icon, available)))
                .orElseThrow();
    }

    /** How far the icon lies beyond the edge; inside, minus its distance to it. The largest names the panel's edge. */
    private static double beyond(Edge edge, Point2D icon, Rectangle2D available) {
        return switch (edge) {
            case TOP -> available.getMinY() - icon.getY();
            case BOTTOM -> icon.getY() - available.getMaxY();
            case LEFT -> available.getMinX() - icon.getX();
            case RIGHT -> icon.getX() - available.getMaxX();
        };
    }

    /**
     * The top left corner of a popup of {@code size} for the icon at {@code icon}, inside {@code available}. It stays
     * clear of the icon too, in case the system counts the panel as available space.
     *
     * @param icon where the tray icon is, in screen coordinates
     * @param size the popup's width and height, in pixels
     * @param available the screen's free area, without the panels that reserve space
     * @return where the popup's top left corner goes, at least {@link #MARGIN} inside {@code available}
     */
    static Point2D position(Point2D icon, Dimension2D size, Rectangle2D available) {
        double x = icon.getX() - size.getWidth() / 2;
        double y = icon.getY() - size.getHeight() / 2;
        switch (panelEdge(icon, available)) {
            case TOP -> y = Math.max(available.getMinY(), icon.getY()) + MARGIN;
            case BOTTOM -> y = Math.min(available.getMaxY(), icon.getY()) - size.getHeight() - MARGIN;
            case LEFT -> x = Math.max(available.getMinX(), icon.getX()) + MARGIN;
            case RIGHT -> x = Math.min(available.getMaxX(), icon.getX()) - size.getWidth() - MARGIN;
        }
        return keepInside(x, y, size, available);
    }

    /**
     * The reported icon position when it lies on one of the screens; otherwise the bottom right of {@code fallback}
     * (the primary screen's free area), where trays usually are. The position comes from another process.
     *
     * @param reported where the tray says its icon is, in screen coordinates
     * @param screens the bounds of every screen
     * @param fallback the primary screen's free area
     * @return the reported position, or the bottom right corner of {@code fallback}
     */
    static Point2D onAScreen(Point2D reported, List<Rectangle2D> screens, Rectangle2D fallback) {
        boolean onAScreen = screens.stream().anyMatch(screen -> screen.contains(reported));
        return onAScreen ? reported : new Point2D(fallback.getMaxX(), fallback.getMaxY());
    }

    /** Moved the least needed to lie inside the area, a margin from its edges; the top left wins if it cannot fit. */
    private static Point2D keepInside(double x, double y, Dimension2D size, Rectangle2D area) {
        double right = area.getMaxX() - MARGIN - size.getWidth();
        double bottom = area.getMaxY() - MARGIN - size.getHeight();
        return new Point2D(
                Math.max(Math.min(x, right), area.getMinX() + MARGIN),
                Math.max(Math.min(y, bottom), area.getMinY() + MARGIN));
    }
}
