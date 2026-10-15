package io.github.enadeag.carchargeplanner.engine.config;

/**
 * Panels that share one tilt and one direction.
 *
 * @param azimuthDeg the direction the panels face: 0 is south, -90 east, 90 west
 * @param systemLosses the share of the power lost in cables, inverter and dirt, from 0 up to (not including) 1
 */
public record PanelArray(String name, double kwp, double tiltDeg, double azimuthDeg, double systemLosses) {

    public PanelArray {
        Checks.require(kwp > 0, "kwp must be more than 0");
        Checks.require(tiltDeg >= 0 && tiltDeg <= 90, "tiltDeg must be between 0 and 90");
        Checks.require(azimuthDeg >= -180 && azimuthDeg <= 180, "azimuthDeg must be between -180 and 180");
        Checks.require(systemLosses >= 0 && systemLosses < 1, "systemLosses must be at least 0 and less than 1");
    }
}
