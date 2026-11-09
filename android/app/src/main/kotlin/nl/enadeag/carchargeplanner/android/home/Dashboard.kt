package nl.enadeag.carchargeplanner.android.home

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.dashboard.ChargeLevels
import nl.enadeag.carchargeplanner.engine.dashboard.DashboardDay
import nl.enadeag.carchargeplanner.engine.dashboard.DashboardView
import nl.enadeag.carchargeplanner.engine.dashboard.DayName
import nl.enadeag.carchargeplanner.engine.forecast.ForecastSource
import nl.enadeag.carchargeplanner.engine.forecast.Freshness
import nl.enadeag.carchargeplanner.engine.forecast.ProviderRole
import nl.enadeag.carchargeplanner.engine.trip.Trip

/**
 * What the dashboard shows of a plan with days: the engine's dashboard with its days, today first, and charge levels;
 * today's grid advice, the coming one-off away periods, the trips and the footer.
 *
 * @property planned the plan the dashboard is made from, kept so a change of the trips or the away periods can make
 *     the engine's dashboard again at once
 * @property view the engine's dashboard of the plan, from the stored trips as they were when it was made
 * @property gridAdvice the grid advice, shown on today's tab; null when there is none
 * @property away the one-off away periods that had not ended when the plan was made, in order of their start
 * @property footer the forecast's credit, source and freshness
 * @property keyRefusedBy the names of the providers that refused their API key, in the order they were asked, also
 *     while another forecast is used; empty when none did
 * @property trips the stored trips, in the engine's order, by their departure; each day's page lists those whose days
 *     include it
 * @property tripAdvice the plan's advice per trip, in the engine's order, by departure; each shows on the tabs
 *     [TripAdviceModel.shownOn] says
 * @throws IllegalArgumentException when [view] has no days, which shows as no forecast instead
 */
data class Dashboard(val planned: Outlook.Planned, val view: DashboardView, val gridAdvice: GridAdviceModel?,
    val away: List<ComingAway>, val footer: Footer, val keyRefusedBy: List<String> = listOf(),
    val trips: List<ListedTrip> = listOf(), val tripAdvice: List<TripAdviceModel> = listOf()) {

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

    /**
     * The grid advice a day's tab shows.
     *
     * @param day one of [days]
     * @return the grid advice on today's tab; null on any other, or when there is none
     */
    fun adviceOn(day: DashboardDay): GridAdviceModel? = gridAdvice.takeIf { day.name() is DayName.Today }

    /** The day the plan was made, in the home's time zone. */
    val today: LocalDate get() = madeAt.atZone(zone).toLocalDate()

    /**
     * The trip advice a day's tab shows.
     *
     * @param day one of [days]
     * @return the advice of the trips that leave that day, and on today's tab also of those that ask the owner to
     *     act, in the engine's order
     */
    fun tripAdviceOn(day: DashboardDay): List<TripAdviceModel> =
        tripAdvice.filter { it.shownOn(day.name().date(), today) }

    /**
     * The same dashboard with the one-off away periods as they are stored now, such as after the owner added or
     * removed one: the engine's dashboard is made again from the kept plan; the rest stays as the plan made it.
     *
     * @param periods the stored one-off away periods, in order of their start; those that ended are left out
     * @return the dashboard with those periods
     */
    fun withAway(periods: List<OneOffAwayPeriod>): Dashboard =
        copy(view = planned.dashboard(periods, trips.map { it.trip }), away = ComingAway.of(periods, zone, madeAt))

    /**
     * A one-off away period as this dashboard tells it, such as one just added or removed.
     *
     * @param period the period
     * @return it by kind, its days named from the day the plan was made
     */
    fun describe(period: OneOffAwayPeriod): ComingAway = ComingAway.describe(period, zone, madeAt)

    /**
     * The trips a day's page lists, as the desktop's: those the car is away for on that day.
     *
     * @param day one of [days]
     * @return the trips from whose day of departure to whose last day the day is, in the engine's order
     */
    fun tripsOn(day: DashboardDay): List<ListedTrip> = trips.filter { it.covers(day.name().date()) }

    /**
     * The same dashboard with the trips as they are stored now, such as after the owner changed or removed one: the
     * engine's dashboard is made again from the kept plan, so a day the car is no longer away on a trip says so at
     * once; the rest stays as the plan made it.
     *
     * @param stored the stored trips, in order of their departure
     * @return the dashboard with those trips
     */
    fun withTrips(stored: List<Trip>): Dashboard =
        copy(view = planned.dashboard(away.map { it.period }, stored), trips = ListedTrip.of(stored, zone, madeAt))

    /**
     * A trip as this dashboard tells it, such as one just removed.
     *
     * @param trip the trip
     * @return it with its days, named from the day the plan was made
     */
    fun describe(trip: Trip): ListedTrip = ListedTrip.of(trip, zone, madeAt)

    companion object {

        /**
         * The dashboard of a plan.
         *
         * @param planned the engine's plan, with at least one day
         * @param away the stored one-off away periods, in order of their start; those that ended are left out
         * @param trips the stored trips, in order of their departure
         * @param names a provider's name by its id, for those that refused their API key; its id by default
         * @return the dashboard
         * @throws IllegalArgumentException when the plan has no days
         */
        fun of(planned: Outlook.Planned, away: List<OneOffAwayPeriod>, trips: List<Trip> = listOf(),
            names: (ProviderId) -> String = ProviderId::value): Dashboard {
            val today = planned.today()
            return Dashboard(planned, planned.dashboard(away, trips),
                planned.gridAdvice().map { GridAdviceModel.of(it, planned.zone(), today) }.orElse(null),
                ComingAway.of(away, planned.zone(), planned.madeAt()), Footer.of(planned),
                planned.forecastSource().keyRefusedBy().map(names),
                ListedTrip.of(trips, planned.zone(), planned.madeAt()), TripAdviceModel.of(planned))
        }
    }
}

/**
 * The forecast behind a plan, as the desktop's footer tells it: the provider's credit, which provider stood in for
 * which, when the forecast was fetched and whether that is stale, and until when the provider is busy.
 *
 * @property credit the credit the provider's terms ask for, in its own words, which stay English
 * @property fallback whether the fallback provider stood in, and for which
 * @property fetchedAt when the forecast was fetched
 * @property stale true when a refresh was due but failed, so the plan uses this older forecast
 * @property busyUntil until when the provider asks not to be asked again; null when it is not busy
 */
data class Footer(val credit: String, val fallback: Fallback, val fetchedAt: Instant, val stale: Boolean,
    val busyUntil: Instant?) {

    /** Whether the fallback provider gave the forecast. */
    sealed interface Fallback {

        /** The primary provider gave it. */
        data object No : Fallback

        /**
         * The fallback gave it, as this primary provider had none.
         *
         * @property primary the primary provider's name
         */
        data class For(val primary: String) : Fallback

        /** The fallback gave it, for a primary provider whose name is not known. */
        data object ForUnknown : Fallback
    }

    companion object {

        /**
         * The footer of a plan.
         *
         * @param planned the plan, with its forecast's source and the moment it was made
         * @return the footer; busy only while the cool-down lasts past the moment the plan was made
         */
        fun of(planned: Outlook.Planned): Footer {
            val source: ForecastSource = planned.forecastSource()
            val fallback = when {
                source.providerRole() != ProviderRole.FALLBACK -> Fallback.No
                source.primaryName().isEmpty() -> Fallback.ForUnknown
                else -> Fallback.For(source.primaryName())
            }
            return Footer(source.attribution(), fallback, source.fetchedAt(), source.freshness() == Freshness.STALE,
                source.cooldownUntil().filter { it.isAfter(planned.madeAt()) }.orElse(null))
        }
    }
}
