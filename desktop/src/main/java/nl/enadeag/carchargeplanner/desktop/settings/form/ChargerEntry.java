package nl.enadeag.carchargeplanner.desktop.settings.form;

import nl.enadeag.carchargeplanner.engine.config.Charger;
import nl.enadeag.carchargeplanner.engine.config.Phases;

/**
 * The wall charger as its page shows it: the efficiency as a percentage, the currents as typed.
 *
 * @param phases the phases the charger charges on
 * @param minAmps the lowest current, in A
 * @param maxAmps the highest current, in A
 * @param volts the voltage per phase, in V
 * @param efficiencyPct the share of the power that reaches the battery, in %
 */
public record ChargerEntry(Phases phases, double minAmps, double maxAmps, double volts, double efficiencyPct) {

    /**
     * What the page shows for a charger.
     *
     * @param charger the charger as the engine keeps it
     * @return its values as the fields show them
     */
    public static ChargerEntry of(Charger charger) {
        return new ChargerEntry(charger.phases(), charger.minAmps(), charger.maxAmps(), charger.volts(),
                charger.efficiency() * 100);
    }

    /**
     * The charger as entered, with the currents rounded to whole amps.
     *
     * @return the charger, as its record checked it
     * @throws nl.enadeag.carchargeplanner.engine.setting.InvalidSetting when the record refuses a value
     */
    public Charger charger() {
        return new Charger(phases, (int) Math.round(minAmps), (int) Math.round(maxAmps), volts, efficiencyPct / 100);
    }
}
