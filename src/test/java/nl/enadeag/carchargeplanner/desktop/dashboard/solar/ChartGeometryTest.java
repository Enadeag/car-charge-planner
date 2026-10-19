package nl.enadeag.carchargeplanner.desktop.dashboard.solar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import nl.enadeag.carchargeplanner.desktop.dashboard.solar.SolarCurveModel.Step;
import java.util.List;
import javafx.geometry.Point2D;
import org.junit.jupiter.api.Test;

/** Where the solar chart puts hours and kW, without a screen. */
class ChartGeometryTest {

    /** 200 by 100 pixels at (20, 10), from 06:00 to 20:00 and 0 to 4 kW. */
    private static final PlotArea PLOT = new PlotArea(20, 10, 200, 100, 6, 20, 4);

    private static Step step(double start, double kw) {
        return new Step(start, start + 1, kw, "");
    }

    @Test
    void hoursRunFromTheLeftEdgeToTheRightEdge() {
        assertThat(PLOT.x(6)).isEqualTo(20);
        assertThat(PLOT.x(13)).isEqualTo(120);
        assertThat(PLOT.x(20)).isEqualTo(220);
    }

    @Test
    void powerRunsFromTheBottomEdgeUpToTheScale() {
        assertThat(PLOT.y(0)).isEqualTo(110);
        assertThat(PLOT.y(1)).isEqualTo(85);
        assertThat(PLOT.y(4)).isEqualTo(10);
    }

    @Test
    void whatLiesOutsideThePlotGoesOnItsEdge() {
        assertThat(PLOT.x(3)).isEqualTo(20);
        assertThat(PLOT.x(23)).isEqualTo(220);
        assertThat(PLOT.y(6.5)).isEqualTo(10);
        assertThat(PLOT.y(-1)).isEqualTo(110);
    }

    @Test
    void thePointersPlaceGivesBackItsHour() {
        assertThat(PLOT.hourAt(120)).isEqualTo(13);
        assertThat(PLOT.hourAt(PLOT.x(9.25))).isCloseTo(9.25, within(1e-9));
        assertThat(PLOT.hourAt(0)).isEqualTo(6);
        assertThat(PLOT.hourAt(500)).isEqualTo(20);
        assertThat(new PlotArea(0, 0, 0, 0, 6, 20, 4).hourAt(10)).isEqualTo(6);
    }

    @Test
    void aPlotNeedsHoursThatRunForwardsAndAPositiveScale() {
        assertThatThrownBy(() -> new PlotArea(0, 0, 10, 10, 8, 8, 4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlotArea(0, 0, 10, 10, 6, 20, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlotArea(0, 0, -1, 10, 6, 20, 4)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theCurvePassesThroughTheMiddleOfEachStepAndClosesAtZero() {
        var points = CurvePoints.of(List.of(step(7, 1), step(8, 3)), 6, 20);

        assertThat(points).containsExactly(new Point2D(7, 0), new Point2D(7.5, 1), new Point2D(8.5, 3), new Point2D(9, 0));
    }

    @Test
    void theCurveStaysWithinTheHoursShown() {
        var points = CurvePoints.of(List.of(step(5, 0), step(19.5, 0.2)), 6, 20);

        assertThat(points).extracting(Point2D::getX).allSatisfy(hour -> assertThat(hour).isBetween(6.0, 20.0));
    }

    @Test
    void withoutStepsTheCurveIsFlatAtZero() {
        assertThat(CurvePoints.of(List.of(), 6, 20)).containsExactly(new Point2D(6, 0), new Point2D(20, 0));
    }

    @Test
    void theCurvesPowerBetweenPointsIsOnTheStraightLine() {
        var points = CurvePoints.of(List.of(step(7, 1), step(8, 3)), 6, 20);

        assertThat(CurvePoints.valueAt(points, 8)).isCloseTo(2, within(1e-9));
        assertThat(CurvePoints.valueAt(points, 7.25)).isCloseTo(0.5, within(1e-9));
        assertThat(CurvePoints.valueAt(points, 12)).isZero();
        assertThat(CurvePoints.valueAt(List.of(), 8)).isZero();
    }

    @Test
    void aBlendStartsInTheOldShapeAndEndsExactlyInTheNewOne() {
        var old = CurvePoints.of(List.of(step(7, 2), step(8, 2)), 6, 20);
        var next = CurvePoints.of(List.of(step(7, 4), step(8, 4)), 6, 20);

        assertThat(CurvePoints.blend(old, next, 0)).extracting(Point2D::getY).containsExactly(0.0, 2.0, 2.0, 0.0);
        assertThat(CurvePoints.blend(old, next, 0.5)).extracting(Point2D::getY).containsExactly(0.0, 3.0, 3.0, 0.0);
        assertThat(CurvePoints.blend(old, next, 1)).isEqualTo(next);
    }

    @Test
    void theFirstCurveGrowsFromZero() {
        var next = CurvePoints.of(List.of(step(7, 4)), 6, 20);

        assertThat(CurvePoints.blend(List.of(), next, 0)).extracting(Point2D::getY).containsOnly(0.0);
    }
}
