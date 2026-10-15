package io.github.enadeag.carchargeplanner.engine;

import io.github.enadeag.carchargeplanner.engine.config.Percent;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastSource;
import io.github.enadeag.carchargeplanner.engine.plan.DayPlan;
import io.github.enadeag.carchargeplanner.engine.plan.GridAdvice;
import io.github.enadeag.carchargeplanner.engine.plan.Window;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/** What the engine tells its clients after planning: a plan, or why there is none. */
public sealed interface Outlook {

    /**
     * A plan for the coming days.
     *
     * @param madeAt when the plan was made
     * @param zone the time zone of the owner's home, in which the days and times are meant
     * @param days the plan of each day, today first
     * @param batteryPct the battery level the plan starts from
     * @param batteryAssumed true while the battery level is a placeholder, not a reading
     * @param gridAdvice present when the battery would end a day below the minimum charge level
     * @param forecast where the forecast came from
     */
    record Planned(
            Instant madeAt,
            ZoneId zone,
            List<DayPlan> days,
            Percent batteryPct,
            boolean batteryAssumed,
            Optional<GridAdvice> gridAdvice,
            ForecastSource forecast) implements Outlook {

        /** Copies the days. */
        public Planned {
            days = List.copyOf(days);
        }

        /**
         * The window the owner should look at now.
         *
         * @return the first charge window that has not ended at {@code madeAt}, or empty when there is none
         */
        public Optional<Window> nextWindow() {
            return days.stream()
                    .flatMap(day -> day.windows().stream())
                    .filter(window -> window.end().isAfter(madeAt))
                    .findFirst();
        }
    }

    /**
     * The config file is missing or wrong.
     *
     * @param reason what to fix, naming the file and the setting
     */
    record NotSetUp(String reason) implements Outlook {
    }

    /**
     * No forecast provider had a usable forecast.
     *
     * @param reason the reason of each provider asked
     */
    record NoForecast(String reason) implements Outlook {
    }
}
