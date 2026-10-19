package nl.enadeag.carchargeplanner.engine.forecast;

import nl.enadeag.carchargeplanner.engine.config.PanelArray;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/** Adding up the forecasts of several panel arrays, for a service that forecasts one array per request. */
public final class ArraySum {

    /** Asks for the forecast of one panel array. */
    @FunctionalInterface
    public interface ArrayFetch {

        /**
         * Asks for one array's forecast.
         *
         * @param array the array to ask for
         * @return its steps, keeping the contract
         * @throws ProviderError when there is no forecast for it
         */
        List<ForecastStep> fetch(PanelArray array) throws ProviderError;
    }

    private ArraySum() {
    }

    /**
     * For a service that forecasts one panel array per request: asks for each array and adds the answers up. A
     * failure for any array fails the whole forecast, because a total without one of the arrays would be wrong.
     *
     * @param system the system whose arrays are asked for, in their order
     * @param fetchOne asks for one array's forecast
     * @return the arrays' forecasts added up, as {@link #addUp} does
     * @throws ProviderError the first array's error, when one of them has no forecast
     */
    public static List<ForecastStep> sumOverArrays(SolarSite system, ArrayFetch fetchOne) throws ProviderError {
        var forecasts = new ArrayList<List<ForecastStep>>();
        for (var array : system.arrays()) {
            forecasts.add(fetchOne.fetch(array));
        }
        return addUp(forecasts);
    }

    /**
     * The forecasts of several panel arrays as one. The steps are cut wherever any array's step starts or ends, and
     * each piece gets the sum of the powers that cover it, so no energy is lost or counted twice when the arrays'
     * steps do not line up. A time no array covers stays a gap. The result covers only the time every array's
     * forecast spans, so a shorter answer for one array never shows as a drop at the end.
     *
     * @param forecasts one forecast per array, each keeping the contract
     * @return the total; the forecast itself when there is one; empty when there is none or one is empty
     */
    public static List<ForecastStep> addUp(List<List<ForecastStep>> forecasts) {
        if (forecasts.size() == 1) {
            return forecasts.getFirst();
        }
        if (forecasts.isEmpty() || forecasts.stream().anyMatch(List::isEmpty)) {
            return List.of();
        }
        var total = new ArrayList<ForecastStep>();
        Instant start = null;
        for (var end : cuts(forecasts)) {
            if (start != null) {
                piece(forecasts, start, end).ifPresent(total::add);
            }
            start = end;
        }
        return total;
    }

    private static TreeSet<Instant> cuts(List<List<ForecastStep>> forecasts) {
        var first = forecasts.stream().map(steps -> steps.getFirst().start()).max(Comparator.naturalOrder()).orElseThrow();
        var last = forecasts.stream().map(steps -> steps.getLast().end()).min(Comparator.naturalOrder()).orElseThrow();
        var cuts = new TreeSet<Instant>();
        for (var steps : forecasts) {
            for (var step : steps) {
                cuts.add(step.start());
                cuts.add(step.end());
            }
        }
        return new TreeSet<>(cuts.subSet(first, true, last, true));
    }

    private static Optional<ForecastStep> piece(List<List<ForecastStep>> forecasts, Instant start, Instant end) {
        var covering = forecasts.stream()
                .flatMap(List::stream)
                .filter(step -> !step.start().isAfter(start) && step.end().isAfter(start))
                .toList();
        if (covering.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ForecastStep(start, end, covering.stream().mapToDouble(ForecastStep::powerKw).sum()));
    }
}
