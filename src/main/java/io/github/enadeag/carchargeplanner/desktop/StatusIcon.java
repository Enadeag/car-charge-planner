package io.github.enadeag.carchargeplanner.desktop;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Optional;

/**
 * The tray icon: the app icon's sun over a plug in one colour, with a {@link StatusDot} at the bottom right. It is
 * drawn here, at the exact size asked for, so every size is sharp without a set of image files per size, shade and
 * dot. The drawing and the SVG are the same shapes on a 64 by 64 grid, those of
 * {@code src/packaging/icons/car-charge-planner-symbolic.svg}.
 */
public final class StatusIcon {

    private static final double GRID = 64;
    private static final double DOT_CENTRE = 52;
    private static final double DOT_RADIUS = 12;
    /** The dot's radius plus the clear ring that keeps it apart from the shapes it covers. */
    private static final double GAP_RADIUS = 15;
    private static final float STROKE = 5;

    /**
     * The SVG, filled in with the shapes' colour, the gap's mask and the dot. KDE Plasma replaces the colour of the
     * class {@code ColorScheme-Text} with its panel's text colour; other desktops show the colour given.
     */
    private static final String SVG = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" width="64" height="64">
              <style id="current-color-scheme">.ColorScheme-Text { color: %s; }</style>
              <mask id="gap"><rect width="64" height="64" fill="#fff"/><circle cx="52" cy="52" r="15" fill="#000"/></mask>
              <g class="ColorScheme-Text" fill="currentColor"%s>
                <path d="M12 34a20 20 0 0 1 40 0Z"/>
                <path d="M32 5v6M12 12l4 4M52 12l-4 4M6 38h52M27 44v-5M37 44v-5M32 56v6" fill="none" \
            stroke="currentColor" stroke-width="5" stroke-linecap="round"/>
                <rect x="22" y="43" width="20" height="13" rx="4"/>
              </g>%s
            </svg>
            """;

    /** Makes the icon; it keeps nothing between drawings. */
    public StatusIcon() {
        // every drawing stands on its own
    }

    /**
     * The icon as pixels, for AWT and for panels that take pixels.
     *
     * @param dot the dot to draw; {@link StatusDot#NONE} draws none
     * @param shade the panel the icon sits on, which decides whether the shapes are drawn light or dark
     * @param size the width and height in pixels, 16 or more; the shapes are drawn for this size, not scaled from
     *     another
     * @return a new image of {@code size} by {@code size} pixels, transparent around the shapes
     * @throws IllegalArgumentException when the size is below 16, where the dot would hide the plug
     */
    public BufferedImage image(StatusDot dot, PanelShade shade, int size) {
        if (size < 16) {
            throw new IllegalArgumentException("a tray icon of " + size + " px is too small");
        }
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.scale(size / GRID, size / GRID);
            drawShapes(g, Color.decode(shapeColour(shade)));
            dotColour(dot).ifPresent(colour -> drawDot(g, Color.decode(colour)));
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * The icon as an SVG file's text, for panels that draw icons from an icon theme and colour them themselves.
     *
     * @param dot the dot to draw; {@link StatusDot#NONE} draws none
     * @param shade the panel the icon is meant for where the desktop does not colour the icon itself
     * @return a complete SVG document of 64 by 64 units
     */
    public String svg(StatusDot dot, PanelShade shade) {
        var colour = dotColour(dot);
        var mask = colour.isPresent() ? " mask=\"url(#gap)\"" : "";
        var circle = colour.map(fill -> "\n  <circle cx=\"52\" cy=\"52\" r=\"12\" fill=\"" + fill + "\"/>").orElse("");
        return SVG.formatted(shapeColour(shade), mask, circle);
    }

    private static String shapeColour(PanelShade shade) {
        return shade == PanelShade.DARK ? "#f5f5f5" : "#1f1f1f";
    }

    /** One amber, blue and red for both shades: bright enough on a dark panel, saturated enough on a light one. */
    private static Optional<String> dotColour(StatusDot dot) {
        return switch (dot) {
            case SUN -> Optional.of("#e39a0b");
            case GRID -> Optional.of("#3b82f6");
            case ACTION -> Optional.of("#e5484d");
            case NONE -> Optional.empty();
        };
    }

    private static void drawShapes(Graphics2D g, Color colour) {
        g.setColor(colour);
        // M12 34a20 20 0 0 1 40 0Z: the upper half of a circle around (32, 34) with radius 20
        g.fill(new Arc2D.Double(12, 14, 40, 40, 0, 180, Arc2D.CHORD));
        g.fill(new RoundRectangle2D.Double(22, 43, 20, 13, 8, 8));
        g.setStroke(new BasicStroke(STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(strokes());
    }

    /** The rays, the ground line, the prongs and the cable. */
    private static Shape strokes() {
        double[][] lines = {
            {32, 5, 32, 11}, {12, 12, 16, 16}, {52, 12, 48, 16}, {6, 38, 58, 38},
            {27, 44, 27, 39}, {37, 44, 37, 39}, {32, 56, 32, 62},
        };
        var path = new Path2D.Double();
        for (var line : lines) {
            path.moveTo(line[0], line[1]);
            path.lineTo(line[2], line[3]);
        }
        return path;
    }

    private static void drawDot(Graphics2D g, Color colour) {
        g.setComposite(AlphaComposite.Clear);
        g.fill(circle(GAP_RADIUS));
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(colour);
        g.fill(circle(DOT_RADIUS));
    }

    private static Shape circle(double radius) {
        return new Ellipse2D.Double(DOT_CENTRE - radius, DOT_CENTRE - radius, 2 * radius, 2 * radius);
    }
}
