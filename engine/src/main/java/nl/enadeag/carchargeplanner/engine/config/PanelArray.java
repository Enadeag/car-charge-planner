package nl.enadeag.carchargeplanner.engine.config;

import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;

/**
 * Panels that share one tilt and one direction.
 *
 * @param name the owner's name for the array, such as "east roof"; may be empty
 * @param size the array's peak power, or its number of panels with their power or the year they were placed
 * @param tiltDeg the tilt from the horizontal in degrees, from 0 (flat) to 90 (upright)
 * @param azimuthDeg the direction the panels face in degrees, from -180 to 180: 0 is south, -90 east, 90 west
 * @param systemLosses the share of the power lost in cables, inverter and dirt, from {@link #MIN_SYSTEM_LOSSES} up to
 *     (not including) {@link #SYSTEM_LOSSES_BELOW}
 */
public record PanelArray(String name, PanelSize size, double tiltDeg, double azimuthDeg, double systemLosses) {

    /** The lowest system losses: nothing lost. */
    public static final int MIN_SYSTEM_LOSSES = 0;

    /** The system losses are below this share: with all of the power lost, the panels give nothing. */
    public static final int SYSTEM_LOSSES_BELOW = 1;

    /** A common array, for an owner who does not know the values: 5 kWp, 35 degrees, facing south, 14 % lost. */
    public static final PanelArray TYPICAL = new PanelArray("", 5, 35, 0, 0.14);

    /**
     * Checks the values.
     *
     * @throws InvalidSetting when a value is outside the range given for it
     */
    public PanelArray {
        if (tiltDeg < 0 || tiltDeg > 90 || !Double.isFinite(tiltDeg)) {
            throw new InvalidSetting(ConfigSetting.PANEL_TILT, InvalidSetting.Rule.OUT_OF_RANGE,
                    "tiltDeg must be between 0 and 90");
        }
        if (azimuthDeg < -180 || azimuthDeg > 180 || !Double.isFinite(azimuthDeg)) {
            throw new InvalidSetting(ConfigSetting.PANEL_DIRECTION, InvalidSetting.Rule.OUT_OF_RANGE,
                    "azimuthDeg must be between -180 and 180");
        }
        if (systemLosses < MIN_SYSTEM_LOSSES || systemLosses >= SYSTEM_LOSSES_BELOW || !Double.isFinite(systemLosses)) {
            throw new InvalidSetting(ConfigSetting.PANEL_LOSSES, InvalidSetting.Rule.OUT_OF_RANGE,
                    "systemLosses must be at least " + MIN_SYSTEM_LOSSES + " and less than " + SYSTEM_LOSSES_BELOW);
        }
    }

    /**
     * An array whose size is its peak power.
     *
     * @param name the owner's name for the array; may be empty
     * @param kwp the peak power in kWp, more than 0
     * @param tiltDeg the tilt from the horizontal in degrees, from 0 to 90
     * @param azimuthDeg the direction in degrees from south, from -180 to 180
     * @param systemLosses the share of the power lost, from 0 up to (not including) 1
     * @throws InvalidSetting when a value is outside the range given for it
     */
    public PanelArray(String name, double kwp, double tiltDeg, double azimuthDeg, double systemLosses) {
        this(name, new PanelSize.Peak(kwp), tiltDeg, azimuthDeg, systemLosses);
    }

    /**
     * The peak power of the whole array, which the planner and the forecast providers use.
     *
     * @return the peak power in kWp, more than 0, as given or worked out from the panels
     */
    public double kwp() {
        return size.kwp();
    }
}
