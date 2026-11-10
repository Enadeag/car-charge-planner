package nl.enadeag.carchargeplanner.engine.dashboard;

import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * What the dashboard shows of a plan, as numbers, hours and flags; each client writes the words and draws it.
 *
 * @param madeAt when the plan was made, never null; its day in {@code zone} is today
 * @param zone the home's time zone, never null, in which the days and times are meant
 * @param days the plan's days, today first, in the plan's order; may be empty
 * @param levels the minimum and target charge levels, which every day's battery bar marks
 * @param startLevel the battery level the plan starts from, never null, which the trip advice tells
 * @param gridAdvice the plan's grid advice; never null, empty when the plan gives none; its end level is below the
 *     levels' minimum
 * @param tripAdvice the plan's advice per trip, in order of departure, never null; may be empty
 * @param footer the forecast behind the plan, never null
 * @param away the stored one-off away periods that had not ended when the plan was made, in the stored order, never
 *     null; may be empty
 * @param trips every stored trip, in the stored order, never null; may be empty
 */
public record DashboardView(Instant madeAt, ZoneId zone, List<DashboardDay> days, ChargeLevels levels,
        StartLevel startLevel, Optional<GridAdviceView> gridAdvice, List<TripAdviceView> tripAdvice, Footer footer,
        List<ComingAway> away, List<ListedTrip> trips) {

    /**
     * Keeps copies of the lists, and checks the grid advice against the minimum.
     *
     * @throws IllegalArgumentException when the grid advice's end level is not below the levels' minimum
     */
    public DashboardView {
        if (gridAdvice.isPresent() && !(gridAdvice.get().endLevelPct() < levels.minimumPct().value())) {
            throw new IllegalArgumentException("the grid advice's end level must be below the minimum of "
                    + levels.minimumPct().value() + "%, not " + gridAdvice.get().endLevelPct() + "%");
        }
        days = List.copyOf(days);
        tripAdvice = List.copyOf(tripAdvice);
        away = List.copyOf(away);
        trips = List.copyOf(trips);
    }

    /**
     * The grid advice shown with a day: only with the day the plan was made, as it tells what to do from then on.
     *
     * @param day one of {@link #days()}, never null
     * @return the grid advice with the day named today; empty with any other day, or when the plan gives none
     */
    public Optional<GridAdviceView> gridAdviceOn(DashboardDay day) {
        return day.name() instanceof DayName.Today ? gridAdvice : Optional.empty();
    }

    /**
     * The trip advice shown with a day: the entry the apps read per day, which applies each advice's own rule,
     * {@link TripAdviceView#shownOn(DayName)}.
     *
     * @param day one of {@link #days()}, never null
     * @return the advice of the trips that leave that day, and with the day named today also of those that ask the
     *     owner to act, in order of departure
     */
    public List<TripAdviceView> tripAdviceOn(DashboardDay day) {
        return tripAdvice.stream().filter(advice -> advice.shownOn(day.name())).toList();
    }

    /**
     * The trips the car is away for on a day: those that {@link Trip#covers(LocalDate, ZoneId) cover} it in the home's
     * time zone.
     *
     * @param day one of {@link #days()}, never null
     * @return the trips from whose day of departure to whose last day the day is, in the stored order
     */
    public List<ListedTrip> tripsOn(DashboardDay day) {
        return trips.stream().filter(listed -> listed.trip().covers(day.name().date(), zone)).toList();
    }

    /**
     * A one-off away period by kind, with its days named from the day the plan was made, also one that ended or one
     * not stored.
     *
     * @param period the period, never null
     * @return it as {@link ComingAway#describe} gives it in the home's time zone
     */
    public ComingAway describe(OneOffAwayPeriod period) {
        return ComingAway.describe(period, zone, today());
    }

    /**
     * A trip with its days named from the day the plan was made, also one not stored.
     *
     * @param trip the trip, never null
     * @return it as {@link ListedTrip#of} gives it in the home's time zone
     */
    public ListedTrip describe(Trip trip) {
        return ListedTrip.of(trip, zone, today());
    }

    /**
     * The dashboard of a plan, from its parts. Clients use {@code Outlook.Planned.dashboard}; this is the engine's own
     * seam. Nothing reads the clock: "now" is the moment the plan was made.
     *
     * @param parts the plan's parts, never null
     * @return one dashboard day per plan day, in the same order, the battery level the plan starts from, the plan's
     *     grid advice, its advice per trip, its footer, the coming one-off away periods and the stored trips
     */
    public static DashboardView of(PlanParts parts) {
        var charts = new ChartBuilder(parts);
        var cards = new DayBuilder(parts);
        var trips = new TripAdviceBuilder(parts);
        return new DashboardView(parts.madeAt(), parts.zone(),
                parts.days().stream().map(day -> cards.day(day, charts.chart(day))).toList(),
                new ChargeLevels(parts.minimumPct(), parts.targetPct()),
                new StartLevel(parts.batteryLevelPct(), parts.batteryAssumed()), GridAdviceBuilder.advice(parts),
                parts.tripAdvice().stream().map(trips::advice).toList(),
                Footer.of(parts.forecastSource(), parts.madeAt()), ListBuilder.away(parts), ListBuilder.trips(parts));
    }

    private LocalDate today() {
        return madeAt.atZone(zone).toLocalDate();
    }
}
