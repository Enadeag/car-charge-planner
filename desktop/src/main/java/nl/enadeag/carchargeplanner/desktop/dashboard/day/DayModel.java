package nl.enadeag.carchargeplanner.desktop.dashboard.day;

import nl.enadeag.carchargeplanner.desktop.dashboard.advice.GridAdviceModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.battery.BatteryModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.parts.ChipModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.solar.SolarChartModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.trip.TripAdviceModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.trip.TripsModel;
import nl.enadeag.carchargeplanner.engine.dashboard.DayKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * One day of the plan, in words, with the advice and the trips its tab shows.
 *
 * @param date the day, in the owner's home
 * @param tab the day's short name: "Today", "Fri"
 * @param title the day's name: "Today", "Tomorrow" or the weekday
 * @param kind where the day's charge comes from
 * @param heading what the day holds: "Charge window", "No charge window", "Charging not needed", "Grid charge needed"
 * @param hero the charge window, such as "11:15 – 14:30"; empty when there is none
 * @param facts what to set and how much it brings: "Set 10 A (2.3 kW)", "about 7.9 kWh"; may be empty
 * @param chips the day's figures by source; may be empty
 * @param note a sentence about the day; may be empty
 * @param spoken the day's charge in one sentence: "Today: charge window 11:15 to 14:30, 10 A, 92% solar."
 * @param curve the day's expected solar power, house use and charge windows, as the solar chart draws them
 * @param battery the day's battery levels, as the battery bar draws them
 * @param tripAdvice the advice of the trips the day's tab shows, in its words, in order of departure; may be empty
 * @param gridAdvice the grid advice the day's tab shows; empty on a day that shows none
 * @param trips the trips the car is away for on the day, in its words, in their stored order; may be empty
 */
public record DayModel(
        LocalDate date,
        String tab,
        String title,
        DayKind kind,
        String heading,
        String hero,
        List<String> facts,
        List<ChipModel> chips,
        String note,
        String spoken,
        SolarChartModel curve,
        BatteryModel battery,
        List<TripAdviceModel> tripAdvice,
        Optional<GridAdviceModel> gridAdvice,
        List<TripsModel.Listed> trips) {

    /**
     * Keeps copies of the lists, so the day cannot change afterwards.
     */
    public DayModel {
        facts = List.copyOf(facts);
        chips = List.copyOf(chips);
        tripAdvice = List.copyOf(tripAdvice);
        trips = List.copyOf(trips);
    }

    /**
     * The same day with grid advice on its tab.
     *
     * @param advice the grid advice the day's tab shows; never null
     * @return a copy of the day that shows it, in place of any it showed
     */
    public DayModel withGridAdvice(GridAdviceModel advice) {
        return new DayModel(date, tab, title, kind, heading, hero, facts, chips, note, spoken, curve, battery,
                tripAdvice, Optional.of(advice), trips);
    }

    /**
     * The same day with the trips its tab lists.
     *
     * @param dayTrips the trips the car is away for on the day, in their stored order; never null, may be empty
     * @return a copy of the day that lists them, in place of any it listed
     */
    public DayModel withTrips(List<TripsModel.Listed> dayTrips) {
        return new DayModel(date, tab, title, kind, heading, hero, facts, chips, note, spoken, curve, battery,
                tripAdvice, gridAdvice, dayTrips);
    }
}
