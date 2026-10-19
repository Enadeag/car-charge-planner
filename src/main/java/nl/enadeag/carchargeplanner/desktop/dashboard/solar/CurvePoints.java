package nl.enadeag.carchargeplanner.desktop.dashboard.solar;

import nl.enadeag.carchargeplanner.desktop.dashboard.solar.SolarCurveModel.Step;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Point2D;

/**
 * The solar curve's outline in hours and kW, before it is put in pixels: the curve passes through the middle of each
 * step at its power, and starts and ends at 0 so it closes into an area. Also blends one outline into the next for the
 * animation.
 */
final class CurvePoints {

    private CurvePoints() {
    }

    /**
     * The outline of the steps.
     *
     * @param steps the forecast steps in time order; may be empty
     * @param fromHour the first hour shown; the outline does not start before it
     * @param toHour the last hour shown, after {@code fromHour}; the outline does not end after it
     * @return points as (hour, kW), in hour order: 0 at the first step's start, each step's middle, 0 at the last
     *     step's end; without steps a flat line at 0 over the hours shown
     */
    static List<Point2D> of(List<Step> steps, double fromHour, double toHour) {
        if (steps.isEmpty()) {
            return List.of(new Point2D(fromHour, 0), new Point2D(toHour, 0));
        }
        var points = new ArrayList<Point2D>();
        points.add(new Point2D(Math.max(fromHour, steps.getFirst().startHour()), 0));
        for (var step : steps) {
            points.add(new Point2D(Math.clamp(step.midHour(), fromHour, toHour), step.kw()));
        }
        points.add(new Point2D(Math.min(toHour, steps.getLast().endHour()), 0));
        return points;
    }

    /**
     * The power an outline has at an hour, straight between its points.
     *
     * @param points an outline as {@link #of} makes it; may be empty
     * @param hour the hour from the start of the day
     * @return the power in kW; 0 outside the outline and for an empty one
     */
    static double valueAt(List<Point2D> points, double hour) {
        for (int i = 1; i < points.size(); i++) {
            var before = points.get(i - 1);
            var after = points.get(i);
            if (hour >= before.getX() && hour <= after.getX()) {
                double span = after.getX() - before.getX();
                double share = span == 0 ? 1 : (hour - before.getX()) / span;
                return before.getY() + (after.getY() - before.getY()) * share;
            }
        }
        return 0;
    }

    /**
     * An outline on its way from one to another.
     *
     * @param from the outline shown so far; may be empty, and then the curve grows from 0
     * @param to the outline to arrive at
     * @param progress how far along, from 0 (the shape of {@code from}) to 1 (exactly {@code to})
     * @return the points of {@code to} at their hours, each with its power between the two outlines
     */
    static List<Point2D> blend(List<Point2D> from, List<Point2D> to, double progress) {
        return to.stream()
                .map(point -> new Point2D(point.getX(),
                        valueAt(from, point.getX()) * (1 - progress) + point.getY() * progress))
                .toList();
    }
}
