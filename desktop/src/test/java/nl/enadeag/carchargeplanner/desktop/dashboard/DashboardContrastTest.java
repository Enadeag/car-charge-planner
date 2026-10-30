package nl.enadeag.carchargeplanner.desktop.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.Contrast;
import nl.enadeag.carchargeplanner.desktop.Fx;
import nl.enadeag.carchargeplanner.desktop.FxTest;
import nl.enadeag.carchargeplanner.desktop.Scenes;
import nl.enadeag.carchargeplanner.desktop.dashboard.outlook.DashboardModel;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.host.AppName;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.application.ColorScheme;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * WCAG AA on the rendered dashboard, in light and in dark: every text reaches 4.5:1 against what it sits on, every
 * glyph and every mark of the charts (the curve's edge, the house line, the windows, "now", the battery and its marks)
 * 3:1. The charts' readout and the away entry with its problem are open, so their texts are checked too. Controls that are switched off are exempt, as WCAG
 * allows.
 */
@FxTest
class DashboardContrastTest {

    private Stage stage;

    @AfterEach
    void close() throws Exception {
        Fx.run(() -> stage.close());
    }

    /** Every day of the example plan, and the message shown without a plan. */
    private List<String> problems(ColorScheme scheme) throws Exception {
        return Fx.call(() -> {
            var view = new DashboardView(() -> { }, () -> { }, Fx.REDUCED_MOTION,
                    AppName.TITLE, new Messages(Language.ENGLISH));
            view.onOneOffAwayChange(_ -> { }, _ -> { }, Models.CLOCK);
            view.onTripChange(_ -> { }, (_, _) -> { }, _ -> { }, Models.CLOCK);
            stage = Scenes.show(view, scheme);
            var problems = new ArrayList<String>();
            var plan = Models.plan(Optional.of(Models.ADVICE));
            for (int day = 0; day < plan.days().size(); day++) {
                view.show(plan);
                ((javafx.scene.control.TabPane) view.lookup(".day-tabs")).getSelectionModel().select(day);
                view.applyCss();
                openReadout(view);
                problems.addAll(Contrast.problemsIn(view));
            }
            openAwayEntryWithAProblem(view);
            problems.addAll(Contrast.problemsIn(view));
            openTripEntryWithAProblem(view);
            problems.addAll(Contrast.problemsIn(view));
            view.show(new DashboardModel.NoPlan("No forecast", "Could not reach Open-Meteo", "Forecast: Open-Meteo", false));
            view.applyCss();
            problems.addAll(Contrast.problemsIn(view));
            return problems;
        });
    }

    /** The first step's values, as the right arrow key on the chart opens them. */
    private static void openReadout(DashboardView view) {
        var chart = view.lookup(".solar-chart");
        chart.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.RIGHT, false, false, false, false));
        view.applyCss();
        view.layout();
    }

    /** The away entry at part of the day, saying why an end before the start cannot be saved. */
    private static void openAwayEntryWithAProblem(DashboardView view) {
        view.lookup(".away-card").lookupAll(".button").stream().map(javafx.scene.control.Button.class::cast)
                .filter(button -> button.getText().equals("Add")).findFirst().orElseThrow().fire();
        OneOffAwayEntry.enterTimes(view, "16:00", "10:00");
        OneOffAwayEntry.save(view);
        view.applyCss();
        view.layout();
    }

    /** The trip entry, saying why a distance that is not a number cannot be saved. */
    private static void openTripEntryWithAProblem(DashboardView view) {
        view.lookup(".trip-card").lookupAll(".button").stream().map(javafx.scene.control.Button.class::cast)
                .filter(button -> button.getText().equals("Add trip")).findFirst().orElseThrow().fire();
        TripEntry.distance(view, "far");
        TripEntry.save(view);
        view.applyCss();
        view.layout();
    }

    @ParameterizedTest
    @EnumSource(ColorScheme.class)
    void everyTextAndGlyphStandsOutEnough(ColorScheme scheme) throws Exception {
        assertThat(problems(scheme)).isEmpty();
    }
}
