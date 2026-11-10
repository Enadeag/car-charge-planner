package nl.enadeag.carchargeplanner.android.home

import java.time.Instant
import java.time.ZoneId
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.dashboard.ChargeLevels
import nl.enadeag.carchargeplanner.engine.dashboard.ComingAway
import nl.enadeag.carchargeplanner.engine.dashboard.DashboardDay
import nl.enadeag.carchargeplanner.engine.dashboard.DashboardView
import nl.enadeag.carchargeplanner.engine.dashboard.Footer
import nl.enadeag.carchargeplanner.engine.dashboard.GridAdviceView
import nl.enadeag.carchargeplanner.engine.dashboard.ListedTrip
import nl.enadeag.carchargeplanner.engine.dashboard.StartLevel
import nl.enadeag.carchargeplanner.engine.dashboard.TripAdviceView
import nl.enadeag.carchargeplanner.engine.trip.Trip

/**
 * What the dashboard shows of a plan with days: the engine's dashboard, made once from the plan with the stored
 * one-off away periods and trips, with its days, today first, and charge levels; today's grid advice, the coming
 * one-off away periods, the trips and the footer.
 *
 * @property planned the plan the dashboard is made from, kept so a change of the trips or the away periods can make
 *     the engine's dashboard again at once
 * @property storedAway the stored one-off away periods, in order of their start, also those that ended
 * @property storedTrips the stored trips, in order of their departure
 * @property keyRefusedBy the names of the providers that refused their API key, in the order they were asked, also
 *     while another forecast is used; empty when none did
 * @throws IllegalArgumentException when [planned] has no days, which shows as no forecast instead
 */
data class Dashboard(val planned: Outlook.Planned, val storedAway: List<OneOffAwayPeriod>,
    val storedTrips: List<Trip>, val keyRefusedBy: List<String> = listOf()) {

    /** The engine's dashboard of [planned] with [storedAway] and [storedTrips]. */
    val view: DashboardView = planned.dashboard(storedAway, storedTrips)

    init {
        if (view.days().isEmpty()) {
            throw IllegalArgumentException("a dashboard has days; a plan without them shows as no forecast")
        }
    }

    /** The home's time zone, in which the days and times are meant. */
    val zone: ZoneId get() = view.zone()

    /** When the plan was made, whose day in [zone] is today. */
    val madeAt: Instant get() = view.madeAt()

    /** The plan's days, today first; never empty. */
    val days: List<DashboardDay> get() = view.days()

    /** The minimum and target charge levels, which every day's battery bar marks. */
    val levels: ChargeLevels get() = view.levels()

    /** The battery level the plan starts from, and whether it is assumed, which the trip advice tells. */
    val startLevel: StartLevel get() = view.startLevel()

    /** The plan's grid advice; null when there is none. */
    val gridAdvice: GridAdviceView? get() = view.gridAdvice().orElse(null)

    /** The one-off away periods that had not ended when the plan was made, in their stored order. */
    val away: List<ComingAway> get() = view.away()

    /** The stored trips, in their stored order. */
    val trips: List<ListedTrip> get() = view.trips()

    /** The forecast's credit, source and freshness. */
    val footer: Footer get() = view.footer()

    /**
     * The grid advice a day's tab shows, as the engine's dashboard says.
     *
     * @param day one of [days]
     * @return the grid advice on today's tab; null on any other, or when there is none
     */
    fun adviceOn(day: DashboardDay): GridAdviceView? = view.gridAdviceOn(day).orElse(null)

    /**
     * The trip advice a day's tab shows, as the engine's dashboard says.
     *
     * @param day one of [days]
     * @return the advice of the trips that leave that day, and on today's tab also of those that ask the owner to
     *     act, in the engine's order
     */
    fun tripAdviceOn(day: DashboardDay): List<TripAdviceView> = view.tripAdviceOn(day)

    /**
     * The same dashboard with the one-off away periods as they are stored now, such as after the owner added or
     * removed one: the engine's dashboard is made again from the kept plan and the stored trips.
     *
     * @param periods the stored one-off away periods, in order of their start, also those that ended
     * @return the dashboard with those periods
     */
    fun withAway(periods: List<OneOffAwayPeriod>): Dashboard = copy(storedAway = periods)

    /**
     * A one-off away period as this dashboard tells it, such as one just added or removed.
     *
     * @param period the period
     * @return it by kind, its days named from the day the plan was made
     */
    fun describe(period: OneOffAwayPeriod): ComingAway = view.describe(period)

    /**
     * The trips a day's page lists, as the engine's dashboard says: those the car is away for on that day.
     *
     * @param day one of [days]
     * @return the trips from whose day of departure to whose last day the day is, in their stored order
     */
    fun tripsOn(day: DashboardDay): List<ListedTrip> = view.tripsOn(day)

    /**
     * The same dashboard with the trips as they are stored now, such as after the owner changed or removed one: the
     * engine's dashboard is made again from the kept plan and the stored away periods, so a day the car is no longer
     * away on a trip says so at once.
     *
     * @param stored the stored trips, in order of their departure
     * @return the dashboard with those trips
     */
    fun withTrips(stored: List<Trip>): Dashboard = copy(storedTrips = stored)

    /**
     * A trip as this dashboard tells it, such as one just removed.
     *
     * @param trip the trip
     * @return it with its days, named from the day the plan was made
     */
    fun describe(trip: Trip): ListedTrip = view.describe(trip)

    companion object {

        /**
         * The dashboard of a plan.
         *
         * @param planned the engine's plan, with at least one day
         * @param away the stored one-off away periods, in order of their start, also those that ended
         * @param trips the stored trips, in order of their departure
         * @param names a provider's name by its id, for those that refused their API key; its id by default
         * @return the dashboard
         * @throws IllegalArgumentException when the plan has no days
         */
        fun of(planned: Outlook.Planned, away: List<OneOffAwayPeriod>, trips: List<Trip> = listOf(),
            names: (ProviderId) -> String = ProviderId::value): Dashboard =
            Dashboard(planned, away, trips, planned.footer().keyRefusedBy().map(names))
    }
}
