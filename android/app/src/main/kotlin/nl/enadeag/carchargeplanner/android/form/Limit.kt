package nl.enadeag.carchargeplanner.android.form

import java.math.BigDecimal
import nl.enadeag.carchargeplanner.engine.config.Car
import nl.enadeag.carchargeplanner.engine.config.Charger
import nl.enadeag.carchargeplanner.engine.config.Coordinates
import nl.enadeag.carchargeplanner.engine.config.House
import nl.enadeag.carchargeplanner.engine.config.PanelArray
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.config.PlanningPreferences
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting

/**
 * What the engine allows for a typed setting, in the unit its field shows, so the field can say it when the engine
 * refuses a value. The ends come from the engine's records ([of]); the forms have no ranges of their own.
 *
 * @property low the lowest end, finite
 * @property lowIncluded whether [low] itself is allowed
 * @property high the highest end, above [low]; positive infinity for none
 * @property highIncluded whether [high] itself is allowed; false when there is no highest end
 */
data class Limit(val low: Double, val lowIncluded: Boolean, val high: Double, val highIncluded: Boolean) {

    init {
        require(low.isFinite()) { "low must be finite, not $low" }
        require(high > low) { "high must be above low, not $high" }
        require(!(highIncluded && high.isInfinite())) { "an end that is not there cannot be included" }
    }

    /** The shape of the limit, which decides the sentence a field says. */
    enum class Shape {
        /** From one end to the other, both allowed. */
        FROM_TO,
        /** A lowest end that is allowed, and no highest. */
        AT_LEAST,
        /** Above a lowest end that is not allowed itself, and no highest. */
        MORE_THAN,
        /** Above a lowest end that is not allowed itself, up to a highest that is. */
        MORE_THAN_AT_MOST,
        /** From a lowest end that is allowed, up to a highest that is not. */
        AT_LEAST_BELOW,
    }

    /** The shape of this limit. */
    val shape: Shape
        get() = when {
            high.isInfinite() -> if (lowIncluded) Shape.AT_LEAST else Shape.MORE_THAN
            !lowIncluded -> Shape.MORE_THAN_AT_MOST
            highIncluded -> Shape.FROM_TO
            else -> Shape.AT_LEAST_BELOW
        }

    /**
     * The ends as a field writes them, without the decimals a whole end does not need: "1000", not "1000.0".
     *
     * @param numbers writes numbers in the screen's language
     * @return the lowest end, and the highest when there is one
     */
    fun written(numbers: NumberText): List<String> =
        listOf(low, high).filter { it.isFinite() }.map { numbers.write(it, decimalsOf(it)) }

    companion object {

        private const val HOURS_PER_DAY = 24.0
        private const val PERCENT_PER_SHARE = 100.0

        /**
         * From one end to the other, both allowed.
         *
         * @param low the lowest value allowed, finite
         * @param high the highest value allowed, above [low]
         * @return the limit
         */
        fun fromTo(low: Double, high: Double) = Limit(low, true, high, true)

        /**
         * A lowest end that is allowed, and no highest.
         *
         * @param low the lowest value allowed, finite
         * @return the limit
         */
        fun atLeast(low: Double) = Limit(low, true, Double.POSITIVE_INFINITY, false)

        /**
         * What the engine allows for a setting, in the unit its field shows: a share in %, the household use in kWh
         * per day.
         *
         * @param setting a setting
         * @return the limit; null for a setting that is chosen rather than typed, or has a rule of its own, such as
         *     the panels' number
         */
        fun of(setting: ConfigSetting): Limit? = when (setting) {
            ConfigSetting.LATITUDE ->
                fromTo(Coordinates.MIN_LATITUDE.toDouble(), Coordinates.MAX_LATITUDE.toDouble())
            ConfigSetting.LONGITUDE ->
                fromTo(Coordinates.MIN_LONGITUDE.toDouble(), Coordinates.MAX_LONGITUDE.toDouble())
            ConfigSetting.PANEL_LOSSES -> Limit(PanelArray.MIN_SYSTEM_LOSSES.toDouble(), true,
                PanelArray.SYSTEM_LOSSES_BELOW.toDouble(), false).scaled(PERCENT_PER_SHARE)
            ConfigSetting.HOUSE_BASELOAD -> atLeast(House.MIN_BASELOAD_KW.toDouble()).scaled(HOURS_PER_DAY)
            ConfigSetting.CAR_BATTERY ->
                fromTo(Car.MIN_USABLE_BATTERY_KWH.toDouble(), Car.MAX_USABLE_BATTERY_KWH.toDouble())
            ConfigSetting.CAR_CONSUMPTION ->
                fromTo(Car.MIN_CONSUMPTION_KWH_PER_100KM.toDouble(), Car.MAX_CONSUMPTION_KWH_PER_100KM.toDouble())
            ConfigSetting.CAR_DAILY_DISTANCE -> fromTo(Car.MIN_KM_PER_DAY.toDouble(), Car.MAX_KM_PER_DAY.toDouble())
            ConfigSetting.CAR_TRIP_SURCHARGE, ConfigSetting.MINIMUM_CHARGE, ConfigSetting.TARGET_CHARGE ->
                fromTo(Percent.MIN.toDouble(), Percent.MAX.toDouble())
            ConfigSetting.CHARGER_MIN_CURRENT, ConfigSetting.CHARGER_MAX_CURRENT ->
                fromTo(Charger.MIN_CURRENT_AMPS.toDouble(), Charger.MAX_CURRENT_AMPS.toDouble())
            ConfigSetting.CHARGER_VOLTAGE -> fromTo(Charger.MIN_VOLTS.toDouble(), Charger.MAX_VOLTS.toDouble())
            ConfigSetting.CHARGER_EFFICIENCY ->
                fromTo(Charger.MIN_EFFICIENCY, Charger.MAX_EFFICIENCY).scaled(PERCENT_PER_SHARE)
            ConfigSetting.PLANNING_MARGIN -> Limit(PlanningPreferences.MARGIN_ABOVE.toDouble(), false,
                PlanningPreferences.MAX_MARGIN.toDouble(), true).scaled(PERCENT_PER_SHARE)
            ConfigSetting.IMPORT_TOLERANCE -> atLeast(PlanningPreferences.MIN_IMPORT_TOLERANCE_KWH.toDouble())
            ConfigSetting.MINIMUM_WINDOW ->
                Limit(PlanningPreferences.MIN_WINDOW_ABOVE_MINUTES.toDouble(), false, Double.POSITIVE_INFINITY, false)
            ConfigSetting.PANEL_SIZE, ConfigSetting.PANEL_COUNT, ConfigSetting.PANEL_POWER, ConfigSetting.PANEL_YEAR,
            ConfigSetting.PANEL_TILT, ConfigSetting.PANEL_DIRECTION, ConfigSetting.CHARGER_PHASES,
            ConfigSetting.PRIMARY_PROVIDER, ConfigSetting.FALLBACK_PROVIDER, ConfigSetting.WEEKLY_AWAY_PERIOD,
            ConfigSetting.NOTIFY_LEAD, ConfigSetting.CHARGING_HOURS -> null
        }

        private fun decimalsOf(value: Double) = maxOf(0, BigDecimal.valueOf(value).stripTrailingZeros().scale())
    }

    /** Worked out exactly in decimals, so 0.01 times 100 is 1. */
    private fun scaled(factor: Double): Limit {
        val times = BigDecimal.valueOf(factor)
        fun scale(end: Double) = if (end.isInfinite()) end else BigDecimal.valueOf(end).multiply(times).toDouble()
        return Limit(scale(low), lowIncluded, scale(high), highIncluded)
    }
}
