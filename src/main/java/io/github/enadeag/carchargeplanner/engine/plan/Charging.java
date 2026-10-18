package io.github.enadeag.carchargeplanner.engine.plan;

import io.github.enadeag.carchargeplanner.engine.config.Charger;
import io.github.enadeag.carchargeplanner.engine.config.HomeSystem;
import io.github.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** The arithmetic of surplus and charge power. */
final class Charging {

    private Charging() {
    }

    /**
     * The surplus the plan counts on: what is left after the house, scaled down by the planning margin. The provider
     * already accounted for the panels.
     *
     * @param step the forecast step, whose power is the whole system's expected AC output in kW
     * @param system the home and equipment, for the house's own use
     * @param preferences the planning preferences, for the margin
     * @return the usable surplus in kW, 0 or more
     */
    static double usableKw(ForecastStep step, HomeSystem system, PlanningPreferences preferences) {
        double surplus = step.powerKw() - system.house().baseloadKw();
        return Math.max(0, surplus) * preferences.margin();
    }

    /**
     * The power the charger delivers at a current: every phase carries it (6 A at 230 V: 1.4 kW on one phase, 4.1 on
     * three).
     *
     * @param amps the current per phase in A
     * @param charger the charger, for its voltage and number of phases
     * @return the charge power in kW, measured at the wall
     */
    static double powerKw(int amps, Charger charger) {
        return amps * charger.volts() * charger.phases().count() / 1000;
    }

    /**
     * The current for a power: whole amps per phase, rounded down, at most the charger's maximum.
     *
     * @param powerKw the power the current must fit under in kW, 0 or more
     * @param charger the charger, for its voltage, phases and maximum
     * @return the current per phase in A; below the charger's minimum when the power is too low to charge
     */
    static int ampsFor(double powerKw, Charger charger) {
        return Math.min(charger.maxAmps(), (int) (powerKw * 1000 / (charger.volts() * charger.phases().count())));
    }

    /**
     * Rounds an energy to six decimals, so amounts that differ only by floating-point noise compare as equal.
     *
     * @param kwh the energy in kWh
     * @return the energy in kWh, rounded half to even
     */
    static double roundKwh(double kwh) {
        return new BigDecimal(kwh).setScale(6, RoundingMode.HALF_EVEN).doubleValue();
    }
}
