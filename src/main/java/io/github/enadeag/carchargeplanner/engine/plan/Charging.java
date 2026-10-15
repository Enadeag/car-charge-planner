package io.github.enadeag.carchargeplanner.engine.plan;

import io.github.enadeag.carchargeplanner.engine.config.Charger;
import io.github.enadeag.carchargeplanner.engine.config.HomeSystem;
import io.github.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastStep;

/** The arithmetic of surplus and charge power. */
final class Charging {

    private Charging() {
    }

    /** Surplus after the house, scaled down by the planning margin. The provider already accounted for the panels. */
    static double usableKw(ForecastStep step, HomeSystem system, PlanningPreferences preferences) {
        double surplus = step.powerKw() - system.house().baseloadKw();
        return Math.max(0, surplus) * preferences.margin();
    }

    /** Power the charger delivers at this current: every phase carries it (6 A at 230 V: 1.4 kW on one phase, 4.1 on three). */
    static double powerKw(int amps, Charger charger) {
        return amps * charger.volts() * charger.phases() / 1000;
    }

    /** Whole amps per phase (rounded down) that fit under the power, at most the charger's maximum. */
    static int ampsFor(double powerKw, Charger charger) {
        return Math.min(charger.maxAmps(), (int) (powerKw * 1000 / (charger.volts() * charger.phases())));
    }
}
