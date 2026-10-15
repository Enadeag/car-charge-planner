package io.github.enadeag.carchargeplanner.desktop;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;

/**
 * The selected day below the tabs, scrolling up and down when the screen is too short for it. It asks for the
 * height its content needs at its width, so a window or popup sized to the scene shows the whole day when it fits.
 */
final class DayScroll extends ScrollPane {

    DayScroll() {
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        getStyleClass().add("day-scroll");
    }

    void show(Node day) {
        setContent(day);
        setVvalue(0);
    }

    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

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
