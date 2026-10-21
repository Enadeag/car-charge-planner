package nl.enadeag.carchargeplanner.desktop.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.Fx;
import nl.enadeag.carchargeplanner.desktop.FxTest;
import nl.enadeag.carchargeplanner.desktop.Keyboard;
import nl.enadeag.carchargeplanner.desktop.Scenes;
import nl.enadeag.carchargeplanner.desktop.dashboard.outlook.DashboardModel;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.host.AppName;
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
            var view = new DashboardView(() -> { }, () -> { }, Fx.REDUCED_MOTION, AppName.TITLE, ENGLISH);
            view.onOneOffAwayChange(_ -> { }, _ -> { }, Models.CLOCK);
            view.onTripChange(_ -> { }, (_, _) -> { }, _ -> { }, Models.CLOCK);
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
            OneOffAwayEntry.enterTimes(view, "10:00", "12:00");
        });

        assertThat(Keyboard.problems(stage.getScene(), view)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(ColorScheme.class)
    void everyControlOfADayWithATripIsReachedTheSameWay(ColorScheme scheme) throws Exception {
        var view = show(scheme);
        Fx.run(() -> {
            view.show(Models.plan(Optional.empty()));
            view.selectDay(1);
            // the trip with its choices, Change and Remove, and the entry of a new trip
            press(view, TextKey.TRIP_ADD);
        });

        assertThat(Keyboard.problems(stage.getScene(), view)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(ColorScheme.class)
    void theMessageBeforeSetUpAndItsButtonAreReachedTheSameWay(ColorScheme scheme) throws Exception {
        var view = show(scheme);
        Fx.run(() -> view.show(new DashboardModel.NoPlan("Not set up yet", "Set up the app to see a plan.",
                "Forecast: none", true)));

        assertThat(Keyboard.problems(stage.getScene(), view)).isEmpty();
    }
}
