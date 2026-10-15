package io.github.enadeag.carchargeplanner.desktop;

import io.github.enadeag.carchargeplanner.engine.Outlook;

/** Turns the engine's outlook into what the dashboard shows. With the day and advice mappers, the only reader of the engine's records. */
final class OutlookMapper {

    private final String forecastSource;

    /**
     * Makes the mapper.
     *
     * @param forecastSource the forecast provider's name, shown in the footer, such as "Open-Meteo"
     */
    OutlookMapper(String forecastSource) {
        this.forecastSource = forecastSource;
    }

    /**
     * What the dashboard shows before the first plan.
     *
     * @return a message that the forecast is being fetched
     */
    DashboardModel loading() {
        return new DashboardModel.Message("Fetching the forecast…", "", footer());
    }

    /**
     * The outlook in words.
     *
     * @param outlook the engine's plan, or why there is none; never null
     * @return a plan with its days, or a message when there is no plan or it has no days
     */
    DashboardModel map(Outlook outlook) {
        return switch (outlook) {
            case Outlook.Planned planned when planned.days().isEmpty() ->
                    new DashboardModel.Message("No forecast", "The forecast has no days to plan.", footer());
            case Outlook.Planned planned -> plan(planned);
            case Outlook.NoForecast none -> new DashboardModel.Message("No forecast", none.reason(), footer());
            case Outlook.NotSetUp notSetUp -> new DashboardModel.Message("Not set up", notSetUp.reason(), footer());
        };
    }

    private DashboardModel.Plan plan(Outlook.Planned planned) {
        var days = new DayMapper(planned).map(planned.days());
        var footer = footer() + " · " + Format.time(planned.madeAt(), planned.zone());
        var advice = planned.gridAdvice().map(new GridAdviceMapper(planned)::map);
        return new DashboardModel.Plan(days, advice, footer);
    }

    private String footer() {
        return "Forecast: " + forecastSource;
    }
}
