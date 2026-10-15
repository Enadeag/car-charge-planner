package io.github.enadeag.carchargeplanner.desktop;

import io.github.enadeag.carchargeplanner.engine.Outlook;
import java.util.Optional;

/** Turns the engine's outlook into what the dashboard shows. With {@link DayMapper}, the only reader of the engine's records. */
final class OutlookMapper {

    private final String forecastSource;

    /** @param forecastSource the forecast provider's name, for the footer */
    OutlookMapper(String forecastSource) {
        this.forecastSource = forecastSource;
    }

    DashboardModel loading() {
        return new DashboardModel.Message("Fetching the forecast…", "", footer());
    }

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
        // the engine gives no grid advice yet; when it does, it is mapped here
        return new DashboardModel.Plan(days, Optional.empty(), footer);
    }

    private String footer() {
        return "Forecast: " + forecastSource;
    }
}
