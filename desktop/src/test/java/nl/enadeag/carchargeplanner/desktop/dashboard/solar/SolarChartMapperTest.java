package nl.enadeag.carchargeplanner.desktop.dashboard.solar;

import static nl.enadeag.carchargeplanner.desktop.dashboard.PlannedDays.FALL_BACK;
import static nl.enadeag.carchargeplanner.desktop.dashboard.PlannedDays.TODAY;
import static nl.enadeag.carchargeplanner.desktop.dashboard.PlannedDays.TWO_DAYS;
import static nl.enadeag.carchargeplanner.desktop.dashboard.PlannedDays.at;
import static nl.enadeag.carchargeplanner.desktop.dashboard.PlannedDays.day;
import static nl.enadeag.carchargeplanner.desktop.dashboard.PlannedDays.hourly;
import static nl.enadeag.carchargeplanner.desktop.dashboard.PlannedDays.planned;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import nl.enadeag.carchargeplanner.desktop.system.ClockFormat;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.engine.dashboard.DayChart;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import nl.enadeag.carchargeplanner.engine.Outlook;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

/** The engine's chart in the desktop's words and marks; the chart's rules are the engine's own tests. */
class SolarChartMapperTest {

    private static final Messages ENGLISH = new Messages(Language.ENGLISH);

    private static SolarChartModel curve(Outlook.Planned planned, int index, Messages messages) {
        return new SolarChartMapper(planned.zone(), messages)
                .map(planned.dashboard(List.of(), List.of()).days().get(index));
    }

    private static SolarChartModel curve(Outlook.Planned planned, int index) {
        return curve(planned, index, ENGLISH);
    }

    /** The readout of the step that starts at an hour from the start of the day. */
    private static String readoutFrom(SolarChartModel curve, double hour) {
        return curve.readout(curve.chart().steps().stream().filter(step -> step.startHour() == hour).findFirst()
                .orElseThrow());
    }

    /** The marks on the time axis, as (hour, label). */
    private static List<Tuple> ticks(SolarChartModel curve) {
        return curve.ticks().stream().map(tick -> tuple(tick.tick().hour(), tick.label())).toList();
    }

    private static Tuple tick(double hour, String label) {
        return tuple(hour, label);
    }

    @Test
    void theChartHasTheEnginesHoursAndStepsAndMarksEveryThirdHourOfTheClock() {
        var today = curve(TWO_DAYS, 0);

        assertThat(today.chart().fromHour()).isEqualTo(6);
        assertThat(today.chart().toHour()).isEqualTo(20);
        assertThat(today.chart().steps()).extracting(DayChart.Step::startHour)
                .containsExactly(6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 17.0, 18.0, 19.0);
        assertThat(today.chart().steps().get(7).kw()).isEqualTo(4.2);
        assertThat(ticks(today)).containsExactly(tick(6, "06"), tick(9, "09"), tick(12, "12"),
                tick(15, "15"), tick(18, "18"));
    }

    /**
     * The marks stay the hours of a 24-hour clock: with a marker, "12 a.m." at midnight runs into "3 a.m." on a chart
     * from midnight to midnight, at every text size. The readout's time follows the system's clock.
     */
    @Test
    void onA12HourClockTheMarksStayTwoDigitHoursAndTheReadoutHasItsMarker() {
        var english = curve(TWO_DAYS, 0, new Messages(Language.ENGLISH, ClockFormat.TWELVE_HOUR));

        assertThat(ticks(english)).containsExactly(tick(6, "06"), tick(9, "09"), tick(12, "12"),
                tick(15, "15"), tick(18, "18"));
        assertThat(readoutFrom(english, 13)).startsWith("1:00\u202fpm");
    }

    @Test
    void aTickOnTheFirstOrTheLastHourShownIsKept() {
        var day = planned(at(TODAY, 8, 0), false, day(TODAY, 50, 50,
                hourly(TODAY, hour -> hour >= 10 && hour < 17 ? 2 : 0)));

        var curve = curve(day, 0);

        assertThat(ticks(curve)).containsExactly(tick(9, "09"), tick(12, "12"), tick(15, "15"),
                tick(18, "18"));
    }

    @Test
    void theScaleSaysItsWholeKilowatts() {
        assertThat(curve(TWO_DAYS, 0).chart().scaleKw()).isEqualTo(6);
        assertThat(curve(TWO_DAYS, 0).scaleText()).isEqualTo("6 kW");
    }

    @Test
    void eachStepReadsOutItsTimeTheSunAndTheBaseload() {
        assertThat(readoutFrom(curve(TWO_DAYS, 0), 13)).isEqualTo("13:00: 4.2 kW solar · 0.4 kW house");
    }

    @Test
    void theWindowsAndNowAreInHoursOfTheDay() {
        var today = curve(TWO_DAYS, 0);

        assertThat(today.chart().windows()).extracting(DayChart.WindowBand::startHour, DayChart.WindowBand::endHour)
                .containsExactly(tuple(11.25, 14.5));
        assertThat(today.chart().now().orElseThrow().hour()).isCloseTo(8.2, within(1e-9));
        assertThat(today.chart().baseloadKw()).isEqualTo(0.4);
        assertThat(curve(TWO_DAYS, 1).chart().now()).isEmpty();
    }

    @Test
    void onAClockChangeDayTheTicksStayOnTheClockHours() {
        var day = planned(at(FALL_BACK, 8, 0), false, day(FALL_BACK, 50, 50,
                hourly(FALL_BACK, hour -> hour >= 9 && hour < 16 ? 2 : 0)));

        var curve = curve(day, 0);

        // the hour from 02:00 to 03:00 comes twice, so 09:00 is 10 hours after midnight
        assertThat(ticks(curve)).contains(tick(10, "09"), tick(13, "12"));
        assertThat(readoutFrom(curve, 10)).startsWith("09:00: 2.0 kW solar");
    }

    @Test
    void anHourTheClocksSkipHasNoTick() {
        var kyiv = ZoneId.of("Europe/Kyiv");
        var date = LocalDate.of(2026, 3, 29);
        var midnight = date.atStartOfDay(kyiv).toInstant();
        // 23 hours: 03:00 becomes 04:00
        var steps = IntStream.range(0, 23).mapToObj(hour -> new ForecastStep(midnight.plus(Duration.ofHours(hour)),
                midnight.plus(Duration.ofHours(hour + 1)), 2)).toList();
        var base = planned(midnight, false, day(date, 50, 50, steps));
        var kyivPlan = new Outlook.Planned(base.madeAt(), kyiv, base.days(), base.batteryLevelPct(), false,
                base.gridAdvice(), base.tripAdvice(), base.forecastSource(), base.baseloadKw(), base.minimumPct(),
                base.targetPct());

        var curve = curve(kyivPlan, 0);

        assertThat(ticks(curve)).containsExactly(tick(0, "00"), tick(5, "06"), tick(8, "09"),
                tick(11, "12"), tick(14, "15"), tick(17, "18"), tick(20, "21"), tick(23, "24"));
    }

    @Test
    void inDutchTheNumbersHaveADecimalCommaAndTheWordsAreDutch() {
        var curve = curve(TWO_DAYS, 0, new Messages(Language.DUTCH));

        assertThat(readoutFrom(curve, 13)).isEqualTo("13:00: 4,2 kW zonnestroom · 0,4 kW huis");
    }
}
