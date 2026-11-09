package nl.enadeag.carchargeplanner.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.dashboard.DashboardDay;
import nl.enadeag.carchargeplanner.engine.dashboard.DashboardView;
import nl.enadeag.carchargeplanner.engine.dashboard.DayName;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastSource;
import nl.enadeag.carchargeplanner.engine.forecast.Freshness;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderRole;
import nl.enadeag.carchargeplanner.engine.forecast.SunnyDays;
import nl.enadeag.carchargeplanner.engine.plan.DayPlan;
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
                .map(date -> new DayPlan(date, new Percent(40), List.of(), new Percent(40), new Percent(40), 0,
                        SunnyDays.day(date, 3.4)))
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
        assertThat(dashboard).isEqualTo(DashboardView.of(MADE_AT, SunnyDays.TZ, planned.days(), 0.4));
        assertThat(dashboard.days().get(0).chart().houseKw()).isEqualTo(0.4);
    }

    @Test
    void theStoredAwayPeriodsAndTripsAreNeeded() {
        var planned = planned(0.4);

        assertThatThrownBy(() -> planned.dashboard(null, List.of())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> planned.dashboard(List.of(), null)).isInstanceOf(NullPointerException.class);
    }
}
