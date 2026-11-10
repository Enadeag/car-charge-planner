package nl.enadeag.carchargeplanner.desktop.dashboard.trip;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.engine.dashboard.ListedTrip;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class TripMapperTest {

    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    /** A Friday. */
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 3);

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(BRUSSELS).toInstant();
    }

    /** The trip as the engine's dashboard of a plan made today lists it, in words. */
    private static TripsModel.Listed map(Units units, Language language, Trip trip) {
        return new TripMapper(BRUSSELS, units, new Messages(language)).map(ListedTrip.of(trip, BRUSSELS, TODAY));
    }

    @Test
    void aTripOnOneDayShowsItsTimesItsDistanceAndThatItWasEnteredByHand() {
        var trip = new Trip(at(TODAY.plusDays(1), 7, 30), at(TODAY.plusDays(1), 18, 0), 312.4, false);

        var listed = map(Units.METRIC, Language.ENGLISH, trip);

        assertThat(listed).isEqualTo(new TripsModel.Listed(trip, TripOrigin.BY_HAND,
                "Away 07:30 – 18:00 · 312 km there and back", "Entered by hand",
                "Change the trip Away 07:30 – 18:00 · 312 km there and back",
                "Remove the trip Away 07:30 – 18:00 · 312 km there and back"));
    }

    @Test
    void aTripOverMoreDaysNamesTheDaysInTheOwnersLanguageAndUnits() {
        var trip = new Trip(at(TODAY, 19, 0), at(TODAY.plusDays(3), 9, 15), 160.9344, true);

        var english = map(Units.IMPERIAL, Language.ENGLISH, trip);
        var dutch = map(Units.METRIC, Language.DUTCH, trip);

        assertThat(english.text()).isEqualTo("Away Today 19:00 – Mon 6 Jul 09:15 · 100 mi there and back");
        assertThat(dutch.text()).isEqualTo("Afwezig Vandaag 19:00 – Ma 6 jul 09:15 · 161 km heen en terug");
        assertThat(dutch.originText()).isEqualTo("Zelf ingevuld");
    }
}
