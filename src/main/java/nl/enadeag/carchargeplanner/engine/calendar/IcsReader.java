package nl.enadeag.carchargeplanner.engine.calendar;

import nl.enadeag.carchargeplanner.engine.config.Coordinates;
import java.io.IOException;
import java.io.StringReader;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Geo;
import net.fortuna.ical4j.model.property.RecurrenceId;

/**
 * Reads the events of a calendar in the iCalendar format (RFC 5545), as an iCal address or an {@code .ics} file gives
 * it, within the days the plan covers. ical4j reads the calendar, repeats each event by its rules and exception dates
 * and places its times in their time zone; a repeat that was moved or changed (one with a {@code RECURRENCE-ID}) takes
 * the place of the one it replaces, and a cancelled event or repeat is left out. A time without a time zone counts in
 * the home's time zone.
 */
public final class IcsReader {

    /** Apple Calendar's location field, whose value is a {@code geo:} address with the place's coordinates. */
    private static final String APPLE_LOCATION = "X-APPLE-STRUCTURED-LOCATION";

    /** Makes the reader; it keeps nothing between two calendars. */
    public IcsReader() {
    }

    /**
     * The events of a calendar.
     *
     * @param text the whole calendar, in the iCalendar format
     * @param days the days whose events to give; an event counts when any part of it falls on one of them
     * @return the occurrences of the events on those days, in order of their start, and how many events could not
     *     be read, such as one without a UID or with a repeat rule that cannot be followed
     * @throws CalendarProblem when the text is not a calendar in the iCalendar format; the message holds nothing of
     *     its events
     */
    public EventList read(String text, CoveredDays days) throws CalendarProblem {
        var calendar = parse(text);
        var window = new Period<>(days.start().atZone(days.zone()), days.end().atZone(days.zone()));
        var byUid = new LinkedHashMap<String, List<VEvent>>();
        int unreadable = 0;
        for (VEvent event : calendar.<VEvent>getComponents(Component.VEVENT)) {
            var uid = text(event, Property.UID);
            if (uid.isBlank()) {
                unreadable++;
            } else {
                byUid.computeIfAbsent(uid, _ -> new ArrayList<>()).add(event);
            }
        }
        var occurrences = new ArrayList<CalendarEvent>();
        for (var entry : byUid.entrySet()) {
            try {
                occurrences.addAll(occurrences(entry.getKey(), entry.getValue(), window, days));
            } catch (DateTimeException | IllegalArgumentException _) {
                unreadable++;
            }
        }
        occurrences.sort(Comparator.comparing((CalendarEvent event) -> start(event.time(), days.zone()))
                .thenComparing(CalendarEvent::uid));
        return new EventList(occurrences, unreadable);
    }

    private static Calendar parse(String text) throws CalendarProblem {
        try {
            return new CalendarBuilder().build(new StringReader(text));
        } catch (ParserException e) {
            // its message may quote the calendar's text
            throw new CalendarProblem(CalendarProblem.Kind.NOT_A_CALENDAR,
                    "not a calendar in the iCalendar format (line " + e.getLineNo() + ")");
        } catch (IOException _) {
            throw new CalendarProblem(CalendarProblem.Kind.NOT_A_CALENDAR, "not a calendar in the iCalendar format");
        }
    }

    /**
     * The occurrences of one event within the window: those of its repeat that no changed repeat replaces, and the
     * changed repeats themselves. ical4j's own {@code ComponentGroup} drops a changed repeat whose start was moved, as
     * it keeps only those that start at their {@code RECURRENCE-ID}, so the replacing is done here.
     */
    private static List<CalendarEvent> occurrences(String uid, List<VEvent> versions, Period<?> window,
            CoveredDays days) {
        var zone = days.zone();
        var replaced = new HashSet<Temporal>();
        var changed = new ArrayList<VEvent>();
        for (var version : versions) {
            Optional<RecurrenceId<Temporal>> recurrenceId = version.getProperty(Property.RECURRENCE_ID);
            if (recurrenceId.isPresent()) {
                replaced.add(key(recurrenceId.get().getDate(), zone));
                changed.add(version);
            }
        }
        var found = new ArrayList<CalendarEvent>();
        for (var version : versions) {
            if (isCancelled(version)) {
                continue;
            }
            Optional<RecurrenceId<Temporal>> recurrenceId = version.getProperty(Property.RECURRENCE_ID);
            for (Period<Temporal> period : version.<Temporal>calculateRecurrenceSet(window)) {
                var planned = recurrenceId.map(id -> key(id.getDate(), zone)).orElse(key(period.getStart(), zone));
                if (recurrenceId.isEmpty() && replaced.contains(planned)) {
                    continue;
                }
                var time = time(period, zone);
                if (falls(time, days)) {
                    found.add(new CalendarEvent(uid, date(planned, zone), time, location(version),
                            coordinates(version), text(version, Property.SUMMARY),
                            text(version, Property.DESCRIPTION)));
                }
            }
        }
        return found;
    }

    /** A start as one value to compare with: the day of an all-day event, or the moment of any other. */
    private static Temporal key(Temporal start, ZoneId zone) {
        return start instanceof LocalDate date ? date : instant(start, zone);
    }

    private static LocalDate date(Temporal key, ZoneId zone) {
        return key instanceof LocalDate date ? date : LocalDate.ofInstant((Instant) key, zone);
    }

    private static Instant instant(Temporal moment, ZoneId zone) {
        return switch (moment) {
            case LocalDate date -> date.atStartOfDay(zone).toInstant();
            case LocalDateTime floating -> floating.atZone(zone).toInstant();
            default -> Instant.from(moment);
        };
    }

    /** An all-day event's end is the day after its last day; one that ends on its first day lasts that day. */
    private static EventTime time(Period<Temporal> period, ZoneId zone) {
        if (period.getStart() instanceof LocalDate first) {
            var end = period.getEnd() instanceof LocalDate date ? date : first.plusDays(1);
            var last = end.isAfter(first) ? end.minus(1, ChronoUnit.DAYS) : first;
            return new EventTime.AllDay(first, last);
        }
        var start = instant(period.getStart(), zone);
        var end = instant(period.getEnd(), zone);
        return new EventTime.Timed(start, end.isBefore(start) ? start : end);
    }

    /** Whether any part of the occurrence falls on the days; one without length counts at its start. */
    private static boolean falls(EventTime time, CoveredDays days) {
        return switch (time) {
            case EventTime.AllDay allDay -> !allDay.first().isAfter(days.last())
                    && !allDay.last().isBefore(days.first());
            case EventTime.Timed timed -> timed.start().isBefore(days.end())
                    && (timed.end().isAfter(days.start()) || timed.start().equals(days.start()));
        };
    }

    private static Instant start(EventTime time, ZoneId zone) {
        return switch (time) {
            case EventTime.AllDay allDay -> allDay.first().atStartOfDay(zone).toInstant();
            case EventTime.Timed timed -> timed.start();
        };
    }

    private static boolean isCancelled(VEvent event) {
        return text(event, Property.STATUS).equalsIgnoreCase("CANCELLED");
    }

    private static Optional<String> location(VEvent event) {
        return Optional.of(text(event, Property.LOCATION)).filter(location -> !location.isEmpty());
    }

    private static Optional<Coordinates> coordinates(VEvent event) {
        Optional<Geo> geo = event.getProperty(Property.GEO);
        if (geo.isPresent()) {
            return coordinates(geo.get().getLatitude().doubleValue(), geo.get().getLongitude().doubleValue());
        }
        return appleCoordinates(text(event, APPLE_LOCATION));
    }

    /** The coordinates of a {@code geo:} address (RFC 5870), such as "geo:52.37,4.89"; anything after them is left. */
    private static Optional<Coordinates> appleCoordinates(String value) {
        if (!value.toLowerCase(Locale.ROOT).startsWith("geo:")) {
            return Optional.empty();
        }
        var parts = value.substring("geo:".length()).split("[;?]", 2)[0].split(",");
        if (parts.length < 2) {
            return Optional.empty();
        }
        try {
            return coordinates(Double.parseDouble(parts[0].strip()), Double.parseDouble(parts[1].strip()));
        } catch (NumberFormatException _) {
            return Optional.empty();
        }
    }

    private static Optional<Coordinates> coordinates(double latitude, double longitude) {
        return Coordinates.areValid(latitude, longitude) ? Optional.of(new Coordinates(latitude, longitude))
                : Optional.empty();
    }

    private static String text(VEvent event, String name) {
        return event.getProperty(name).map(Property::getValue).map(String::strip).orElse("");
    }

    /**
     * The events of a calendar on the days asked for.
     *
     * @param events the occurrences, in order of their start
     * @param unreadable how many events could not be read and are left out, 0 or more
     */
    public record EventList(List<CalendarEvent> events, int unreadable) {

        /**
         * Copies the occurrences and checks the count.
         *
         * @throws IllegalArgumentException when the count is negative
         */
        public EventList {
            events = List.copyOf(events);
            if (unreadable < 0) {
                throw new IllegalArgumentException("unreadable must be 0 or more, not " + unreadable);
            }
        }
    }
}
