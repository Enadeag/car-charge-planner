package nl.enadeag.carchargeplanner.desktop.dashboard.trip;

import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.dashboard.ListedTrip;
import java.time.ZoneId;

/** The trips the owner entered, as the dashboard's day tabs show them, in the time zone of the owner's home. */
public final class TripMapper {

    private final Messages messages;
    private final ZoneId zone;
    private final Units units;

    /**
     * Makes the mapper for one plan.
     *
     * @param zone the time zone of the owner's home, in which the times are told
     * @param units the units the distances are told in
     * @param messages the texts, in the owner's language
     */
    public TripMapper(ZoneId zone, Units units, Messages messages) {
        this.messages = messages;
        this.zone = zone;
        this.units = units;
    }

    /**
     * A trip in words. All trips are entered by hand.
     *
     * @param listed the trip with its days, as the engine's dashboard lists it; never null
     * @return the trip with its times, distance and origin
     */
    public TripsModel.Listed map(ListedTrip listed) {
        var text = messages.text(TextKey.TRIP_AWAY, times(listed)) + " · " + messages.text(TextKey.TRIP_DISTANCE,
                messages.whole(units.distance(listed.trip().distanceKm())), units.distanceUnit());
        var origin = TripOrigin.BY_HAND;
        return new TripsModel.Listed(listed.trip(), origin, text, messages.text(origin.text()),
                messages.text(TextKey.TRIP_CHANGE_SPOKEN, text), messages.text(TextKey.TRIP_REMOVE_SPOKEN, text));
    }

    /** The times alone when the trip returns on the day it leaves; otherwise each with its day. */
    private String times(ListedTrip listed) {
        var trip = listed.trip();
        if (listed.sameDay()) {
            return messages.span(trip.departure(), trip.returnTime(), zone);
        }
        return messages.text(TextKey.TRIP_DAYS, messages.date(listed.departureDay()),
                messages.time(trip.departure(), zone), messages.date(listed.returnDay()),
                messages.time(trip.returnTime(), zone));
    }
}
