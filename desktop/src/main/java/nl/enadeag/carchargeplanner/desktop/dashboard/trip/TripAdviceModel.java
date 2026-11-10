package nl.enadeag.carchargeplanner.desktop.dashboard.trip;

import nl.enadeag.carchargeplanner.engine.dashboard.EnergySource;

/**
 * What the plan does so the car can make one trip, in the words of the day's tab it shows on: with "today", "tonight"
 * and "tomorrow" on today's tab, with the weekdays on another day's.
 *
 * @param source where the trip's charge comes from, whose colours its chip has
 * @param label the advice's name, on its chip: "Trip advice"
 * @param title the trip: "Trip tomorrow at 08:15, 150 km there and back."
 * @param plan what to charge and when: "Charge to 85%: on solar power today 12:00 – 15:00, then from the grid. Start
 *     tonight at 21:00. Set 16 A (3.7 kW), and a charge limit of 85% in the car or the charger."
 * @param level the battery level the advice starts from: "Assuming a battery level of 45%."
 */
public record TripAdviceModel(EnergySource source, String label, String title, String plan, String level) {

    /**
     * The advice in one sentence, for a screen reader.
     *
     * @return the label, then the title, the plan and the battery level
     */
    String spoken() {
        return label + ": " + title + " " + plan + " " + level;
    }
}
