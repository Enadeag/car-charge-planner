package nl.enadeag.carchargeplanner.engine;

import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import nl.enadeag.carchargeplanner.engine.config.ConfigProblem;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.dashboard.DashboardView;
import nl.enadeag.carchargeplanner.engine.dashboard.PlanParts;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastSource;
import nl.enadeag.carchargeplanner.engine.plan.DayPlan;
import nl.enadeag.carchargeplanner.engine.plan.GridAdvice;
import nl.enadeag.carchargeplanner.engine.plan.TripAdvice;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** What the engine tells its clients after planning: a plan, or why there is none. */
public sealed interface Outlook {

    /**
     * A plan for the coming days.
     *
     * @param madeAt when the plan was made
     * @param zone the time zone of the owner's home, in which the days and times are meant
     * @param days the plan of each day, today first
     * @param batteryLevelPct the battery level the plan starts from
     * @param batteryAssumed true while the battery level is a placeholder, not a reading
     * @param gridAdvice present when the battery would end a day below the minimum charge level
     * @param tripAdvice what the plan does for each trip that departs before the forecast ends, in order of departure;
     *     possibly none
     * @param forecastSource where the forecast came from
     * @param baseloadKw the power the house uses on its own in kW, 0 or more, as an average over the whole day
     * @param minimumPct the minimum charge level of the battery
     * @param targetPct the target charge level of the battery
     */
    record Planned(
            Instant madeAt,
            ZoneId zone,
            List<DayPlan> days,
            Percent batteryLevelPct,
            boolean batteryAssumed,
            Optional<GridAdvice> gridAdvice,
            List<TripAdvice> tripAdvice,
            ForecastSource forecastSource,
            double baseloadKw,
            Percent minimumPct,
            Percent targetPct) implements Outlook {

        /**
         * Copies the days and the trip advice, and checks the baseload.
         *
         * @throws IllegalArgumentException when the baseload is negative or not a number
         */
        public Planned {
            if (!(baseloadKw >= 0)) {
                throw new IllegalArgumentException("baseloadKw must not be negative, not " + baseloadKw);
            }
            days = List.copyOf(days);
            tripAdvice = List.copyOf(tripAdvice);
        }

        /**
         * The day the plan was made, in the home's time zone.
         *
         * @return the local date of {@code madeAt} in {@code zone}
         */
        public LocalDate today() {
            return madeAt.atZone(zone).toLocalDate();
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

        /**
         * What the dashboard shows of this plan, as numbers, hours and flags; each client writes the words and draws
         * it. "Now" is {@code madeAt}.
         *
         * @param away the stored one-off away periods, in order of their start; may be empty, never null; not used
         *     yet, the later parts of #341 add them to the dashboard
         * @param trips the stored trips, in order of their departure; may be empty, never null; not used yet, the
         *     later parts of #341 add them to the dashboard
         * @return the dashboard, with a day for each of the plan's days
         * @throws NullPointerException when {@code away} or {@code trips} is null
         */
        public DashboardView dashboard(List<OneOffAwayPeriod> away, List<Trip> trips) {
            Objects.requireNonNull(away, "away");
            Objects.requireNonNull(trips, "trips");
            return DashboardView.of(new PlanParts(madeAt, zone, days, baseloadKw));
        }
    }

    /**
     * There is no config file yet: the owner has not finished set-up.
     *
     * @param file where the config file is looked for
     */
    record NotSetUp(Path file) implements Outlook {
    }

    /**
     * The config file is there, but cannot be used: it could not be read, it is not valid, or a newer version of the
     * app wrote it.
     *
     * @param kind what is wrong with the file: {@link ConfigProblem.Kind#UNREADABLE},
     *     {@link ConfigProblem.Kind#INVALID} or {@link ConfigProblem.Kind#NEWER_VERSION}
     * @param file the config file's path
     * @param detail in English: the system's error, or the setting and what is wrong with it
     */
    record ConfigUnusable(ConfigProblem.Kind kind, Path file, String detail) implements Outlook {

        /**
         * Checks the kind.
         *
         * @throws IllegalArgumentException when the kind is {@link ConfigProblem.Kind#MISSING}, which is
         *     {@link NotSetUp}
         */
        public ConfigUnusable {
            if (kind == ConfigProblem.Kind.MISSING) {
                throw new IllegalArgumentException("a missing config file is not set up, not unusable");
            }
        }
    }

    /**
     * No forecast provider had a usable forecast.
     *
     * @param reason in English: the reason of each provider asked, which may quote the provider's own reply
     * @param keychainLocked true when the API keys were not available because the system keychain that holds them is
     *     locked, so the owner should unlock it
     * @param keyRefusedBy the providers that refused their API key, in the order they were asked, so the owner can be
     *     told that only a new key helps; empty when none did, also when no provider was asked
     */
    record NoForecast(String reason, boolean keychainLocked, List<ProviderId> keyRefusedBy) implements Outlook {

        /** Copies the providers that refused their key. */
        public NoForecast {
            keyRefusedBy = List.copyOf(keyRefusedBy);
        }

        /**
         * No forecast, for a reason other than a refused API key.
         *
         * @param reason in English: the reason of each provider asked, which may quote the provider's own reply
         * @param keychainLocked true when the API keys were not available because the system keychain that holds
         *     them is locked, so the owner should unlock it
         */
        public NoForecast(String reason, boolean keychainLocked) {
            this(reason, keychainLocked, List.of());
        }

        /**
         * No forecast, for a reason other than a locked keychain or a refused API key.
         *
         * @param reason in English: the reason of each provider asked, which may quote the provider's own reply
         */
        public NoForecast(String reason) {
            this(reason, false);
        }
    }
}
