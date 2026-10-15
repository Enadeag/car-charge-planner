package io.github.enadeag.carchargeplanner.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.ColorScheme;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.control.Button;
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
            var view = new DashboardView(detached::incrementAndGet);
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
    void gridAdviceComesRightBelowTodaysMainCard() throws Exception {
        var view = shown(Models.plan(Optional.of(Models.ADVICE)));

        var cards = Fx.call(() -> Scenes.all(selectedPage(view)).stream()
                .filter(node -> node.getStyleClass().contains("card"))
                .map(node -> node.getStyleClass().contains("advice-card") ? "advice" : "main")
                .toList());

        assertThat(cards).containsExactly("main", "advice");
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
    void withoutAPlanTheMessageIsShownInsteadOfTheDays() throws Exception {
        var view = shown(new DashboardModel.Message("No forecast", "Could not reach Open-Meteo", "Forecast: Open-Meteo"));

        var texts = Fx.call(() -> Scenes.visibleTexts(view));

        assertThat(texts).contains("No forecast", "Could not reach Open-Meteo", "Forecast: Open-Meteo");
        assertThat(Fx.call(() -> tabs(view))).isNull();
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
