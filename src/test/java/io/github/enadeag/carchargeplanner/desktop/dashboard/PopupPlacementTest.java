package io.github.enadeag.carchargeplanner.desktop.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.desktop.dashboard.PopupPlacement.Edge;
import java.util.List;
import javafx.geometry.Dimension2D;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import org.junit.jupiter.api.Test;

class PopupPlacementTest {

    private static final Dimension2D POPUP = new Dimension2D(420, 600);
    private static final double M = PopupPlacement.MARGIN;

    private static Rectangle2D area(double minX, double minY, double maxX, double maxY) {
        return new Rectangle2D(minX, minY, maxX - minX, maxY - minY);
    }

    @Test
    void aboveATaskbarAtTheBottomCentredOnTheIconAndKeptOnTheScreen() {
        var available = area(0, 0, 1920, 1040);

        var middle = PopupPlacement.position(new Point2D(960, 1060), POPUP, available);
        var corner = PopupPlacement.position(new Point2D(1880, 1060), POPUP, available);

        assertThat(middle).isEqualTo(new Point2D(960 - 210, 1040 - 600 - M));
        assertThat(corner).isEqualTo(new Point2D(1920 - 420 - M, 1040 - 600 - M));
    }

    @Test
    void belowAPanelAtTheTop() {
        assertThat(PopupPlacement.position(new Point2D(1700, 15), POPUP, area(0, 30, 1920, 1200)))
                .isEqualTo(new Point2D(1700 - 210, 30 + M));
    }

    @Test
    void besideAPanelOnTheLeftOrRight() {
        var left = PopupPlacement.position(new Point2D(20, 700), POPUP, area(48, 0, 1920, 1200));
        var right = PopupPlacement.position(new Point2D(1900, 700), POPUP, area(0, 0, 1872, 1200));

        assertThat(left).isEqualTo(new Point2D(48 + M, 700 - 300));
        assertThat(right).isEqualTo(new Point2D(1872 - 420 - M, 700 - 300));
    }

    @Test
    void anIconInsideTheAvailableAreaNamesTheNearestEdge() {
        var available = area(0, 0, 1920, 1200);

        assertThat(PopupPlacement.panelEdge(new Point2D(1800, 1180), available)).isEqualTo(Edge.BOTTOM);
        assertThat(PopupPlacement.panelEdge(new Point2D(1800, 12), available)).isEqualTo(Edge.TOP);
        assertThat(PopupPlacement.panelEdge(new Point2D(10, 500), available)).isEqualTo(Edge.LEFT);
    }

    @Test
    void thePopupStaysClearOfAnIconInAPanelTheSystemCountsAsAvailable() {
        var position = PopupPlacement.position(new Point2D(1800, 1180), POPUP, area(0, 0, 1920, 1200));

        assertThat(position.getY() + POPUP.getHeight()).isEqualTo(1180 - M);
    }

    @Test
    void aReportedIconPositionIsUsedOnlyWhenItLiesOnAScreen() {
        var screens = List.of(area(0, 0, 1920, 1200), area(1920, 0, 3840, 1080));
        var primaryFree = area(0, 0, 1920, 1168);

        assertThat(PopupPlacement.onAScreen(new Point2D(2500, 1060), screens, primaryFree))
                .isEqualTo(new Point2D(2500, 1060));
        assertThat(PopupPlacement.onAScreen(new Point2D(2500, 1150), screens, primaryFree))
                .isEqualTo(new Point2D(1920, 1168));
        assertThat(PopupPlacement.onAScreen(new Point2D(-2_147_483_648, 0), screens, primaryFree))
                .isEqualTo(new Point2D(1920, 1168));
    }

    @Test
    void aPopupTooLargeForTheScreenShowsItsTopLeft() {
        var position = PopupPlacement.position(new Point2D(300, 500), new Dimension2D(800, 900), area(0, 0, 640, 480));

        assertThat(position).isEqualTo(new Point2D(M, M));
    }
}
