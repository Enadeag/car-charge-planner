package nl.enadeag.carchargeplanner.engine.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.Outlook;
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastSource;
import nl.enadeag.carchargeplanner.engine.forecast.Freshness;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderRole;
import nl.enadeag.carchargeplanner.engine.plan.DayPlan;
import nl.enadeag.carchargeplanner.engine.plan.GridAdvice;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

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

    private static DashboardView dashboard(Instant madeAt, Optional<GridAdvice> grid, double minimumPct,
            List<OneOffAwayPeriod> away, List<Trip> trips) {
        var days = List.of(TODAY, TODAY.plusDays(1), TODAY.plusDays(2)).stream()
                .map(date -> new DayPlan(date, new Percent(40), List.of(), new Percent(40), new Percent(40), 0,
                        List.of()))
                .toList();
        var source = new ForecastSource(new ProviderId("main"), "Main", "Forecast by Main", madeAt, Freshness.FRESH,
                ProviderRole.PRIMARY, "", Optional.empty(), List.of());
        return new Outlook.Planned(madeAt, AMSTERDAM, days, new Percent(40), false, grid, List.of(), source, 0.3,
                new Percent(minimumPct), new Percent(100)).dashboard(away, trips);
    }

    private static Optional<GridAdviceView> advice(Instant madeAt, Optional<GridAdvice> grid, double minimumPct) {
        return dashboard(madeAt, grid, minimumPct, List.of(), List.of()).gridAdvice();
    }

    private static GridAdviceView advice(GridAdvice grid) {
        return advice(MADE_AT, Optional.of(grid), 30).orElseThrow();
    }

    private static GridAdvice withoutWindow(LocalDate day, double endLevelPct) {
        return new GridAdvice(day, endLevelPct, 30 - endLevelPct, 6.0, Optional.empty(), Optional.empty());
    }

    @Test
    void theGridAdviceDoesNotDependOnTheStoredAwayPeriodsAndTrips() {
        // the tray has neither and shows the same advice
        var grid = new GridAdvice(TODAY.plusDays(1), 12, 18, 3.7, Optional.of(window(at(TODAY, 12, 0))),
                Optional.of(new Percent(60)));
        var trip = new Trip(at(TODAY.plusDays(1), 7, 0), at(TODAY.plusDays(1), 18, 0), 200, false);
        var away = new OneOffAwayPeriod(at(TODAY, 8, 0), at(TODAY, 10, 0));

        var without = dashboard(MADE_AT, Optional.of(grid), 30, List.of(), List.of()).gridAdvice();
        var with = dashboard(MADE_AT, Optional.of(grid), 30, List.of(away), List.of(trip)).gridAdvice();

        assertThat(without).isPresent();
        assertThat(with).isEqualTo(without);
    }

    @Test
    void withoutGridAdviceTheDashboardHasNone() {
        assertThat(advice(MADE_AT, Optional.empty(), 30)).isEmpty();
    }

    @Test
    void gridAdviceWithAWindowNamesItsDayInTheHomesZoneWithTheChargeLimitAnd() {
        // 22:30 in UTC on the 2nd is past midnight in Amsterdam: the window is on the 3rd, not tomorrow
        var window = window(Instant.parse("2026-06-02T22:30:00Z"));
        var grid = new GridAdvice(TODAY.plusDays(2), 17, 13, 3.7, Optional.of(window), Optional.of(new Percent(55)));

        assertThat(advice(grid)).isEqualTo(new GridAdviceView(new DayName.Other(TODAY.plusDays(2)), 3.7, 17,
                Optional.of(new GridWindow(window, new DayName.Other(TODAY.plusDays(2)),
                        new Percent(55)))));
    }

    @Test
    void aWindowJustBeforeMidnightIsOnThatDayAndOneAtMidnightOnTheNext() {
        var lastMinute = window(at(TODAY, 23, 59));
        var midnight = window(at(TODAY.plusDays(1), 0, 0));
        var limit = Optional.of(new Percent(60));

        var before = advice(new GridAdvice(TODAY.plusDays(1), 12, 18, 3.7, Optional.of(lastMinute), limit));
        var at = advice(new GridAdvice(TODAY.plusDays(1), 12, 18, 3.7, Optional.of(midnight), limit));

        assertThat(before.window().orElseThrow().day()).isEqualTo(new DayName.Today(TODAY));
        assertThat(at.window().orElseThrow().day()).isEqualTo(new DayName.Tomorrow(TODAY.plusDays(1)));
    }

    @Test
    void gridAdviceWithoutRoomHasNoWindowAndNoChargeLimit() {
        var advice = advice(withoutWindow(TODAY.plusDays(1), 12));

        assertThat(advice.window()).isEmpty();
        assertThat(advice.day()).isEqualTo(new DayName.Tomorrow(TODAY.plusDays(1)));
        assertThat(advice.energyKwh()).isEqualTo(6.0);
        assertThat(advice.endLevelPct()).isEqualTo(12);
    }

    private static Stream<Arguments> daysAheadAndTheirNames() {
        return Stream.of(Arguments.of(0, new DayName.Today(TODAY)),
                Arguments.of(1, new DayName.Tomorrow(TODAY.plusDays(1))),
                Arguments.of(2, new DayName.Other(TODAY.plusDays(2))));
    }

    @ParameterizedTest
    @MethodSource("daysAheadAndTheirNames")
    void theAdvicesDayIsNamedFromTheDayThePlanWasMade(int daysAhead, DayName name) {
        var advice = advice(withoutWindow(TODAY.plusDays(daysAhead), 12));

        assertThat(advice.day()).isEqualTo(name);
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
    void theDashboardsMinimumIsThePlansAsItIsNotTheEndLevelAndTheDeficit(double minimumPct) {
        // the deficit's sum with the end level is a hair above 100: the minimum is not made from it
        var grid = new GridAdvice(TODAY.plusDays(1), -3.3, 103.30000000000001, 6.0, Optional.empty(),
                Optional.empty());

        var dashboard = dashboard(MADE_AT, Optional.of(grid), minimumPct, List.of(), List.of());

        assertThat(dashboard.levels().minimumPct()).isEqualTo(new Percent(minimumPct));
        assertThat(dashboard.gridAdvice()).isPresent();
    }
}
