package io.github.enadeag.carchargeplanner.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.desktop.Chip.Source;
import io.github.enadeag.carchargeplanner.engine.Outlook;
import io.github.enadeag.carchargeplanner.engine.plan.DayPlan;
import io.github.enadeag.carchargeplanner.engine.plan.Window;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OutlookMapperTest {

    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    /** A Friday. */
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 3);
    private static final OutlookMapper MAPPER = new OutlookMapper("Open-Meteo");

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(BRUSSELS).toInstant();
    }

    private static Window window(LocalDate day, int startHour, int endHour, double importKwh) {
        return new Window(at(day, startHour, 15), at(day, endHour, 30), 10, 2.3, 7.3, importKwh);
    }

    private static DayPlan day(LocalDate date, Window... windows) {
        return new DayPlan(date, 50, List.of(windows), 74, 62, 12);
    }

    private static Outlook.Planned planned(boolean batteryAssumed, DayPlan... days) {
        return new Outlook.Planned(at(TODAY, 8, 12), BRUSSELS, List.of(days), 50, batteryAssumed, Optional.empty());
    }

    private static DashboardModel.Plan plan(DayPlan... days) {
        return (DashboardModel.Plan) MAPPER.map(planned(false, days));
    }

    @Test
    void theMainCardReadsAsOneSentence() {
        var today = plan(day(TODAY, window(TODAY, 11, 14, 0.6))).days().getFirst();

        assertThat(today.spoken()).isEqualTo("Today: charge window 11:15 to 14:30, 10 A, 92 % sun.");
    }

    @Test
    void aDayWithAWindowShowsItInTheHomesTimeZoneWithWhatToSet() {
        var today = plan(day(TODAY, window(TODAY, 11, 14, 0))).days().getFirst();

        assertThat(today.kind()).isEqualTo(DayKind.SUN);
        assertThat(today.hero()).isEqualTo("11:15 – 14:30");
        assertThat(today.facts()).containsExactly("Set 10 A (2.3 kW)", "about 7.3 kWh");
    }

    @Test
    void theTabsStartWithTodayAndNameTheOtherDays() {
        var days = plan(day(TODAY), day(TODAY.plusDays(1)), day(TODAY.plusDays(2))).days();

        assertThat(days).extracting(DayModel::tab).containsExactly("Today", "Sat", "Sun");
        assertThat(days).extracting(DayModel::title).containsExactly("Today", "Tomorrow", "Sunday");
    }

    @Test
    void aDayWithoutAWindowSaysSoAndShowsNoTimes() {
        var tomorrow = plan(day(TODAY), day(TODAY.plusDays(1))).days().get(1);

        assertThat(tomorrow.kind()).isEqualTo(DayKind.NONE);
        assertThat(tomorrow.hero()).isEmpty();
        assertThat(tomorrow.spoken()).startsWith("Tomorrow: no charge window.");
    }

    @Test
    void theChipsShowSunShareAndBatteryAndGridOnlyWhenTheWindowTakesFromIt() {
        var withoutGrid = plan(day(TODAY, window(TODAY, 11, 14, 0.04))).days().getFirst().chips();
        var withGrid = plan(day(TODAY, window(TODAY, 11, 14, 0.6))).days().getFirst().chips();

        assertThat(withoutGrid).extracting(Chip::source).containsExactly(Source.SUN, Source.CAR);
        assertThat(withGrid).containsExactly(
                new Chip(Source.SUN, "92 % sun"), new Chip(Source.CAR, "Battery 50 → 74 %"),
                new Chip(Source.GRID, "0.6 kWh from the grid"));
    }

    @Test
    void laterWindowsOfTheSameDayAreNamedInTheNote() {
        var today = plan(day(TODAY, window(TODAY, 9, 10, 0), window(TODAY, 13, 15, 0))).days().getFirst();

        assertThat(today.hero()).isEqualTo("09:15 – 10:30");
        assertThat(today.note()).isEqualTo("Also 13:15 – 15:30 at 10 A.");
    }

    @Test
    void anAssumedBatteryLevelIsMentionedOnTodayOnly() {
        var days = ((DashboardModel.Plan) MAPPER.map(planned(true, day(TODAY), day(TODAY.plusDays(1))))).days();

        assertThat(days.get(0).note()).contains("assumes the battery is at 50 %");
        assertThat(days.get(1).note()).doesNotContain("assumes");
    }

    @Test
    void theFooterNamesTheForecastAndWhenThePlanWasMade() {
        assertThat(plan(day(TODAY)).footer()).isEqualTo("Forecast: Open-Meteo · 08:12");
    }

    @Test
    void numbersUseADecimalPointWhateverTheSystemLanguage() {
        var before = Locale.getDefault();
        Locale.setDefault(Locale.GERMANY);
        try {
            assertThat(plan(day(TODAY, window(TODAY, 11, 14, 0))).days().getFirst().facts())
                    .contains("Set 10 A (2.3 kW)");
        } finally {
            Locale.setDefault(before);
        }
    }

    @Test
    void withoutAPlanTheReasonIsShown() {
        var message = (DashboardModel.Message) MAPPER.map(new Outlook.NoForecast("Could not reach Open-Meteo"));

        assertThat(message.headline()).isEqualTo("No forecast");
        assertThat(message.detail()).isEqualTo("Could not reach Open-Meteo");
        assertThat(message.footer()).isEqualTo("Forecast: Open-Meteo");
    }

    @Test
    void aPlanWithoutDaysIsShownAsNoForecast() {
        assertThat(MAPPER.map(planned(false))).isInstanceOf(DashboardModel.Message.class);
    }
}
