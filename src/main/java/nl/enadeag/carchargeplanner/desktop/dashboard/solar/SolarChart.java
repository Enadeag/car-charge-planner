package nl.enadeag.carchargeplanner.desktop.dashboard.solar;

import nl.enadeag.carchargeplanner.desktop.theme.Texts;
import nl.enadeag.carchargeplanner.desktop.dashboard.parts.Tween;
import nl.enadeag.carchargeplanner.desktop.dashboard.solar.SolarCurveModel.Step;
import nl.enadeag.carchargeplanner.desktop.theme.ScreenReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Point2D;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.shape.Rectangle;

/**
 * A day's expected solar power as an amber area, the house's use as a dashed line, the charge windows in green and a
 * line at "now". Pointing at the chart, tapping it or moving along it with the arrow keys reads out one step's values.
 * A new day or plan morphs the curve into its new shape. Colours come from dashboard.css.
 *
 * <p>A screen reader reads the day in words; while the arrow keys read out a step, it reads that step's values
 * instead, each time they move.
 */
final class SolarChart extends Region {

    private static final double GAP = 4;
    private static final double READOUT_GAP = 6;

    private final Path area = shape(new Path(), "solar-area", "chart-stroke");
    private final Line house = shape(new Line(), "house-line", "chart-stroke");
    private final Line now = shape(new Line(), "now-line", "chart-stroke");
    private final Line pointer = shape(new Line(), "pointer-line");
    private final Line top = shape(new Line(), "chart-grid");
    private final Line middle = shape(new Line(), "chart-grid");
    private final Line baseline = shape(new Line(), "chart-grid");
    private final Label nowLabel;
    private final Label scale = Texts.label("", "chart-axis");
    private final Label zero = Texts.label("0", "chart-axis");
    private final Label readoutText = Texts.label("", "chart-readout-text");
    private final StackPane readout = new StackPane(readoutText);
    private final List<Rectangle> bands = new ArrayList<>();
    private final List<Line> marks = new ArrayList<>();
    private final List<Label> ticks = new ArrayList<>();
    private final Tween tween;

    private SolarCurveModel model;
    private List<Point2D> before = List.of();
    private List<Point2D> target = List.of();
    private List<Point2D> shown = List.of();
    /** The step read out; null while none is. */
    private Step selected;
    /** Whether the pointer chose the step read out, rather than the arrow keys. */
    private boolean pointed;
    private PlotArea plot;

    /**
     * Makes the chart, empty until {@link #show(SolarCurveModel)}.
     *
     * @param reducedMotion true while the system asks for reduced motion; the curve then changes without moving
     * @param now the label on the line at the current time, such as "now"
     * @param keys what the arrow keys do, for a screen reader to read after the chart's text
     */
    SolarChart(ObservableValue<Boolean> reducedMotion, String now, String keys) {
        this.nowLabel = Texts.label(now, "chart-now");
        this.tween = new Tween(reducedMotion, this::frame);
        readout.getStyleClass().add("chart-readout");
        readout.setManaged(false);
        readout.setVisible(false);
        readout.setMouseTransparent(true);
        pointer.setVisible(false);
        getStyleClass().add("solar-chart");
        setAccessibleRole(AccessibleRole.TEXT);
        setAccessibleHelp(keys);
        setFocusTraversable(true);
        setOnMouseMoved(this::point);
        setOnMousePressed(this::point);
        setOnMouseDragged(this::point);
        setOnMouseExited(_ -> {
            if (pointed) {
                clearSelection();
            }
        });
        addEventHandler(KeyEvent.KEY_PRESSED, this::step);
        focusedProperty().subscribe(focused -> {
            if (!focused && !pointed) {
                clearSelection();
            }
        });
    }

    /**
     * Shows a day, moving the curve from what it showed before unless the system asks for reduced motion.
     *
     * @param day the day's data; never null
     */
    void show(SolarCurveModel day) {
        this.model = day;
        scale.setText(day.scaleText());
        nowLabel.setVisible(day.nowHour().isPresent());
        now.setVisible(day.nowHour().isPresent());
        bands.clear();
        marks.clear();
        day.windows().forEach(window -> {
            bands.add(shape(new Rectangle(), "window-band"));
            marks.add(shape(new Line(), "window-mark", "chart-stroke"));
        });
        ticks.clear();
        day.ticks().forEach(tick -> ticks.add(Texts.label(tick.label(), "chart-axis")));
        var children = new ArrayList<Node>(List.of(top, middle, baseline));
        children.addAll(bands);
        children.addAll(List.of(area, house));
        children.addAll(marks);
        children.addAll(List.of(now, nowLabel, scale, zero, pointer));
        children.addAll(ticks);
        children.add(readout);
        getChildren().setAll(children);
        before = shown;
        target = CurvePoints.of(day.steps(), day.fromHour(), day.toHour());
        clearSelection();
        tween.play();
    }

    /**
     * The step whose values are read out.
     *
     * @return the step pointed at or chosen with the arrow keys; empty when none is
     */
    Optional<Step> selected() {
        return Optional.ofNullable(selected);
    }

    /**
     * The curve's outline as it is drawn now, which is mid-way while the curve moves.
     *
     * @return points as (hour, kW)
     */
    List<Point2D> shownPoints() {
        return shown;
    }

    /**
     * The change that moves the curve from its old shape to its new one.
     *
     * @return the tween, also while it is not running
     */
    Tween tween() {
        return tween;
    }

    /**
     * Where the plot lies after the last layout.
     *
     * @return the plot's place and its hours and power; empty before the first layout of a day
     */
    Optional<PlotArea> plot() {
        return Optional.ofNullable(plot);
    }

    @Override
    protected double computePrefWidth(double height) {
        return 0; // the card decides the width
    }

    @Override
    protected void layoutChildren() {
        if (model == null) {
            return;
        }
        double labelHeight = zero.prefHeight(-1);
        double left = Math.max(scale.prefWidth(-1), zero.prefWidth(-1)) + GAP;
        double topEdge = labelHeight / 2;
        double width = Math.max(0, getWidth() - left - GAP);
        double height = Math.max(0, getHeight() - topEdge - labelHeight - GAP);
        plot = new PlotArea(left, topEdge, width, height, model.fromHour(), model.toHour(), model.scaleKw());
        placeGrid(labelHeight);
        placeWindows();
        area.getElements().setAll(outline(plot, shown));
        horizontal(house, plot, plot.y(model.houseKw()));
        model.nowHour().ifPresent(hour -> {
            vertical(now, plot.x(hour), plot.top(), plot.bottom());
            nowLabel.resizeRelocate(Math.min(plot.x(hour) + GAP / 2, plot.right() - nowLabel.prefWidth(-1)),
                    plot.top(), nowLabel.prefWidth(-1), labelHeight);
        });
        placeTicks(labelHeight);
        placeReadout();
    }

    private void placeGrid(double labelHeight) {
        horizontal(top, plot, plot.y(model.scaleKw()));
        horizontal(middle, plot, plot.y(model.scaleKw() / 2));
        horizontal(baseline, plot, plot.bottom());
        scale.resizeRelocate(0, plot.top() - labelHeight / 2, scale.prefWidth(-1), labelHeight);
        zero.resizeRelocate(0, plot.bottom() - labelHeight / 2, zero.prefWidth(-1), labelHeight);
    }

    private void placeWindows() {
        for (int i = 0; i < bands.size(); i++) {
            var window = model.windows().get(i);
            double start = plot.x(window.startHour());
            double end = plot.x(window.endHour());
            var band = bands.get(i);
            band.setX(start);
            band.setY(plot.top());
            band.setWidth(end - start);
            band.setHeight(plot.height());
            var mark = marks.get(i);
            mark.setStartX(start);
            mark.setEndX(end);
            mark.setStartY(plot.bottom());
            mark.setEndY(plot.bottom());
        }
    }

    private void placeTicks(double labelHeight) {
        for (int i = 0; i < ticks.size(); i++) {
            var label = ticks.get(i);
            double width = label.prefWidth(-1);
            double x = Math.clamp(plot.x(model.ticks().get(i).hour()) - width / 2, plot.left(), plot.right() - width);
            label.resizeRelocate(x, plot.bottom() + GAP, width, labelHeight);
        }
    }

    private void placeReadout() {
        readout.setVisible(selected != null);
        pointer.setVisible(selected != null);
        if (selected != null) {
            double x = plot.x(selected.midHour());
            vertical(pointer, x, plot.top(), plot.bottom());
            double width = readout.prefWidth(-1);
            double height = readout.prefHeight(width);
            double left = Math.clamp(x - width / 2, 0, Math.max(0, getWidth() - width));
            double y = Math.max(0, plot.y(selected.kw()) - height - READOUT_GAP);
            readout.resizeRelocate(left, y, width, height);
        }
    }

    /**
     * The curve's outline as a closed path in pixels.
     *
     * @param plot where the curve goes
     * @param points the outline as (hour, kW), in hour order; fewer than two points draw nothing
     * @return the path's elements, closed along the bottom edge
     */
    static List<PathElement> outline(PlotArea plot, List<Point2D> points) {
        if (points.size() < 2) {
            return List.of();
        }
        var elements = new ArrayList<PathElement>();
        var first = points.getFirst();
        elements.add(new MoveTo(plot.x(first.getX()), plot.y(first.getY())));
        points.subList(1, points.size()).forEach(point -> elements.add(new LineTo(plot.x(point.getX()), plot.y(point.getY()))));
        elements.add(new ClosePath());
        return elements;
    }

    private void frame(double progress) {
        shown = CurvePoints.blend(before, target, progress);
        requestLayout();
    }

    private void point(MouseEvent event) {
        if (plot != null && model != null) {
            model.stepAt(plot.hourAt(event.getX())).ifPresent(step -> select(step, true));
        }
    }

    private void step(KeyEvent event) {
        if (model == null || model.steps().isEmpty()) {
            return;
        }
        int direction = event.getCode() == KeyCode.RIGHT ? 1 : event.getCode() == KeyCode.LEFT ? -1 : 0;
        if (direction == 0) {
            return;
        }
        var steps = model.steps();
        int index = selected == null ? -1 : steps.indexOf(selected);
        int next = index < 0 ? (direction > 0 ? 0 : steps.size() - 1) : Math.clamp(index + direction, 0, steps.size() - 1);
        select(steps.get(next), false);
        event.consume();
    }

    /** Reads out a step of the day shown; a step chosen by keyboard is read to the screen reader too. */
    private void select(Step step, boolean byPointer) {
        selected = step;
        pointed = byPointer;
        readoutText.setText(step.readout());
        if (byPointer) {
            setAccessibleText(model.spoken());
        } else {
            setAccessibleText(step.readout());
            ScreenReader.readAgain(this);
        }
        requestLayout();
    }

    private void clearSelection() {
        selected = null;
        pointed = false;
        if (model != null) {
            setAccessibleText(model.spoken());
        }
        requestLayout();
    }

    private static void horizontal(Line line, PlotArea plot, double y) {
        line.setStartX(plot.left());
        line.setEndX(plot.right());
        line.setStartY(y);
        line.setEndY(y);
    }

    private static void vertical(Line line, double x, double from, double to) {
        line.setStartX(x);
        line.setEndX(x);
        line.setStartY(from);
        line.setEndY(to);
    }

    private static <T extends Node> T shape(T node, String... styleClasses) {
        node.getStyleClass().addAll(styleClasses);
        node.setManaged(false);
        return node;
    }
}
