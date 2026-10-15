package io.github.enadeag.carchargeplanner.desktop;

import java.util.List;
import java.util.Optional;

/** Everything the dashboard shows, as plain text and values: no JavaFX, so the texts can be tested on their own. */
sealed interface DashboardModel {

    /** The forecast's source and freshness, for the footer. */
    String footer();

    /** A plan: one tab per day, today first, and grid advice when the owner has to act. */
    record Plan(List<DayModel> days, Optional<GridAdvice> advice, String footer) implements DashboardModel {

        public Plan {
            days = List.copyOf(days);
        }
    }

    /** No plan to show (still fetching, not set up, no forecast), and why. */
    record Message(String headline, String detail, String footer) implements DashboardModel {
    }
}
