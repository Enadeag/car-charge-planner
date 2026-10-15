package io.github.enadeag.carchargeplanner.desktop;

import io.github.enadeag.carchargeplanner.engine.Outlook;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Turns the engine's outlook into what the dashboard shows. With the day and advice mappers, the only reader of the engine's records. */
final class OutlookMapper {

    /** Makes the mapper; it keeps nothing between outlooks. */
    OutlookMapper() {
        // every outlook is mapped on its own
    }

    /**
     * What the dashboard shows before the first plan.
     *
     * @return a message that the forecast is being fetched
     */
    DashboardModel loading() {
        return new DashboardModel.Message("Fetching the forecast…", "", "");
    }

    /**
     * The outlook in words.
     *
     * @param outlook the engine's plan, or why there is none; never null
     * @return a plan with its days, or a message when there is no plan or it has no days; only a plan has a footer,
     *     because only a plan has a forecast to credit
     */
    DashboardModel map(Outlook outlook) {
        return switch (outlook) {
            case Outlook.Planned planned when planned.days().isEmpty() ->
                    new DashboardModel.Message("No forecast", "The forecast has no days to plan.", footer(planned));
            case Outlook.Planned planned -> plan(planned);
            case Outlook.NoForecast none -> new DashboardModel.Message("No forecast", none.reason(), "");
            case Outlook.NotSetUp notSetUp -> new DashboardModel.Message("Not set up", notSetUp.reason(), "");
        };
    }

    private DashboardModel.Plan plan(Outlook.Planned planned) {
        var days = new DayMapper(planned).map(planned.days());
        var advice = planned.gridAdvice().map(new GridAdviceMapper(planned)::map);
        return new DashboardModel.Plan(days, advice, footer(planned));
    }

    /**
     * The credit the provider's terms ask for, which provider stood in for which, and how fresh the forecast is, such
     * as "Weather data: Open-Meteo.com (CC BY 4.0) · updated 10:51".
     */
    private static String footer(Outlook.Planned planned) {
        var source = planned.forecast();
        var parts = new ArrayList<>(List.of(source.attribution()));
        if (source.fromFallback()) {
            parts.add((source.primaryName().isEmpty() ? "the chosen provider" : source.primaryName()) + " unavailable");
        }
        var fetched = Format.time(source.fetchedAt(), planned.zone());
        parts.add(source.stale() ? "not refreshed since " + fetched : "updated " + fetched);
        busyUntil(source, planned).ifPresent(parts::add);
        return String.join(" · ", parts);
    }

    private static Optional<String> busyUntil(ForecastSource source, Outlook.Planned planned) {
        return source.cooldownUntil().filter(until -> until.isAfter(planned.madeAt()))
                .map(until -> "provider busy until " + Format.time(until, planned.zone()));
    }
}
