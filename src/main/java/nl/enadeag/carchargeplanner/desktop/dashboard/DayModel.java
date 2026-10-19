package nl.enadeag.carchargeplanner.desktop.dashboard;

import java.time.LocalDate;
import java.util.List;

/**
 * One day of the plan, in words.
 *
 * @param date the day, in the owner's home
 * @param tab the day's short name: "Today", "Fri"
 * @param title the day's name: "Today", "Tomorrow" or the weekday
 * @param kind where the day's charge comes from
 * @param heading what the day holds: "Charge window", "No charge window", "Grid charge needed"
 * @param hero the charge window, such as "11:15 – 14:30"; empty when there is none
 * @param facts what to set and how much it brings: "Set 10 A (2.3 kW)", "about 7.9 kWh"; may be empty
 * @param chips the day's figures by source; may be empty
 * @param note a sentence about the day; may be empty
 * @param spoken the day's charge in one sentence: "Today: charge window 11:15 to 14:30, 10 A, 92 % sun."
 * @param curve the day's expected sun, house use and charge windows, as the solar chart draws them
 * @param battery the day's battery levels, as the battery bar draws them
 */
record DayModel(
        LocalDate date,
        String tab,
        String title,
        DayKind kind,
        String heading,
        String hero,
        List<String> facts,
        List<Chip> chips,
        String note,
        String spoken,
        SolarCurveModel curve,
        BatteryModel battery) {

    /**
     * Keeps copies of the lists, so the day cannot change afterwards.
     *
     * @param date the day, in the owner's home
     * @param tab the day's short name
     * @param title the day's name
     * @param kind where the day's charge comes from
     * @param heading what the day holds
     * @param hero the charge window; may be empty
     * @param facts what to set and how much it brings; may be empty
     * @param chips the day's figures by source; may be empty
     * @param note a sentence about the day; may be empty
     * @param spoken the day's charge in one sentence
     * @param curve the day's expected sun, house use and charge windows
     * @param battery the day's battery levels
     */
    DayModel {
        facts = List.copyOf(facts);
        chips = List.copyOf(chips);
    }
}
