package nl.enadeag.carchargeplanner.engine.forecast;

import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The rules a provider's steps must keep: the contract every forecast is checked against. */
public final class StepRules {

    /**
     * The longest time from the start of a forecast's first step to the end of its last: the providers give two to
     * four days, and the planner goes through a forecast day by day.
     */
    public static final Duration MAX_SPAN = Duration.ofDays(16);

    private StepRules() {
    }

    /**
     * What is wrong with a provider's answer, in words; empty when it keeps the contract. Each step ends after it
     * starts, the steps follow each other in order without overlap, each power is a finite number that is not
     * negative, each step falls within a trip's range of moments, from {@link Trip#EARLIEST_INSTANT} to before
     * {@link Trip#INSTANTS_END}, and the steps together span at most {@link #MAX_SPAN}. Gaps (the night) are allowed.
     *
     * @param steps a provider's answer, possibly empty
     * @return one text per problem, such as "step 3: power cannot be negative"; empty when there is none
     */
    public static List<String> problems(List<ForecastStep> steps) {
        var problems = new ArrayList<String>();
        Instant previousEnd = null;
        for (int index = 0; index < steps.size(); index++) {
            var step = steps.get(index);
            problems.addAll(problemsOf(step, previousEnd).stream().map(("step " + index + ": ")::concat).toList());
            previousEnd = step.end();
        }
        if (!steps.isEmpty()) {
            var first = steps.stream().map(ForecastStep::start).min(Comparator.naturalOrder()).orElseThrow();
            var last = steps.stream().map(ForecastStep::end).max(Comparator.naturalOrder()).orElseThrow();
            if (Duration.between(first, last).compareTo(MAX_SPAN) > 0) {
                problems.add("the steps must span at most " + MAX_SPAN.toDays() + " days");
            }
        }
        return problems;
    }

    private static List<String> problemsOf(ForecastStep step, Instant previousEnd) {
        var problems = new ArrayList<String>();
        if (step.start().isBefore(Trip.EARLIEST_INSTANT)) {
            problems.add("must start at " + Trip.EARLIEST_INSTANT + " or later");
        }
        if (!step.end().isBefore(Trip.INSTANTS_END)) {
            problems.add("must end before " + Trip.INSTANTS_END);
        }
        if (!step.end().isAfter(step.start())) {
            problems.add("must end after it starts");
        }
        if (previousEnd != null && step.start().isBefore(previousEnd)) {
            problems.add("overlaps or precedes the step before it");
        }
        if (!Double.isFinite(step.powerKw())) {
            problems.add("power must be a finite number");
        } else if (step.powerKw() < 0) {
            problems.add("power cannot be negative");
        }
        return problems;
    }

    /**
     * Refuses steps that break the contract.
     *
     * @param steps a provider's answer
     * @return the same steps, when they keep the contract
     * @throws ProviderError.BadResponse naming the first three problems, when they do not
     */
    public static List<ForecastStep> requireValid(List<ForecastStep> steps) throws ProviderError.BadResponse {
        var problems = problems(steps);
        if (!problems.isEmpty()) {
            throw new ProviderError.BadResponse(String.join("; ", problems.subList(0, Math.min(3, problems.size()))));
        }
        return steps;
    }
}
