package nl.enadeag.carchargeplanner.engine.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import nl.enadeag.carchargeplanner.engine.config.Car;
import nl.enadeag.carchargeplanner.engine.config.Charger;
import nl.enadeag.carchargeplanner.engine.config.Coordinates;
import nl.enadeag.carchargeplanner.engine.config.HomeSystem;
import nl.enadeag.carchargeplanner.engine.config.House;
import nl.enadeag.carchargeplanner.engine.config.PanelArray;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.Phases;
import nl.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import nl.enadeag.carchargeplanner.engine.plan.AwayTimes;
import nl.enadeag.carchargeplanner.engine.plan.Planner;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Why a day has no window, from what the planner really hands over. The car has a 100 kWh battery, so a percent is a
 * kWh, and uses 18 kWh per 100 km; with the 15 % trip surcharge 300 km take 62.1 %, and a trip of 300 km needs 92.1 %
 * with the minimum of 30 %. The target is 80 %.
 */
class PlannedReasonTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Amsterdam");
    private static final LocalDate THU = LocalDate.of(2026, 7, 2);
    private static final LocalDate FRI = THU.plusDays(1);
    private static final LocalDate SAT = FRI.plusDays(1);
    private static final PlanningPreferences PREFERENCES = new PlanningPreferences(new Percent(30), new Percent(80),
            0.85, 0.2, Duration.ofMinutes(45), Duration.ofMinutes(15));
    /** 8 kW from 09:00 to 17:00. */
    private static final Map<Integer, Double> SUNNY = Map.of(9, 8.0, 10, 8.0, 11, 8.0, 12, 8.0, 13, 8.0, 14, 8.0,
            15, 8.0, 16, 8.0);

    private static HomeSystem car(double kmPerDay) {
        return new HomeSystem(new Coordinates(51.0, 5.0), Optional.empty(), ZONE,
                List.of(new PanelArray("", 4.8, 35, -45, 0.14)), new House(0.6), new Car(100, 18, kmPerDay),
                new Charger(Phases.ONE, 6, 32, 230, 0.90));
    }

    private static Instant at(LocalDate day, int hour) {
        return day.atTime(hour, 0).atZone(ZONE).toInstant();
    }

    private static List<ForecastStep> days(List<Map<Integer, Double>> powerKw) {
        var steps = new ArrayList<ForecastStep>();
        for (int i = 0; i < powerKw.size(); i++) {
            var start = THU.plusDays(i).atStartOfDay(ZONE).toInstant();
            for (int hour = 0; hour < 24; hour++) {
                var from = start.plus(Duration.ofHours(hour));
                steps.add(new ForecastStep(from, from.plus(Duration.ofHours(1)), powerKw.get(i).getOrDefault(hour, 0.0)));
            }
        }
        return steps;
    }

    private static List<Optional<NoWindowReason>> reasons(HomeSystem system, List<ForecastStep> forecast,
            double levelPct, Instant now, AwayTimes away) {
        var plan = new Planner(system, PREFERENCES, away).planWithTrips(forecast, new Percent(levelPct), now,
                List.of());
        var parts = new PlanParts(now, ZONE, plan.days(), 0.6, new Percent(levelPct), false, Optional.empty(),
                plan.trips(), new Percent(30), new Percent(80), away.oneOff(), away.trips(), Parts.source(now));
        return DashboardView.of(parts).days().stream().map(DashboardDay::noWindow).toList();
    }

    private static List<Optional<NoWindowReason>> reasons(HomeSystem system, List<ForecastStep> forecast,
            double levelPct, Instant now, Trip... trips) {
        return reasons(system, forecast, levelPct, now, new AwayTimes(List.of(), List.of(), List.of(trips)));
    }

    @Test
    void aLaterDayStartsFromWhatTheDayBeforeLeftAfterItsDriving() {
        // 50 km a day take 9 %: 85 % on Thursday, 76 % on Friday, 67 % on Saturday, without sun
        var shown = reasons(car(50), days(List.of(Map.of(), Map.of(), Map.of())), 85, at(THU, 0));

        assertThat(shown).containsExactly(Optional.of(NoWindowReason.NOT_NEEDED),
                Optional.of(NoWindowReason.TOO_LITTLE_SOLAR), Optional.of(NoWindowReason.TOO_LITTLE_SOLAR));
    }

    @Test
    void aLevelShownAsTheTargetNeedsNoChargingThoughItIsJustBelowIt() {
        var shown = reasons(car(0), days(List.of(Map.of(), Map.of())), 79.6, at(THU, 0));

        assertThat(shown).containsOnly(Optional.of(NoWindowReason.NOT_NEEDED));
    }

    @Test
    void afterSunsetTodayHasTooLittleSolarPowerAndTomorrowsSunCharges() {
        var shown = reasons(car(0), days(List.of(SUNNY, SUNNY)), 60, at(THU, 21));

        assertThat(shown).containsExactly(Optional.of(NoWindowReason.TOO_LITTLE_SOLAR), Optional.empty());
    }

    @Test
    void afterSunsetTodayNeedsNoChargingAtTheTarget() {
        var shown = reasons(car(0), days(List.of(SUNNY, SUNNY)), 80, at(THU, 21));

        assertThat(shown).containsExactly(Optional.of(NoWindowReason.NOT_NEEDED), Optional.of(NoWindowReason.NOT_NEEDED));
    }

    @Test
    void aSunnyDayTheCarIsAwayForHasTooLittleSolarPowerWhileItIsHome() {
        var away = new AwayTimes(List.of(), List.of(new OneOffAwayPeriod(at(THU, 7), at(THU, 19))), List.of());

        var shown = reasons(car(0), days(List.of(SUNNY)), 50, at(THU, 0), away);

        assertThat(shown).containsExactly(Optional.of(NoWindowReason.TOO_LITTLE_SOLAR));
    }

    @Test
    void theEveningBeforeATripWithAGridChargeHasTooLittleSolarPowerThoughItStartsAboveTheTarget() {
        // no sun: the trip's grid charge starts on Friday evening; Sunday starts from the 30 % left on return
        var shown = reasons(car(0), days(List.of(Map.of(), Map.of(), Map.of(), Map.of())), 85, at(FRI, 0),
                new Trip(at(SAT, 8), at(SAT, 18), 300, false));

        assertThat(shown).containsExactly(Optional.of(NoWindowReason.TOO_LITTLE_SOLAR),
                Optional.of(NoWindowReason.DEPARTURE), Optional.of(NoWindowReason.TOO_LITTLE_SOLAR));
    }

    @Test
    void aDayThatChargesAboveTheTargetForATripHasTooLittleSolarPowerBelowWhatTheTripNeeds() {
        // Thursday's sun reaches the 92.1 % and Friday has none; Friday's 9 % of driving leaves 83.1 %, above the
        // target but below what the trip needs, which a grid charge on Friday makes up
        var trip = new Trip(at(SAT, 8), at(SAT, 18), 300, false);
        var shown = reasons(car(50), days(List.of(SUNNY, Map.of(), Map.of())), 50, at(THU, 0), trip);

        assertThat(shown).containsExactly(Optional.empty(), Optional.of(NoWindowReason.TOO_LITTLE_SOLAR),
                Optional.of(NoWindowReason.DEPARTURE));
    }

    @Test
    void aDayThatStartsAtWhatTheTripNeedsAfterTheSunCharges() {
        // without driving, Friday starts at the 92.1 % Thursday's sun charged to; on the whole percent shown that is
        // 92 %, below the 93 % the trip advice gives as its level to charge to
        var trip = new Trip(at(SAT, 8), at(SAT, 18), 300, false);
        var shown = reasons(car(0), days(List.of(SUNNY, Map.of(), Map.of())), 50, at(THU, 0), trip);

        assertThat(shown).containsExactly(Optional.empty(), Optional.of(NoWindowReason.TOO_LITTLE_SOLAR),
                Optional.of(NoWindowReason.DEPARTURE));
    }
}
