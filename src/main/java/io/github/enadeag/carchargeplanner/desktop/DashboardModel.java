package io.github.enadeag.carchargeplanner.desktop;

import java.util.List;
import java.util.Optional;

/** Everything the dashboard shows, as plain text and values: no JavaFX, so the texts can be tested on their own. */
sealed interface DashboardModel {

    /**
     * The forecast's source and freshness.
     *
     * @return such as "Forecast: Open-Meteo · 10:51"
     */
    String footer();

    /**
     * A plan: one day after the other, today first, grid advice when the owner has to act, and the away periods
     * entered for the coming days.
     *
     * @param days the days of the plan, today first; may be empty
     * @param advice the grid advice, or empty when there is none
     * @param away the away periods entered for specific days that have not ended
     * @param footer the forecast's source and freshness
     */
    record Plan(List<DayModel> days, Optional<GridAdviceModel> advice, AwayModel away, String footer)
            implements DashboardModel {

        /**
         * Keeps a copy of the days, so the plan cannot change afterwards.
         *
         * @param days the days of the plan, today first; may be empty
         * @param advice the grid advice, or empty when there is none
         * @param away the away periods entered for specific days that have not ended
         * @param footer the forecast's source and freshness
         */
        public Plan {
            days = List.copyOf(days);
        }
    }

    /**
     * No plan to show (still fetching, not set up, no forecast), and why.
     *
     * @param headline the situation in a few words, such as "No forecast"
     * @param detail the reason, such as "Could not reach Open-Meteo"; may be empty
     * @param footer the forecast's source and freshness
     * @param offersSetUp true when set-up is what the owner should do next, as before the first set-up
     */
    record Message(String headline, String detail, String footer, boolean offersSetUp) implements DashboardModel {

        /**
         * A message that leaves what to do to the owner.
         *
         * @param headline the situation in a few words, such as "No forecast"
         * @param detail the reason, such as "Could not reach Open-Meteo"; may be empty
         * @param footer the forecast's source and freshness
         */
        Message(String headline, String detail, String footer) {
            this(headline, detail, footer, false);
        }
    }
}
