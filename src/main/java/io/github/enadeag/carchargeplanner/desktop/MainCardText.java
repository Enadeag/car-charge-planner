package io.github.enadeag.carchargeplanner.desktop;

import io.github.enadeag.carchargeplanner.engine.Outlook;
import io.github.enadeag.carchargeplanner.engine.plan.DayPlan;
import io.github.enadeag.carchargeplanner.engine.plan.Window;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** What the dashboard's main card says, worked out from the engine's outlook without any JavaFX. */
public record MainCardText(String headline, String detail, List<Pill> pills) {

    /** A figure with the colour of its source: sun amber, car green, grid blue. */
    public record Pill(Source source, String text) {
    }

    public enum Source { SUN, CAR, GRID }

    /** Less grid energy than this is shown as none: it is within the forecast's precision. */
    private static final double NO_GRID_KWH = 0.05;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH);

    public static MainCardText loading() {
        return new MainCardText("Fetching the forecast…", "", List.of());
    }

    public static MainCardText of(Outlook outlook) {
        return switch (outlook) {
            case Outlook.Planned planned -> planned.nextWindow()
                    .map(window -> forWindow(planned, window))
                    .orElseGet(() -> new MainCardText("No charge window",
                            "The car needs no charging, or the coming days have too little sun for it.", List.of()));
            case Outlook.NoForecast none -> new MainCardText("No forecast", none.reason(), List.of());
            case Outlook.NotSetUp notSetUp -> new MainCardText("Not set up", notSetUp.reason(), List.of());
        };
    }

    private static MainCardText forWindow(Outlook.Planned planned, Window window) {
        var zone = planned.zone();
        var day = window.start().atZone(zone).toLocalDate();
        var headline = dayName(day, planned.madeAt().atZone(zone).toLocalDate()) + " "
                + TIME.format(window.start().atZone(zone)) + " – " + TIME.format(window.end().atZone(zone));
        var detail = format("Set the charger to %d A (%.1f kW).", window.amps(), window.powerKw());
        if (planned.batteryAssumed()) {
            detail += format(" The plan assumes the battery is at %.0f %%.", planned.batteryPct());
        }
        var dayPlan = planned.days().stream().filter(plan -> plan.date().equals(day)).findFirst().orElseThrow();
        return new MainCardText(headline, detail, List.of(
                new Pill(Source.SUN, format("%.1f kWh from the sun", window.solarKwh())),
                new Pill(Source.CAR, batteryRange(dayPlan)),
                new Pill(Source.GRID, window.importKwh() < NO_GRID_KWH
                        ? "No grid" : format("%.1f kWh from the grid", window.importKwh()))));
    }

    private static String dayName(LocalDate day, LocalDate today) {
        if (day.equals(today)) {
            return "Today";
        }
        if (day.equals(today.plusDays(1))) {
            return "Tomorrow";
        }
        return WEEKDAY.format(day);
    }

    private static String batteryRange(DayPlan day) {
        return format("%.0f → %.0f %%", day.socStartPct(), day.socAfterChargingPct());
    }

    /** One number format everywhere until the app is translated. */
    private static String format(String pattern, Object... values) {
        return String.format(Locale.ENGLISH, pattern, values);
    }
}
