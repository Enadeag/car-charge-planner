package nl.enadeag.carchargeplanner.engine;

import nl.enadeag.carchargeplanner.engine.config.ConfigProblem;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastSource;
import nl.enadeag.carchargeplanner.engine.plan.DayPlan;
import nl.enadeag.carchargeplanner.engine.plan.GridAdvice;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import java.nio.file.Path;
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
     * @param forecastSource where the forecast came from
     * @param houseKw the power the house uses on its own in kW, 0 or more
     * @param minimumPct the minimum charge level of the battery
     * @param targetPct the target charge level of the battery
     */
    record Planned(
            Instant madeAt,
            ZoneId zone,
            List<DayPlan> days,
            Percent batteryPct,
            boolean batteryAssumed,
            Optional<GridAdvice> gridAdvice,
            ForecastSource forecastSource,
            double houseKw,
            Percent minimumPct,
            Percent targetPct) implements Outlook {

        /**
         * Copies the days and checks the house's use.
         *
         * @throws IllegalArgumentException when the house's use is negative or not a number
         */
        public Planned {
            if (!(houseKw >= 0)) {
                throw new IllegalArgumentException("houseKw must not be negative, not " + houseKw);
            }
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
     * @param kind what is wrong with the file
     * @param file the config file's path
     * @param detail in English: the system's error, or the setting and what is wrong with it; empty for a missing file
     */
    record NotSetUp(ConfigProblem.Kind kind, Path file, String detail) implements Outlook {
    }

    /**
     * No forecast provider had a usable forecast.
     *
     * @param reason in English: the reason of each provider asked, which may quote the provider's own reply
     * @param keychainLocked true when the API keys were not available because the system keychain that holds them is
     *     locked, so the owner should unlock it
     */
    record NoForecast(String reason, boolean keychainLocked) implements Outlook {

        /**
         * No forecast, for a reason other than a locked keychain.
         *
         * @param reason in English: the reason of each provider asked, which may quote the provider's own reply
         */
        public NoForecast(String reason) {
            this(reason, false);
        }
    }
}
