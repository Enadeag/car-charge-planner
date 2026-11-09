package nl.enadeag.carchargeplanner.engine.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import nl.enadeag.carchargeplanner.engine.plan.DayPlan;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DayChartTest {

    private static final ZoneId AMSTERDAM = ZoneId.of("Europe/Amsterdam");
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);
    private static final double HOUSE_KW = 0.3;

    /** Sun from 07:00 to 17:00, at most 3.2 kW at 12:00; 0.005 kW at 06:00 is no sun. */
    private static final double[] SUNNY = {0, 0, 0, 0, 0, 0, 0.005, 0.4, 1.0, 1.8, 2.5, 3.0, 3.2, 3.0, 2.4, 1.6, 0.8,
        0.2, 0, 0, 0, 0, 0, 0};

    private static Instant at(LocalDate day, int hour) {
        return day.atTime(hour, 0).atZone(AMSTERDAM).toInstant();
    }

    /** Hourly steps from the day's midnight, one per power given; the hours after the last have none. */
    private static List<ForecastStep> forecast(LocalDate day, double... kw) {
        var midnight = day.atStartOfDay(AMSTERDAM);
        return IntStream.range(0, kw.length).mapToObj(hour -> new ForecastStep(
                midnight.plusHours(hour).toInstant(), midnight.plusHours(hour + 1L).toInstant(), kw[hour])).toList();
    }

    private static Window window(LocalDate day, int from, int until) {
        return new Window(at(day, from), at(day, until), 6, 1.38, 1.38 * (until - from), 0);
    }

    private static DayPlan day(LocalDate date, List<Window> windows, List<ForecastStep> forecast) {
        return new DayPlan(date, new Percent(40), windows, new Percent(50), new Percent(50), 0, forecast);
    }

    /** Days from today on, each with the power of {@code kw} for its date. */
    private static List<DashboardDay> days(Instant madeAt, int count, double houseKw,
            Function<LocalDate, double[]> kw) {
        var plans = new ArrayList<DayPlan>();
        for (int i = 0; i < count; i++) {
            var date = TODAY.plusDays(i);
            plans.add(day(date, List.of(), forecast(date, kw.apply(date))));
        }
        return DashboardView.of(madeAt, AMSTERDAM, plans, houseKw).days();
    }

    private static DayChart today(double... kw) {
        return days(at(TODAY, 9), 1, HOUSE_KW, ignored -> kw).get(0).chart();
    }

    private static DayChart only(Instant madeAt, DayPlan day) {
        return DashboardView.of(madeAt, AMSTERDAM, List.of(day), HOUSE_KW).days().get(0).chart();
    }

    private static double[] hours(double kw, int... sunnyHours) {
        var kws = new double[24];
        Arrays.stream(sunnyHours).forEach(hour -> kws[hour] = kw);
        return kws;
    }

    @Test
    void theChartShowsFromTheHourBeforeTheFirstSunToTheHourAfterTheLastWithTheStepsInThoseHours() {
        var chart = today(SUNNY);

        // the first sun is at 07:00, the last ends at 18:00; the steps that end at 06:00 or start at 19:00 are out
        assertThat(chart.fromHour()).isEqualTo(6);
        assertThat(chart.toHour()).isEqualTo(19);
        assertThat(chart.steps()).extracting(DayChart.Step::startHour)
                .containsExactly(6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 17.0, 18.0);
        assertThat(chart.steps().get(6)).isEqualTo(new DayChart.Step(12, 13, 3.2, at(TODAY, 12)));
    }

    @Test
    void stepsThatStartAtHalfPastTheHourShowWholeHoursAroundTheSun() {
        // sun from 07:30 to 17:30, in steps of an hour from half past
        var midnight = TODAY.atStartOfDay(AMSTERDAM);
        var steps = IntStream.range(0, 23).mapToObj(hour -> new ForecastStep(
                midnight.plusHours(hour).plusMinutes(30).toInstant(), midnight.plusHours(hour + 1L).plusMinutes(30)
                .toInstant(), hour >= 7 && hour <= 16 ? 1 : 0)).toList();

        var chart = only(at(TODAY, 9), day(TODAY, List.of(), steps));

        assertThat(chart.fromHour()).isEqualTo(6);
        assertThat(chart.toHour()).isEqualTo(19);
    }

    @Test
    void justMoreThanAHundredthOfAKilowattIsSun() {
        var chart = today(hours(0.011, 12));

        assertThat(chart.fromHour()).isEqualTo(11);
        assertThat(chart.toHour()).isEqualTo(14);
        assertThat(chart.peak()).contains(new DayChart.Peak(0.011, at(TODAY, 12)));
    }

    @Test
    void aHundredthOfAKilowattIsNoSunSoTheDayShowsSixInTheMorningToEightInTheEveningAndHasNoPeak() {
        var kw = new double[24];
        Arrays.fill(kw, 0.01);

        var chart = today(kw);

        assertThat(chart.fromHour()).isEqualTo(6);
        assertThat(chart.toHour()).isEqualTo(20);
        assertThat(chart.peak()).isEmpty();
        assertThat(chart.ticks()).extracting(DayChart.Tick::clockHour)
                .containsExactlyElementsOf(IntStream.rangeClosed(6, 20).boxed().toList());
    }

    @Test
    void aDayWithoutAForecastShowsSixInTheMorningToEightInTheEveningWithoutStepsOrPeak() {
        var chart = only(at(TODAY, 9), day(TODAY, List.of(), List.of()));

        assertThat(chart.fromHour()).isEqualTo(6);
        assertThat(chart.toHour()).isEqualTo(20);
        assertThat(chart.steps()).isEmpty();
        assertThat(chart.peak()).isEmpty();
        assertThat(chart.scaleKw()).isEqualTo(1);
        assertThat(chart.now()).contains(new DayChart.Moment(9, at(TODAY, 9)));
    }

    @Test
    void aPlanWithoutDaysHasNoDashboardDays() {
        assertThat(DashboardView.of(at(TODAY, 9), AMSTERDAM, List.of(), HOUSE_KW).days()).isEmpty();
    }

    @Test
    void ofTwoEqualPeaksTheFirstIsThePeak() {
        var kw = hours(1, 8, 9, 10, 12, 13, 15, 16);
        kw[11] = 3;
        kw[14] = 3;

        assertThat(today(kw).peak()).contains(new DayChart.Peak(3, at(TODAY, 11)));
    }

    @Test
    void thePeakIsTheHighestStepAtItsStart() {
        assertThat(today(SUNNY).peak()).contains(new DayChart.Peak(3.2, at(TODAY, 12)));
    }

    @Test
    void theSunsHoursWithTheirMarginStayWithinTheDay() {
        var kw = new double[24];
        Arrays.fill(kw, 1);

        var chart = today(kw);

        assertThat(chart.fromHour()).isEqualTo(0);
        assertThat(chart.toHour()).isEqualTo(24);
    }

    @ParameterizedTest
    @CsvSource({"3.2, 0.3, 4.0", "0.4, 0.3, 1.0", "0.0, 0.0, 1.0", "2.0, 2.4, 3.0", "4.0, 0.3, 4.0"})
    void everyDayHasOneScaleTheWholeKilowattAboveTheHighestSunOrTheHouse(double peak, double house, double scale) {
        // the peak is on the second day, so the first one's scale shows it is shared
        var days = days(at(TODAY, 9), 2, house, date -> date.equals(TODAY) ? hours(0.2, 12) : hours(peak, 12));

        assertThat(days).extracting(day -> day.chart().scaleKw()).containsExactly(scale, scale);
        assertThat(days.get(0).chart().houseKw()).isEqualTo(house);
    }

    @Test
    void nowIsMarkedOnTodayOnlyAtTheMomentThePlanWasMade() {
        var days = days(at(TODAY, 9), 2, HOUSE_KW, ignored -> SUNNY);

        assertThat(days.get(0).chart().now()).contains(new DayChart.Moment(9, at(TODAY, 9)));
        assertThat(days.get(1).chart().now()).isEmpty();
    }

    @Test
    void aDayBeforeTodayHasNoNowEvenWhenThePlanWasMadeAtTheEndOfItsHoursShown() {
        // made at midnight, with the day before still in the plan and sun until its last hour: 24:00 is shown there
        var allDay = new double[24];
        Arrays.fill(allDay, 1);
        var yesterday = TODAY.minusDays(1);
        var plans = List.of(day(yesterday, List.of(), forecast(yesterday, allDay)),
                day(TODAY, List.of(), forecast(TODAY, allDay)));

        var days = DashboardView.of(TODAY.atStartOfDay(AMSTERDAM).toInstant(), AMSTERDAM, plans, HOUSE_KW).days();

        assertThat(days.get(0).chart().toHour()).isEqualTo(24);
        assertThat(days).extracting(day -> day.chart().now().map(DayChart.Moment::hour))
                .containsExactly(Optional.empty(), Optional.of(0.0));
    }

    /** The hours shown are 06:00 to 19:00; "now" on either edge is marked. */
    @ParameterizedTest
    @CsvSource({"5, false", "6, true", "19, true", "20, false"})
    void nowIsMarkedOnlyWithinTheHoursShownTheirEdgesIncluded(int madeAt, boolean marked) {
        var chart = days(at(TODAY, madeAt), 1, HOUSE_KW, ignored -> SUNNY).get(0).chart();

        assertThat(chart.now().isPresent()).isEqualTo(marked);
    }

    @Test
    void theChargeWindowsAreAtTheirHoursWithTheirMoments() {
        var windows = List.of(window(TODAY, 10, 12), window(TODAY, 14, 15));

        var chart = only(at(TODAY, 9), day(TODAY, windows, forecast(TODAY, SUNNY)));

        assertThat(chart.windows()).containsExactly(
                new DayChart.WindowBand(10, 12, at(TODAY, 10), at(TODAY, 12)),
                new DayChart.WindowBand(14, 15, at(TODAY, 14), at(TODAY, 15)));
    }

    /** On the short day 02:00 is skipped; on the long day 02:00 to 03:00 comes twice. */
    @ParameterizedTest
    @CsvSource({"2026-03-29, 3.0, 23.0", "2026-10-25, 5.0, 25.0"})
    void onTheShortAndTheLongDayAWindowTheHoursShownAndTheTicksAreAtTheirRealHours(LocalDate date, double windowEnd,
            double dayHours) {
        var kw = new double[(int) dayHours];
        Arrays.fill(kw, 1);

        var chart = only(date.atTime(0, 30).atZone(AMSTERDAM).toInstant(),
                day(date, List.of(window(date, 1, 4)), forecast(date, kw)));

        assertThat(chart.windows().get(0).endHour()).isEqualTo(windowEnd);
        assertThat(chart.toHour()).isEqualTo(dayHours);
        var three = chart.ticks().stream().filter(tick -> tick.clockHour() == 3).findFirst().orElseThrow();
        assertThat(three.hour()).isCloseTo(windowEnd - 1, within(1e-9));
        assertThat(three.at()).isEqualTo(at(date, 3));
        assertThat(chart.ticks().get(chart.ticks().size() - 1))
                .isEqualTo(new DayChart.Tick(dayHours, 24, date.plusDays(1).atStartOfDay(AMSTERDAM).toInstant()));
        assertThat(chart.ticks()).filteredOn(tick -> tick.clockHour() % 6 == 0).extracting(DayChart.Tick::hour)
                .containsExactly(0.0, windowEnd + 2, windowEnd + 8, windowEnd + 14, dayHours);
    }

    @Test
    void theShortDayHasNoTickAtTheHourTheClocksSkip() {
        var date = LocalDate.of(2026, 3, 29);
        var kw = new double[23];
        Arrays.fill(kw, 1);

        var chart = only(date.atTime(0, 30).atZone(AMSTERDAM).toInstant(), day(date, List.of(), forecast(date, kw)));

        assertThat(chart.ticks()).extracting(DayChart.Tick::clockHour).hasSize(24).doesNotContain(2);
    }

    @Test
    void theTicksAreEveryWholeClockHourShownTheEdgesIncluded() {
        var chart = today(SUNNY);

        assertThat(chart.ticks()).extracting(DayChart.Tick::clockHour)
                .containsExactlyElementsOf(IntStream.rangeClosed(6, 19).boxed().toList());
        assertThat(chart.ticks()).extracting(DayChart.Tick::hour)
                .containsExactlyElementsOf(IntStream.rangeClosed(6, 19).mapToObj(hour -> (double) hour).toList());
        assertThat(chart.ticks().get(0).at()).isEqualTo(at(TODAY, 6));
    }
}
