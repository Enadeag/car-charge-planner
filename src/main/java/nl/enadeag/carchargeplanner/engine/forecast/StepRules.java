package nl.enadeag.carchargeplanner.engine.forecast;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** The rules a provider's steps must keep: the contract every forecast is checked against. */
public final class StepRules {

    private StepRules() {
    }

    /**
     * What is wrong with a provider's answer, in words; empty when it keeps the contract. Each step ends after it
     * starts, the steps follow each other in order without overlap, and each power is a finite number that is not
     * negative. Gaps (the night) are allowed.
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
        return problems;
    }

    private static List<String> problemsOf(ForecastStep step, Instant previousEnd) {
        var problems = new ArrayList<String>();
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
