package nl.enadeag.carchargeplanner.desktop.dashboard.trip;

import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/** The trips the owner entered, as the dashboard's day tabs show them, in the time zone of the owner's home. */
public final class TripMapper {

    private final Messages messages;
    private final ZoneId zone;
    private final Instant madeAt;
    private final Units units;

    /**
     * Makes the mapper for one plan.
     *
     * @param zone the time zone of the owner's home, in which the days and times are told
     * @param madeAt the moment the plan was made, which decides which days are today and tomorrow
     * @param units the units the distances are told in
     * @param messages the texts, in the owner's language
     */
    public TripMapper(ZoneId zone, Instant madeAt, Units units, Messages messages) {
        this.messages = messages;
        this.zone = zone;
        this.madeAt = madeAt;
        this.units = units;
    }

    /**
     * The trips in words. All of them are entered by hand.
     *
     * @param trips the stored trips, in order of their departure; may be empty
     * @return the model with every trip, in the same order
     */
    public TripsModel map(List<Trip> trips) {
        return new TripsModel(zone, madeAt, units, trips.stream().map(this::listed).toList());
    }

    private TripsModel.Listed listed(Trip trip) {
        var text = messages.text(TextKey.TRIP_AWAY, times(trip)) + " · " + messages.text(TextKey.TRIP_DISTANCE,
                messages.whole(units.distance(trip.distanceKm())), units.distanceUnit());
        var origin = TripOrigin.BY_HAND;
        return new TripsModel.Listed(trip, day(trip.departure()), lastDay(trip), origin, text,
                messages.text(origin.text()), messages.text(TextKey.TRIP_CHANGE_SPOKEN, text),
                messages.text(TextKey.TRIP_REMOVE_SPOKEN, text));
    }

    /** The times alone when the trip returns on the day it leaves; otherwise each with its day. */
    private String times(Trip trip) {
        var first = day(trip.departure());
        var back = day(trip.returnTime());
        if (first.equals(back)) {
            return messages.span(trip.departure(), trip.returnTime(), zone);
        }
        var today = day(madeAt);
        return messages.text(TextKey.TRIP_DAYS, messages.date(first, today), messages.time(trip.departure(), zone),
                messages.date(back, today), messages.time(trip.returnTime(), zone));
    }

    /** A trip back at midnight takes no time of the day it returns on. */
    private LocalDate lastDay(Trip trip) {
        var back = trip.returnTime().atZone(zone);
        return back.toLocalTime().equals(LocalTime.MIDNIGHT) ? back.toLocalDate().minusDays(1)
                : back.toLocalDate();
    }

    private LocalDate day(Instant moment) {
        return moment.atZone(zone).toLocalDate();
    }
}
