package nl.enadeag.carchargeplanner.engine.config;

/** The directions the owner chooses from for a panel array: the eight compass points, clockwise from north. */
public enum CompassPoint {

    /** North: 180 degrees from south. */
    NORTH(180),

    /** North-east: -135 degrees. */
    NORTH_EAST(-135),

    /** East: -90 degrees. */
    EAST(-90),

    /** South-east: -45 degrees. */
    SOUTH_EAST(-45),

    /** South: 0 degrees. */
    SOUTH(0),

    /** South-west: 45 degrees. */
    SOUTH_WEST(45),

    /** West: 90 degrees. */
    WEST(90),

    /** North-west: 135 degrees. */
    NORTH_WEST(135);

    private final double azimuthDeg;

    CompassPoint(double azimuthDeg) {
        this.azimuthDeg = azimuthDeg;
    }

    /**
     * The direction this choice stores.
     *
     * @return the direction in degrees from south, as {@link PanelArray#azimuthDeg()} holds it: north is 180
     */
    public double azimuthDeg() {
        return azimuthDeg;
    }

    /**
     * The choice a stored direction is shown as.
     *
     * @param azimuthDeg a direction in degrees from south, from -180 to 180, such as one a file holds; -180 is north
     *     too
     * @return the point nearest to it around the compass; of two equally near, the one furthest from south
     */
    public static CompassPoint nearest(double azimuthDeg) {
        var nearest = SOUTH;
        for (var point : values()) {
            double distance = point.distanceTo(azimuthDeg);
            double best = nearest.distanceTo(azimuthDeg);
            if (distance < best || distance == best && Math.abs(point.azimuthDeg) > Math.abs(nearest.azimuthDeg)) {
                nearest = point;
            }
        }
        return nearest;
    }

    private double distanceTo(double other) {
        double apart = Math.abs(azimuthDeg - other) % 360;
        return Math.min(apart, 360 - apart);
    }
}
