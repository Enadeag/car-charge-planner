package nl.enadeag.carchargeplanner.desktop.dashboard.outlook;

import nl.enadeag.carchargeplanner.desktop.dashboard.advice.GridAdviceModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.away.OneOffAwayModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.day.DayModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.trip.TripAdviceModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.trip.TripsModel;
import java.util.List;
import java.util.Optional;

/** Everything the dashboard shows, as plain text and values: no JavaFX, so the texts can be tested on their own. */
public sealed interface DashboardModel {

    /**
     * The forecast's source and freshness.
     *
     * @return such as "Weather data: Open-Meteo.com (CC BY 4.0) · updated 10:51"; empty without a plan
     */
    String footer();

    /**
     * A plan: one day after the other, today first, the advice for each trip, grid advice when the owner has to act,
     * the away periods entered for the coming days, and the trips.
     *
     * @param days the days of the plan, today first; may be empty
     * @param tripAdvice the advice for each trip the plan charges for, in order of departure; may be empty
     * @param advice the grid advice, or empty when there is none
     * @param away the away periods entered for specific days that have not ended
     * @param trips the trips the owner entered
     * @param footer the forecast's source and freshness
     */
    record Plan(List<DayModel> days, List<TripAdviceModel> tripAdvice, Optional<GridAdviceModel> advice,
            OneOffAwayModel away, TripsModel trips, String footer)
            implements DashboardModel {

        /**
         * Keeps a copy of the days and the trip advice, so the plan cannot change afterwards.
         */
        public Plan {
            days = List.copyOf(days);
            tripAdvice = List.copyOf(tripAdvice);
        }
    }

    /**
     * No plan to show (still fetching, not set up, a config file that cannot be used, no forecast), and why.
     *
     * @param headline the situation in a few words, such as "No forecast"
     * @param detail the reason, such as "Could not reach Open-Meteo"; may be empty
     * @param footer the forecast's source and freshness
     * @param offersSetUp true when set-up is what the owner should do next, as before the first set-up
     */
    record NoPlan(String headline, String detail, String footer, boolean offersSetUp) implements DashboardModel {

        /**
         * No plan, leaving what to do to the owner.
         *
         * @param headline the situation in a few words, such as "No forecast"
         * @param detail the reason, such as "Could not reach Open-Meteo"; may be empty
         * @param footer the forecast's source and freshness
         */
        NoPlan(String headline, String detail, String footer) {
            this(headline, detail, footer, false);
        }
    }
}
