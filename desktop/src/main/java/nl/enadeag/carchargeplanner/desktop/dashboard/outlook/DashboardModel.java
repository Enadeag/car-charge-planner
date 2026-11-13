package nl.enadeag.carchargeplanner.desktop.dashboard.outlook;

import nl.enadeag.carchargeplanner.desktop.dashboard.away.OneOffAwayModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.day.DayModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.trip.TripsModel;
import java.util.List;

/** Everything the dashboard shows, as plain text and values: no JavaFX, so the texts can be tested on their own. */
public sealed interface DashboardModel {

    /**
     * The forecast's source and freshness.
     *
     * @return such as "Weather data: Open-Meteo.com (CC BY 4.0) · updated 10:51"; empty without a plan
     */
    String footer();

    /**
     * The warnings that providers refused their API key, which only a new key ends.
     *
     * @return a line per provider that refused its key, in the order they were asked, such as "Open-Meteo refused the
     *     API key. Check it under Forecast in Settings."; empty when none did
     */
    List<String> keyRefused();

    /**
     * A plan: one day after the other, today first, each with the advice and the trips its tab shows, and the away
     * periods entered for the coming days.
     *
     * @param days the days of the plan, today first, each with its trip advice, grid advice and trips; may be empty
     * @param away the away periods entered for specific days that have not ended
     * @param trips what the trips are told and entered in
     * @param footer the forecast's source and freshness
     * @param keyRefused a warning per provider that refused its API key, also while a fallback or an older forecast
     *     gives the plan; may be empty
     */
    record Plan(List<DayModel> days, OneOffAwayModel away, TripsModel trips, String footer, List<String> keyRefused)
            implements DashboardModel {

        /**
         * Keeps a copy of the days and the warnings, so the plan cannot change afterwards.
         */
        public Plan {
            days = List.copyOf(days);
            keyRefused = List.copyOf(keyRefused);
        }
    }

    /**
     * No plan to show (still fetching, not set up, a settings file that cannot be used, no forecast), and why.
     *
     * @param headline the situation in a few words, such as "No forecast"
     * @param detail the reason, such as "Could not reach Open-Meteo"; may be empty
     * @param footer the forecast's source and freshness
     * @param offersSetUp true when set-up is what the owner should do next, as before the first set-up
     * @param keyRefused a warning per provider that refused its API key, shown above the headline; may be empty
     */
    record NoPlan(String headline, String detail, String footer, boolean offersSetUp, List<String> keyRefused)
            implements DashboardModel {

        /**
         * Keeps a copy of the warnings, so the model cannot change afterwards.
         */
        public NoPlan {
            keyRefused = List.copyOf(keyRefused);
        }

        /**
         * No plan, leaving what to do to the owner, and no provider that refused its key.
         *
         * @param headline the situation in a few words, such as "No forecast"
         * @param detail the reason, such as "Could not reach Open-Meteo"; may be empty
         * @param footer the forecast's source and freshness
         */
        NoPlan(String headline, String detail, String footer) {
            this(headline, detail, footer, false, List.of());
        }
    }
}
