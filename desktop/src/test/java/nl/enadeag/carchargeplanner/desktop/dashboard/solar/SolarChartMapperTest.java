package nl.enadeag.carchargeplanner.desktop.dashboard.solar;

import static nl.enadeag.carchargeplanner.desktop.dashboard.PlannedDays.FALL_BACK;
import static nl.enadeag.carchargeplanner.desktop.dashboard.PlannedDays.TODAY;
import static nl.enadeag.carchargeplanner.desktop.dashboard.PlannedDays.TWO_DAYS;
import static nl.enadeag.carchargeplanner.desktop.dashboard.PlannedDays.at;
import static nl.enadeag.carchargeplanner.desktop.dashboard.PlannedDays.day;
import static nl.enadeag.carchargeplanner.desktop.dashboard.PlannedDays.hourly;
import static nl.enadeag.carchargeplanner.desktop.dashboard.PlannedDays.planned;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import nl.enadeag.carchargeplanner.desktop.dashboard.solar.SolarChartModel.Step;
import nl.enadeag.carchargeplanner.desktop.dashboard.solar.SolarChartModel.Tick;
import nl.enadeag.carchargeplanner.desktop.dashboard.solar.SolarChartModel.WindowSpan;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import nl.enadeag.carchargeplanner.engine.Outlook;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;
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

    /** The step that starts at an hour from the start of the day. */
    private static Step stepFrom(SolarChartModel curve, double hour) {
        return curve.steps().stream().filter(step -> step.startHour() == hour).findFirst().orElseThrow();
    }

    @Test
    void theChartHasTheEnginesHoursAndStepsAndMarksEveryThirdHourOfTheClock() {
        var today = curve(TWO_DAYS, 0);

        assertThat(today.fromHour()).isEqualTo(6);
        assertThat(today.toHour()).isEqualTo(20);
        assertThat(today.steps()).extracting(SolarChartModel.Step::startHour)
                .containsExactly(6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 17.0, 18.0, 19.0);
        assertThat(today.steps().get(7).kw()).isEqualTo(4.2);
        assertThat(today.ticks()).containsExactly(new Tick(6, "06"), new Tick(9, "09"), new Tick(12, "12"),
                new Tick(15, "15"), new Tick(18, "18"));
    }

    @Test
    void aTickOnTheFirstOrTheLastHourShownIsKept() {
        var day = planned(at(TODAY, 8, 0), false, day(TODAY, 50, 50,
                hourly(TODAY, hour -> hour >= 10 && hour < 17 ? 2 : 0)));

        var curve = curve(day, 0);

        assertThat(curve.ticks()).containsExactly(new Tick(9, "09"), new Tick(12, "12"), new Tick(15, "15"),
                new Tick(18, "18"));
    }

    @Test
    void theScaleSaysItsWholeKilowatts() {
        assertThat(curve(TWO_DAYS, 0).scaleKw()).isEqualTo(6);
        assertThat(curve(TWO_DAYS, 0).scaleText()).isEqualTo("6 kW");
    }

    @Test
    void eachStepReadsOutItsTimeTheSunAndTheHouse() {
        assertThat(stepFrom(curve(TWO_DAYS, 0), 13).readout()).isEqualTo("13:00: 4.2 kW solar · 0.4 kW house");
    }

    @Test
    void theWindowsAndNowAreInHoursOfTheDay() {
        var today = curve(TWO_DAYS, 0);

        assertThat(today.windows()).containsExactly(new WindowSpan(11.25, 14.5));
        assertThat(today.madeAtHour().orElseThrow()).isCloseTo(8.2, within(1e-9));
        assertThat(today.houseKw()).isEqualTo(0.4);
        assertThat(curve(TWO_DAYS, 1).madeAtHour()).isEmpty();
    }

    @Test
    void theChartReadsAsSentencesForAScreenReader() {
        assertThat(curve(TWO_DAYS, 0).spoken()).isEqualTo("Today, expected solar power: peak 4.2 kW around 13:00; the "
                + "house uses about 0.4 kW. Charging: 11:15 to 14:30. Now 08:12.");
        assertThat(curve(TWO_DAYS, 1).spoken())
                .isEqualTo("Tomorrow, expected solar power: peak 5.3 kW around 13:00; the house uses about 0.4 kW.");
    }

    @Test
    void aDayWithoutSunSaysSo() {
        var dark = planned(at(TODAY, 8, 0), false, day(TODAY, 50, 50, hourly(TODAY, _ -> 0)));

        assertThat(curve(dark, 0).spoken()).isEqualTo("Today, expected solar power: none.");
    }

    @Test
    void onAClockChangeDayTheTicksStayOnTheClockHours() {
        var day = planned(at(FALL_BACK, 8, 0), false, day(FALL_BACK, 50, 50,
                hourly(FALL_BACK, hour -> hour >= 9 && hour < 16 ? 2 : 0)));

        var curve = curve(day, 0);

        // the hour from 02:00 to 03:00 comes twice, so 09:00 is 10 hours after midnight
        assertThat(curve.ticks()).contains(new Tick(10, "09"), new Tick(13, "12"));
        assertThat(stepFrom(curve, 10).readout()).startsWith("09:00: 2.0 kW solar");
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

        assertThat(curve.ticks()).containsExactly(new Tick(0, "00"), new Tick(5, "06"), new Tick(8, "09"),
                new Tick(11, "12"), new Tick(14, "15"), new Tick(17, "18"), new Tick(20, "21"), new Tick(23, "24"));
    }

    @Test
    void inDutchTheNumbersHaveADecimalCommaAndTheWordsAreDutch() {
        var curve = curve(TWO_DAYS, 0, new Messages(Language.DUTCH));

        assertThat(stepFrom(curve, 13).readout()).isEqualTo("13:00: 4,2 kW zonnestroom · 0,4 kW huis");
        assertThat(curve.spoken()).isEqualTo("Vandaag, verwachte zonnestroom: piek 4,2 kW rond 13:00; het huis "
                + "gebruikt ongeveer 0,4 kW. Laden: 11:15 tot 14:30. Nu 08:12.");
    }
}
