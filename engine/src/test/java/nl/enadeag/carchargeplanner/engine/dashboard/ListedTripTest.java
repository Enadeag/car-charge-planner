package nl.enadeag.carchargeplanner.engine.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.plan.DayPlan;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ListedTripTest {

    private static final ZoneId AMSTERDAM = ZoneId.of("Europe/Amsterdam");
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);
    private static final Instant MADE_AT = at(TODAY, 8, 0);

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(AMSTERDAM).toInstant();
    }

    private static Trip trip(Instant from, Instant until) {
        return new Trip(from, until, 300, false);
    }

    /** Five days from the day the plan was made, in a zone, with these stored trips. */
    private static DashboardView dashboard(Instant madeAt, ZoneId zone, List<Trip> trips) {
        var first = madeAt.atZone(zone).toLocalDate();
        var days = Stream.iterate(first, day -> day.plusDays(1)).limit(5)
                .map(date -> new DayPlan(date, new Percent(40), List.of(), new Percent(40), new Percent(40), 0,
                        List.of()))
                .toList();
        var parts = Parts.of(madeAt, zone, days, 0.3);
        return DashboardView.of(new PlanParts(madeAt, zone, days, 0.3, parts.batteryLevelPct(), false,
                parts.gridAdvice(), List.of(), parts.minimumPct(), parts.targetPct(), List.of(), trips,
                parts.forecastSource()));
    }

    private static DashboardView dashboard(Trip... trips) {
        return dashboard(MADE_AT, AMSTERDAM, List.of(trips));
    }

    private static ListedTrip listed(Trip trip) {
        return dashboard(trip).trips().get(0);
    }

    /** The trips each of the five days lists. */
    private static List<List<Trip>> byDay(DashboardView shown) {
        return shown.days().stream().map(day -> shown.tripsOn(day).stream().map(ListedTrip::trip).toList()).toList();
    }

    @Test
    void aTripBackTheDayItLeavesIsToldWithoutItsDays() {
        var listed = listed(trip(at(TODAY, 7, 30), at(TODAY, 18, 0)));

        assertThat(listed.sameDay()).isTrue();
        assertThat(listed.departureDay()).isEqualTo(new DayName.Today(TODAY));
        assertThat(listed.returnDay()).isEqualTo(new DayName.Today(TODAY));
    }

    @Test
    void aTripOverSeveralDaysNamesTheDaysOfItsDepartureAndReturn() {
        var listed = listed(trip(at(TODAY.plusDays(1), 7, 30), at(TODAY.plusDays(3), 0, 30)));

        assertThat(listed.sameDay()).isFalse();
        assertThat(listed.departureDay()).isEqualTo(new DayName.Tomorrow(TODAY.plusDays(1)));
        assertThat(listed.returnDay()).isEqualTo(new DayName.Other(TODAY.plusDays(3)));
    }

    @Test
    void eachDayListsTheTripsTheCarIsAwayForThatDayInTheStoredOrder() {
        var saturday = trip(at(TODAY.plusDays(1), 7, 30), at(TODAY.plusDays(1), 18, 0));
        var weekend = trip(at(TODAY.plusDays(1), 6, 0), at(TODAY.plusDays(3), 9, 0));

        assertThat(byDay(dashboard(saturday, weekend))).containsExactly(List.of(), List.of(saturday, weekend),
                List.of(weekend), List.of(weekend), List.of());
    }

    @Test
    void aTripBackAtMidnightIsToldWithItsDaysYetTheDayItReturnsOnDoesNotListIt() {
        var evening = trip(at(TODAY, 18, 0), at(TODAY.plusDays(1), 0, 0));
        var shown = dashboard(evening);

        assertThat(shown.trips().get(0).sameDay()).isFalse();
        assertThat(shown.trips().get(0).returnDay()).isEqualTo(new DayName.Tomorrow(TODAY.plusDays(1)));
        assertThat(byDay(shown)).containsExactly(List.of(evening), List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void aTripBackAMinuteAfterMidnightIsListedOnTheDayItReturnsOn() {
        var evening = trip(at(TODAY, 18, 0), at(TODAY.plusDays(1), 0, 1));

        assertThat(byDay(dashboard(evening))).containsExactly(List.of(evening), List.of(evening), List.of(), List.of(),
                List.of());
    }

    @ParameterizedTest
    @CsvSource({"Europe/Amsterdam, 2026-06-02", "America/New_York, 2026-06-01", "Pacific/Auckland, 2026-06-02"})
    void theDaysAreTheHomesZonesNotTheMomentsInUtc(ZoneId zone, LocalDate day) {
        // 23:30 UTC on 1 June
        var trip = trip(Instant.parse("2026-06-01T23:30:00Z"), Instant.parse("2026-06-02T01:00:00Z"));
        var madeAt = Instant.parse("2026-05-31T12:00:00Z");

        var shown = dashboard(madeAt, zone, List.of(trip));

        assertThat(shown.trips().get(0).departureDay().date()).isEqualTo(day);
        assertThat(shown.days().stream().filter(shownDay -> !shown.tripsOn(shownDay).isEmpty())
                .map(shownDay -> shownDay.name().date())).containsExactly(day);
    }

    @Test
    void everyStoredTripIsListedInTheStoredOrderAlsoOneBackAlreadyAndOneWithoutThisCar() {
        var coming = trip(at(TODAY.plusDays(1), 9, 0), at(TODAY.plusDays(1), 17, 0));
        var back = trip(at(TODAY, 5, 0), at(TODAY, 7, 0));
        var withoutThisCar = new Trip(at(TODAY.plusDays(2), 9, 0), at(TODAY.plusDays(2), 17, 0), 50, false, true);

        assertThat(dashboard(coming, back, withoutThisCar).trips()).extracting(ListedTrip::trip)
                .containsExactly(coming, back, withoutThisCar);
    }

    @Test
    void aTripIsDescribedFromTheDayThePlanWasMadeAlsoOneNotStored() {
        var trip = trip(at(TODAY.plusDays(1), 9, 0), at(TODAY.plusDays(2), 17, 0));

        assertThat(dashboard().describe(trip)).isEqualTo(new ListedTrip(trip, new DayName.Tomorrow(TODAY.plusDays(1)),
                new DayName.Other(TODAY.plusDays(2))));
    }

    @Test
    void withoutAPlanATripIsDescribedFromTheDayGiven() {
        var trip = trip(at(TODAY.plusDays(1), 9, 0), at(TODAY.plusDays(1), 17, 0));

        assertThat(ListedTrip.of(trip, AMSTERDAM, TODAY.plusDays(1)).departureDay())
                .isEqualTo(new DayName.Today(TODAY.plusDays(1)));
        assertThat(ListedTrip.of(trip, AMSTERDAM, TODAY)).isEqualTo(dashboard().describe(trip));
    }

    @ParameterizedTest
    @CsvSource({"-1, true", "0, false", "1, false"})
    void aTripReturnsOnOrAfterTheDayItLeaves(int returnDaysAfter, boolean refused) {
        var trip = trip(at(TODAY, 7, 30), at(TODAY, 18, 0));
        var returnDay = DayName.of(TODAY.plusDays(returnDaysAfter), TODAY);

        if (refused) {
            assertThatThrownBy(() -> new ListedTrip(trip, new DayName.Today(TODAY), returnDay))
                    .isInstanceOf(IllegalArgumentException.class);
        } else {
            assertThat(new ListedTrip(trip, new DayName.Today(TODAY), returnDay).returnDay()).isEqualTo(returnDay);
        }
    }

    @Test
    void withoutStoredTripsNoDayListsOne() {
        var shown = dashboard();

        assertThat(shown.trips()).isEmpty();
        assertThat(byDay(shown)).containsOnly(List.of());
    }

    @Test
    void theDashboardKeepsItsOwnListOfTrips() {
        var trips = new ArrayList<>(List.of(listed(trip(at(TODAY, 7, 30), at(TODAY, 18, 0)))));
        var shown = dashboard();
        var view = new DashboardView(MADE_AT, AMSTERDAM, List.of(), shown.levels(), shown.startLevel(),
                shown.gridAdvice(), List.of(), shown.footer(), List.of(), trips);

        trips.clear();

        assertThat(view.trips()).hasSize(1);
    }
}
