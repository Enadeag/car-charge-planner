package io.github.enadeag.carchargeplanner.desktop;

import static io.github.enadeag.carchargeplanner.desktop.Format.text;

import io.github.enadeag.carchargeplanner.engine.Outlook;
import io.github.enadeag.carchargeplanner.engine.plan.GridAdvice;
import io.github.enadeag.carchargeplanner.engine.plan.Window;
import java.time.LocalDate;
import java.time.ZoneId;

/** The engine's grid advice in words, with the days named as the tabs name them. */
final class GridAdviceMapper {

    private final ZoneId zone;
    private final LocalDate today;

    GridAdviceMapper(Outlook.Planned planned) {
        this.zone = planned.zone();
        this.today = planned.madeAt().atZone(zone).toLocalDate();
    }

    GridAdviceModel map(GridAdvice advice) {
        var title = advice.window()
                .map(window -> text("Charge %.1f kWh from the grid: %s %s.", advice.energyKwh(), day(window), span(window)))
                .orElseGet(() -> text("Charge %.1f kWh from the grid before %s.", advice.energyKwh(), dipDay(advice)));
        var settings = advice.window()
                .map(window -> text(" Set %d A (%.1f kW).", window.amps(), window.powerKw()))
                .orElse(" No day before it has room for a grid charge.");
        return new GridAdviceModel(title, why(advice) + settings);
    }

    private String why(GridAdvice advice) {
        double minimum = advice.socEndPct() + advice.deficitPct();
        if (advice.socEndPct() <= 0) {
            return text("Without it the battery runs empty %s; the minimum is %.0f %%.", dipDay(advice), minimum);
        }
        return text("Without it the battery ends %s at %.0f %%, below the minimum of %.0f %%.", dipDay(advice),
                advice.socEndPct(), minimum);
    }

    private String dipDay(GridAdvice advice) {
        return Format.inSentence(advice.day(), today);
    }

    private String day(Window window) {
        return Format.inSentence(window.start().atZone(zone).toLocalDate(), today);
    }

    private String span(Window window) {
        return Format.time(window.start(), zone) + " – " + Format.time(window.end(), zone);
    }
}
