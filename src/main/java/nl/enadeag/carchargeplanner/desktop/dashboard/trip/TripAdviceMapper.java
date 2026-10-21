package nl.enadeag.carchargeplanner.desktop.dashboard.trip;

import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.desktop.dashboard.parts.EnergySource;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.Outlook;
import nl.enadeag.carchargeplanner.engine.plan.TripAdvice;
import nl.enadeag.carchargeplanner.engine.plan.TripGridCharge;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The engine's advice for each trip in words: the trip, how far to charge, the solar windows and the grid charge it
 * takes, when to start the grid charge and with what charge limit, and the battery level it starts from. The level on
 * solar power alone is left out: the engine gives it at the departure, after the driving in between, so it would read
 * as less than the solar windows bring.
 */
public final class TripAdviceMapper {

    /** From this hour a start later today is "tonight". */
    private static final LocalTime EVENING = LocalTime.of(18, 0);
    /** Keeps a window's times together on one line when the advice wraps. */
    private static final char NO_BREAK = '\u00A0';
    /** A level this little above a whole percentage is the plan's rounding, not a percent more to charge. */
    private static final double LEVEL_NOISE_PCT = 1e-6;

    private final Messages messages;
    private final ZoneId zone;
    private final Instant madeAt;
    private final LocalDate today;
    private final Units units;
    private final double targetPct;
    private final String level;

    /**
     * Makes the mapper for one plan.
     *
     * @param planned the plan whose time zone, moment of making, target charge level and battery level the advice is
     *     told in
     * @param units the units the distances are told in
     * @param messages the texts, in the owner's language
     */
    public TripAdviceMapper(Outlook.Planned planned, Units units, Messages messages) {
        this.messages = messages;
        this.zone = planned.zone();
        this.madeAt = planned.madeAt();
        this.today = madeAt.atZone(zone).toLocalDate();
        this.units = units;
        this.targetPct = planned.targetPct().value();
        this.level = messages.text(planned.batteryAssumed() ? TextKey.TRIP_ADVICE_ASSUMED : TextKey.TRIP_ADVICE_ENTERED,
                messages.whole(planned.batteryLevelPct().value()));
    }

    /**
     * The advice for each trip in words.
     *
     * @param advice the engine's advice per trip, in order of departure; may be empty
     * @return one model per trip, in the same order
     */
    public List<TripAdviceModel> map(List<TripAdvice> advice) {
        return advice.stream().map(this::map).toList();
    }

    private TripAdviceModel map(TripAdvice advice) {
        boolean enough = advice.grid().isEmpty() && advice.kmShort() <= 0
                && (advice.solarWindows().isEmpty() || advice.neededPct().value() <= targetPct);
        return new TripAdviceModel(day(advice.trip().departure()), !enough, source(advice, enough),
                messages.text(TextKey.TRIP_ADVICE_LABEL), wording(advice, enough, DayNames.RELATIVE),
                wording(advice, enough, DayNames.WEEKDAYS), level);
    }

    private TripAdviceModel.Wording wording(TripAdvice advice, boolean enough, DayNames names) {
        var trip = advice.trip();
        var title = messages.text(TextKey.TRIP_ADVICE_TITLE, dayName(day(trip.departure()), names),
                messages.time(trip.departure(), zone), messages.whole(units.distance(trip.distanceKm())),
                units.distanceUnit());
        var plan = enough
                ? messages.text(TextKey.TRIP_ADVICE_ENOUGH, messages.whole(advice.departureLevelPct().value()))
                : plan(advice, names);
        return new TripAdviceModel.Wording(title, plan);
    }

    /** How the advice names a day in a sentence. */
    private enum DayNames {
        /** "today", "tonight", "tomorrow", then the weekday: on today's tab, where they mean what they say. */
        RELATIVE,
        /** Always the weekday: on another day's tab, where "tomorrow" would read as the day after that tab. */
        WEEKDAYS
    }

    private String dayName(LocalDate date, DayNames names) {
        return names == DayNames.RELATIVE ? messages.inSentence(date, today) : messages.weekday(date);
    }

    private LocalDate day(Instant moment) {
        return moment.atZone(zone).toLocalDate();
    }

    private static EnergySource source(TripAdvice advice, boolean enough) {
        if (enough) {
            return EnergySource.CAR;
        }
        return advice.grid().isEmpty() && advice.kmShort() <= 0 ? EnergySource.SOLAR : EnergySource.GRID;
    }

    /** What to charge, when to start the grid charge and what to set for it, and how far the battery falls short. */
    private String plan(TripAdvice advice, DayNames names) {
        var limit = chargeLimit(advice);
        var sentences = new ArrayList<String>();
        var solar = advice.solarWindows();
        if (advice.grid().isPresent()) {
            var grid = advice.grid().get();
            sentences.add(solar.isEmpty() ? messages.text(TextKey.TRIP_ADVICE_GRID, limit)
                    : messages.text(TextKey.TRIP_ADVICE_SOLAR_GRID, limit, windows(solar, names)));
            sentences.add(start(grid, names));
            var window = grid.window();
            sentences.add(messages.text(TextKey.ADVICE_SET, window.amps(), messages.decimal(window.powerKw()), limit));
        } else if (!solar.isEmpty()) {
            sentences.add(messages.text(TextKey.TRIP_ADVICE_SOLAR, limit, windows(solar, names)));
        } else {
            sentences.add(messages.text(TextKey.TRIP_ADVICE_LEAVES,
                    messages.whole(advice.departureLevelPct().value())));
        }
        if (advice.kmShort() > 0) {
            sentences.add(messages.text(TextKey.TRIP_ADVICE_SHORT,
                    messages.whole(Math.ceil(units.distance(advice.kmShort()))), units.distanceUnit()));
        }
        return String.join(" ", sentences);
    }

    /**
     * The level to charge to, which is also the charge limit to set: rounded up to a whole percentage, so the charge
     * stops only once the trip's energy is in.
     */
    private String chargeLimit(TripAdvice advice) {
        return messages.whole(Math.ceil(advice.neededPct().value() - LEVEL_NOISE_PCT));
    }

    private String start(TripGridCharge grid, DayNames names) {
        var when = when(grid.window().start(), names);
        return switch (grid.start()) {
            case PLANNED -> messages.text(TextKey.TRIP_ADVICE_START_PLANNED, when);
            case NOW -> messages.text(TextKey.TRIP_ADVICE_START_NOW, when);
            case NOW_OUTSIDE_CHARGING_HOURS -> messages.text(TextKey.TRIP_ADVICE_START_OUTSIDE, when);
        };
    }

    /** "now" up to the moment of the plan; otherwise the day, or tonight, with the time. */
    private String when(Instant start, DayNames names) {
        if (!start.isAfter(madeAt)) {
            return messages.text(TextKey.TRIP_ADVICE_NOW);
        }
        var local = start.atZone(zone);
        var day = names == DayNames.RELATIVE && local.toLocalDate().equals(today)
                && !local.toLocalTime().isBefore(EVENING)
                ? messages.text(TextKey.TRIP_ADVICE_TONIGHT)
                : dayName(local.toLocalDate(), names);
        return messages.text(TextKey.TRIP_ADVICE_AT, day, messages.time(start, zone));
    }

    /**
     * The windows grouped by their day, each day named once: "today 12:00 – 13:30, 14:00 – 15:00, tomorrow …" or
     * "Friday 12:00 – 13:30, …", each window's times without a break.
     */
    private String windows(List<Window> windows, DayNames names) {
        var byDay = new LinkedHashMap<LocalDate, List<String>>();
        for (var window : windows) {
            byDay.computeIfAbsent(day(window.start()), _ -> new ArrayList<>())
                    .add(messages.span(window.start(), window.end(), zone).replace(' ', NO_BREAK));
        }
        return byDay.entrySet().stream()
                .map(day -> dayName(day.getKey(), names) + " " + String.join(", ", day.getValue()))
                .collect(Collectors.joining(", "));
    }
}
