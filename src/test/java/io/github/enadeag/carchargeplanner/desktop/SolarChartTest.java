package io.github.enadeag.carchargeplanner.desktop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import javafx.application.ColorScheme;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Point2D;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SolarChartTest {

    private static final SolarCurveModel TODAY = Models.SUNNY_TODAY.curve();
    private static final SolarCurveModel TOMORROW = Models.CLOUDY_TOMORROW.curve();

    private Stage stage;
    private SolarChart chart;

    @AfterEach
    void close() throws Exception {
        Fx.run(() -> stage.close());
    }

    /** The chart in its card, 400 pixels wide, showing a day. */
    private void show(SolarCurveModel day, ObservableValue<Boolean> reducedMotion) throws Exception {
        Fx.run(() -> {
            var card = new SolarCard(reducedMotion, new Messages(Language.ENGLISH));
            chart = card.chart();
            card.show(day);
            stage = Scenes.show(card, ColorScheme.LIGHT);
            stage.getScene().getRoot().resize(400, 300);
            stage.getScene().getRoot().layout();
        });
    }

    private void show(SolarCurveModel day) throws Exception {
        show(day, Fx.REDUCED_MOTION);
    }

    private PlotArea plot() throws Exception {
        return Fx.call(() -> chart.plot().orElseThrow());
    }

    private <T extends Node> T part(String styleClass, Class<T> type) throws Exception {
        return Fx.call(() -> type.cast(chart.lookup("." + styleClass)));
    }

    private void relayout() throws Exception {
        Fx.run(() -> stage.getScene().getRoot().layout());
    }

    @Test
    void theAreaFollowsTheMiddleOfEachStepFromZeroToZero() throws Exception {
        show(TODAY);
        var plot = plot();

        var area = part("solar-area", Path.class);
        var elements = Fx.call(() -> List.copyOf(area.getElements()));

        var start = (MoveTo) elements.getFirst();
        assertThat(new Point2D(start.getX(), start.getY())).isEqualTo(new Point2D(plot.x(7), plot.y(0)));
        var peak = (LineTo) elements.get(7); // the step from 13:00 to 14:00
        var step = TODAY.steps().get(6);
        assertThat(peak.getX()).isCloseTo(plot.x(13.5), within(1e-9));
        assertThat(peak.getY()).isCloseTo(plot.y(step.kw()), within(1e-9));
        assertThat(peak.getY()).isLessThan(plot.y(2)); // above the middle of the 4 kW scale
        var end = (LineTo) elements.get(elements.size() - 2);
        assertThat(new Point2D(end.getX(), end.getY())).isEqualTo(new Point2D(plot.x(19), plot.y(0)));
    }

    @Test
    void thePlotFillsTheChartBesideTheScaleAndAboveTheTimes() throws Exception {
        show(TODAY);
        var plot = plot();

        assertThat(plot.left()).isGreaterThan(0);
        assertThat(plot.right()).isLessThanOrEqualTo(Fx.call(() -> chart.getWidth()));
        assertThat(plot.bottom()).isLessThan(Fx.call(() -> chart.getHeight()));
        assertThat(plot.width()).isGreaterThan(250);
        assertThat(plot.height()).isGreaterThan(60);
    }

    @Test
    void theChargeWindowIsAGreenBandWithABarOnTheBaseline() throws Exception {
        show(TODAY);
        var plot = plot();

        var band = part("window-band", Rectangle.class);
        var mark = part("window-mark", Line.class);

        assertThat(band.getX()).isCloseTo(plot.x(11.25), within(1e-9));
        assertThat(band.getWidth()).isCloseTo(plot.x(14.5) - plot.x(11.25), within(1e-9));
        assertThat(band.getHeight()).isEqualTo(plot.height());
        assertThat(List.of(mark.getStartX(), mark.getEndX())).containsExactly(plot.x(11.25), plot.x(14.5));
        assertThat(mark.getStartY()).isEqualTo(plot.bottom());
    }

    @Test
    void theHouseIsALineAtItsPower() throws Exception {
        show(TODAY);
        var plot = plot();

        var house = part("house-line", Line.class);

        assertThat(house.getStartY()).isEqualTo(plot.y(0.4));
        assertThat(List.of(house.getStartX(), house.getEndX())).containsExactly(plot.left(), plot.right());
    }

    @Test
    void nowIsALineOnTodayOnly() throws Exception {
        show(TODAY);
        var now = part("now-line", Line.class);

        assertThat(Fx.call(now::isVisible)).isTrue();
        assertThat(now.getStartX()).isCloseTo(plot().x(8.2), within(1e-9));

        Fx.run(() -> chart.show(TOMORROW));
        relayout();
        assertThat(Fx.call(() -> chart.lookup(".now-line").isVisible())).isFalse();
        assertThat(Fx.call(() -> chart.lookup(".window-band"))).isNull();
    }

    /** What the chart reads out right after an event, in one go: the headless platform may send its own events. */
    private record Readout(double startHour, String text, boolean visible) {
    }

    private Readout after(MouseEvent event) throws Exception {
        return Fx.call(() -> {
            chart.fireEvent(event);
            stage.getScene().getRoot().layout();
            return new Readout(chart.selected().map(SolarCurveModel.Step::startHour).orElse(-1.0),
                    ((Label) chart.lookup(".chart-readout-text")).getText(), chart.lookup(".chart-readout").isVisible());
        });
    }

    @Test
    void pointingAtTheChartReadsOutTheStepUnderThePointer() throws Exception {
        show(TODAY);
        double x = plot().x(13.4);

        var pointed = after(Fx.call(() -> mouse(MouseEvent.MOUSE_MOVED, x)));

        assertThat(pointed).isEqualTo(new Readout(13, TODAY.steps().get(6).readout(), true));
        assertThat(after(Fx.call(() -> mouse(MouseEvent.MOUSE_EXITED, x))).visible()).isFalse();
    }

    @Test
    void aTapReadsOutTheStepToo() throws Exception {
        show(TODAY);
        double x = plot().x(9.6);

        assertThat(after(Fx.call(() -> mouse(MouseEvent.MOUSE_PRESSED, x))).startHour()).isEqualTo(9);
    }

    @Test
    void theArrowKeysMoveTheReadoutFromStepToStep() throws Exception {
        show(TODAY);

        key(KeyCode.RIGHT);
        assertThat(Fx.call(() -> chart.selected().orElseThrow().startHour())).isEqualTo(7);
        key(KeyCode.RIGHT);
        key(KeyCode.RIGHT);
        key(KeyCode.LEFT);
        assertThat(Fx.call(() -> chart.selected().orElseThrow().startHour())).isEqualTo(8);
        assertThat(Fx.call(() -> chart.queryAccessibleAttribute(AccessibleAttribute.HELP)))
                .isEqualTo(TODAY.steps().get(1).readout());
    }

    @Test
    void aScreenReaderReadsTheChartAsText() throws Exception {
        show(TODAY);

        assertThat(Fx.call(() -> chart.queryAccessibleAttribute(AccessibleAttribute.ROLE))).isEqualTo(AccessibleRole.TEXT);
        assertThat(Fx.call(() -> chart.queryAccessibleAttribute(AccessibleAttribute.TEXT))).isEqualTo(TODAY.spoken());
        assertThat(Fx.call(chart::isFocusTraversable)).isTrue();
    }

    @Test
    void underReducedMotionANewDayShowsAtOnce() throws Exception {
        show(TODAY);

        Fx.run(() -> chart.show(TOMORROW));

        assertThat(Fx.call(chart::shownPoints)).isEqualTo(CurvePoints.of(TOMORROW.steps(), 6, 20));
    }

    @Test
    void otherwiseTheCurveMovesToItsNewShape() throws Exception {
        var motion = new SimpleBooleanProperty(false);
        show(TODAY, motion);
        Thread.sleep((long) Tween.LENGTH.toMillis() * 2);

        var target = CurvePoints.of(TOMORROW.steps(), 6, 20);
        var moving = Fx.call(() -> {
            chart.show(TOMORROW);
            return chart.shownPoints();
        });

        assertThat(moving).isNotEqualTo(target);
        Thread.sleep((long) Tween.LENGTH.toMillis() * 2);
        assertThat(Fx.call(chart::shownPoints)).isEqualTo(target);
    }

    @Test
    void reducedMotionIsAskedAtEachChange() throws Exception {
        var motion = new SimpleBooleanProperty(false);
        show(TODAY, motion);

        Fx.run(() -> {
            motion.set(true);
            chart.show(TOMORROW);
        });

        assertThat(Fx.call(chart::shownPoints)).isEqualTo(CurvePoints.of(TOMORROW.steps(), 6, 20));
    }

    private String readout() throws Exception {
        return Fx.call(() -> ((Label) chart.lookup(".chart-readout-text")).getText());
    }

    private void key(KeyCode code) throws Exception {
        Fx.run(() -> chart.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false)));
    }

    /** A mouse event at a place in the chart; the event carries it in the scene's coordinates, as a real one does. */
    private MouseEvent mouse(javafx.event.EventType<MouseEvent> type, double x) {
        var scene = chart.localToScene(x, 40);
        return new MouseEvent(type, scene.getX(), scene.getY(), scene.getX(), scene.getY(), MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, false, null);
    }
}
