package nl.enadeag.carchargeplanner.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.dashboard.ChargeLevels;
import nl.enadeag.carchargeplanner.engine.dashboard.ComingAway;
import nl.enadeag.carchargeplanner.engine.dashboard.DashboardDay;
import nl.enadeag.carchargeplanner.engine.dashboard.DashboardView;
import nl.enadeag.carchargeplanner.engine.dashboard.DayName;
import nl.enadeag.carchargeplanner.engine.dashboard.Footer;
import nl.enadeag.carchargeplanner.engine.dashboard.NoWindowReason;
import nl.enadeag.carchargeplanner.engine.dashboard.PlanParts;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastSource;
import nl.enadeag.carchargeplanner.engine.forecast.Freshness;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderRole;
import nl.enadeag.carchargeplanner.engine.forecast.SunnyDays;
import nl.enadeag.carchargeplanner.engine.plan.DayPlan;
import nl.enadeag.carchargeplanner.engine.plan.GridAdvice;
import nl.enadeag.carchargeplanner.engine.plan.TripAdvice;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlannedDashboardTest {

    // 23:30 in Amsterdam, already the next day in UTC: today is the home's
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);
    private static final Instant MADE_AT = TODAY.atTime(23, 30).atZone(SunnyDays.TZ).toInstant();

    private static Outlook.Planned planned(double baseloadKw) {
        var days = List.of(TODAY, TODAY.plusDays(1), TODAY.plusDays(2)).stream()
                .map(date -> new DayPlan(date, new Percent(40), List.of(), new Percent(40), new Percent(80),
                        new Percent(40), 0, SunnyDays.day(date, 3.4)))
                .toList();
        var source = new ForecastSource(new ProviderId("main"), "Main", "Forecast by Main", MADE_AT, Freshness.FRESH,
                ProviderRole.PRIMARY, "", Optional.empty(), List.of());
        return new Outlook.Planned(MADE_AT, SunnyDays.TZ, days, new Percent(40), true, Optional.empty(), List.of(),
                source, baseloadKw, new Percent(20), new Percent(80));
    }

    @Test
    void thePlansDashboardHasItsDaysNamedFromTheDayItWasMadeInTheHomeWithTheirCharts() {
        var planned = planned(0.4);

        var dashboard = planned.dashboard(List.of(), List.of());

        assertThat(dashboard.days()).extracting(DashboardDay::name).containsExactly(new DayName.Today(TODAY),
                new DayName.Tomorrow(TODAY.plusDays(1)), new DayName.Other(TODAY.plusDays(2)));
        assertThat(dashboard.days().get(0).chart().baseloadKw()).isEqualTo(0.4);
    }

    @Test
    void thePlanHandsOverItsLevelsAssumptionAdviceForecastAndTheStoredAwayPeriodsAndTrips() {
        var leaving = new Trip(TODAY.plusDays(1).atTime(7, 0).atZone(SunnyDays.TZ).toInstant(),
                TODAY.plusDays(2).atTime(20, 0).atZone(SunnyDays.TZ).toInstant(), 300, false);
        var advice = new TripAdvice(leaving, new Percent(60), List.of(), new Percent(60), Optional.empty(),
                new Percent(60), 0, new Percent(20));
        var grid = new GridAdvice(TODAY, 10, 10, 3, Optional.empty(), Optional.empty());
        var plain = planned(0.4);
        var planned = new Outlook.Planned(MADE_AT, SunnyDays.TZ, plain.days(), new Percent(40), true,
                Optional.of(grid), List.of(advice), plain.forecastSource(), 0.4, new Percent(20), new Percent(80));

        var away = new OneOffAwayPeriod(TODAY.plusDays(2).atTime(9, 0).atZone(SunnyDays.TZ).toInstant(),
                TODAY.plusDays(2).atTime(12, 0).atZone(SunnyDays.TZ).toInstant());

        var dashboard = planned.dashboard(List.of(away), List.of(leaving));

        assertThat(dashboard).isEqualTo(DashboardView.of(new PlanParts(MADE_AT, SunnyDays.TZ, planned.days(), 0.4,
                new Percent(40), true, Optional.of(grid), List.of(advice), new Percent(20), new Percent(80),
                List.of(away), List.of(leaving), planned.forecastSource())));
        assertThat(dashboard.away()).extracting(ComingAway::period).containsExactly(away);
        assertThat(dashboard.footer()).isEqualTo(Footer.of(planned.forecastSource(), MADE_AT));
        assertThat(dashboard.days()).extracting(DashboardDay::noWindow).containsExactly(
                Optional.of(NoWindowReason.GRID), Optional.of(NoWindowReason.DEPARTURE),
                Optional.of(NoWindowReason.AWAY));
        assertThat(dashboard.days().get(0).assumedLevel()).contains(new Percent(40));
        assertThat(dashboard.levels()).isEqualTo(new ChargeLevels(new Percent(20), new Percent(80)));
        assertThat(dashboard.madeAt()).isEqualTo(MADE_AT);
        assertThat(dashboard.zone()).isEqualTo(SunnyDays.TZ);
    }

    @Test
    void aPlanWithoutDaysHasTheFooterItsDashboardHas() {
        var busy = new ForecastSource(new ProviderId("main"), "Main", "Forecast by Main", MADE_AT, Freshness.STALE,
                ProviderRole.FALLBACK, "Other", Optional.of(MADE_AT.plusSeconds(60)), List.of(new ProviderId("other")));
        var withDays = planned(0.4);
        var planned = new Outlook.Planned(MADE_AT, SunnyDays.TZ, withDays.days(), new Percent(40), true,
                Optional.empty(), List.of(), busy, 0.4, new Percent(20), new Percent(80));
        var withoutDays = new Outlook.Planned(MADE_AT, SunnyDays.TZ, List.of(), new Percent(40), true,
                Optional.empty(), List.of(), busy, 0.4, new Percent(20), new Percent(80));

        assertThat(withoutDays.footer()).isEqualTo(Footer.of(busy, MADE_AT));
        assertThat(withoutDays.footer()).isEqualTo(planned.dashboard(List.of(), List.of()).footer());
    }

    @Test
    void theStoredAwayPeriodsAndTripsAreNeeded() {
        var planned = planned(0.4);

        assertThatThrownBy(() -> planned.dashboard(null, List.of())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> planned.dashboard(List.of(), null)).isInstanceOf(NullPointerException.class);
    }
}
