package io.github.enadeag.carchargeplanner.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.desktop.MainCardText.Source;
import io.github.enadeag.carchargeplanner.engine.Outlook;
import io.github.enadeag.carchargeplanner.engine.plan.DayPlan;
import io.github.enadeag.carchargeplanner.engine.plan.Window;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class MainCardTextTest {

    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 3);

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(BRUSSELS).toInstant();
    }

    private static Outlook.Planned planned(LocalDate windowDay, double importKwh) {
        var window = new Window(at(windowDay, 10, 0), at(windowDay, 14, 25), 14, 3.22, 14.2, importKwh);
        var today = new DayPlan(TODAY, 50, TODAY.equals(windowDay) ? List.of(window) : List.of(), 50, 32, 18);
        var tomorrow = new DayPlan(TODAY.plusDays(1), 32, TODAY.equals(windowDay) ? List.of() : List.of(window), 50, 32, 18);
        return new Outlook.Planned(at(TODAY, 8, 0), BRUSSELS, List.of(today, tomorrow), 50, true);
    }

    @Test
    void theHeadlineGivesTheWindowInTheHomesTimeZone() {
        var text = MainCardText.of(planned(TODAY, 0));
        assertThat(text.headline()).contains("10:00", "14:25");
        assertThat(text.detail()).contains("14 A", "3.2 kW");
    }

    @Test
    void aWindowTomorrowIsNotPresentedAsToday() {
        assertThat(MainCardText.of(planned(TODAY.plusDays(1), 0)).headline()).doesNotContain("Today");
        assertThat(MainCardText.of(planned(TODAY, 0)).headline()).doesNotContain("Tomorrow");
    }

    @Test
    void thePillsShowSunCarAndGridWithTheirFigures() {
        var pills = MainCardText.of(planned(TODAY, 0.3)).pills();
        assertThat(pills).extracting(MainCardText.Pill::source).containsExactly(Source.SUN, Source.CAR, Source.GRID);
        assertThat(pills.get(0).text()).contains("14.2 kWh");
        assertThat(pills.get(1).text()).contains("50", "50");
        assertThat(pills.get(2).text()).contains("0.3 kWh");
    }

    @Test
    void numbersUseADecimalPointWhateverTheSystemLanguage() {
        var before = java.util.Locale.getDefault();
        java.util.Locale.setDefault(java.util.Locale.GERMANY);
        try {
            assertThat(MainCardText.of(planned(TODAY, 0)).pills().getFirst().text()).contains("14.2");
        } finally {
            java.util.Locale.setDefault(before);
        }
    }

    @Test
    void withoutAPlanTheReasonIsShown() {
        var text = MainCardText.of(new Outlook.NoForecast("Could not reach Open-Meteo (ConnectException)"));
        assertThat(text.detail()).isEqualTo("Could not reach Open-Meteo (ConnectException)");
        assertThat(text.pills()).isEmpty();
    }
}
