package io.github.enadeag.carchargeplanner.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.engine.Outlook;
import io.github.enadeag.carchargeplanner.engine.config.Percent;
import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastSource;
import io.github.enadeag.carchargeplanner.engine.forecast.Freshness;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderRole;
import io.github.enadeag.carchargeplanner.engine.plan.DayPlan;
import io.github.enadeag.carchargeplanner.engine.plan.GridAdvice;
import io.github.enadeag.carchargeplanner.engine.plan.Window;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TrayStatusMapperTest {

    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    /** A Friday. */
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 3);
    private static final TrayStatusMapper MAPPER = new TrayStatusMapper();

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(BRUSSELS).toInstant();
    }

    private static Window window(LocalDate day, int startHour, int endHour) {
        return new Window(at(day, startHour, 15), at(day, endHour, 30), 10, 2.3, 7.3, 0);
    }

    private static DayPlan day(LocalDate date, Window... windows) {
        return new DayPlan(date, new Percent(50), List.of(windows), new Percent(74), new Percent(62), 12);
    }

    private static Outlook.Planned plannedAt(int hour, Optional<GridAdvice> advice, DayPlan... days) {
        var source = new ForecastSource(new ProviderId("open-meteo"), "Open-Meteo", "Weather data: Open-Meteo.com (CC BY 4.0)",
                at(TODAY, 7, 45), Freshness.FRESH, ProviderRole.PRIMARY, "", Optional.empty());
        return new Outlook.Planned(at(TODAY, hour, 0), BRUSSELS, List.of(days), new Percent(50), false, advice, source);
    }

    private static TrayStatus at(int hour, DayPlan... days) {
        return MAPPER.map(plannedAt(hour, Optional.empty(), days));
    }

    @Test
    void withoutASetUpTheOwnerMustActAndIsToldWhatToFix() {
        var status = MAPPER.map(new Outlook.NotSetUp("engine.json has no location."));

        assertThat(status.dot()).isEqualTo(StatusDot.ACTION);
        assertThat(status.text()).contains("engine.json has no location.");
    }

    @Test
    void withoutAForecastTheOwnerMustActAndIsToldWhy() {
        var status = MAPPER.map(new Outlook.NoForecast("Open-Meteo: no connection."));

        assertThat(status.dot()).isEqualTo(StatusDot.ACTION);
        assertThat(status.text()).contains("Open-Meteo: no connection.");
    }

    @Test
    void aForecastWithoutDaysAsksTheOwnerToActToo() {
        assertThat(at(8).dot()).isEqualTo(StatusDot.ACTION);
    }

    @Test
    void aWindowRunningNowIsChargingOnSunUntilItsEnd() {
        var status = at(12, day(TODAY, window(TODAY, 11, 14)));

        assertThat(status).isEqualTo(new TrayStatus(StatusDot.SUN, "Charge on sun now, until 14:30, at 10 A."));
    }

    @Test
    void aWindowLaterTodayIsChargingOnSunSoon() {
        var status = at(8, day(TODAY, window(TODAY, 11, 14)));

        assertThat(status).isEqualTo(new TrayStatus(StatusDot.SUN, "Charge on sun today 11:15 – 14:30, at 10 A."));
    }

    @Test
    void aWindowThatStartedYesterdayAndStillRunsIsChargingOnSunNow() {
        var overnight = new Window(at(TODAY.minusDays(1), 22, 0), at(TODAY, 2, 0), 10, 2.3, 7.3, 0);

        assertThat(at(1, day(TODAY, overnight)).dot()).isEqualTo(StatusDot.SUN);
    }

    @Test
    void aWindowOnlyOnALaterDayLeavesNothingToDoTodayButSaysWhen() {
        var tomorrow = TODAY.plusDays(1);
        var status = at(15, day(TODAY, window(TODAY, 11, 14)), day(tomorrow, window(tomorrow, 10, 13)));

        assertThat(status).isEqualTo(
                new TrayStatus(StatusDot.NONE, "Nothing to do today. Next charge on sun: tomorrow 10:15 – 13:30."));
    }

    @Test
    void noWindowAtAllLeavesNothingToDo() {
        var status = at(8, day(TODAY), day(TODAY.plusDays(1)));

        assertThat(status.dot()).isEqualTo(StatusDot.NONE);
        assertThat(status.text()).startsWith("Nothing to do");
    }

    @Test
    void gridAdviceComesBeforeAWindowOnSunBecauseTheCarWouldDropBelowItsMinimum() {
        var sunday = TODAY.plusDays(2);
        var advice = new GridAdvice(sunday, 17, 13, 8.4, Optional.of(window(sunday, 9, 15)));

        var status = MAPPER.map(plannedAt(8, Optional.of(advice), day(TODAY, window(TODAY, 11, 14))));

        assertThat(status).isEqualTo(
                new TrayStatus(StatusDot.GRID, "Charge 8.4 kWh from the grid: Sunday 09:15 – 15:30."));
    }

    @Test
    void beforeTheFirstPlanThereIsNoDot() {
        assertThat(MAPPER.loading().dot()).isEqualTo(StatusDot.NONE);
    }
}
