package nl.enadeag.carchargeplanner.desktop.dashboard.outlook;

import nl.enadeag.carchargeplanner.desktop.dashboard.advice.GridAdviceMapper;
import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.desktop.dashboard.away.OneOffAwayMapper;
import nl.enadeag.carchargeplanner.desktop.dashboard.day.DayMapper;
import nl.enadeag.carchargeplanner.desktop.dashboard.trip.TripAdviceMapper;
import nl.enadeag.carchargeplanner.desktop.dashboard.trip.TripMapper;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.Outlook;
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastSource;
import nl.enadeag.carchargeplanner.engine.forecast.Freshness;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderRole;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Turns the engine's outlook into what the dashboard shows, with the mappers of each part of it. */
public final class OutlookMapper {

    private final Messages messages;

    /**
     * Makes the mapper; it keeps nothing between outlooks.
     *
     * @param messages the texts, in the owner's language
     */
    public OutlookMapper(Messages messages) {
        this.messages = messages;
    }

    /**
     * What the dashboard shows before the first plan.
     *
     * @return no plan yet, because the forecast is being fetched
     */
    public DashboardModel loading() {
        return new DashboardModel.NoPlan(messages.text(TextKey.LOADING), "", "");
    }

    /**
     * The outlook in words, without away periods entered for specific days or trips, as set-up's preview shows it.
     *
     * @param outlook the engine's plan, or why there is none; never null
     * @return a plan with its days, or why there is none when there is no plan or it has no days; only an outlook
     *     with a plan has a footer, also when the plan has no days, because only a plan has a forecast to credit
     */
    public DashboardModel map(Outlook outlook) {
        return map(outlook, List.of(), List.of(), Units.METRIC);
    }

    /**
     * The outlook in words, with the away periods the owner entered for specific days and the trips.
     *
     * @param outlook the engine's plan, or why there is none; never null
     * @param away the stored away periods entered for specific days, in order of their start; may be empty, and those
     *     that ended before the plan was made are left out
     * @param trips the stored trips, in order of their departure; may be empty
     * @param units the units the trips' distances are told in, in their list and their advice
     * @return a plan with its days, or why there is none when there is no plan or it has no days; only an outlook
     *     with a plan has a footer, also when the plan has no days, because only a plan has a forecast to credit
     */
    public DashboardModel map(Outlook outlook, List<OneOffAwayPeriod> away, List<Trip> trips, Units units) {
        return switch (outlook) {
            case Outlook.Planned planned when planned.days().isEmpty() -> new DashboardModel.NoPlan(
                    messages.text(TextKey.NO_FORECAST), messages.text(TextKey.NO_FORECAST_NO_DAYS), footer(planned));
            case Outlook.Planned planned -> plan(planned, away, trips, units);
            case Outlook.NoForecast none -> new DashboardModel.NoPlan(messages.text(TextKey.NO_FORECAST),
                    noForecast(none, messages), "");
            case Outlook.NotSetUp _ ->
                    new DashboardModel.NoPlan(messages.text(TextKey.NOT_SET_UP), messages.text(TextKey.NOT_SET_UP_FIRST),
                            "", true);
            case Outlook.ConfigUnusable unusable ->
                    new DashboardModel.NoPlan(messages.text(TextKey.NOT_SET_UP), whatToFix(unusable, messages), "");
        };
    }

    /**
     * What is wrong with the config file, in the owner's language; the engine's details stay in English. A missing
     * file is never unusable: {@link Outlook.ConfigUnusable} refuses it, and the engine reports it as
     * {@link Outlook.NotSetUp}, which each caller handles itself.
     *
     * @param unusable the engine's outlook with a config file that cannot be read or is not valid
     * @param messages the texts, in the owner's language
     * @return such as "Fix engine.json: system: is required"
     * @throws IllegalStateException when the outlook says the file is missing, which its constructor refuses
     */
    public static String whatToFix(Outlook.ConfigUnusable unusable, Messages messages) {
        return switch (unusable.kind()) {
            case MISSING -> throw new IllegalStateException(
                    "a missing config file is not set up, never unusable: " + unusable.file());
            case UNREADABLE -> messages.text(TextKey.NOT_SET_UP_UNREADABLE, unusable.file().toString(), unusable.detail());
            case INVALID -> messages.text(TextKey.NOT_SET_UP_INVALID, String.valueOf(unusable.file().getFileName()),
                    unusable.detail());
        };
    }

    /**
     * Why there is no forecast, in the owner's language: what to do when the keychain that holds the API keys is
     * locked, and otherwise the engine's reasons, which stay in English.
     *
     * @param none the engine's outlook without a forecast
     * @param messages the texts, in the owner's language
     * @return such as "Unlock your keychain so the app can read your API key."
     */
    static String noForecast(Outlook.NoForecast none, Messages messages) {
        return none.keychainLocked() ? messages.text(TextKey.NO_FORECAST_KEYCHAIN_LOCKED)
                : messages.text(TextKey.NO_FORECAST_DETAIL, none.reason());
    }

    private DashboardModel.Plan plan(Outlook.Planned planned, List<OneOffAwayPeriod> away, List<Trip> trips,
            Units units) {
        var days = new DayMapper(planned, messages).map(planned.days());
        var tripAdvice = new TripAdviceMapper(planned, units, messages).map(planned.tripAdvice());
        var advice = planned.gridAdvice().map(new GridAdviceMapper(planned, messages)::map);
        var coming = new OneOffAwayMapper(planned.zone(), planned.madeAt(), messages).map(away);
        var entered = new TripMapper(planned.zone(), planned.madeAt(), units, messages).map(trips);
        return new DashboardModel.Plan(days, tripAdvice, advice, coming, entered, footer(planned));
    }

    /**
     * The credit the provider's terms ask for, which provider stood in for which, and how fresh the forecast is, such
     * as "Weather data: Open-Meteo.com (CC BY 4.0) · updated 10:51". The credit is the provider's own, in English.
     */
    private String footer(Outlook.Planned planned) {
        var source = planned.forecastSource();
        var parts = new ArrayList<>(List.of(source.attribution()));
        if (source.providerRole() == ProviderRole.FALLBACK) {
            parts.add(source.primaryName().isEmpty() ? messages.text(TextKey.FOOTER_FALLBACK_UNKNOWN)
                    : messages.text(TextKey.FOOTER_FALLBACK, source.primaryName()));
        }
        var fetched = messages.time(source.fetchedAt(), planned.zone());
        parts.add(messages.text(source.freshness() == Freshness.STALE ? TextKey.FOOTER_STALE : TextKey.FOOTER_UPDATED,
                fetched));
        busyText(source, planned).ifPresent(parts::add);
        return String.join(" · ", parts);
    }

    private Optional<String> busyText(ForecastSource source, Outlook.Planned planned) {
        return source.cooldownUntil().filter(until -> until.isAfter(planned.madeAt()))
                .map(until -> messages.text(TextKey.FOOTER_BUSY, messages.time(until, planned.zone())));
    }
}
