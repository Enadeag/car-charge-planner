package nl.enadeag.carchargeplanner.android.form

import androidx.lifecycle.SavedStateHandle
import java.time.Duration
import java.time.LocalTime
import nl.enadeag.carchargeplanner.engine.config.ChargingHours
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.PlanningPreferences
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting

/**
 * The planning preferences: the minimum and target charge levels and the margin in %, the grid energy a window may
 * take in kWh and the shortest window in whole minutes, all typed; the notification's lead, chosen from the engine's
 * `NotifyLeadChoices`; and the charging hours, chosen on a picker. They are choices, never typical values.
 *
 * @param handle where the fields' texts are kept
 * @param numbers how the screen's language writes numbers
 */
class PlanningForm(handle: SavedStateHandle, numbers: NumberText) :
    FieldsForm<PlanningForm.Field>(FormState(handle, "planning", Field.entries), numbers) {

    /**
     * A field of the form.
     *
     * @property setting the engine's name for the setting whose problems it shows
     */
    enum class Field(val setting: ConfigSetting) {
        /** The minimum charge level, in %. */
        MINIMUM_CHARGE(ConfigSetting.MINIMUM_CHARGE),
        /** The target charge level, in %. */
        TARGET_CHARGE(ConfigSetting.TARGET_CHARGE),
        /** The share of the forecast surplus to count on, in %, which the engine keeps as a share of 1. */
        MARGIN(ConfigSetting.PLANNING_MARGIN),
        /** The grid energy a charge window may take, in kWh. */
        IMPORT_TOLERANCE(ConfigSetting.IMPORT_TOLERANCE),
        /** The shortest charge window, in whole minutes. */
        MINIMUM_WINDOW(ConfigSetting.MINIMUM_WINDOW),
        /** How long before a window the owner is told, in whole minutes, chosen rather than typed. */
        NOTIFY_LEAD(ConfigSetting.NOTIFY_LEAD),
        /** The start of the charging hours, such as 07:00, chosen rather than typed. */
        CHARGING_START(ConfigSetting.CHARGING_HOURS),
        /** The end of the charging hours, which shows the problems of the hours as a whole. */
        CHARGING_END(ConfigSetting.CHARGING_HOURS),
    }

    private companion object {
        const val IMPORT_DECIMALS = 2
    }

    /**
     * The notification's lead chosen now.
     *
     * @return the lead; null when the kept text is no whole number of minutes from 0 up, as only an older version of
     *     the app can leave
     */
    fun lead(): Duration? = state.text(Field.NOTIFY_LEAD).trim().toLongOrNull()?.takeIf { it >= 0 }
        ?.let { Duration.ofMinutes(it) }

    /**
     * Chooses the notification's lead; choosing the one shown changes nothing.
     *
     * @param lead the lead, in whole minutes, 0 or more
     */
    fun choose(lead: Duration) = chosen(Field.NOTIFY_LEAD, lead.toMinutes().toString())

    /**
     * A time of the charging hours as it shows and is saved.
     *
     * @param field [Field.CHARGING_START] or [Field.CHARGING_END]
     * @return the time chosen; the standard hours' when the kept text is no time, as only an older version of the app
     *     can leave
     */
    fun time(field: Field): LocalTime = TimeText.read(state.text(field))
        ?: if (field == Field.CHARGING_START) ChargingHours.STANDARD.start() else ChargingHours.STANDARD.end()

    /**
     * Chooses a time of the charging hours; choosing the one shown changes nothing.
     *
     * @param field [Field.CHARGING_START] or [Field.CHARGING_END]
     * @param time the time, to the minute
     */
    fun choose(field: Field, time: LocalTime) = chosen(field, TimeText.write(time))

    override fun load(config: EngineConfig) {
        val planning = config.planning()
        state.fill(Field.MINIMUM_CHARGE, numbers.write(planning.minimumPct().value(), 0))
        state.fill(Field.TARGET_CHARGE, numbers.write(planning.targetPct().value(), 0))
        state.fill(Field.MARGIN, numbers.write(planning.margin() * 100, 0))
        state.fill(Field.IMPORT_TOLERANCE, numbers.write(planning.importToleranceKwh(), IMPORT_DECIMALS))
        state.fill(Field.MINIMUM_WINDOW, planning.minWindow().toMinutes().toString())
        state.fill(Field.NOTIFY_LEAD, planning.notifyLead().toMinutes().toString())
        state.fill(Field.CHARGING_START, TimeText.write(planning.chargingHours().start()))
        state.fill(Field.CHARGING_END, TimeText.write(planning.chargingHours().end()))
    }

    override fun readInto(config: EngineConfig, reader: FieldReader<Field>): EngineConfig? {
        val stored = config.planning()
        val minimum = reader.percent(Field.MINIMUM_CHARGE, stored.minimumPct(), 0)
        val target = reader.percent(Field.TARGET_CHARGE, stored.targetPct(), 0)
        val margin = reader.number(Field.MARGIN, stored.margin(), 0, scale = 100.0)
        val tolerance = reader.number(Field.IMPORT_TOLERANCE, stored.importToleranceKwh(), IMPORT_DECIMALS)
        val window = reader.minutes(Field.MINIMUM_WINDOW, stored.minWindow())
        val lead = reader.minutes(Field.NOTIFY_LEAD, stored.notifyLead())
        val start = time(Field.CHARGING_START)
        val end = time(Field.CHARGING_END)
        val planning = reader.records({ setting -> fieldOf(setting) }) {
            PlanningPreferences(minimum, target, margin, tolerance, window, lead, ChargingHours(start, end))
        } ?: return null
        return config.withPlanning(planning)
    }

    private fun chosen(field: Field, text: String) {
        if (state.text(field) != text) {
            state.edit(field, text)
        }
    }

    private fun fieldOf(setting: ConfigSetting) = if (setting == ConfigSetting.CHARGING_HOURS) Field.CHARGING_END
        else Field.entries.first { it.setting == setting }
}
