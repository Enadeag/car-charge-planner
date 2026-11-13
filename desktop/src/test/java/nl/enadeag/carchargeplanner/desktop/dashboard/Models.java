package nl.enadeag.carchargeplanner.desktop.dashboard;

import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.desktop.dashboard.advice.GridAdviceModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.away.OneOffAwayModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.battery.BatteryModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.day.DayModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.outlook.DashboardModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.parts.ChipModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.solar.SolarChartModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.solar.SolarChartModel.TickLabel;
import nl.enadeag.carchargeplanner.desktop.dashboard.trip.TripAdviceModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.trip.TripOrigin;
import nl.enadeag.carchargeplanner.desktop.dashboard.trip.TripsModel;
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.dashboard.DayChart;
import nl.enadeag.carchargeplanner.engine.dashboard.DayKind;
import nl.enadeag.carchargeplanner.engine.dashboard.EnergySource;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderInfo;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/** Dashboard models as the mapper makes them, for the tests of the screens. */
public final class Models {

    public static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    /** A Friday. */
    public static final LocalDate TODAY = LocalDate.of(2026, 7, 3);

    public static final Trip TRIP_SATURDAY = new Trip(TODAY.plusDays(1).atTime(7, 30).atZone(BRUSSELS).toInstant(),
            TODAY.plusDays(1).atTime(18, 0).atZone(BRUSSELS).toInstant(), 300, false);
    public static final Trip TRIP_SUNDAY_NIGHT = new Trip(TODAY.plusDays(2).atTime(17, 0).atZone(BRUSSELS).toInstant(),
            TODAY.plusDays(3).atTime(9, 30).atZone(BRUSSELS).toInstant(), 120, true);

    /** The trip on Saturday, which Saturday lists. */
    public static final TripsModel.Listed LISTED_SATURDAY = new TripsModel.Listed(TRIP_SATURDAY, TripOrigin.BY_HAND,
            "Away 07:30 – 18:00 · 300 km there and back", "Entered by hand",
            "Change the trip Away 07:30 – 18:00 · 300 km there and back",
            "Remove the trip Away 07:30 – 18:00 · 300 km there and back");
    /** The trip from Sunday to Monday, which Sunday and Monday list. */
    public static final TripsModel.Listed LISTED_SUNDAY_NIGHT = new TripsModel.Listed(TRIP_SUNDAY_NIGHT,
            TripOrigin.BY_HAND, "Away Sun 5 Jul 17:00 – Mon 6 Jul 09:30 · 120 km there and back", "Entered by hand",
            "Change the trip Away Sun 5 Jul 17:00 – Mon 6 Jul 09:30 · 120 km there and back",
            "Remove the trip Away Sun 5 Jul 17:00 – Mon 6 Jul 09:30 · 120 km there and back");

    public static final DayModel SUNNY_TODAY = new DayModel(TODAY, "Today", "Today", DayKind.SOLAR, "Charge window", "11:15 – 14:30",
            List.of("Set 10 A (2.3 kW)", "about 7.9 kWh"),
            List.of(new ChipModel(EnergySource.SOLAR, "92 % sun"), new ChipModel(EnergySource.CAR, "Battery 50 → 74 %"),
                    new ChipModel(EnergySource.GRID, "0.6 kWh from the grid")),
            "The plan assumes the battery is at 50 %.", "Today: charge window 11:15 to 14:30, 10 A, 92 % sun.",
            curve(3.6, List.of(window(11.25, 14.5)), OptionalDouble.of(8.2)),
            new BatteryModel(50, 74, 30, 80, "50 → 74 %", "min 30", "target 80", true,
                    "Car battery 50 % now, 74 % after charging; minimum 30 %, target 80 %."),
            List.of(), Optional.empty(), List.of());

    public static final DayModel CLOUDY_TOMORROW = new DayModel(TODAY.plusDays(1), "Sat", "Tomorrow", DayKind.NONE, "No charge window", "",
            List.of(), List.of(),
            "There is too little solar power while the car is home.",
            "Tomorrow: no charge window. There is too little solar power while the car is home.",
            curve(0.9, List.of(), OptionalDouble.empty()),
            new BatteryModel(62, 62, 30, 80, "62 → 62 %", "min 30", "target 80", false,
                    "Car battery 62 % at the start of tomorrow, 62 % after charging; minimum 30 %, target 80 %."),
            List.of(), Optional.empty(), List.of(LISTED_SATURDAY));

    public static final DayModel GRID_SUNDAY = new DayModel(TODAY.plusDays(2), "Sun", "Sunday", DayKind.GRID, "No good window", "01:00 – 04:00",
            List.of("Set 16 A (3.7 kW)"), List.of(new ChipModel(EnergySource.GRID, "11.0 kWh from the grid")),
            "Charge from the grid to stay above the minimum.", "Sunday: grid charge 01:00 to 04:00, 16 A.",
            curve(2.0, List.of(), OptionalDouble.empty()),
            new BatteryModel(20, 20, 30, 80, "20 → 20 %", "min 30", "target 80", false,
                    "Car battery 20 % at the start of Sunday, 20 % after charging; minimum 30 %, target 80 %."),
            List.of(), Optional.empty(), List.of(LISTED_SUNDAY_NIGHT));

    public static final GridAdviceModel ADVICE = new GridAdviceModel("Grid advice", "Charge from the grid on Saturday night.",
            "Sunday is cloudy: charge 01:00 – 04:00 at 16 A to stay above 30 %.");

    public static final String FOOTER = "Forecast: Open-Meteo · 08:12";

    public static final OneOffAwayPeriod AWAY_TOMORROW = OneOffAwayPeriod.allDay(TODAY.plusDays(1), BRUSSELS);
    public static final OneOffAwayPeriod AWAY_SUNDAY = OneOffAwayPeriod.on(TODAY.plusDays(2), LocalTime.of(10, 0), LocalTime.of(16, 30),
            BRUSSELS);

    /** An away period tomorrow and one on Sunday. */
    public static final OneOffAwayModel AWAY = new OneOffAwayModel(BRUSSELS, List.of(
            new OneOffAwayModel.Listed(AWAY_TOMORROW, "Tomorrow, all day", "Remove Tomorrow, all day"),
            new OneOffAwayModel.Listed(AWAY_SUNDAY, "Sun 5 Jul, 10:00 – 16:30", "Remove Sun 5 Jul, 10:00 – 16:30")));

    /** In km. */
    public static final TripsModel TRIPS = new TripsModel(BRUSSELS, Units.METRIC);

    /** Saturday's trip, which needs a grid charge tonight, as today's tab tells it. */
    public static final TripAdviceModel ADVICE_SATURDAY_TODAY = new TripAdviceModel(EnergySource.GRID, "Trip advice",
            "Trip tomorrow at 07:30, 300 km there and back.",
            "Charge to 95% from the grid. Start tonight at 21:00. Set 16 A (3.7 kW), and a charge limit of "
                    + "95% in the car or the charger.",
            "Assuming a battery level of 50%.");

    /** Saturday's trip, as Saturday's tab tells it. */
    public static final TripAdviceModel ADVICE_SATURDAY = new TripAdviceModel(EnergySource.GRID, "Trip advice",
            "Trip Saturday at 07:30, 300 km there and back.",
            "Charge to 95% from the grid. Start Friday at 21:00. Set 16 A (3.7 kW), and a charge limit of "
                    + "95% in the car or the charger.",
            "Assuming a battery level of 50%.");

    /** Sunday's trip, for which the plan charges enough. */
    public static final TripAdviceModel ADVICE_SUNDAY = new TripAdviceModel(EnergySource.CAR, "Trip advice",
            "Trip Sunday at 17:00, 120 km there and back.",
            "The plan charges enough for it: the battery is at 62% when you leave.",
            "Assuming a battery level of 50%.");

    /** A clock that stays at 08:12 today, when the plan of these models was made. */
    public static final InstantSource CLOCK = InstantSource.fixed(TODAY.atTime(8, 12).atZone(BRUSSELS).toInstant());

    private Models() {
    }

    /**
     * A bell of sun from 07:00 to 19:00 in hourly steps, peaking at 13:00, with a baseload of 0.4 kW, shown from 06:00
     * to 20:00 up to 4 kW, with every third clock hour on the time axis.
     */
    public static SolarChartModel curve(double peakKw, List<DayChart.WindowBand> windows, OptionalDouble now) {
        var steps = new ArrayList<DayChart.Step>();
        var readouts = new ArrayList<String>();
        for (int hour = 7; hour < 19; hour++) {
            double kw = peakKw * Math.max(0, Math.sin(Math.PI * (hour + 0.5 - 7) / 12));
            steps.add(new DayChart.Step(hour, hour + 1, kw, at(hour)));
            readouts.add("%02d:00 – %.1f kW sun · 0.4 kW house".formatted(hour, kw));
        }
        var ticks = new ArrayList<DayChart.Tick>();
        var labels = new ArrayList<TickLabel>();
        for (int hour = 6; hour <= 20; hour++) {
            var tick = new DayChart.Tick(hour, hour, at(hour));
            ticks.add(tick);
            if (hour % 3 == 0) {
                labels.add(new TickLabel(tick, "%02d".formatted(hour)));
            }
        }
        var moment = now.isPresent()
                ? Optional.of(new DayChart.Moment(now.getAsDouble(), at(now.getAsDouble())))
                : Optional.<DayChart.Moment>empty();
        var peak = steps.stream().filter(step -> step.kw() > 0).max(Comparator.comparingDouble(DayChart.Step::kw))
                .map(step -> new DayChart.Peak(step.kw(), step.start()));
        var chart = new DayChart(6, 20, 4, steps, 0.4, windows, moment, ticks, peak);
        return new SolarChartModel(chart, "4 kW", readouts, labels,
                "Expected sun: peak %.1f kW around 13:00.".formatted(peakKw));
    }

    /** A charge window today, from and to an hour from the start of the day. */
    public static DayChart.WindowBand window(double startHour, double endHour) {
        return new DayChart.WindowBand(startHour, endHour, at(startHour), at(endHour));
    }

    /** The moment an hour after the start of today in Brussels. */
    private static Instant at(double hour) {
        return TODAY.atStartOfDay(BRUSSELS).toInstant().plus(Duration.ofSeconds(Math.round(hour * 3600)));
    }

    /** The day with this advice on its tab instead of its own. */
    public static DayModel with(DayModel day, List<TripAdviceModel> tripAdvice, Optional<GridAdviceModel> gridAdvice) {
        var withTrips = new DayModel(day.date(), day.tab(), day.title(), day.kind(), day.heading(), day.hero(),
                day.facts(), day.chips(), day.note(), day.spoken(), day.curve(), day.battery(), tripAdvice,
                Optional.empty(), day.trips());
        return gridAdvice.map(withTrips::withGridAdvice).orElse(withTrips);
    }

    /** The warning that Open-Meteo refused its API key, as the mapper words it in English. */
    public static final String OPEN_METEO_REFUSED =
            "Open-Meteo refused the API key. Check it under Forecast in Settings.";

    /** The two providers the app knows, whose ids are not their names. */
    public static final List<ProviderInfo> PROVIDERS = List.of(provider("open-meteo", "Open-Meteo"),
            provider("forecast-solar", "Forecast.Solar"));

    private static ProviderInfo provider(String id, String name) {
        return new ProviderInfo(new ProviderId(id), name, "", "https://example.org", "", "", Duration.ofMinutes(15),
                Duration.ofDays(2), Duration.ofHours(6), List.of());
    }

    /** The plan of these days, with the away periods and trips. */
    public static DashboardModel.Plan plan(DayModel... days) {
        return new DashboardModel.Plan(List.of(days), AWAY, TRIPS, FOOTER, List.of());
    }

    /** The same plan, with these warnings that providers refused their API key. */
    public static DashboardModel.Plan withKeyRefused(DashboardModel.Plan plan, String... lines) {
        return new DashboardModel.Plan(plan.days(), plan.away(), plan.trips(), plan.footer(), List.of(lines));
    }

    /** The plan of today, tomorrow and Sunday, with this grid advice on today's tab and no trip advice. */
    public static DashboardModel.Plan plan(Optional<GridAdviceModel> advice) {
        return plan(with(SUNNY_TODAY, List.of(), advice), CLOUDY_TOMORROW, GRID_SUNDAY);
    }

    /**
     * The plan of today, tomorrow and Sunday with the trip advice where the mapper puts it: Saturday's trip, which
     * asks to charge, on today's tab and Saturday's, Sunday's on Sunday's; and this grid advice on today's tab.
     */
    public static DashboardModel.Plan planWithTripAdvice(Optional<GridAdviceModel> advice) {
        return plan(with(SUNNY_TODAY, List.of(ADVICE_SATURDAY_TODAY), advice),
                with(CLOUDY_TOMORROW, List.of(ADVICE_SATURDAY), Optional.empty()),
                with(GRID_SUNDAY, List.of(ADVICE_SUNDAY), Optional.empty()));
    }
}
