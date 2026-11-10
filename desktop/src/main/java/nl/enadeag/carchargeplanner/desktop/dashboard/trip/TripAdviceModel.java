package nl.enadeag.carchargeplanner.desktop.dashboard.trip;

import nl.enadeag.carchargeplanner.engine.dashboard.DayName;
import nl.enadeag.carchargeplanner.engine.dashboard.EnergySource;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * What the plan does so the car can make one trip, in words: once for today's tab, with "today", "tonight" and
 * "tomorrow", and once for the other tabs, with the weekdays; and the tabs it shows on.
 *
 * @param source where the trip's charge comes from, whose colours its chip has
 * @param label the advice's name, on its chip: "Trip advice"
 * @param onToday the trip and its plan as today's tab tells them: "Trip tomorrow at 08:15, …"
 * @param onOtherDays the trip and its plan as another day's tab tells them: "Trip Saturday at 08:15, …"
 * @param level the battery level the advice starts from: "Assuming a battery level of 45%."
 * @param shownOn the days of the plan whose tabs show the advice, as the engine's dashboard says, in the plan's order
 */
public record TripAdviceModel(EnergySource source, String label, Wording onToday, Wording onOtherDays, String level,
        List<DayName> shownOn) {

    /** Keeps a copy of the days it shows on. */
    public TripAdviceModel {
        shownOn = List.copyOf(shownOn);
    }

    /**
     * The trip and its plan, with the days named one way.
     *
     * @param title the trip: "Trip tomorrow at 08:15, 150 km there and back."
     * @param plan what to charge and when: "Charge to 85%: on solar power today 12:00 – 15:00, then from the grid.
     *     Start tonight at 21:00. Set 16 A (3.7 kW), and a charge limit of 85% in the car or the charger."
     */
    public record Wording(String title, String plan) {
    }

    /**
     * The words a day's tab shows.
     *
     * @param day the day of the tab
     * @return {@link #onToday()} on today's tab, {@link #onOtherDays()} on any other that shows the advice; empty on a
     *     tab that does not show it
     */
    public Optional<Wording> on(LocalDate day) {
        return shownOn.stream().filter(name -> name.date().equals(day)).findFirst()
                .map(name -> name instanceof DayName.Today ? onToday : onOtherDays);
    }

    /**
     * The advice in one sentence, for a screen reader.
     *
     * @param wording the words of the tab it shows on
     * @return the label, then the title, the plan and the battery level
     */
    String spoken(Wording wording) {
        return label + ": " + wording.title() + " " + wording.plan() + " " + level;
    }
}
