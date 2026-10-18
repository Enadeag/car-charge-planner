package io.github.enadeag.carchargeplanner.desktop.dashboard;

import io.github.enadeag.carchargeplanner.desktop.dashboard.Chip.Source;
import io.github.enadeag.carchargeplanner.desktop.dashboard.SolarCurveModel.Span;
import io.github.enadeag.carchargeplanner.desktop.dashboard.SolarCurveModel.Step;
import io.github.enadeag.carchargeplanner.desktop.dashboard.SolarCurveModel.Tick;
import io.github.enadeag.carchargeplanner.engine.away.AwayPeriod;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/** Dashboard models as the mapper makes them, for the tests of the screens. */
final class Models {

    static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    /** A Friday. */
    static final LocalDate TODAY = LocalDate.of(2026, 7, 3);

    static final DayModel SUNNY_TODAY = new DayModel(TODAY, "Today", "Today", DayKind.SUN, "Charge window", "11:15 – 14:30",
            List.of("Set 10 A (2.3 kW)", "about 7.9 kWh"),
            List.of(new Chip(Source.SUN, "92 % sun"), new Chip(Source.CAR, "Battery 50 → 74 %"),
                    new Chip(Source.GRID, "0.6 kWh from the grid")),
            "The plan assumes the battery is at 50 %.", "Today: charge window 11:15 to 14:30, 10 A, 92 % sun.",
            curve(3.6, List.of(new Span(11.25, 14.5)), OptionalDouble.of(8.2)),
            new BatteryModel(50, 74, 30, 80, "50 → 74 %", "min 30", "target 80", true,
                    "Car battery 50 % now, 74 % after charging; minimum 30 %, target 80 %."));

    static final DayModel CLOUDY_TOMORROW = new DayModel(TODAY.plusDays(1), "Sat", "Tomorrow", DayKind.NONE, "No charge window", "",
            List.of(), List.of(new Chip(Source.CAR, "Battery 62 → 62 %")),
            "The car needs no charging, or there is too little sun for it.",
            "Tomorrow: no charge window. The car needs no charging, or there is too little sun for it.",
            curve(0.9, List.of(), OptionalDouble.empty()),
            new BatteryModel(62, 62, 30, 80, "62 → 62 %", "min 30", "target 80", false,
                    "Car battery 62 % at the start of tomorrow, 62 % after charging; minimum 30 %, target 80 %."));

    static final DayModel GRID_SUNDAY = new DayModel(TODAY.plusDays(2), "Sun", "Sunday", DayKind.GRID, "No good window", "01:00 – 04:00",
            List.of("Set 16 A (3.7 kW)"), List.of(new Chip(Source.GRID, "11.0 kWh from the grid")),
            "Charge from the grid to stay above the minimum.", "Sunday: grid charge 01:00 to 04:00, 16 A.",
            curve(2.0, List.of(), OptionalDouble.empty()),
            new BatteryModel(20, 20, 30, 80, "20 → 20 %", "min 30", "target 80", false,
                    "Car battery 20 % at the start of Sunday, 20 % after charging; minimum 30 %, target 80 %."));

    static final GridAdviceModel ADVICE = new GridAdviceModel("Grid advice", "Charge from the grid on Saturday night.",
            "Sunday is cloudy: charge 01:00 – 04:00 at 16 A to stay above 30 %.");

    static final String FOOTER = "Forecast: Open-Meteo · 08:12";

    static final AwayPeriod AWAY_TOMORROW = AwayPeriod.allDay(TODAY.plusDays(1), BRUSSELS);
    static final AwayPeriod AWAY_SUNDAY = AwayPeriod.on(TODAY.plusDays(2), LocalTime.of(10, 0), LocalTime.of(16, 30),
            BRUSSELS);

    /** Made at 08:12 today, with an away period tomorrow and one on Sunday. */
    static final AwayModel AWAY = new AwayModel(BRUSSELS, TODAY.atTime(8, 12).atZone(BRUSSELS).toInstant(), List.of(
            new AwayModel.Entry(AWAY_TOMORROW, "Tomorrow, all day", "Remove Tomorrow, all day"),
            new AwayModel.Entry(AWAY_SUNDAY, "Sun 5 Jul, 10:00 – 16:30", "Remove Sun 5 Jul, 10:00 – 16:30")));

    private Models() {
    }

    /** A bell of sun from 07:00 to 19:00 in hourly steps, peaking at 13:00, with the house at 0.4 kW. */
    static SolarCurveModel curve(double peakKw, List<Span> windows, OptionalDouble now) {
        var steps = new ArrayList<Step>();
        for (int hour = 7; hour < 19; hour++) {
            double kw = peakKw * Math.max(0, Math.sin(Math.PI * (hour + 0.5 - 7) / 12));
            steps.add(new Step(hour, hour + 1, kw, "%02d:00 – %.1f kW sun · 0.4 kW house".formatted(hour, kw)));
        }
        var ticks = List.of(new Tick(6, "06"), new Tick(9, "09"), new Tick(12, "12"), new Tick(15, "15"),
                new Tick(18, "18"));
        return new SolarCurveModel(6, 20, 4, "4 kW", steps, 0.4, windows, now, ticks,
                "Expected sun: peak %.1f kW around 13:00.".formatted(peakKw));
    }

    static DashboardModel.Plan plan(Optional<GridAdviceModel> advice) {
        return new DashboardModel.Plan(List.of(SUNNY_TODAY, CLOUDY_TOMORROW, GRID_SUNDAY), advice, AWAY, FOOTER);
    }
}
