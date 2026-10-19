package nl.enadeag.carchargeplanner.desktop.dashboard;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;

/**
 * The selected day below the tabs, scrolling up and down when the screen is too short for it. It asks for the
 * height its content needs at its width, so a window or popup sized to the scene shows the whole day when it fits.
 */
final class DayScroll extends ScrollPane {

    /** Makes the scroll pane, empty, scrolling up and down only. */
    DayScroll() {
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        getStyleClass().add("day-scroll");
    }

    /**
     * Shows a day from its top.
     *
     * @param day the day's cards; null for none
     */
    void show(Node day) {
        setContent(day);
        setVvalue(0);
    }

    /**
     * Its height depends on its width, as wrapped text does.
     *
     * @return {@link Orientation#HORIZONTAL}
     */
    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    /**
     * The height the day needs at this width, so a window sized to its scene shows the whole day when it fits.
     *
     * @param width the width it gets, in pixels; -1 when not known yet, then the day's own preferred width is used
     * @return the day's height at that width plus the insets, in pixels; 0 without a day
     */
    @Override
    protected double computePrefHeight(double width) {
        var day = getContent();
        if (day == null) {
            return 0;
        }
        double inner = width < 0 ? day.prefWidth(-1) : width - snappedLeftInset() - snappedRightInset();
        return day.prefHeight(inner) + snappedTopInset() + snappedBottomInset();
    }
}
