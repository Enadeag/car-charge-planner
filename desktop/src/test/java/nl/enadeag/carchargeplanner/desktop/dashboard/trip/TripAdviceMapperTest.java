package nl.enadeag.carchargeplanner.desktop.dashboard.trip;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.engine.Outlook;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.dashboard.EnergySource;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastSource;
import nl.enadeag.carchargeplanner.engine.forecast.Freshness;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderRole;
import nl.enadeag.carchargeplanner.engine.plan.DayPlan;
import nl.enadeag.carchargeplanner.engine.plan.TripAdvice;
import nl.enadeag.carchargeplanner.engine.plan.TripGridCharge;
import nl.enadeag.carchargeplanner.engine.plan.TripGridCharge.Start;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TripAdviceMapperTest {

    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    /** A Friday. */
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 3);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);
    private static final Instant MADE_AT = at(TODAY, 10, 20);
    /** Tomorrow from 08:15 to 19:00, 150 km. */
    private static final Trip TRIP = new Trip(at(TOMORROW, 8, 15), at(TOMORROW, 19, 0), 150, false);
    private static final Window SOLAR = new Window(at(TODAY, 12, 0), at(TODAY, 15, 0), 8, 1.8, 5.2, 0.1);

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(BRUSSELS).toInstant();
    }

    /** A grid charge of 16 A on one phase, from a start to an hour later. */
    private static TripGridCharge grid(Instant start, Start when) {
        return new TripGridCharge(new Window(start, start.plusSeconds(3600), 16, 3.7, 0, 3.7), when);
    }

    private static TripAdvice advice(double neededPct, List<Window> solar, double solarPct,
            Optional<TripGridCharge> grid, double departurePct, double kmShort) {
        return new TripAdvice(TRIP, new Percent(neededPct), solar, new Percent(solarPct), grid,
                new Percent(departurePct), kmShort, new Percent(Math.max(0, departurePct - 52)));
    }

    /** Made at 10:20 today from a battery level of 45%, with a target of 80%, for today and the next two days. */
    private static Outlook.Planned planned(boolean assumed, TripAdvice... advice) {
        var source = new ForecastSource(new ProviderId("open-meteo"), "Open-Meteo", "Weather data", MADE_AT,
                Freshness.FRESH, ProviderRole.PRIMARY, "", Optional.empty(), List.of());
        var days = List.of(TODAY, TOMORROW, TODAY.plusDays(2)).stream()
                .map(date -> new DayPlan(date, new Percent(45), List.of(), new Percent(45), new Percent(45), 0,
                        List.of()))
                .toList();
        return new Outlook.Planned(MADE_AT, BRUSSELS, days, new Percent(45), assumed, Optional.empty(),
                List.of(advice), source, 0.4, new Percent(30), new Percent(80));
    }

    /** The advice each day's tab shows, today first. */
    private static List<List<TripAdviceModel>> byDay(Outlook.Planned planned, Language language, Units units) {
        var dashboard = planned.dashboard(List.of(), List.of());
        var mapper = new TripAdviceMapper(BRUSSELS, units, new Messages(language));
        return dashboard.days().stream().map(day -> mapper.map(dashboard, day)).toList();
    }

    /** The one advice on today's tab, for a trip that asks the owner to act. */
    private static TripAdviceModel onToday(Language language, Units units, TripAdvice advice) {
        return byDay(planned(true, advice), language, units).getFirst().getFirst();
    }

    private static TripAdviceModel onToday(TripAdvice advice) {
        return onToday(Language.ENGLISH, Units.METRIC, advice);
    }

    /** The one advice on tomorrow's tab, the day the trip leaves. */
    private static TripAdviceModel onTomorrow(TripAdvice advice) {
        return byDay(planned(true, advice), Language.ENGLISH, Units.METRIC).get(1).getFirst();
    }

    @Test
    void aTripChargedOnSolarPowerAndThenFromTheGridNamesBothPartsTheStartAndTheChargeLimitOnEachOfItsTabs() {
        var planned = planned(true,
                advice(84.2, List.of(SOLAR), 60, Optional.of(grid(at(TODAY, 21, 0), Start.PLANNED)), 84.2, 0));

        var tabs = byDay(planned, Language.ENGLISH, Units.METRIC);

        assertThat(tabs).containsExactly(
                List.of(new TripAdviceModel(EnergySource.GRID, "Trip advice",
                        "Trip tomorrow at 08:15, 150 km there and back.",
                        "Charge to 85%: on solar power today 12:00\u00A0–\u00A015:00, then from the grid. "
                                + "Start tonight at 21:00. Set 16 A (3.7 kW), and a charge limit of 85% in the car or "
                                + "the charger.",
                        "Assuming a battery level of 45%.")),
                List.of(new TripAdviceModel(EnergySource.GRID, "Trip advice",
                        "Trip Saturday at 08:15, 150 km there and back.",
                        "Charge to 85%: on solar power Friday 12:00\u00A0–\u00A015:00, then from the grid. "
                                + "Start Friday at 21:00. Set 16 A (3.7 kW), and a charge limit of 85% in the car or "
                                + "the charger.",
                        "Assuming a battery level of 45%.")),
                List.of());
    }

    @Test
    void anotherDaysTabNamesEveryDayByItsWeekdayInTheOwnersLanguage() {
        var tomorrowNight = new Window(at(TOMORROW, 6, 0), at(TOMORROW, 7, 0), 6, 1.4, 0.3, 0.2);
        var tabs = byDay(planned(true, advice(90, List.of(SOLAR, tomorrowNight), 60,
                Optional.of(grid(at(TODAY, 16, 0), Start.NOW)), 90, 0)), Language.DUTCH, Units.METRIC);

        var saturday = tabs.get(1).getFirst();
        var today = tabs.getFirst().getFirst();

        assertThat(saturday.title()).isEqualTo("Rit zaterdag om 08:15, 150 km heen en terug.");
        assertThat(saturday.plan()).startsWith("Laad tot 90%: op zonnestroom vrijdag 12:00\u00A0–\u00A015:00, "
                + "zaterdag 06:00\u00A0–\u00A007:00, daarna uit het net. Start het laden vrijdag om 16:00: ");
        assertThat(today.title()).isEqualTo("Rit morgen om 08:15, 150 km heen en terug.");
        assertThat(today.plan()).startsWith("Laad tot 90%: op zonnestroom vandaag 12:00\u00A0–\u00A015:00, "
                + "morgen 06:00\u00A0–\u00A007:00, daarna uit het net. Start het laden vandaag om 16:00: ");
    }

    @Test
    void inDutchTheAdviceSpeaksOfRitZonnestroomUitHetNetAndLaadlimiet() {
        var model = onToday(Language.DUTCH, Units.METRIC,
                advice(84.2, List.of(SOLAR), 60, Optional.of(grid(at(TODAY, 21, 0), Start.PLANNED)), 84.2, 0));

        assertThat(model.label()).isEqualTo("Advies voor de rit");
        assertThat(model.title()).isEqualTo("Rit morgen om 08:15, 150 km heen en terug.");
        assertThat(model.plan()).isEqualTo("Laad tot 85%: op zonnestroom vandaag 12:00\u00A0–\u00A015:00, "
                + "daarna uit het net. Start het laden vanavond om 21:00. Stel 16 A in (3,7 kW), en een laadlimiet van 85% in de "
                + "auto of de laadpaal.");
        assertThat(model.level()).isEqualTo("Uitgaande van een accuniveau van 45%.");
    }

    @Test
    void aTripChargedFromTheGridAloneSaysSoAndNamesTheDayOfTheStart() {
        var model = onToday(advice(90, List.of(), 45, Optional.of(grid(at(TOMORROW, 7, 0), Start.PLANNED)), 90, 0));

        assertThat(model.plan()).isEqualTo("Charge to 90% from the grid. Start tomorrow at 07:00. "
                + "Set 16 A (3.7 kW), and a charge limit of 90% in the car or the charger.");
    }

    static Stream<Arguments> startsThatCannotWait() {
        return Stream.of(
                Arguments.of(MADE_AT, Start.NOW, "Start now: little time is left before the departure."),
                Arguments.of(at(TODAY, 14, 30), Start.NOW, "Start today at 14:30: little time is left before the "
                        + "departure."),
                Arguments.of(MADE_AT, Start.NOW_OUTSIDE_CHARGING_HOURS, "Start now, although that is outside your "
                        + "charging hours: waiting for them would leave too little time."),
                Arguments.of(at(TODAY, 23, 0), Start.NOW_OUTSIDE_CHARGING_HOURS, "Start tonight at 23:00, although "
                        + "that is outside your charging hours: waiting for them would leave too little time."));
    }

    /** A start later than now is when the car is home or the charging hours begin, so it is named. */
    @ParameterizedTest
    @MethodSource("startsThatCannotWait")
    void aGridChargeThatCannotWaitHasItsOwnWording(Instant start, Start when, String sentence) {
        var model = onToday(advice(90, List.of(), 45, Optional.of(grid(start, when)), 90, 0));

        assertThat(model.plan()).isEqualTo("Charge to 90% from the grid. " + sentence
                + " Set 16 A (3.7 kW), and a charge limit of 90% in the car or the charger.");
    }

    @Test
    void aTripThatEvenAFullBatteryCannotMakeAsksForAChargingStopInTheOwnersUnits() {
        var advice = advice(100, List.of(SOLAR), 60, Optional.of(grid(at(TODAY, 21, 0), Start.PLANNED)), 100, 40.2);

        var metric = onToday(advice);
        var imperial = onToday(Language.ENGLISH, Units.IMPERIAL, advice);

        assertThat(metric.plan())
                .startsWith("Charge to 100%: on solar power today 12:00\u00A0–\u00A015:00, then from the grid.")
                .endsWith("a charge limit of 100% in the car or the charger. "
                        + "The battery is 41 km short for the trip: plan a charging stop.");
        assertThat(imperial.plan()).endsWith("The battery is 25 mi short for the trip: plan a charging stop.");
        assertThat(imperial.title()).isEqualTo("Trip tomorrow at 08:15, 93 mi there and back.");
    }

    @Test
    void aTripTheCarCannotChargeForBeforeItLeavesSaysTheLevelItLeavesWithAndHowFarItFallsShort() {
        var model = onToday(Language.DUTCH, Units.METRIC, advice(95, List.of(), 60, Optional.empty(), 60, 120));

        assertThat(model.plan()).isEqualTo("Bij vertrek staat de accu op 60%. "
                + "De accu komt 120 km tekort voor de rit: plan een laadstop.");
    }

    @Test
    void solarPowerAboveTheTargetNamesEachWindowWithItsDayOnce() {
        var later = new Window(at(TODAY, 16, 0), at(TODAY, 17, 30), 6, 1.4, 2.0, 0);
        var tomorrow = new Window(at(TOMORROW, 6, 30), at(TOMORROW, 7, 15), 6, 1.4, 0.6, 0.1);

        var model = onToday(advice(93.6, List.of(SOLAR, later, tomorrow), 94, Optional.empty(), 94, 0));

        assertThat(model.plan()).isEqualTo("Charge to 94% on solar power: today 12:00\u00A0–\u00A015:00, "
                + "16:00\u00A0–\u00A017:30, tomorrow 06:30\u00A0–\u00A007:15.");
    }

    static Stream<Arguments> tripsThatNeedNothingExtra() {
        return Stream.of(
                // the plan's own windows up to the target are enough
                Arguments.of(advice(70, List.of(SOLAR), 75, Optional.empty(), 75, 0)),
                // exactly the target is what the plan charges to anyway
                Arguments.of(advice(80, List.of(SOLAR), 80, Optional.empty(), 80, 0)),
                // above the target, but the battery holds it already
                Arguments.of(advice(88, List.of(), 91, Optional.empty(), 91, 0)));
    }

    @ParameterizedTest
    @MethodSource("tripsThatNeedNothingExtra")
    void aTripThatNeedsNothingExtraSaysSoBriefly(TripAdvice advice) {
        var model = onTomorrow(advice);

        assertThat(model.plan()).isEqualTo("The plan charges enough for it: the battery is at "
                + Math.round(advice.departureLevelPct().value()) + "% when you leave.");
    }

    @Test
    void aLevelTheOwnerEnteredIsNotCalledAssumed() {
        var planned = planned(false, advice(70, List.of(), 75, Optional.empty(), 75, 0));

        var english = byDay(planned, Language.ENGLISH, Units.METRIC).get(1);
        var dutch = byDay(planned, Language.DUTCH, Units.METRIC).get(1);

        assertThat(english.getFirst().level()).isEqualTo("Based on the battery level of 45% you entered.");
        assertThat(dutch.getFirst().level()).isEqualTo("Uitgaande van het accuniveau van 45% dat je invulde.");
    }

    @Test
    void aLevelOnlyAFractionAboveAWholePercentageIsNotRoundedUpToTheNext() {
        var model = onToday(advice(85 + 1e-9, List.of(), 45, Optional.of(grid(at(TODAY, 21, 0), Start.PLANNED)), 85,
                0));

        assertThat(model.plan()).startsWith("Charge to 85% from the grid.").contains("a charge limit of 85%");
    }

    @Test
    void aTabShowsItsTripsAdviceInOrder() {
        var sunday = new Trip(at(TODAY.plusDays(2), 9, 0), at(TODAY.plusDays(2), 12, 0), 40, true);
        var grid = Optional.of(grid(at(TODAY, 21, 0), Start.PLANNED));
        var second = new TripAdvice(sunday, new Percent(90), List.of(), new Percent(45), grid, new Percent(90), 0,
                new Percent(86));
        var planned = planned(true, advice(90, List.of(), 45, grid, 90, 0), second);

        var today = byDay(planned, Language.ENGLISH, Units.METRIC).getFirst();

        assertThat(today).extracting(TripAdviceModel::title).containsExactly(
                "Trip tomorrow at 08:15, 150 km there and back.", "Trip Sunday at 09:00, 40 km there and back.");
    }

    @Test
    void aTabShowsTheAdviceTheEnginesDashboardShowsWithItsDay() {
        var planned = planned(true, advice(70, List.of(), 75, Optional.empty(), 75, 0));

        var tabs = byDay(planned, Language.ENGLISH, Units.METRIC);

        assertThat(tabs).extracting(List::size).containsExactly(0, 1, 0);
    }

    @Test
    void theAdviceHasTheColoursOfWhereItsChargeComesFrom() {
        var asks = onToday(advice(90, List.of(), 45, Optional.of(grid(at(TODAY, 21, 0), Start.PLANNED)), 90, 0));
        var enough = onTomorrow(advice(70, List.of(), 75, Optional.empty(), 75, 0));

        assertThat(asks.source()).isEqualTo(EnergySource.GRID);
        assertThat(enough.source()).isEqualTo(EnergySource.CAR);
    }

    @Test
    void aScreenReaderReadsTheWholeAdvice() {
        var model = onTomorrow(advice(70, List.of(), 75, Optional.empty(), 75, 0));

        assertThat(model.spoken()).isEqualTo("Trip advice: Trip Saturday at 08:15, 150 km there and back. "
                + "The plan charges enough for it: the battery is at 75% when you leave. "
                + "Assuming a battery level of 45%.");
    }
}
