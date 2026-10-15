package io.github.enadeag.carchargeplanner.desktop;

/**
 * Where the solar chart's plot lies in the chart, and the hours and power it spans: turns hours and kW into pixels and
 * back.
 *
 * @param left the plot's left edge in pixels from the chart's left
 * @param top the plot's top edge in pixels from the chart's top
 * @param width the plot's width in pixels, 0 or more
 * @param height the plot's height in pixels, 0 or more
 * @param fromHour the hour at the left edge
 * @param toHour the hour at the right edge, after {@code fromHour}
 * @param scaleKw the power at the top edge in kW, more than 0
 */
record PlotArea(double left, double top, double width, double height, double fromHour, double toHour,
        double scaleKw) {

    /**
     * Checks the ranges.
     *
     * @throws IllegalArgumentException when a size is negative, the hours are not in order or the scale is not
     *     positive
     */
    PlotArea {
        if (!(width >= 0 && height >= 0)) {
            throw new IllegalArgumentException("a plot cannot have a negative size");
        }
        if (!(toHour > fromHour)) {
            throw new IllegalArgumentException("a plot's hours must run forwards");
        }
        if (!(scaleKw > 0)) {
            throw new IllegalArgumentException("a plot's scale must be more than 0 kW");
        }
    }

    /**
     * The plot's bottom edge, where the power is 0.
     *
     * @return pixels from the chart's top
     */
    double bottom() {
        return top + height;
    }

    /**
     * The plot's right edge.
     *
     * @return pixels from the chart's left
     */
    double right() {
        return left + width;
    }

    /**
     * Where an hour is.
     *
     * @param hour the hour from the start of the day; one outside the plot's hours is put on its nearest edge
     * @return pixels from the chart's left
     */
    double x(double hour) {
        double share = (Math.clamp(hour, fromHour, toHour) - fromHour) / (toHour - fromHour);
        return left + width * share;
    }

    /**
     * Where a power is.
     *
     * @param kw the power in kW; below 0 is put on the bottom edge and above the scale on the top edge
     * @return pixels from the chart's top
     */
    double y(double kw) {
        return bottom() - height * Math.clamp(kw, 0, scaleKw) / scaleKw;
    }

    /**
     * The hour at a place, such as the pointer's.
     *
     * @param x pixels from the chart's left; outside the plot counts as its nearest edge
     * @return the hour from the start of the day; the plot's first hour when it has no width
     */
    double hourAt(double x) {
        if (width == 0) {
            return fromHour;
        }
        return fromHour + (Math.clamp(x, left, right()) - left) / width * (toHour - fromHour);
    }
}
