package nl.enadeag.carchargeplanner.engine.route;

import nl.enadeag.carchargeplanner.engine.config.Coordinates;

/** The straight-line distance between two places over the earth's surface, as a sphere. */
final class GreatCircle {

    /** The earth's mean radius in km. */
    private static final double EARTH_RADIUS_KM = 6371.0;

    private GreatCircle() {
    }

    /**
     * The distance along the earth's surface, by the haversine formula.
     *
     * @param from one place
     * @param to the other place
     * @return the distance in km, 0 or more
     */
    static double distanceKm(Coordinates from, Coordinates to) {
        double fromLatitude = Math.toRadians(from.latitude());
        double toLatitude = Math.toRadians(to.latitude());
        double latitudes = Math.sin((toLatitude - fromLatitude) / 2);
        double longitudes = Math.sin(Math.toRadians(to.longitude() - from.longitude()) / 2);
        double haversine = latitudes * latitudes
                + Math.cos(fromLatitude) * Math.cos(toLatitude) * longitudes * longitudes;
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(haversine)));
    }
}
