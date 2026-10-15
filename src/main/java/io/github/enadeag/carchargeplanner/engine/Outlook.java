package io.github.enadeag.carchargeplanner.engine;

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
     * @param zone the time zone of the owner's home, in which the days and times are meant
     * @param batteryAssumed true while the battery level is a placeholder, not a reading
     * @param gridAdvice present when the battery would end a day below the minimum charge level
     */
    record Planned(
            Instant madeAt,
            ZoneId zone,
            List<DayPlan> days,
            double batteryPct,
            boolean batteryAssumed,
            Optional<GridAdvice> gridAdvice) implements Outlook {

        public Planned {
            days = List.copyOf(days);
        }

        /** The first charge window that has not ended yet. */
        public Optional<Window> nextWindow() {
            return days.stream()
                    .flatMap(day -> day.windows().stream())
                    .filter(window -> window.end().isAfter(madeAt))
                    .findFirst();
        }
    }

    /** The config file is missing or wrong; the reason says what to fix. */
    record NotSetUp(String reason) implements Outlook {
    }

    record NoForecast(String reason) implements Outlook {
    }
}
