package io.github.enadeag.carchargeplanner.desktop;

import javafx.scene.paint.Color;

/** WCAG 2 contrast arithmetic. */
final class Contrast {

    private Contrast() {
    }

    static double ratio(Color a, Color b) {
        double lighter = Math.max(luminance(a), luminance(b));
        double darker = Math.min(luminance(a), luminance(b));
        return (lighter + 0.05) / (darker + 0.05);
    }

    /** A colour with transparency as it shows over an opaque one. */
    static Color over(Color top, Color bottom) {
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
}
