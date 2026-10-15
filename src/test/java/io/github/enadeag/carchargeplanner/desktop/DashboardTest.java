package io.github.enadeag.carchargeplanner.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.javafx.stage.WindowHelper;
import io.github.enadeag.carchargeplanner.engine.Outlook;
import io.github.enadeag.carchargeplanner.engine.plan.DayPlan;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.ColorScheme;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Point2D;
import javafx.scene.control.Button;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.robot.Robot;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The dashboard as the tray and the composition root use it, on JavaFX's headless platform (a 1000 px screen). */
class DashboardTest {

    private static final Point2D ICON_AT_BOTTOM_RIGHT = new Point2D(960, 990);

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-15T08:00:00Z"));
    private final InstantSource clock = now::get;
    private Dashboard dashboard;

    @BeforeEach
    void make() throws Exception {
        dashboard = Fx.call(() -> new Dashboard(new AppIcon(), new SimpleObjectProperty<>(ColorScheme.LIGHT), "Open-Meteo", clock));
    }

    @AfterEach
    void closeAll() throws Exception {
        Fx.run(() -> List.copyOf(Window.getWindows()).forEach(Window::hide));
    }

    private static Optional<Stage> showing(StageStyle style) throws Exception {
        return Fx.call(() -> Window.getWindows().stream()
                .filter(window -> window instanceof Stage stage && stage.getStyle() == style && stage.isShowing())
                .map(Stage.class::cast)
                .findFirst());
    }

    private static Optional<Stage> popup() throws Exception {
        return showing(StageStyle.TRANSPARENT);
    }

    private static Optional<Stage> window() throws Exception {
        return showing(StageStyle.DECORATED);
    }

    private void clickTheIcon() throws Exception {
        dashboard.popUp(ICON_AT_BOTTOM_RIGHT);
        Fx.settle();
    }

    private void later(Duration duration) {
        now.updateAndGet(instant -> instant.plus(duration));
    }

    @Test
    void aClickOnTheIconOpensThePopupAboveIt() throws Exception {
        clickTheIcon();

        var popup = popup().orElseThrow();
        assertThat(popup.getY() + popup.getHeight()).isLessThanOrEqualTo(ICON_AT_BOTTOM_RIGHT.getY());
        assertThat(popup.getX()).isLessThan(ICON_AT_BOTTOM_RIGHT.getX());
        assertThat(popup.getX() + popup.getWidth()).isLessThanOrEqualTo(1000);
    }

    @Test
    void aPlanThatCameWhileThePopupWasClosedFitsWhenItOpensAgain() throws Exception {
        clickTheIcon();
        clickTheIcon();
        dashboard.show(planWithTwoDays());
        later(Duration.ofSeconds(2));

        clickTheIcon();

        var popup = popup().orElseThrow();
        double needed = Fx.call(() -> {
            var root = popup.getScene().getRoot();
            root.applyCss();
            root.layout();
            return root.prefHeight(popup.getWidth());
        });
        assertThat(popup.getHeight()).isEqualTo(needed);
    }

    @Test
    void escapeClosesThePopup() throws Exception {
        clickTheIcon();

        Fx.run(() -> {
            var robot = new Robot();
            robot.keyPress(KeyCode.ESCAPE);
            robot.keyRelease(KeyCode.ESCAPE);
        });

        assertThat(popup()).isEmpty();
    }

    /**
     * What a click on another window, or on the panel, does to the popup. The headless platform has no window manager
     * that moves the focus, so the test tells the popup what the system would.
     */
    private static void focusElsewhere() throws Exception {
        var popup = popup().orElseThrow();
        Fx.run(() -> {
            WindowHelper.setFocused(popup.getOwner(), false);
            WindowHelper.setFocused(popup, false);
        });
        Fx.settle();
    }

    @Test
    void theKeyboardReachesThePopup() throws Exception {
        clickTheIcon();
        dashboard.show(planWithTwoDays());
        Fx.settle();
        var tabs = (TabPane) popup().orElseThrow().getScene().lookup(".day-tabs");

        Fx.run(() -> {
            tabs.requestFocus();
            var robot = new Robot();
            robot.keyPress(KeyCode.RIGHT);
            robot.keyRelease(KeyCode.RIGHT);
        });

        assertThat(Fx.call(() -> tabs.getSelectionModel().getSelectedIndex())).isEqualTo(1);
    }

    private static Outlook planWithTwoDays() {
        var zone = ZoneId.of("Europe/Brussels");
        var today = LocalDate.of(2026, 10, 15);
        var days = List.of(new DayPlan(today, 50, List.of(), 50, 40, 10),
                new DayPlan(today.plusDays(1), 40, List.of(), 40, 30, 10));
        return new Outlook.Planned(today.atTime(8, 0).atZone(zone).toInstant(), zone, days, 50, false, Optional.empty());
    }

    @Test
    void aReportedIconPositionOffEveryScreenFallsBackToTheCornerOfThePrimaryScreen() throws Exception {
        dashboard.popUp(new Point2D(-40_000, 90_000));
        Fx.settle();

        var popup = popup().orElseThrow();
        assertThat(popup.getX() + popup.getWidth()).isEqualTo(1000 - PopupPlacement.MARGIN);
        assertThat(popup.getY() + popup.getHeight()).isEqualTo(1000 - PopupPlacement.MARGIN);
    }

    @Test
    void aClickElsewhereClosesThePopup() throws Exception {
        clickTheIcon();

        focusElsewhere();

        assertThat(popup()).isEmpty();
    }

    @Test
    void aSecondClickOnTheIconClosesThePopup() throws Exception {
        clickTheIcon();
        later(Duration.ofSeconds(2));

        clickTheIcon();

        assertThat(popup()).isEmpty();
    }

    @Test
    void theClickThatClosedThePopupByTakingTheFocusDoesNotOpenItAgain() throws Exception {
        clickTheIcon();
        focusElsewhere();

        later(Duration.ofMillis(100));
        clickTheIcon();
        assertThat(popup()).isEmpty();

        later(Duration.ofSeconds(1));
        clickTheIcon();
        assertThat(popup()).isPresent();
    }

    @Test
    void thePopupDetachesIntoAWindowThatTheIconThenBringsBack() throws Exception {
        clickTheIcon();

        var detach = (Button) popup().orElseThrow().getScene().lookup(".detach");
        Fx.run(detach::fire);

        assertThat(popup()).isEmpty();
        var window = window().orElseThrow();
        assertThat(Fx.call(() -> window.getScene().lookup(".dashboard"))).isNotNull();
        assertThat(Fx.call(() -> window.getScene().lookup(".detach").isVisible())).isFalse();

        later(Duration.ofSeconds(2));
        clickTheIcon();
        assertThat(popup()).isEmpty();
        assertThat(window()).isPresent();
    }

    @Test
    void theMenuOpensTheWindow() throws Exception {
        dashboard.openWindow();
        Fx.settle();

        assertThat(window()).isPresent();
    }

    @Test
    void theDashboardShowsTheLatestOutlook() throws Exception {
        clickTheIcon();

        dashboard.show(new Outlook.NotSetUp("No engine.json in ~/.config/car-charge-planner"));
        Fx.settle();

        var popup = popup().orElseThrow();
        var texts = Fx.call(() -> Scenes.visibleTexts(popup.getScene().getRoot()));
        assertThat(texts).contains("Not set up", "No engine.json in ~/.config/car-charge-planner");
    }
}
