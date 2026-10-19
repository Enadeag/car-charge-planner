package nl.enadeag.carchargeplanner.engine.plan;

import nl.enadeag.carchargeplanner.engine.config.HomeSystem;
import nl.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Finds the charge windows and their charge power in the forecast surplus. */
final class WindowFinder {

    private static final double EPS = 1e-9;

    private final HomeSystem system;
    private final PlanningPreferences preferences;
    private final Duration minWindow;

    /**
     * Makes a finder for one home and one set of preferences.
     *
     * @param system the home and equipment
     * @param preferences the planning preferences, for the minimum window length and the import tolerance
     */
    WindowFinder(HomeSystem system, PlanningPreferences preferences) {
        this.system = system;
        this.preferences = preferences;
        this.minWindow = preferences.minWindow();
    }

    /**
     * The runs from now on in which the surplus reaches the charger's minimum current and the charger is free.
     *
     * @param forecast the forecast, in any order
     * @param now the current moment; earlier time is cut off
     * @param unavailable the times in which the charger is not free
     * @return the runs in time order
     */
    List<SurplusRun> runs(List<ForecastStep> forecast, Instant now, List<BlockedTime> unavailable) {
        var runs = new ArrayList<SurplusRun>();
        var current = new ArrayList<Segment>();
        for (var step : forecast.stream().sorted(Comparator.comparing(ForecastStep::start)).toList()) {
            var start = step.start().isAfter(now) ? step.start() : now;
            if (!step.end().isAfter(start)) {
                continue;
            }
            var segment = new Segment(start, step.end(), Charging.usableSurplusKw(step, system, preferences));
            if (canCharge(segment, unavailable)) {
                if (!current.isEmpty() && !current.getLast().end().equals(segment.start())) {
                    runs.add(new SurplusRun(current));
                    current.clear();
                }
                current.add(segment);
            } else if (!current.isEmpty()) {
                runs.add(new SurplusRun(current));
                current.clear();
            }
        }
        if (!current.isEmpty()) {
            runs.add(new SurplusRun(current));
        }
        return runs;
    }

    private boolean canCharge(Segment segment, List<BlockedTime> unavailable) {
        boolean free = unavailable.stream().noneMatch(time -> time.overlaps(segment.start(), segment.end()));
        return free && Charging.ampsFor(segment.usableKw(), system.charger()) >= system.charger().minAmps();
    }

    /**
     * Groups runs by day: a run belongs to the local date it starts on, even when it goes on past midnight.
     *
     * @param runs the runs to group, such as every run in the forecast; may be empty
     * @param zone the time zone that decides the dates
     * @return the runs of each date that has one, in the order they were given
     */
    static Map<LocalDate, List<SurplusRun>> byDay(List<SurplusRun> runs, ZoneId zone) {
        var days = new LinkedHashMap<LocalDate, List<SurplusRun>>();
        for (var run : runs) {
            days.computeIfAbsent(run.start().atZone(zone).toLocalDate(), _ -> new ArrayList<>()).add(run);
        }
        return days;
    }

    /**
     * Picks the windows, one per run at most, that deliver the energy needed.
     *
     * @param runs the runs of one day
     * @param neededKwh the wall-side energy to take from the solar surplus in kWh; import within the tolerance is not
     *     counted toward it
     * @return the chosen windows in time order; they may fall short when the runs hold too little surplus
     */
    List<Window> windows(List<SurplusRun> runs, double neededKwh) {
        double remaining = neededKwh;
        var unused = new ArrayList<>(runs);
        var chosen = new ArrayList<Window>();
        while (remaining > EPS && !unused.isEmpty()) {
            SurplusRun bestRun = null;
            Window best = null;
            for (var run : unused) {
                var candidate = bestCandidate(run, remaining);
                if (candidate == null) {
                    continue;
                }
                var window = window(run, candidate, remaining);
                // the most solar energy first; on a tie the earlier window
                if (best == null || window.solarKwh() > best.solarKwh()
                        || window.solarKwh() == best.solarKwh() && window.start().isBefore(best.start())) {
                    best = window;
                    bestRun = run;
                }
            }
            if (best == null) {
                break;
            }
            chosen.add(best);
            remaining -= best.solarKwh();
            unused.remove(bestRun);
        }
        chosen.sort(Comparator.comparing(Window::start));
        return chosen;
    }

    /** Charge power and stretch of segments, first and last inclusive. */
    private record Candidate(int amps, int first, int last) {
    }

    /**
     * The values a candidate is judged on, best first: solar energy used, then least import, the earliest start, the
     * most amps.
     */
    private record Score(double solar, double imported, Instant start, int amps) implements Comparable<Score> {

        private static final Comparator<Score> ORDER = Comparator.comparingDouble(Score::solar)
                .thenComparing(Comparator.comparingDouble(Score::imported).reversed())
                .thenComparing(Comparator.comparing(Score::start).reversed())
                .thenComparingInt(Score::amps);

        @Override
        public int compareTo(Score other) {
            return ORDER.compare(this, other);
        }
    }

    private Candidate bestCandidate(SurplusRun run, double remaining) {
        var charger = system.charger();
        Candidate best = null;
        Score bestScore = null;
        for (int amps = charger.maxAmps(); amps >= charger.minAmps(); amps--) {
            double power = Charging.powerKw(amps, charger);
            for (int i = 0; i < run.size(); i++) {
                double solar = 0;
                double imported = 0;
                for (int j = i; j < run.size(); j++) {
                    var segment = run.get(j);
                    solar += Math.min(power, segment.usableKw()) * segment.hours();
                    imported += Math.max(0, power - segment.usableKw()) * segment.hours();
                    if (imported > preferences.importToleranceKwh() + EPS) {
                        break;
                    }
                    if (Duration.between(run.get(i).start(), segment.end()).compareTo(minWindow) < 0) {
                        continue;
                    }
                    var score = new Score(Charging.roundKwh(Math.min(solar, remaining)), Charging.roundKwh(imported),
                            run.get(i).start(), amps);
                    if (bestScore == null || score.compareTo(bestScore) > 0) {
                        bestScore = score;
                        best = new Candidate(amps, i, j);
                    }
                }
            }
        }
        return best;
    }

    /** The candidate as a window, cut short once it has delivered the energy needed, but never below the minimum length. */
    private Window window(SurplusRun run, Candidate candidate, double remaining) {
        double power = Charging.powerKw(candidate.amps(), system.charger());
        var segments = run.segments().subList(candidate.first(), candidate.last() + 1);
        var start = segments.getFirst().start();
        var end = segments.getLast().end();

        var trimmedEnd = end;
        double delivered = 0;
        for (var segment : segments) {
            double rate = Math.min(power, segment.usableKw());
            if (delivered + rate * segment.hours() >= remaining - EPS) {
                double hoursNeeded = rate > 0 ? (remaining - delivered) / rate : segment.hours();
                trimmedEnd = Hours.ceilToFiveMinutes(Hours.plus(segment.start(), hoursNeeded), system.timezone());
                break;
            }
            delivered += rate * segment.hours();
        }
        trimmedEnd = Hours.earliest(trimmedEnd, end);
        end = Hours.earliest(Hours.latest(trimmedEnd, start.plus(minWindow)), end);

        double solar = 0;
        double imported = 0;
        for (var segment : segments) {
            var from = Hours.latest(segment.start(), start);
            var to = Hours.earliest(segment.end(), end);
            if (!to.isAfter(from)) {
                continue;
            }
            double hours = Hours.between(from, to);
            solar += Math.min(power, segment.usableKw()) * hours;
            imported += Math.max(0, power - segment.usableKw()) * hours;
        }
        return new Window(start, end, candidate.amps(), power, solar, imported);
    }
}
