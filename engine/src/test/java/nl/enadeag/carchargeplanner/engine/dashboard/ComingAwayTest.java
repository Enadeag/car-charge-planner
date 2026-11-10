package nl.enadeag.carchargeplanner.engine.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ComingAwayTest {

    private static final ZoneId AMSTERDAM = ZoneId.of("Europe/Amsterdam");
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);
    private static final Instant MADE_AT = at(TODAY, 10, 0);

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(AMSTERDAM).toInstant();
    }

    private static OneOffAwayPeriod part(LocalDate day, int startHour, int endHour) {
        return OneOffAwayPeriod.on(day, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0), AMSTERDAM);
    }

    private static DashboardView dashboard(Instant madeAt, ZoneId zone, List<OneOffAwayPeriod> away) {
        var parts = Parts.of(madeAt, zone, List.of(), 0.3);
        return DashboardView.of(new PlanParts(parts.madeAt(), zone, parts.days(), parts.baseloadKw(),
                parts.batteryLevelPct(), parts.batteryAssumed(), parts.gridAdvice(), parts.tripAdvice(),
                parts.minimumPct(), parts.targetPct(), away, List.of(), parts.forecastSource()));
    }

    private static List<ComingAway> listed(OneOffAwayPeriod... away) {
        return dashboard(MADE_AT, AMSTERDAM, List.of(away)).away();
    }

    @Test
    void thePeriodsThatEndedAreLeftOut() {
        var ended = part(TODAY, 7, 10);
        var running = part(TODAY, 9, 11);

        assertThat(listed(ended, running)).extracting(ComingAway::period).containsExactly(running);
    }

    @Test
    void theComingPeriodsAreListedInTheStoredOrder() {
        var later = part(TODAY.plusDays(7), 9, 17);
        var running = part(TODAY, 9, 11);
        var allDay = OneOffAwayPeriod.allDay(TODAY.plusDays(1), AMSTERDAM);

        assertThat(listed(later, running, allDay)).extracting(ComingAway::period)
                .containsExactly(later, running, allDay);
    }

    @Test
    void eachComingPeriodIsListedByKindWithItsDays() {
        var running = part(TODAY, 9, 11);
        var allDay = OneOffAwayPeriod.allDay(TODAY.plusDays(1), AMSTERDAM);
        var days = new OneOffAwayPeriod(at(TODAY.plusDays(2), 18, 0), at(TODAY.plusDays(4), 9, 0));

        assertThat(listed(running, allDay, days)).containsExactly(
                new ComingAway.PartOfDay(new DayName.Today(TODAY), running),
                new ComingAway.AllDay(new DayName.Tomorrow(TODAY.plusDays(1)), allDay),
                new ComingAway.Days(new DayName.Other(TODAY.plusDays(2)), new DayName.Other(TODAY.plusDays(4)), days));
    }

    @ParameterizedTest
    @CsvSource({"-1, false", "0, false", "1, true"})
    void aPeriodIsListedOnlyWhenItEndsAfterTheMomentThePlanWasMade(long secondsAfter, boolean shown) {
        var period = new OneOffAwayPeriod(MADE_AT.minusSeconds(3600), MADE_AT.plusSeconds(secondsAfter));

        assertThat(listed(period)).hasSize(shown ? 1 : 0);
    }

    @ParameterizedTest
    @CsvSource({"2026-03-29", "2026-10-25"})
    void aWholeShortOrLongDayIsAllDayAndTwentyFourHoursOfItAreNot(LocalDate day) {
        var start = day.atStartOfDay(AMSTERDAM).toInstant();
        var whole = OneOffAwayPeriod.allDay(day, AMSTERDAM);
        var twentyFourHours = new OneOffAwayPeriod(start, start.plusSeconds(24 * 3600));

        var listed = dashboard(start, AMSTERDAM, List.of(whole, twentyFourHours)).away();

        assertThat(listed.get(0)).isEqualTo(new ComingAway.AllDay(new DayName.Today(day), whole));
        assertThat(listed.get(1)).isNotInstanceOf(ComingAway.AllDay.class);
    }

    @Test
    void aPeriodThatEndsAtMidnightIsOverTwoDaysAndOneThatEndsAMinuteBeforeIsPartOfADay() {
        var toMidnight = new OneOffAwayPeriod(at(TODAY, 18, 0), at(TODAY.plusDays(1), 0, 0));
        var toAMinuteBefore = new OneOffAwayPeriod(at(TODAY, 18, 0), at(TODAY, 23, 59));

        assertThat(listed(toMidnight, toAMinuteBefore)).containsExactly(
                new ComingAway.Days(new DayName.Today(TODAY), new DayName.Tomorrow(TODAY.plusDays(1)), toMidnight),
                new ComingAway.PartOfDay(new DayName.Today(TODAY), toAMinuteBefore));
    }

    @Test
    void aPeriodFromMidnightThatEndsBeforeTheNextIsPartOfTheDay() {
        var morning = new OneOffAwayPeriod(at(TODAY.plusDays(1), 0, 0), at(TODAY.plusDays(1), 12, 0));

        assertThat(listed(morning)).containsExactly(
                new ComingAway.PartOfDay(new DayName.Tomorrow(TODAY.plusDays(1)), morning));
    }

    @ParameterizedTest
    @CsvSource({"Europe/Amsterdam, 2026-06-02", "America/New_York, 2026-06-01", "Pacific/Auckland, 2026-06-02"})
    void theDaysAreTheHomesZonesNotTheMomentsInUtc(ZoneId zone, LocalDate day) {
        // 23:30 UTC on 1 June, for an hour
        var period = new OneOffAwayPeriod(Instant.parse("2026-06-01T23:30:00Z"), Instant.parse("2026-06-02T00:30:00Z"));
        var madeAt = Instant.parse("2026-06-01T12:00:00Z");

        var listed = dashboard(madeAt, zone, List.of(period)).away();

        assertThat(listed).singleElement().isInstanceOfSatisfying(ComingAway.PartOfDay.class,
                partOfDay -> assertThat(partOfDay.day().date()).isEqualTo(day));
    }

    @Test
    void theDaysAreNamedFromTheDayThePlanWasMadeInTheHomesZone() {
        // 23:30 in Auckland is still the day before in UTC
        var auckland = ZoneId.of("Pacific/Auckland");
        var today = LocalDate.of(2026, 6, 2);
        var madeAt = today.atTime(23, 30).atZone(auckland).toInstant();
        var tomorrow = OneOffAwayPeriod.allDay(today.plusDays(1), auckland);

        assertThat(dashboard(madeAt, auckland, List.of(tomorrow)).away())
                .containsExactly(new ComingAway.AllDay(new DayName.Tomorrow(today.plusDays(1)), tomorrow));
    }

    @Test
    void aPeriodPartOfADayAndOneOverDaysStartAndEndWhenTheirPeriodsDo() {
        var partOfDay = new ComingAway.PartOfDay(new DayName.Today(TODAY), part(TODAY, 9, 11));
        var period = new OneOffAwayPeriod(at(TODAY, 18, 0), at(TODAY.plusDays(2), 9, 0));
        var days = new ComingAway.Days(new DayName.Today(TODAY), new DayName.Other(TODAY.plusDays(2)), period);

        assertThat(List.of(partOfDay.start(), partOfDay.end())).containsExactly(at(TODAY, 9, 0), at(TODAY, 11, 0));
        assertThat(List.of(days.start(), days.end())).containsExactly(period.start(), period.end());
    }

    @Test
    void aPeriodWhileTheClockRunsBackOverMidnightIsPartOfTheDayItStarts() {
        // St. John's set its clock back from 00:01 to 23:01 the day before, at 02:31 UTC
        var stJohns = ZoneId.of("America/St_Johns");
        var today = LocalDate.of(2010, 11, 7);
        var period = new OneOffAwayPeriod(Instant.parse("2010-11-07T02:30:30Z"), Instant.parse("2010-11-07T02:45:00Z"));

        assertThat(ComingAway.describe(period, stJohns, today))
                .isEqualTo(new ComingAway.PartOfDay(new DayName.Today(today), period));
    }

    @Test
    void aPeriodThatEndedIsDescribedFromTheDayThePlanWasMade() {
        var ended = part(TODAY, 7, 8);
        var tomorrow = OneOffAwayPeriod.allDay(TODAY.plusDays(1), AMSTERDAM);
        var shown = dashboard(MADE_AT, AMSTERDAM, List.of(ended));

        assertThat(shown.away()).isEmpty();
        assertThat(shown.describe(ended)).isEqualTo(new ComingAway.PartOfDay(new DayName.Today(TODAY), ended));
        assertThat(shown.describe(tomorrow))
                .isEqualTo(new ComingAway.AllDay(new DayName.Tomorrow(TODAY.plusDays(1)), tomorrow));
    }

    @Test
    void aPeriodIsDescribedFromTheHomesTodayAlsoWhenUtcIsStillTheDayBefore() {
        // 08:00 in Auckland is 20:00 UTC the day before
        var auckland = ZoneId.of("Pacific/Auckland");
        var today = LocalDate.of(2026, 6, 2);
        var madeAt = today.atTime(8, 0).atZone(auckland).toInstant();
        var allDay = OneOffAwayPeriod.allDay(today, auckland);

        assertThat(dashboard(madeAt, auckland, List.of()).describe(allDay))
                .isEqualTo(new ComingAway.AllDay(new DayName.Today(today), allDay));
    }

    @Test
    void withoutAPlanAPeriodIsDescribedFromTheDayGiven() {
        var period = part(TODAY.plusDays(1), 9, 11);

        assertThat(ComingAway.describe(period, AMSTERDAM, TODAY.plusDays(1)))
                .isEqualTo(new ComingAway.PartOfDay(new DayName.Today(TODAY.plusDays(1)), period));
        assertThat(ComingAway.describe(period, AMSTERDAM, TODAY))
                .isEqualTo(dashboard(MADE_AT, AMSTERDAM, List.of()).describe(period));
    }

    @ParameterizedTest
    @CsvSource({"0, true", "-1, true", "1, false"})
    void aPeriodOverDaysEndsOnALaterDayThanItStarts(int endDaysAfter, boolean refused) {
        var period = new OneOffAwayPeriod(at(TODAY, 18, 0), at(TODAY.plusDays(2), 9, 0));
        var endDay = new DayName.Other(TODAY.plusDays(endDaysAfter));

        if (refused) {
            assertThatThrownBy(() -> new ComingAway.Days(new DayName.Today(TODAY), endDay, period))
                    .isInstanceOf(IllegalArgumentException.class);
        } else {
            assertThat(new ComingAway.Days(new DayName.Today(TODAY), endDay, period).endDay()).isEqualTo(endDay);
        }
    }

    @Test
    void withoutStoredPeriodsNoneIsListed() {
        assertThat(dashboard(MADE_AT, AMSTERDAM, List.of()).away()).isEmpty();
    }

    @Test
    void thePlanPartsKeepTheirOwnListOfPeriods() {
        var periods = new ArrayList<>(List.of(part(TODAY, 11, 12)));
        var parts = Parts.of(MADE_AT, AMSTERDAM, List.of(), 0.3);
        var withPeriods = new PlanParts(MADE_AT, AMSTERDAM, List.of(), 0.3, parts.batteryLevelPct(), false,
                parts.gridAdvice(), List.of(), parts.minimumPct(), parts.targetPct(), periods, List.of(),
                parts.forecastSource());

        periods.clear();

        assertThat(withPeriods.away()).hasSize(1);
    }

    @Test
    void theDashboardKeepsItsOwnListOfPeriods() {
        var listed = new ArrayList<>(List.<ComingAway>of(new ComingAway.PartOfDay(new DayName.Today(TODAY),
                part(TODAY, 11, 12))));
        var shown = DashboardView.of(Parts.of(MADE_AT, AMSTERDAM, List.of(), 0.3));
        var view = new DashboardView(MADE_AT, AMSTERDAM, List.of(), shown.levels(), shown.startLevel(),
                shown.gridAdvice(), List.of(), shown.footer(), listed, List.of());

        listed.clear();

        assertThat(view.away()).hasSize(1);
    }
}
