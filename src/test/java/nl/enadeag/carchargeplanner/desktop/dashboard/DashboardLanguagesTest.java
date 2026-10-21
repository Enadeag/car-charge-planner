package nl.enadeag.carchargeplanner.desktop.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.Fx;
import nl.enadeag.carchargeplanner.desktop.FxTest;
import nl.enadeag.carchargeplanner.desktop.Scenes;
import nl.enadeag.carchargeplanner.desktop.TextScale;
import nl.enadeag.carchargeplanner.desktop.dashboard.outlook.OutlookMapper;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.Outlook;
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastSource;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import nl.enadeag.carchargeplanner.engine.forecast.Freshness;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderRole;
import nl.enadeag.carchargeplanner.engine.plan.DayPlan;
import nl.enadeag.carchargeplanner.engine.plan.GridAdvice;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import nl.enadeag.carchargeplanner.host.AppName;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javafx.application.ColorScheme;
import javafx.scene.control.Button;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * In every language, and with the system's text at 150 and 200 %, the dashboard shows each text whole and on its own:
 * a longer word widens or wraps its place, never cuts it off or runs into another text.
 */
@FxTest
class DashboardLanguagesTest {

    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    /** A Wednesday: the tabs show the longest Dutch day names, "Vandaag" and "Do". */
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 1);

    private Stage stage;

    @AfterEach
    void close() throws Exception {
        Fx.run(() -> stage.close());
    }

    private static Instant at(LocalDate day, int hour) {
        return day.atTime(hour, 15).atZone(BRUSSELS).toInstant();
    }

    /** Hourly steps with sun from 07:00 to 19:00, peaking at 13:00 at 4.3 kW. */
    private static List<ForecastStep> sunny(LocalDate day) {
        var steps = new ArrayList<ForecastStep>();
        for (int hour = 0; hour < 24; hour++) {
            var start = day.atTime(hour, 0).atZone(BRUSSELS).toInstant();
            double kw = hour >= 7 && hour < 19 ? 4.3 - Math.abs(hour - 13) * 0.6 : 0;
            steps.add(new ForecastStep(start, start.plusSeconds(3600), kw));
        }
        return steps;
    }

    /** Every kind of day: one with two windows and grid energy, one that needs the grid charge, one without a window. */
    private static Outlook.Planned planned() {
        var today = new DayPlan(TODAY, new Percent(50), List.of(new Window(at(TODAY, 10), at(TODAY, 13), 10, 2.3, 7.3, 0.6),
                new Window(at(TODAY, 14), at(TODAY, 15), 8, 1.8, 1.8, 0)), new Percent(74), new Percent(62), 12, sunny(TODAY));
        var tomorrow = new DayPlan(TODAY.plusDays(1), new Percent(62), List.of(), new Percent(62), new Percent(22), 12, sunny(TODAY.plusDays(1)));
        var friday = new DayPlan(TODAY.plusDays(2), new Percent(22), List.of(), new Percent(22), new Percent(10), 12, List.of());
        var night = new Window(at(TODAY, 22), at(TODAY.plusDays(1), 4), 16, 3.7, 0, 11.0);
        var source = new ForecastSource(new ProviderId("open-meteo"), "Open-Meteo", "Weather data: Open-Meteo.com (CC BY 4.0)",
                at(TODAY, 7), Freshness.STALE, ProviderRole.FALLBACK, "Forecast.Solar", Optional.of(at(TODAY, 9)));
        return new Outlook.Planned(at(TODAY, 8), BRUSSELS, List.of(today, tomorrow, friday), new Percent(50), true,
                Optional.of(new GridAdvice(TODAY.plusDays(1), 22, 8, 11.0, Optional.of(night),
                        Optional.of(new Percent(67)))), List.of(), source, 0.45,
                new Percent(30), new Percent(80));
    }

    /** One of each kind: a whole day, part of a day, and a period over several days. */
    private static List<OneOffAwayPeriod> awayPeriods() {
        return List.of(OneOffAwayPeriod.allDay(TODAY.plusDays(1), BRUSSELS),
                OneOffAwayPeriod.on(TODAY.plusDays(2), LocalTime.of(10, 0), LocalTime.of(16, 30), BRUSSELS),
                new OneOffAwayPeriod(at(TODAY.plusDays(4), 18), at(TODAY.plusDays(6), 12)));
    }

    /** Every day, the readout and both entries open, a refused away period and the message without a config. */
    private List<String> problems(Language language, Optional<TextScale> scale) throws Exception {
        return Fx.call(() -> {
            var messages = new Messages(language);
            var mapper = new OutlookMapper(messages);
            var view = new DashboardView(() -> { }, () -> { }, Fx.REDUCED_MOTION, AppName.TITLE, messages);
            view.onBatteryEntered(_ -> { });
            view.onOneOffAwayChange(_ -> { }, _ -> { }, Models.CLOCK);
            view.lookup(".day-tabs").setStyle("-fx-open-tab-animation: none;"); // a growing tab is narrow on purpose
            stage = scale.map(larger -> Scenes.show(view, ColorScheme.LIGHT, larger))
                    .orElseGet(() -> Scenes.show(view, ColorScheme.LIGHT));
            var found = new ArrayList<String>();
            var plan = mapper.map(planned(), awayPeriods());
            for (int day = 0; day < 3; day++) {
                view.show(plan);
                ((TabPane) view.lookup(".day-tabs")).getSelectionModel().select(day);
                found.addAll(check(view));
            }
            // today's readout and battery entry open, with their longest texts
            ((TabPane) view.lookup(".day-tabs")).getSelectionModel().select(0);
            view.lookup(".solar-chart").fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.RIGHT, false, false, false, false));
            view.lookupAll(".button").stream().map(Button.class::cast)
                    .filter(button -> button.getText().equals(messages.text(TextKey.BATTERY_UPDATE))).findFirst().orElseThrow().fire();
            found.addAll(check(view));
            // the away entry open at part of the day, refusing an end before the start
            view.lookupAll(".button").stream().map(Button.class::cast)
                    .filter(button -> button.getText().equals(messages.text(TextKey.ONE_OFF_ADD))).findFirst().orElseThrow().fire();
            OneOffAwayEntry.enterTimes(view, "16:00", "10:00");
            OneOffAwayEntry.save(view);
            found.addAll(check(view));
            view.show(mapper.map(new Outlook.NotSetUp(Path.of("/home/owner/.config/car-charge-planner/engine.json"))));
            found.addAll(check(view));
            return found;
        });
    }

    private static List<String> check(DashboardView view) {
        view.applyCss();
        view.layout();
        var found = new ArrayList<>(Scenes.cutOff(view));
        found.addAll(Scenes.overlapping(view));
        return found;
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void noTextIsCutOffOrOverlapsOnAnyDayOrMessage(Language language) throws Exception {
        assertThat(problems(language, Optional.empty())).isEmpty();
    }

    static Stream<Arguments> languagesAndScales() {
        return Stream.of(Language.values()).flatMap(language -> Stream.of(TextScale.values())
                .map(scale -> Arguments.of(language, scale)));
    }

    /** As the owner's system makes the text larger, at 150 and 200 %: a day scrolls, nothing is cut off. */
    @ParameterizedTest
    @MethodSource("languagesAndScales")
    void withLargerTextNoTextIsCutOffOrOverlapsEither(Language language, TextScale scale) throws Exception {
        assertThat(problems(language, Optional.of(scale))).isEmpty();
    }
}
