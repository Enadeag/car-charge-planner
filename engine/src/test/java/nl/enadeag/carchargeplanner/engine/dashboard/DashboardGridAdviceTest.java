package nl.enadeag.carchargeplanner.engine.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.plan.DayPlan;
import nl.enadeag.carchargeplanner.engine.plan.GridAdvice;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DashboardGridAdviceTest {

    private static final ZoneId AMSTERDAM = ZoneId.of("Europe/Amsterdam");
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);
    private static final Instant MADE_AT = at(TODAY, 10, 0);

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(AMSTERDAM).toInstant();
    }

    private static Window window(Instant start) {
        return new Window(start, start.plusSeconds(3600), 16, 3.7, 0, 3.7);
    }

    private static Optional<GridAdviceView> advice(Instant madeAt, Optional<GridAdvice> grid, double minimumPct) {
        var days = List.of(TODAY, TODAY.plusDays(1), TODAY.plusDays(2)).stream()
                .map(date -> new DayPlan(date, new Percent(40), List.of(), new Percent(40), new Percent(40), 0,
                        List.of()))
                .toList();
        return DashboardView.of(new PlanParts(madeAt, AMSTERDAM, days, 0.3, new Percent(40), false, grid, List.of(),
                new Percent(minimumPct), new Percent(100), List.of())).gridAdvice();
    }

    private static GridAdviceView advice(GridAdvice grid) {
        return advice(MADE_AT, Optional.of(grid), 30).orElseThrow();
    }

    private static GridAdvice withoutWindow(LocalDate day, double endLevelPct) {
        return new GridAdvice(day, endLevelPct, 30 - endLevelPct, 6.0, Optional.empty(), Optional.empty());
    }

    @Test
    void withoutGridAdviceTheDashboardHasNone() {
        assertThat(advice(MADE_AT, Optional.empty(), 30)).isEmpty();
    }

    @Test
    void gridAdviceWithAWindowNamesItsDayInTheHomesZoneWithTheChargeLimitAndThePlansMinimum() {
        // 22:30 in UTC on the 2nd is past midnight in Amsterdam: the window is on the 3rd, not tomorrow
        var window = window(Instant.parse("2026-06-02T22:30:00Z"));
        var grid = new GridAdvice(TODAY.plusDays(2), 17, 13, 3.7, Optional.of(window), Optional.of(new Percent(55)));

        assertThat(advice(grid)).isEqualTo(new GridAdviceView(new DayName.Other(TODAY.plusDays(2)), 3.7, 17,
                new Percent(30), Optional.of(new GridAdviceView.Charge(window, new DayName.Other(TODAY.plusDays(2)),
                        new Percent(55)))));
    }

    @Test
    void aWindowJustBeforeMidnightIsOnThatDayAndOneAtMidnightOnTheNext() {
        var lastMinute = window(at(TODAY, 23, 59));
        var midnight = window(at(TODAY.plusDays(1), 0, 0));
        var limit = Optional.of(new Percent(60));

        var before = advice(new GridAdvice(TODAY.plusDays(1), 12, 18, 3.7, Optional.of(lastMinute), limit));
        var at = advice(new GridAdvice(TODAY.plusDays(1), 12, 18, 3.7, Optional.of(midnight), limit));

        assertThat(before.charge().orElseThrow().day()).isEqualTo(new DayName.Today(TODAY));
        assertThat(at.charge().orElseThrow().day()).isEqualTo(new DayName.Tomorrow(TODAY.plusDays(1)));
    }

    @Test
    void gridAdviceWithoutRoomHasNoWindowAndNoChargeLimit() {
        var advice = advice(withoutWindow(TODAY.plusDays(1), 12));

        assertThat(advice.charge()).isEmpty();
        assertThat(advice.day()).isEqualTo(new DayName.Tomorrow(TODAY.plusDays(1)));
        assertThat(advice.energyKwh()).isEqualTo(6.0);
        assertThat(advice.endLevelPct()).isEqualTo(12);
    }

    @ParameterizedTest
    @CsvSource({"0, Today", "1, Tomorrow", "2, Other"})
    void theAdvicesDayIsNamedFromTheDayThePlanWasMade(int daysAhead, String name) {
        var advice = advice(withoutWindow(TODAY.plusDays(daysAhead), 12));

        assertThat(advice.day().getClass().getSimpleName()).isEqualTo(name);
        assertThat(advice.day().date()).isEqualTo(TODAY.plusDays(daysAhead));
    }

    @Test
    void theAdvicesDayIsNamedFromTheDayThePlanWasMadeInTheHomesZone() {
        // 22:30 in UTC on the 1st is 00:30 on the 2nd in Amsterdam: the 3rd is tomorrow, not the day after
        var lateAtNight = Instant.parse("2026-06-01T22:30:00Z");

        var advice = advice(lateAtNight, Optional.of(withoutWindow(TODAY.plusDays(2), 12)), 30).orElseThrow();

        assertThat(advice.day()).isEqualTo(new DayName.Tomorrow(TODAY.plusDays(2)));
    }

    @ParameterizedTest
    @CsvSource({"0, true", "-0.01, true", "-4, true", "0.01, false", "0.5, false"})
    void gridAdviceRunsEmptyOnlyWhenTheDayEndsAtOrBelowNothing(double endLevelPct, boolean runsEmpty) {
        assertThat(advice(withoutWindow(TODAY.plusDays(1), endLevelPct)).runsEmpty()).isEqualTo(runsEmpty);
    }

    @ParameterizedTest
    @CsvSource({"30", "33.3", "100"})
    void theMinimumIsThePlansAsItIsNotTheEndLevelAndTheDeficit(double minimumPct) {
        // the deficit's sum with the end level is a hair above 100: the minimum is not made from it
        var grid = new GridAdvice(TODAY.plusDays(1), -3.3, 103.30000000000001, 6.0, Optional.empty(),
                Optional.empty());

        var advice = advice(MADE_AT, Optional.of(grid), minimumPct).orElseThrow();

        assertThat(advice.minimumPct()).isEqualTo(new Percent(minimumPct));
    }
}
