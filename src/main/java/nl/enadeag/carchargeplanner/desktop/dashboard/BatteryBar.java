package nl.enadeag.carchargeplanner.desktop.dashboard;

import java.util.List;
import javafx.beans.value.ObservableValue;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

/**
 * The battery as a bar: the level in green, the level after charging outlined in green beyond it, and marks at the
 * minimum and target charge levels. A change of level slides to the new one unless the system asks for reduced
 * motion. Colours come from dashboard.css.
 */
final class BatteryBar extends Region {

    private static final double MARK_OVERHANG = 3;
    private static final double GAP = 3;
    private static final double BAR_SHARE_OF_LINE = 0.75;

    private final Rectangle track = shape(new Rectangle(), "battery-track");
    private final Rectangle after = shape(new Rectangle(), "battery-after", "chart-stroke");
    private final Rectangle level = shape(new Rectangle(), "battery-level", "chart-fill");
    private final Line minimum = shape(new Line(), "battery-mark", "chart-stroke");
    private final Line target = shape(new Line(), "battery-mark", "chart-stroke");
    private final Label minimumText = Texts.label("", "chart-axis");
    private final Label targetText = Texts.label("", "chart-axis");
    private final Tween tween;

    private BatteryModel model;
    private double levelFrom;
    private double afterFrom;
    private double shownLevel;
    private double shownAfter;

    /**
     * Makes the bar, empty until {@link #show(BatteryModel)}.
     *
     * @param reducedMotion true while the system asks for reduced motion; the levels then change without sliding
     */
    BatteryBar(ObservableValue<Boolean> reducedMotion) {
        this.tween = new Tween(reducedMotion, this::frame);
        getChildren().setAll(track, after, level, minimum, target, minimumText, targetText);
        getStyleClass().add("battery-bar");
        setAccessibleRole(AccessibleRole.TEXT);
        setFocusTraversable(true);
    }

    /**
     * Shows a day's battery, sliding from the levels shown before.
     *
     * @param day the day's levels; never null
     */
    void show(BatteryModel day) {
        model = day;
        setAccessibleText(day.spoken());
        minimumText.setText(day.minimumText());
        targetText.setText(day.targetText());
        levelFrom = shownLevel;
        afterFrom = shownAfter;
        tween.play();
    }

    /**
     * The levels as they are drawn now, which are mid-way while they slide.
     *
     * @return the level and the level after charging, in percent
     */
    double[] shownLevels() {
        return new double[] {shownLevel, shownAfter};
    }

    /**
     * The change that moves the levels from the old ones to the new ones.
     *
     * @return the tween, also while it is not running
     */
    Tween tween() {
        return tween;
    }

    /**
     * Where a level is on a bar.
     *
     * @param pct the level in percent; outside 0 to 100 is put on the nearest end
     * @param width the bar's width in pixels, 0 or more
     * @return pixels from the bar's left
     */
    static double x(double pct, double width) {
        return width * Math.clamp(pct, 0, 100) / 100;
    }

    /**
     * Where a mark's label goes: centred below its mark, but never past either end of the bar.
     *
     * @param markX the mark's place in pixels from the bar's left
     * @param labelWidth the label's width in pixels
     * @param width the bar's width in pixels
     * @return the label's left edge in pixels from the bar's left; 0 when the label is wider than the bar
     */
    static double labelX(double markX, double labelWidth, double width) {
        return Math.clamp(markX - labelWidth / 2, 0, Math.max(0, width - labelWidth));
    }

    @Override
    protected double computePrefWidth(double height) {
        return 0; // the card decides the width
    }

    @Override
    protected double computePrefHeight(double width) {
        double labelHeight = minimumText.prefHeight(-1);
        return MARK_OVERHANG + barHeight() + MARK_OVERHANG + GAP + labelHeight;
    }

    @Override
    protected double computeMinHeight(double width) {
        return computePrefHeight(width);
    }

    @Override
    protected void layoutChildren() {
        if (model == null) {
            return;
        }
        double width = getWidth();
        double barHeight = barHeight();
        double barTop = MARK_OVERHANG;
        bar(track, 0, width, barTop, barHeight);
        bar(after, 0, x(shownAfter, width), barTop, barHeight);
        bar(level, 0, x(shownLevel, width), barTop, barHeight);
        double labelTop = barTop + barHeight + MARK_OVERHANG + GAP;
        mark(minimum, minimumText, x(model.minimumPct(), width), barTop, barHeight, labelTop);
        mark(target, targetText, x(model.targetPct(), width), barTop, barHeight, labelTop);
    }

    private double barHeight() {
        return Math.round(minimumText.prefHeight(-1) * BAR_SHARE_OF_LINE);
    }

    private void mark(Line line, Label text, double x, double barTop, double barHeight, double labelTop) {
        line.setStartX(x);
        line.setEndX(x);
        line.setStartY(barTop - MARK_OVERHANG);
        line.setEndY(barTop + barHeight + MARK_OVERHANG);
        double labelWidth = text.prefWidth(-1);
        text.resizeRelocate(labelX(x, labelWidth, getWidth()), labelTop, labelWidth, text.prefHeight(-1));
    }

    private static void bar(Rectangle rectangle, double left, double width, double top, double height) {
        rectangle.setX(left);
        rectangle.setY(top);
        rectangle.setWidth(width);
        rectangle.setHeight(height);
        rectangle.setArcWidth(height);
        rectangle.setArcHeight(height);
    }

    private void frame(double progress) {
        shownLevel = levelFrom + (model.levelPct() - levelFrom) * progress;
        shownAfter = afterFrom + (model.afterPct() - afterFrom) * progress;
        requestLayout();
    }

    private static <T extends Node> T shape(T node, String... styleClasses) {
        node.getStyleClass().addAll(styleClasses);
        node.setManaged(false);
        return node;
    }

    /**
     * The bar's parts that are drawn in a colour, for the tests.
     *
     * @return the track, the level after charging, the level and the two marks
     */
    List<Node> parts() {
        return List.of(track, after, level, minimum, target);
    }
}
