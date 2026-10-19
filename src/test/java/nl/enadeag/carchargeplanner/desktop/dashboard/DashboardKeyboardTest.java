package nl.enadeag.carchargeplanner.desktop.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.Fx;
import nl.enadeag.carchargeplanner.desktop.FxTest;
import nl.enadeag.carchargeplanner.desktop.Keyboard;
import nl.enadeag.carchargeplanner.desktop.Scenes;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import java.util.Optional;
import javafx.application.ColorScheme;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The dashboard with only a keyboard, and with a screen reader on Windows: Tab reaches every control in reading
 * order, each with a name of its own and a focus ring that stands out 3:1, in light and in dark.
 */
@FxTest
class DashboardKeyboardTest {

    private static final Messages ENGLISH = new Messages(Language.ENGLISH);

    private Stage stage;

    @AfterEach
    void close() throws Exception {
        Fx.run(() -> stage.close());
    }

    /** The headless screen is 1000 px tall: the window stays on it, and the day scrolls as in a short popup. */
    private DashboardView show(ColorScheme scheme) throws Exception {
        return Fx.call(() -> {
            var view = new DashboardView(() -> { }, () -> { }, Fx.REDUCED_MOTION, ENGLISH);
            view.onAwayChange(_ -> { }, _ -> { });
            view.onBatteryEntered(_ -> { });
            view.onSetUp(() -> { });
            stage = Scenes.show(view, scheme);
            stage.setY(0);
            stage.setHeight(900);
            return view;
        });
    }

    private static void press(DashboardView view, TextKey text) {
        view.lookupAll(".button").stream().map(Button.class::cast)
                .filter(button -> ENGLISH.text(text).equals(button.getText())).findFirst().orElseThrow().fire();
    }

    @ParameterizedTest
    @EnumSource(ColorScheme.class)
    void everyControlOfADayIsReachedInOrderWithANameAndAVisibleFocus(ColorScheme scheme) throws Exception {
        var view = show(scheme);
        Fx.run(() -> {
            view.show(Models.plan(Optional.of(Models.ADVICE)));
            // both entries open, with the times of part of the day: every control the day can show
            press(view, TextKey.BATTERY_UPDATE);
            press(view, TextKey.ONE_OFF_ADD);
            ((OneOffAwayCard) view.lookup(".away-card")).editor().enterTimes("10:00", "12:00");
        });

        assertThat(Keyboard.problems(stage.getScene(), view)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(ColorScheme.class)
    void theMessageBeforeSetUpAndItsButtonAreReachedTheSameWay(ColorScheme scheme) throws Exception {
        var view = show(scheme);
        Fx.run(() -> view.show(new DashboardModel.Message("Not set up yet", "Set up the app to see a plan.",
                "Forecast: none", true)));

        assertThat(Keyboard.problems(stage.getScene(), view)).isEmpty();
    }
}
