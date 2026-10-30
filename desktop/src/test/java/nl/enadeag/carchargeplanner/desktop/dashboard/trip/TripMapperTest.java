package nl.enadeag.carchargeplanner.desktop.dashboard.trip;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class TripMapperTest {

    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    /** A Friday. */
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 3);
    private static final Instant MADE_AT = TODAY.atTime(8, 12).atZone(BRUSSELS).toInstant();

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(BRUSSELS).toInstant();
    }

    private static TripsModel map(Units units, Language language, Trip... trips) {
        return new TripMapper(BRUSSELS, MADE_AT, units, new Messages(language)).map(List.of(trips));
    }

    @Test
    void aTripOnOneDayShowsItsTimesItsDistanceAndThatItWasEnteredByHand() {
        var trip = new Trip(at(TODAY.plusDays(1), 7, 30), at(TODAY.plusDays(1), 18, 0), 312.4, false);

        var model = map(Units.METRIC, Language.ENGLISH, trip);

        assertThat(model.trips()).containsExactly(new TripsModel.Listed(trip, TODAY.plusDays(1), TODAY.plusDays(1),
                TripOrigin.BY_HAND, "Away 07:30 – 18:00 · 312 km there and back", "Entered by hand",
                "Change the trip Away 07:30 – 18:00 · 312 km there and back",
                "Remove the trip Away 07:30 – 18:00 · 312 km there and back"));
        assertThat(model.zone()).isEqualTo(BRUSSELS);
        assertThat(model.madeAt()).isEqualTo(MADE_AT);
        assertThat(model.units()).isEqualTo(Units.METRIC);
    }

    @Test
    void aTripOverMoreDaysNamesTheDaysInTheOwnersLanguageAndUnits() {
        var trip = new Trip(at(TODAY, 19, 0), at(TODAY.plusDays(3), 9, 15), 160.9344, true);

        var english = map(Units.IMPERIAL, Language.ENGLISH, trip).trips().getFirst();
        var dutch = map(Units.METRIC, Language.DUTCH, trip).trips().getFirst();

        assertThat(english.text()).isEqualTo("Away Today 19:00 – Mon 6 Jul 09:15 · 100 mi there and back");
        assertThat(dutch.text()).isEqualTo("Afwezig Vandaag 19:00 – Ma 6 jul 09:15 · 161 km heen en terug");
        assertThat(dutch.originText()).isEqualTo("Zelf ingevuld");
        assertThat(english.firstDay()).isEqualTo(TODAY);
        assertThat(english.lastDay()).isEqualTo(TODAY.plusDays(3));
    }

    @Test
    void aDayShowsTheTripsTheCarIsAwayForThatDayInOrder() {
        var saturday = new Trip(at(TODAY.plusDays(1), 7, 30), at(TODAY.plusDays(1), 18, 0), 300, false);
        var weekend = new Trip(at(TODAY.plusDays(1), 20, 0), at(TODAY.plusDays(3), 9, 0), 80, false);
        var model = map(Units.METRIC, Language.ENGLISH, saturday, weekend);

        assertThat(model.on(TODAY)).isEmpty();
        assertThat(model.on(TODAY.plusDays(1))).extracting(TripsModel.Listed::trip).containsExactly(saturday, weekend);
        assertThat(model.on(TODAY.plusDays(2))).extracting(TripsModel.Listed::trip).containsExactly(weekend);
        assertThat(model.on(TODAY.plusDays(3))).extracting(TripsModel.Listed::trip).containsExactly(weekend);
        assertThat(model.on(TODAY.plusDays(4))).isEmpty();
    }

    @Test
    void aTripBackAtMidnightTakesNoTimeOfTheDayItReturnsOn() {
        var evening = new Trip(at(TODAY, 18, 0), at(TODAY.plusDays(1), 0, 0), 50, false);
        var justAfter = new Trip(at(TODAY, 18, 0), at(TODAY.plusDays(1), 0, 1), 50, false);

        assertThat(map(Units.METRIC, Language.ENGLISH, evening).trips().getFirst().lastDay()).isEqualTo(TODAY);
        assertThat(map(Units.METRIC, Language.ENGLISH, justAfter).trips().getFirst().lastDay())
                .isEqualTo(TODAY.plusDays(1));
    }
}
