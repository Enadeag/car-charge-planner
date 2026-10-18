package io.github.enadeag.carchargeplanner.desktop.settings.form;

import io.github.enadeag.carchargeplanner.engine.config.PanelArray;

/**
 * A panel array as its card shows it: the losses as a percentage.
 *
 * @param name the array's name; may be empty
 * @param kwp the array's peak power, in kWp
 * @param tiltDeg the panels' tilt, in degrees from flat
 * @param azimuthDeg the direction they face, in degrees from south
 * @param lossesPct the system's losses, in %
 */
public record PanelArrayEntry(String name, double kwp, double tiltDeg, double azimuthDeg, double lossesPct) {

    /**
     * What the card shows for an array.
     *
     * @param array the array as the engine keeps it
     * @return its values as the fields show them
     */
    public static PanelArrayEntry of(PanelArray array) {
        return new PanelArrayEntry(array.name(), array.kwp(), array.tiltDeg(), array.azimuthDeg(),
                array.systemLosses() * 100);
    }

    /**
     * The array as entered.
     *
     * @return the array, as its record checked it
     * @throws io.github.enadeag.carchargeplanner.engine.setting.InvalidSetting when the record refuses a value
     */
    public PanelArray panelArray() {
        return new PanelArray(name, kwp, tiltDeg, azimuthDeg, lossesPct / 100);
    }
}
