package io.github.enadeag.carchargeplanner.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.ColorScheme;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.robot.Robot;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DashboardViewTest {

    private final AtomicInteger detached = new AtomicInteger();
    private Stage stage;

    private DashboardView shown(DashboardModel model) throws Exception {
        return Fx.call(() -> {
            var view = new DashboardView(detached::incrementAndGet, () -> { }, Fx.REDUCED_MOTION, new Messages(Language.ENGLISH));
            view.show(model);
            stage = Scenes.show(view, ColorScheme.LIGHT);
            return view;
        });
    }

    @AfterEach
    void close() throws Exception {
        Fx.run(() -> stage.close());
    }

    private static TabPane tabs(DashboardView view) {
        return (TabPane) view.lookup(".day-tabs");
    }

    /** The page of the selected day, below the tabs. */
    private static Node selectedPage(DashboardView view) {
        return view.lookup(".dashboard-content");
    }

    @Test
    void thereIsATabForEveryDayWithTodayOpen() throws Exception {
        var view = shown(Models.plan(Optional.empty()));

        var texts = Fx.call(() -> Scenes.visibleTexts(view));

        assertThat(texts).containsSubsequence("Today", "Sat", "Sun");
        assertThat(texts).contains("11:15 – 14:30", "Set 10 A (2.3 kW)", "92 % sun");
    }

    @Test
    void theMainCardReadsAsOneSentence() throws Exception {
        var view = shown(Models.plan(Optional.empty()));

        var spoken = Fx.call(() -> selectedPage(view).lookup(".main-card").queryAccessibleAttribute(AccessibleAttribute.TEXT));

        assertThat(spoken).isEqualTo("Today: charge window 11:15 to 14:30, 10 A, 92 % sun.");
    }

    @Test
    void gridAdviceComesRightBelowTodaysMainCardAndTheChartsFollow() throws Exception {
        var view = shown(Models.plan(Optional.of(Models.ADVICE)));

        var cards = Fx.call(() -> Scenes.all(selectedPage(view)).stream()
                .filter(node -> node.getStyleClass().contains("card"))
                .map(node -> node.getStyleClass().stream()
                        .filter(style -> style.endsWith("-card")).findFirst().orElseThrow())
                .toList());

        assertThat(cards).containsExactly("main-card", "advice-card", "solar-card", "battery-card");
    }

    @Test
    void theChartsFollowTheSelectedDay() throws Exception {
        var view = shown(Models.plan(Optional.empty()));

        Fx.run(() -> tabs(view).getSelectionModel().select(1));

        assertThat(Fx.call(() -> selectedPage(view).lookup(".solar-chart").queryAccessibleAttribute(AccessibleAttribute.TEXT)))
                .isEqualTo(Models.CLOUDY_TOMORROW.curve().spoken());
        assertThat(Fx.call(() -> selectedPage(view).lookup(".battery-bar").queryAccessibleAttribute(AccessibleAttribute.TEXT)))
                .isEqualTo(Models.CLOUDY_TOMORROW.battery().spoken());
    }

    @Test
    void theBatteryLevelEnteredOnTodaysCardReachesTheDashboardsHandler() throws Exception {
        var view = shown(Models.plan(Optional.empty()));
        var levels = new ArrayList<Integer>();
        Fx.run(() -> view.onBatteryEntered(levels::add));
        var update = Fx.call(() -> (Button) selectedPage(view).lookupAll(".button").stream()
                .filter(node -> "Update".equals(((Button) node).getText())).findFirst().orElseThrow());

        Fx.run(() -> {
            update.fire();
            var editor = (BatteryEditor) selectedPage(view).lookup(".battery-editor");
            editor.field().getEditor().setText("81");
            editor.save();
        });

        assertThat(levels).containsExactly(81);
    }

    @Test
    void gridAdviceIsOnlyOnTodaysTab() throws Exception {
        var view = shown(Models.plan(Optional.of(Models.ADVICE)));

        var adviceOnTomorrow = Fx.call(() -> {
            tabs(view).getSelectionModel().select(1);
            return view.lookup(".advice-card");
        });

        assertThat(adviceOnTomorrow).isNull();
    }

    @Test
    void withoutAdviceThereIsNoAdviceCard() throws Exception {
        var view = shown(Models.plan(Optional.empty()));

        assertThat(Fx.call(() -> view.lookup(".advice-card"))).isNull();
    }

    @Test
    void theArrowKeysMoveBetweenTheDays() throws Exception {
        var view = shown(Models.plan(Optional.empty()));

        Fx.run(() -> {
            tabs(view).requestFocus();
            var robot = new Robot();
            robot.keyPress(KeyCode.RIGHT);
            robot.keyRelease(KeyCode.RIGHT);
        });

        var spoken = Fx.call(() -> selectedPage(view).lookup(".main-card").getAccessibleText());
        assertThat(spoken).startsWith("Tomorrow:");
    }

    @Test
    void aNewPlanKeepsTheDayThatIsOpen() throws Exception {
        var view = shown(Models.plan(Optional.empty()));

        Fx.run(() -> {
            tabs(view).getSelectionModel().select(2);
            view.show(Models.plan(Optional.empty()));
        });

        assertThat(Fx.call(() -> tabs(view).getSelectionModel().getSelectedIndex())).isEqualTo(2);
    }

    @Test
    void aPlanWithoutDaysShowsNoTabsAndDoesNotFail() throws Exception {
        var view = shown(Models.plan(Optional.empty()));

        Fx.run(() -> view.show(new DashboardModel.Plan(List.of(), Optional.empty(), Models.FOOTER)));

        assertThat(Fx.call(() -> tabs(view).getTabs())).isEmpty();
        assertThat(Fx.call(() -> view.lookup(".main-card"))).isNull();
        assertThat(Fx.call(() -> Scenes.visibleTexts(view))).contains(Models.FOOTER);
    }

    @Test
    void aDayTallerThanTheSpaceScrollsBelowHeaderAndTabsThatStayInView() throws Exception {
        var busy = new DayModel("Sat", "Tomorrow", DayKind.SUN, "Charge window", "06:00 – 06:10",
                Models.SUNNY_TODAY.facts(), Models.SUNNY_TODAY.chips(), "Also 06:15 – 06:25 at 6 A. ".repeat(80), "",
                Models.SUNNY_TODAY.curve(), Models.CLOUDY_TOMORROW.battery());
        var plan = new DashboardModel.Plan(List.of(Models.CLOUDY_TOMORROW, busy), Optional.empty(), Models.FOOTER);
        var view = Fx.call(() -> {
            var it = new DashboardView(() -> { }, () -> { }, Fx.REDUCED_MOTION, new Messages(Language.ENGLISH));
            it.show(plan);
            stage = Scenes.show(it, ColorScheme.LIGHT);
            stage.getScene().getRoot().resize(420, 600);
            return it;
        });
        double tabsHeight = Fx.call(() -> tabs(view).prefHeight(-1));

        var placed = Fx.call(() -> {
            tabs(view).getSelectionModel().select(1);
            var root = view.getScene().getRoot();
            root.applyCss();
            root.resize(420, 600);
            root.layout();
            return List.of(view.lookup(".dashboard-header"), tabs(view), view.lookup(".dashboard-footer")).stream()
                    .map(node -> node.localToScene(node.getBoundsInLocal())).toList();
        });

        assertThat(placed.get(0).getMinY()).as("header").isGreaterThanOrEqualTo(0);
        assertThat(placed.get(1).getHeight()).as("tabs").isEqualTo(tabsHeight);
        assertThat(placed.get(2).getMaxY()).as("footer").isLessThanOrEqualTo(600);
        var scroll = (ScrollPane) view.lookup(".day-scroll");
        double viewport = Fx.call(() -> scroll.getViewportBounds().getHeight());
        double day = Fx.call(() -> scroll.getContent().getLayoutBounds().getHeight());
        assertThat(day).as("the day is taller than its viewport, so it scrolls").isGreaterThan(viewport);
    }

    @Test
    void withoutAPlanTheMessageIsShownInsteadOfTheDays() throws Exception {
        var view = shown(new DashboardModel.Message("No forecast", "Could not reach Open-Meteo", "Forecast: Open-Meteo"));

        var texts = Fx.call(() -> Scenes.visibleTexts(view));

        assertThat(texts).contains("No forecast", "Could not reach Open-Meteo", "Forecast: Open-Meteo");
        assertThat(Fx.call(() -> Scenes.reallyVisible(tabs(view)))).isFalse();
    }

    @Test
    void theFooterNamesTheForecastAndOffersNothingThatIsNotThereYet() throws Exception {
        var view = shown(Models.plan(Optional.empty()));

        var buttons = Fx.call(() -> Scenes.all(view.lookup(".dashboard-footer")).stream()
                .filter(Button.class::isInstance).map(Button.class::cast).toList());

        assertThat(Fx.call(() -> Scenes.visibleTexts(view.lookup(".dashboard-footer")))).contains(Models.FOOTER);
        assertThat(buttons).hasSize(2).allSatisfy(button -> {
            assertThat(button.isDisabled()).isTrue();
            assertThat(button.getAccessibleText()).endsWith("not available yet");
        });
    }

    @Test
    void theDetachButtonOpensAWindowAndOnlyShowsInThePopup() throws Exception {
        var view = shown(Models.plan(Optional.empty()));
        var detach = Fx.call(() -> (Button) view.lookup(".detach"));

        Fx.run(detach::fire);
        assertThat(detached).hasValue(1);

        Fx.run(() -> view.setDetachable(false));
        assertThat(Fx.call(() -> Scenes.reallyVisible(detach))).isFalse();
    }
}
