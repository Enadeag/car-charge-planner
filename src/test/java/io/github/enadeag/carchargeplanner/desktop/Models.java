package io.github.enadeag.carchargeplanner.desktop;

import io.github.enadeag.carchargeplanner.desktop.Chip.Source;
import java.util.List;
import java.util.Optional;

/** Dashboard models as the mapper makes them, for the tests of the screens. */
final class Models {

    static final DayModel SUNNY_TODAY = new DayModel("Today", "Today", DayKind.SUN, "Charge window", "11:15 – 14:30",
            List.of("Set 10 A (2.3 kW)", "about 7.9 kWh"),
            List.of(new Chip(Source.SUN, "92 % sun"), new Chip(Source.CAR, "Battery 50 → 74 %"),
                    new Chip(Source.GRID, "0.6 kWh from the grid")),
            "The plan assumes the battery is at 50 %.", "Today: charge window 11:15 to 14:30, 10 A, 92 % sun.");

    static final DayModel CLOUDY_TOMORROW = new DayModel("Sat", "Tomorrow", DayKind.NONE, "No charge window", "",
            List.of(), List.of(new Chip(Source.CAR, "Battery 62 → 62 %")),
            "The car needs no charging, or there is too little sun for it.",
            "Tomorrow: no charge window. The car needs no charging, or there is too little sun for it.");

    static final DayModel GRID_SUNDAY = new DayModel("Sun", "Sunday", DayKind.GRID, "No good window", "01:00 – 04:00",
            List.of("Set 16 A (3.7 kW)"), List.of(new Chip(Source.GRID, "11.0 kWh from the grid")),
            "Charge from the grid to stay above the minimum.", "Sunday: grid charge 01:00 to 04:00, 16 A.");

    static final GridAdviceModel ADVICE = new GridAdviceModel("Charge from the grid on Saturday night.",
            "Sunday is cloudy: charge 01:00 – 04:00 at 16 A to stay above 30 %.");

    static final String FOOTER = "Forecast: Open-Meteo · 08:12";

    private Models() {
    }

    static DashboardModel.Plan plan(Optional<GridAdviceModel> advice) {
        return new DashboardModel.Plan(List.of(SUNNY_TODAY, CLOUDY_TOMORROW, GRID_SUNDAY), advice, FOOTER);
    }
}
