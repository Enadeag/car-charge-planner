package io.github.enadeag.carchargeplanner.desktop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.enadeag.carchargeplanner.desktop.SolarCurveModel.Span;
import io.github.enadeag.carchargeplanner.desktop.SolarCurveModel.Tick;
import io.github.enadeag.carchargeplanner.engine.Outlook;
import io.github.enadeag.carchargeplanner.engine.config.Percent;
import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastSource;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import io.github.enadeag.carchargeplanner.engine.forecast.Freshness;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderRole;
import io.github.enadeag.carchargeplanner.engine.plan.DayPlan;
import io.github.enadeag.carchargeplanner.engine.plan.Window;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.IntToDoubleFunction;
import org.junit.jupiter.api.Test;

class ChartMapperTest {

    private static final Messages ENGLISH = new Messages(Language.ENGLISH);
    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    /** A Friday. */
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 3);
    /** 25 hours: 03:00 summer time becomes 02:00 winter time. */
    private static final LocalDate FALL_BACK = LocalDate.of(2026, 10, 25);

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(BRUSSELS).toInstant();
    }

    /** Hourly steps over the whole day, with the power the function gives for each hour of the clock. */
    private static List<ForecastStep> hourly(LocalDate day, IntToDoubleFunction kwAtHour) {
        var steps = new ArrayList<ForecastStep>();
        for (var start = day.atStartOfDay(BRUSSELS).toInstant(); start.isBefore(day.plusDays(1).atStartOfDay(BRUSSELS).toInstant());
                start = start.plus(Duration.ofHours(1))) {
            steps.add(new ForecastStep(start, start.plus(Duration.ofHours(1)), kwAtHour.applyAsDouble(start.atZone(BRUSSELS).getHour())));
        }
        return steps;
    }

    /** Sun from 07:00 to 19:00, peaking at 13:00 at the given power. */
    private static List<ForecastStep> sunny(LocalDate day, double peakKw) {
        return hourly(day, hour -> hour >= 7 && hour < 19 ? peakKw - Math.abs(hour - 13) * peakKw / 7 : 0);
    }

    private static DayPlan day(LocalDate date, double start, double after, List<ForecastStep> forecast, Window... windows) {
        return new DayPlan(date, new Percent(start), List.of(windows), new Percent(after), new Percent(start), 12, forecast);
    }

    private static Outlook.Planned planned(Instant madeAt, boolean assumed, DayPlan... days) {
        var source = new ForecastSource(new ProviderId("open-meteo"), "Open-Meteo", "Weather data", madeAt, Freshness.FRESH,
                ProviderRole.PRIMARY, "", Optional.empty());
        return new Outlook.Planned(madeAt, BRUSSELS, List.of(days), new Percent(50), assumed, Optional.empty(), source, 0.4,
                new Percent(30), new Percent(80));
    }

    private static final Window WINDOW = new Window(at(TODAY, 11, 15), at(TODAY, 14, 30), 10, 2.3, 7.3, 0.2);

    private static final Outlook.Planned TWO_DAYS = planned(at(TODAY, 8, 12), true,
            day(TODAY, 50, 74, sunny(TODAY, 4.2), WINDOW),
            day(TODAY.plusDays(1), 62, 62, sunny(TODAY.plusDays(1), 5.3)));

    private static SolarCurveModel curve(Outlook.Planned planned, int index) {
        return new ChartMapper(planned, ENGLISH).curve(planned.days().get(index));
    }

    @Test
    void theChartShowsTheHoursWithSunAndAnHourOnEitherSide() {
        var today = curve(TWO_DAYS, 0);

        assertThat(today.fromHour()).isEqualTo(6);
        assertThat(today.toHour()).isEqualTo(20);
        assertThat(today.steps()).extracting(SolarCurveModel.Step::startHour)
                .containsExactly(6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 17.0, 18.0, 19.0);
        assertThat(today.steps().get(7).kw()).isEqualTo(4.2);
        assertThat(today.ticks()).containsExactly(new Tick(6, "06"), new Tick(9, "09"), new Tick(12, "12"),
                new Tick(15, "15"), new Tick(18, "18"));
    }

    @Test
    void allDaysShareOnePowerScaleRoundedUpToAWholeKilowatt() {
        assertThat(curve(TWO_DAYS, 0).scaleKw()).isEqualTo(6);
        assertThat(curve(TWO_DAYS, 1).scaleKw()).isEqualTo(6);
        assertThat(curve(TWO_DAYS, 0).scaleText()).isEqualTo("6 kW");
    }

    @Test
    void eachStepReadsOutItsTimeTheSunAndTheHouse() {
        assertThat(curve(TWO_DAYS, 0).stepAt(13.4).orElseThrow().readout()).isEqualTo("13:00 – 4.2 kW sun · 0.4 kW house");
    }

    @Test
    void theWindowsAndNowAreInHoursOfTheDay() {
        var today = curve(TWO_DAYS, 0);

        assertThat(today.windows()).containsExactly(new Span(11.25, 14.5));
        assertThat(today.nowHour().orElseThrow()).isCloseTo(8.2, within(1e-9));
        assertThat(today.houseKw()).isEqualTo(0.4);
        assertThat(curve(TWO_DAYS, 1).nowHour()).isEmpty();
    }

    @Test
    void nowIsLeftOutWhileItIsOutsideTheHoursShown() {
        var early = planned(at(TODAY, 5, 0), false, day(TODAY, 50, 74, sunny(TODAY, 4.2)));

        assertThat(curve(early, 0).nowHour()).isEmpty();
    }

    @Test
    void theChartReadsAsSentencesForAScreenReader() {
        assertThat(curve(TWO_DAYS, 0).spoken()).isEqualTo("Today, expected sun: peak 4.2 kW around 13:00; the house uses "
                + "about 0.4 kW. Charge window 11:15 to 14:30. Now 08:12.");
        assertThat(curve(TWO_DAYS, 1).spoken())
                .isEqualTo("Tomorrow, expected sun: peak 5.3 kW around 13:00; the house uses about 0.4 kW.");
    }

    @Test
    void aDayWithoutSunShowsTheDaytimeAndSaysSo() {
        var dark = planned(at(TODAY, 8, 0), false, day(TODAY, 50, 50, hourly(TODAY, hour -> 0)));

        var curve = curve(dark, 0);

        assertThat(curve.fromHour()).isEqualTo(6);
        assertThat(curve.toHour()).isEqualTo(20);
        assertThat(curve.scaleKw()).isEqualTo(1);
        assertThat(curve.spoken()).isEqualTo("Today, expected sun: none.");
    }

    @Test
    void onAClockChangeDayTheTicksStayOnTheClockHours() {
        var day = planned(at(FALL_BACK, 8, 0), false, day(FALL_BACK, 50, 50,
                hourly(FALL_BACK, hour -> hour >= 9 && hour < 16 ? 2 : 0)));

        var curve = curve(day, 0);

        // the hour from 02:00 to 03:00 comes twice, so 09:00 is 10 hours after midnight
        assertThat(curve.ticks()).contains(new Tick(10, "09"), new Tick(13, "12"));
        assertThat(curve.fromHour()).isEqualTo(9);
        assertThat(curve.stepAt(10.5).orElseThrow().readout()).startsWith("09:00 – 2.0 kW sun");
    }

    @Test
    void todaysBatteryIsNowAndCanBeEntered() {
        var battery = new ChartMapper(TWO_DAYS, ENGLISH).battery(TWO_DAYS.days().getFirst());

        assertThat(battery).isEqualTo(new BatteryModel(50, 74, 30, 80, "50 → 74 %", "min 30", "target 80", true,
                "Car battery 50 % now, 74 % after charging; minimum 30 %, target 80 %. "
                        + "The level is assumed: enter the level the car shows."));
    }

    @Test
    void anotherDaysBatteryIsAtItsStartAndCannotBeEntered() {
        var battery = new ChartMapper(TWO_DAYS, ENGLISH).battery(TWO_DAYS.days().get(1));

        assertThat(battery.editable()).isFalse();
        assertThat(battery.levelPct()).isEqualTo(62);
        assertThat(battery.spoken()).isEqualTo(
                "Car battery 62 % at the start of tomorrow, 62 % after charging; minimum 30 %, target 80 %.");
    }

    @Test
    void anEnteredLevelIsNotCalledAssumed() {
        var entered = planned(at(TODAY, 8, 12), false, day(TODAY, 50, 74, sunny(TODAY, 4.2)));

        assertThat(new ChartMapper(entered, ENGLISH).battery(entered.days().getFirst()).spoken()).doesNotContain("assumed");
    }

    @Test
    void theDayModelsCarryTheirCharts() {
        var plan = (DashboardModel.Plan) new OutlookMapper(ENGLISH).map(TWO_DAYS);

        assertThat(plan.days()).extracting(day -> day.curve().nowHour().isPresent()).containsExactly(true, false);
        assertThat(plan.days()).extracting(day -> day.battery().editable()).containsExactly(true, false);
    }

    @Test
    void aStepOutsideTheStepsIsTheNearestOne() {
        var today = curve(TWO_DAYS, 0);

        assertThat(today.stepAt(2).orElseThrow()).isEqualTo(today.steps().getFirst());
        assertThat(today.stepAt(23).orElseThrow()).isEqualTo(today.steps().getLast());
        assertThat(new SolarCurveModel(6, 20, 1, "1 kW", List.of(), 0, List.of(), OptionalDouble.empty(), List.of(), "")
                .stepAt(10)).isEmpty();
    }

    @Test
    void inDutchTheNumbersHaveADecimalCommaAndTheWordsAreDutch() {
        var dutch = new Messages(Language.DUTCH);
        var mapper = new ChartMapper(TWO_DAYS, dutch);

        var curve = mapper.curve(TWO_DAYS.days().getFirst());
        var battery = mapper.battery(TWO_DAYS.days().get(1));

        assertThat(curve.stepAt(13.4).orElseThrow().readout()).isEqualTo("13:00 – 4,2 kW zon · 0,4 kW huis");
        assertThat(curve.spoken()).isEqualTo("Vandaag, verwachte zon: piek 4,2 kW rond 13:00; het huis gebruikt "
                + "ongeveer 0,4 kW. Laadvenster 11:15 tot 14:30. Nu 08:12.");
        assertThat(battery.minimumText()).isEqualTo("min. 30");
        assertThat(battery.spoken()).isEqualTo(
                "Accu van de auto 62% aan het begin van morgen, na het laden 62%; minimum 30%, doel 80%.");
    }
}
