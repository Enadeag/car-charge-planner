package io.github.enadeag.carchargeplanner.desktop;

import io.github.enadeag.carchargeplanner.engine.Outlook;
import io.github.enadeag.carchargeplanner.engine.config.ConfigProblem;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastSource;
import io.github.enadeag.carchargeplanner.engine.forecast.Freshness;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Turns the engine's outlook into what the dashboard shows. With the day and advice mappers, the only reader of the engine's records. */
final class OutlookMapper {

    private final Messages messages;

    /**
     * Makes the mapper; it keeps nothing between outlooks.
     *
     * @param messages the texts, in the owner's language
     */
    OutlookMapper(Messages messages) {
        this.messages = messages;
    }

    /**
     * What the dashboard shows before the first plan.
     *
     * @return a message that the forecast is being fetched
     */
    DashboardModel loading() {
        return new DashboardModel.Message(messages.text(TextKey.LOADING), "", "");
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
            case Outlook.Planned planned when planned.days().isEmpty() -> new DashboardModel.Message(
                    messages.text(TextKey.NO_FORECAST), messages.text(TextKey.NO_FORECAST_NO_DAYS), footer(planned));
            case Outlook.Planned planned -> plan(planned);
            case Outlook.NoForecast none -> new DashboardModel.Message(messages.text(TextKey.NO_FORECAST),
                    messages.text(TextKey.NO_FORECAST_DETAIL, none.reason()), "");
            case Outlook.NotSetUp notSetUp when notSetUp.kind() == ConfigProblem.Kind.MISSING ->
                    new DashboardModel.Message(messages.text(TextKey.NOT_SET_UP), messages.text(TextKey.NOT_SET_UP_FIRST),
                            "", true);
            case Outlook.NotSetUp notSetUp ->
                    new DashboardModel.Message(messages.text(TextKey.NOT_SET_UP), whatToFix(notSetUp, messages), "");
        };
    }

    /**
     * What is wrong with the config file, in the owner's language; the engine's details stay in English.
     *
     * @param notSetUp the engine's outlook without a usable config file
     * @param messages the texts, in the owner's language
     * @return such as "There is no config file at /home/me/.config/car-charge-planner/engine.json."
     */
    static String whatToFix(Outlook.NotSetUp notSetUp, Messages messages) {
        return switch (notSetUp.kind()) {
            case MISSING -> messages.text(TextKey.NOT_SET_UP_MISSING, notSetUp.file().toString());
            case UNREADABLE -> messages.text(TextKey.NOT_SET_UP_UNREADABLE, notSetUp.file().toString(), notSetUp.detail());
            case INVALID -> messages.text(TextKey.NOT_SET_UP_INVALID, String.valueOf(notSetUp.file().getFileName()),
                    notSetUp.detail());
        };
    }

    private DashboardModel.Plan plan(Outlook.Planned planned) {
        var days = new DayMapper(planned, messages).map(planned.days());
        var advice = planned.gridAdvice().map(new GridAdviceMapper(planned, messages)::map);
        return new DashboardModel.Plan(days, advice, footer(planned));
    }

    /**
     * The credit the provider's terms ask for, which provider stood in for which, and how fresh the forecast is, such
     * as "Weather data: Open-Meteo.com (CC BY 4.0) · updated 10:51". The credit is the provider's own, in English.
     */
    private String footer(Outlook.Planned planned) {
        var source = planned.forecast();
        var parts = new ArrayList<>(List.of(source.attribution()));
        if (source.providerRole() == ProviderRole.FALLBACK) {
            parts.add(source.primaryName().isEmpty() ? messages.text(TextKey.FOOTER_FALLBACK_UNKNOWN)
                    : messages.text(TextKey.FOOTER_FALLBACK, source.primaryName()));
        }
        var fetched = messages.time(source.fetchedAt(), planned.zone());
        parts.add(messages.text(source.freshness() == Freshness.STALE ? TextKey.FOOTER_STALE : TextKey.FOOTER_UPDATED,
                fetched));
        busyUntil(source, planned).ifPresent(parts::add);
        return String.join(" · ", parts);
    }

    private Optional<String> busyUntil(ForecastSource source, Outlook.Planned planned) {
        return source.cooldownUntil().filter(until -> until.isAfter(planned.madeAt()))
                .map(until -> messages.text(TextKey.FOOTER_BUSY, messages.time(until, planned.zone())));
    }
}
