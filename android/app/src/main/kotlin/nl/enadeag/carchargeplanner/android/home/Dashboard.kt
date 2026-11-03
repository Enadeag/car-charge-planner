package nl.enadeag.carchargeplanner.android.home

import java.time.Instant
import java.time.ZoneId
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod
import nl.enadeag.carchargeplanner.engine.forecast.ForecastSource
import nl.enadeag.carchargeplanner.engine.forecast.Freshness
import nl.enadeag.carchargeplanner.engine.forecast.ProviderRole

/**
 * What the dashboard shows of a plan with days: the days, today first, today's grid advice, the coming one-off away
 * periods and the footer.
 *
 * @property zone the home's time zone, in which the days and times are meant
 * @property days the plan's days, today first; never empty
 * @property gridAdvice the grid advice, shown on today's tab; null when there is none
 * @property away the one-off away periods that had not ended when the plan was made, in order of their start
 * @property footer the forecast's credit, source and freshness
 * @throws IllegalArgumentException when [days] is empty, which shows as no forecast instead
 */
data class Dashboard(val zone: ZoneId, val days: List<DayModel>, val gridAdvice: GridAdviceModel?,
    val away: List<ComingAway>, val footer: Footer) {

    init {
        if (days.isEmpty()) {
            throw IllegalArgumentException("a dashboard has days; a plan without them shows as no forecast")
        }
    }

    /**
     * The grid advice a day's tab shows.
     *
     * @param day one of [days]
     * @return the grid advice on today's tab; null on any other, or when there is none
     */
    fun adviceOn(day: DayModel): GridAdviceModel? = gridAdvice.takeIf { day.name == DayName.Today }

    companion object {

        /**
         * The dashboard of a plan.
         *
         * @param planned the engine's plan, with at least one day
         * @param away the stored one-off away periods, in order of their start; those that ended are left out
         * @return the dashboard
         * @throws IllegalArgumentException when the plan has no days
         */
        fun of(planned: Outlook.Planned, away: List<OneOffAwayPeriod>): Dashboard {
            val today = planned.madeAt().atZone(planned.zone()).toLocalDate()
            return Dashboard(planned.zone(), DayMapper(planned).map(planned.days()),
                planned.gridAdvice().map { GridAdviceModel.of(it, planned.zone(), today) }.orElse(null),
                ComingAway.of(away, planned.zone(), planned.madeAt()), Footer.of(planned))
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
