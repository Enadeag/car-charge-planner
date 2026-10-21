package nl.enadeag.carchargeplanner.engine.plan;

/**
 * The part of a trip's energy that comes from the grid, because the solar power before the departure is not enough.
 *
 * @param window the window to charge in, whose solar and import energy together are the grid part
 * @param now true when there is too little time left to finish an hour before the departure, so the owner should start
 *     at once, or as soon as the car is at home; false when the window is planned as late as possible
 */
public record TripGridCharge(Window window, boolean now) {
}
