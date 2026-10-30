package nl.enadeag.carchargeplanner.engine.plan;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import nl.enadeag.carchargeplanner.engine.away.WeeklyAwayPeriod;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AwayTimesTest {

    private static final ZoneId TZ = Forecasts.TZ;
    private static final LocalDate THURSDAY = LocalDate.of(2026, 7, 2);
    private static final LocalDate FRIDAY = THURSDAY.plusDays(1);
    /** The night the clocks go back an hour, from 03:00 summer time to 02:00 winter time. */
    private static final LocalDate WINTER_TIME = LocalDate.of(2026, 10, 25);

    private static Instant at(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(TZ).toInstant();
    }

    private static Instant at(LocalDate date, int hour) {
        return at(date, hour, 0);
    }

    /** A forecast for these days; only its first and last moment count for the away time. */
    private static List<ForecastStep> forecast(LocalDate first, LocalDate last) {
        return List.of(new ForecastStep(at(first, 0), at(last, 23), 0));
    }

    private static WeeklyAwayPeriod weekly(DayOfWeek weekday, int fromHour, int fromMinute, int toHour) {
        return new WeeklyAwayPeriod(weekday, LocalTime.of(fromHour, fromMinute), LocalTime.of(toHour, 0));
    }

    private static Trip trip(Instant departure, Instant back) {
        return new Trip(departure, back, 300, false);
    }

    @Test
    void tripsAndAwayPeriodsAddUpAndTimesThatOverlapMerge() {
        var away = new AwayTimes(List.of(weekly(DayOfWeek.THURSDAY, 6, 0, 19)),
                List.of(new OneOffAwayPeriod(at(FRIDAY, 9), at(FRIDAY, 12)),
                        new OneOffAwayPeriod(at(FRIDAY, 15), at(FRIDAY, 16))),
                List.of(trip(at(THURSDAY, 17), at(FRIDAY, 10))));

        assertThat(away.during(forecast(THURSDAY, FRIDAY), TZ)).containsExactly(
                new BlockedTime(at(THURSDAY, 6), at(FRIDAY, 12)),
                new BlockedTime(at(FRIDAY, 15), at(FRIDAY, 16)));
    }

    @Test
    void aTripWithinAnAwayPeriodAddsNothing() {
        var away = new AwayTimes(List.of(weekly(DayOfWeek.THURSDAY, 6, 0, 19)), List.of(),
                List.of(trip(at(THURSDAY, 8), at(THURSDAY, 10))));

        assertThat(away.during(forecast(THURSDAY, THURSDAY), TZ))
                .containsExactly(new BlockedTime(at(THURSDAY, 6), at(THURSDAY, 19)));
    }

    @Test
    void tripsThatOverlapEachOtherMerge() {
        var away = new AwayTimes(List.of(), List.of(), List.of(trip(at(FRIDAY, 12), at(FRIDAY, 20)),
                trip(at(THURSDAY, 8), at(FRIDAY, 14))));

        assertThat(away.during(forecast(THURSDAY, FRIDAY), TZ))
                .containsExactly(new BlockedTime(at(THURSDAY, 8), at(FRIDAY, 20)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void aTripBackAtMidnightJoinsAnAwayPeriodFromMidnightAndOneBackLaterDoesNot(int minutesGap) {
        var back = at(FRIDAY, 0).minus(Duration.ofMinutes(minutesGap));
        var away = new AwayTimes(List.of(weekly(DayOfWeek.FRIDAY, 0, 0, 8)), List.of(),
                List.of(trip(at(THURSDAY, 20), back)));

        assertThat(away.during(forecast(THURSDAY, FRIDAY), TZ)).isEqualTo(minutesGap == 0
                ? List.of(new BlockedTime(at(THURSDAY, 20), at(FRIDAY, 8)))
                : List.of(new BlockedTime(at(THURSDAY, 20), back), new BlockedTime(at(FRIDAY, 0), at(FRIDAY, 8))));
    }

    @Test
    void aTripOverTheNightTheClocksGoBackMergesWithTheMorningsAwayPeriod() {
        // 02:30 comes twice that night; the weekly period starts at the first, in summer time
        var saturday = WINTER_TIME.minusDays(1);
        var departure = at(saturday, 22);
        var away = new AwayTimes(List.of(weekly(DayOfWeek.SUNDAY, 2, 30, 9)), List.of(),
                List.of(trip(departure, at(WINTER_TIME, 3))));

        var times = away.during(forecast(saturday, WINTER_TIME), TZ);

        assertThat(times).containsExactly(new BlockedTime(departure, at(WINTER_TIME, 9)));
        assertThat(Duration.between(times.get(0).start(), times.get(0).end())).isEqualTo(Duration.ofHours(12));
    }

    @Test
    void withoutAForecastTheTripsCountAsTheOneOffPeriodsDo() {
        var trip = trip(at(FRIDAY, 8), at(FRIDAY, 18));
        var away = new AwayTimes(List.of(weekly(DayOfWeek.FRIDAY, 6, 0, 19)),
                List.of(new OneOffAwayPeriod(at(FRIDAY, 20), at(FRIDAY, 21))), List.of(trip));

        assertThat(away.during(List.of(), TZ)).containsExactly(new BlockedTime(trip.departure(), trip.returnTime()),
                new BlockedTime(at(FRIDAY, 20), at(FRIDAY, 21)));
    }
}
