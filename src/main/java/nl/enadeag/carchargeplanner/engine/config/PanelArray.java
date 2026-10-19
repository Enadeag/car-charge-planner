package nl.enadeag.carchargeplanner.engine.config;

import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import nl.enadeag.carchargeplanner.engine.setting.Setting;

/**
 * Panels that share one tilt and one direction.
 *
 * @param name the owner's name for the array, such as "east roof"; may be empty
 * @param kwp the peak power of the array in kWp, more than 0
 * @param tiltDeg the tilt from the horizontal in degrees, from 0 (flat) to 90 (upright)
 * @param azimuthDeg the direction the panels face in degrees, from -180 to 180: 0 is south, -90 east, 90 west
 * @param systemLosses the share of the power lost in cables, inverter and dirt, from 0 up to (not including) 1
 */
public record PanelArray(String name, double kwp, double tiltDeg, double azimuthDeg, double systemLosses) {

    /** A common array, for an owner who does not know the values: 5 kWp, 35 degrees, facing south, 14 % lost. */
    public static final PanelArray TYPICAL = new PanelArray("", 5, 35, 0, 0.14);

    /**
     * Checks the values.
     *
     * @throws InvalidSetting when a value is outside the range given for it
     */
    public PanelArray {
        if (kwp <= 0 || Double.isNaN(kwp)) {
            throw new InvalidSetting(Setting.PANEL_SIZE, InvalidSetting.Rule.OUT_OF_RANGE,
                    "kwp must be more than 0");
        }
        if (tiltDeg < 0 || tiltDeg > 90 || Double.isNaN(tiltDeg)) {
            throw new InvalidSetting(Setting.PANEL_TILT, InvalidSetting.Rule.OUT_OF_RANGE,
                    "tiltDeg must be between 0 and 90");
        }
        if (azimuthDeg < -180 || azimuthDeg > 180 || Double.isNaN(azimuthDeg)) {
            throw new InvalidSetting(Setting.PANEL_DIRECTION, InvalidSetting.Rule.OUT_OF_RANGE,
                    "azimuthDeg must be between -180 and 180");
        }
        if (systemLosses < 0 || systemLosses >= 1 || Double.isNaN(systemLosses)) {
            throw new InvalidSetting(Setting.PANEL_LOSSES, InvalidSetting.Rule.OUT_OF_RANGE,
                    "systemLosses must be at least 0 and less than 1");
        }
    }
}
