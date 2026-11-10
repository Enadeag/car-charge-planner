package nl.enadeag.carchargeplanner.engine.dashboard;

import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.util.OptionalDouble;

/**
 * The plan's advice for one trip: what the plan does for it and how far the battery falls short, with its days named
 * from the day the plan was made.
 *
 * @param trip the trip, never null
 * @param departureDay the day of the trip's departure in the home's time zone, never null
 * @param plan what the plan does for the trip, never null
 * @param shortKm how many km of the trip the battery cannot hold at the departure, finite and more than 0; never null,
 *     empty when the car can make it
 */
public record TripAdviceView(Trip trip, DayName departureDay, TripPlan plan, OptionalDouble shortKm) {

    /**
     * Checks the distance short against the plan.
     *
     * @throws IllegalArgumentException when the distance short is not finite and more than 0, when a plan that charges
     *     enough is short, or when a plan that only leaves is not
     */
    public TripAdviceView {
        if (shortKm.isPresent() && (!(shortKm.getAsDouble() > 0) || Double.isInfinite(shortKm.getAsDouble()))) {
            throw new IllegalArgumentException("the distance short must be finite and more than 0, not "
                    + shortKm.getAsDouble() + " km");
        }
        if (plan instanceof TripPlan.Enough && shortKm.isPresent()) {
            throw new IllegalArgumentException("a plan that charges enough is not short, not " + shortKm.getAsDouble()
                    + " km");
        }
        if (plan instanceof TripPlan.Leaves && shortKm.isEmpty()) {
            throw new IllegalArgumentException("a plan that only leaves is short");
        }
    }

    /**
     * Whether the owner has to do something for the trip: charge above the target charge level, charge from the grid,
     * or plan a charging stop.
     *
     * @return false when the plan charges enough already, true otherwise
     */
    public boolean asksAction() {
        return !(plan instanceof TripPlan.Enough);
    }

    /**
     * Where the trip's charge comes from.
     *
     * @return {@link EnergySource#CAR} when the plan charges enough already, {@link EnergySource#SOLAR} when solar
     *     power above the target charge level is enough, {@link EnergySource#GRID} when it needs a grid charge or the
     *     battery falls short
     */
    public EnergySource source() {
        if (plan instanceof TripPlan.Enough) {
            return EnergySource.CAR;
        }
        return plan instanceof TripPlan.Solar && shortKm.isEmpty() ? EnergySource.SOLAR : EnergySource.GRID;
    }

    /**
     * Whether the advice is shown with a day: the rule for one advice, which {@link DashboardView#tripAdviceOn} applies
     * per day. Always with the day of the departure, and with the day the plan was made when the owner has to do
     * something.
     *
     * @param day the day's name, never null
     * @return true when the advice is shown with that day
     */
    public boolean shownOn(DayName day) {
        return day.date().equals(departureDay.date()) || asksAction() && day instanceof DayName.Today;
    }
}
