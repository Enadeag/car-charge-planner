package io.github.enadeag.carchargeplanner.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.engine.Outlook;
import io.github.enadeag.carchargeplanner.engine.config.ConfigProblem;
import io.github.enadeag.carchargeplanner.engine.config.Percent;
import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastSource;
import io.github.enadeag.carchargeplanner.engine.forecast.Freshness;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderRole;
import io.github.enadeag.carchargeplanner.engine.plan.DayPlan;
import io.github.enadeag.carchargeplanner.engine.plan.GridAdvice;
import io.github.enadeag.carchargeplanner.engine.plan.Window;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javafx.application.ColorScheme;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** In every language the dashboard shows each text whole: a longer word widens or wraps its place, never cuts it off. */
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

    /** Every kind of day: one with two windows and grid energy, one that needs the grid charge, one without a window. */
    private static Outlook.Planned planned() {
        var today = new DayPlan(TODAY, new Percent(50), List.of(new Window(at(TODAY, 10), at(TODAY, 13), 10, 2.3, 7.3, 0.6),
                new Window(at(TODAY, 14), at(TODAY, 15), 8, 1.8, 1.8, 0)), new Percent(74), new Percent(62), 12);
        var tomorrow = new DayPlan(TODAY.plusDays(1), new Percent(62), List.of(), new Percent(62), new Percent(22), 12);
        var friday = new DayPlan(TODAY.plusDays(2), new Percent(22), List.of(), new Percent(22), new Percent(10), 12);
        var night = new Window(at(TODAY, 22), at(TODAY.plusDays(1), 4), 16, 3.7, 0, 11.0);
        var source = new ForecastSource(new ProviderId("open-meteo"), "Open-Meteo", "Weather data: Open-Meteo.com (CC BY 4.0)",
                at(TODAY, 7), Freshness.STALE, ProviderRole.FALLBACK, "Forecast.Solar", Optional.of(at(TODAY, 9)));
        return new Outlook.Planned(at(TODAY, 8), BRUSSELS, List.of(today, tomorrow, friday), new Percent(50), true,
                Optional.of(new GridAdvice(TODAY.plusDays(1), 22, 8, 11.0, Optional.of(night))), source);
    }

    /** Texts shortened with an ellipsis, or wider than a clip above them, as a tab clips its label. */
    private static List<String> cutOff(Node root) {
        return Scenes.all(root).stream()
                .filter(Scenes::reallyVisible)
                .flatMap(node -> node instanceof Labeled labeled && (labeled.isTextTruncated() || clipped(labeled))
                        ? Stream.of(labeled.getText()) : Stream.empty())
                .toList();
    }

    /** Only sideways: a day taller than the screen scrolls, which clips it from below on purpose. */
    private static boolean clipped(Labeled labeled) {
        var bounds = labeled.localToScene(labeled.getLayoutBounds());
        for (var at = labeled.getParent(); at != null; at = at.getParent()) {
            if (at.getClip() != null) {
                var clip = at.localToScene(at.getClip().getBoundsInParent());
                if (bounds.getMinX() < clip.getMinX() - 0.5 || bounds.getMaxX() > clip.getMaxX() + 0.5) {
                    return true;
                }
            }
        }
        return false;
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void noTextIsCutOffOnAnyDayOrMessage(Language language) throws Exception {
        var problems = Fx.call(() -> {
            var messages = new Messages(language);
            var mapper = new OutlookMapper(messages);
            var view = new DashboardView(() -> { }, () -> { }, messages);
            view.lookup(".day-tabs").setStyle("-fx-open-tab-animation: none;"); // a growing tab is narrow on purpose
            stage = Scenes.show(view, ColorScheme.LIGHT);
            var found = new ArrayList<String>();
            var plan = mapper.map(planned());
            for (int day = 0; day < 3; day++) {
                view.show(plan);
                ((TabPane) view.lookup(".day-tabs")).getSelectionModel().select(day);
                view.applyCss();
                view.layout();
                found.addAll(cutOff(view));
            }
            view.show(mapper.map(new Outlook.NotSetUp(ConfigProblem.Kind.MISSING,
                    Path.of("/home/owner/.config/car-charge-planner/engine.json"), "")));
            view.applyCss();
            view.layout();
            found.addAll(cutOff(view));
            return found;
        });

        assertThat(problems).isEmpty();
    }
}
