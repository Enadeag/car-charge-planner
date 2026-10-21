package nl.enadeag.carchargeplanner.desktop.dashboard.trip;

import nl.enadeag.carchargeplanner.desktop.dashboard.parts.EnergySource;
import java.time.LocalDate;

/**
 * What the plan does so the car can make one trip, in words.
 *
 * @param departureDay the day of the trip's departure, in the owner's home
 * @param asksAction true when the owner has to do something for the trip: charge above the target charge level, charge
 *     from the grid, or plan a charging stop
 * @param source where the trip's charge comes from: the grid when it needs a grid charge or the battery is short, solar
 *     when solar power above the target is enough, the car when the plan charges enough already
 * @param label the advice's name, on its chip: "Trip advice"
 * @param title the trip: "Trip tomorrow at 08:15, 150 km there and back."
 * @param plan what to charge and when: "Charge to 85%: on solar power today 12:00 – 15:00, then from the grid.
 *     Start tonight at 21:00. Set 16 A (3.7 kW), and a charge limit of 85% in the car or the charger."
 * @param level the battery level the advice starts from: "Assuming a battery level of 45%."
 */
public record TripAdviceModel(LocalDate departureDay, boolean asksAction, EnergySource source, String label,
        String title, String plan, String level) {

    /**
     * Whether the advice belongs on a day's tab: always on the day of the departure, and on today's when the owner has
     * to do something.
     *
     * @param day the day of the tab
     * @param today the day the plan was made, in the owner's home
     * @return true when the tab of the day shows the advice
     */
    public boolean shownOn(LocalDate day, LocalDate today) {
        return day.equals(departureDay) || asksAction && day.equals(today);
    }

    /**
     * The advice in one sentence, for a screen reader.
     *
     * @return the label, then the title, the plan and the battery level
     */
    String spoken() {
        return label + ": " + title + " " + plan + " " + level;
    }
}
