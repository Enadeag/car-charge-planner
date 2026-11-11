package nl.enadeag.carchargeplanner.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.form.CarForm
import nl.enadeag.carchargeplanner.android.form.ChargerForm
import nl.enadeag.carchargeplanner.android.form.HouseForm
import nl.enadeag.carchargeplanner.android.form.LocationForm
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.form.PanelsForm
import nl.enadeag.carchargeplanner.android.form.PlanningForm
import nl.enadeag.carchargeplanner.android.form.WeeklyAwayForm
import nl.enadeag.carchargeplanner.android.settings.SettingsPage
import nl.enadeag.carchargeplanner.android.settings.SettingsSession
import nl.enadeag.carchargeplanner.android.ui.components.EntryField
import nl.enadeag.carchargeplanner.android.ui.components.FieldKind
import nl.enadeag.carchargeplanner.android.ui.components.NumberField
import nl.enadeag.carchargeplanner.android.ui.components.ProblemFocus
import nl.enadeag.carchargeplanner.android.ui.components.RowFocus
import nl.enadeag.carchargeplanner.android.ui.components.RowHeading
import nl.enadeag.carchargeplanner.android.ui.components.SectionHeading
import nl.enadeag.carchargeplanner.android.ui.components.TimeField
import nl.enadeag.carchargeplanner.android.ui.components.rememberFieldFocus
import nl.enadeag.carchargeplanner.android.ui.setup.AwayPeriods
import nl.enadeag.carchargeplanner.android.ui.setup.CarStep
import nl.enadeag.carchargeplanner.android.ui.setup.ChargerStep
import nl.enadeag.carchargeplanner.android.ui.setup.Coordinates
import nl.enadeag.carchargeplanner.android.ui.setup.Edits
import nl.enadeag.carchargeplanner.android.ui.setup.HouseFields
import nl.enadeag.carchargeplanner.android.ui.setup.LocationFields
import nl.enadeag.carchargeplanner.android.ui.setup.PanelArrayFields
import nl.enadeag.carchargeplanner.android.ui.setup.UseTypical
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.Phases
import nl.enadeag.carchargeplanner.android.ui.components.AppOutlinedButton
import nl.enadeag.carchargeplanner.android.ui.components.AppTextButton

/**
 * @return such as "2 arrays, 6.5 kWp"
 */
@Composable
internal fun summary(page: SettingsPage, config: EngineConfig, session: SettingsSession): String {
    val numbers = NumberText(LocalConfiguration.current.locales[0])
    val system = config.system()
    return when (page) {
        SettingsPage.SOLAR -> pluralStringResource(R.plurals.settings_solar_summary, system.panels().size,
            system.panels().size, numbers.write(system.panels().sumOf { it.kwp() }, 2))
        SettingsPage.LOCATION -> stringResource(R.string.settings_location_summary,
            numbers.write(system.location().latitude(), 4), numbers.write(system.location().longitude(), 4),
            system.timezone().id)
        SettingsPage.CAR_AND_HOUSE -> stringResource(R.string.settings_car_house_summary,
            numbers.write(system.car().usableBatteryKwh(), 1), numbers.write(system.house().baseloadKw(), 2))
        SettingsPage.CHARGER -> stringResource(R.string.settings_charger_summary,
            stringResource(phasesName(system.charger().phases())), system.charger().minAmps(),
            system.charger().maxAmps())
        SettingsPage.FORECAST -> {
            val forecast = config.forecast()
            val primary = session.providerName(forecast.primary())
            val fallback = forecast.fallback().orElse(null)
            if (fallback == null) stringResource(R.string.settings_forecast_summary_alone, primary)
            else stringResource(R.string.settings_forecast_summary, primary, session.providerName(fallback))
        }
        SettingsPage.AWAY -> config.away().weekly().size.let { count ->
            if (count == 0) stringResource(R.string.settings_away_none)
            else pluralStringResource(R.plurals.settings_away_summary, count, count)
        }
        SettingsPage.PLANNING -> stringResource(R.string.settings_planning_summary,
            numbers.write(config.planning().targetPct().value(), 0),
            numbers.write(config.planning().minimumPct().value(), 0))
    }
}

/**
 * @return the text's resource
 */
internal fun phasesName(phases: Phases): Int = if (phases == Phases.ONE) R.string.phases_one else R.string.phases_three

/**
 * Every panel array, each named by its place and its name ("Array 2, east roof"), with its own Remove, which acts at
 * once; the last array's Remove is off, with the reason, which a screen reader reads with it. There is no reordering.
 * After Add the focus goes to the new array's heading, after Remove to the one before.
 *
 * @param problems the page's fields in order, for the focus after Save
 */
@Composable
internal fun SolarPage(panels: PanelsForm, edits: Edits, problems: ProblemFocus) {
    val rows by panels.rowIds.collectAsStateWithLifecycle()
    val focus = remember { RowFocus<Int>() }
    Text(stringResource(R.string.settings_solar_intro))
    rows.forEachIndexed { index, row ->
        key(row) {
            PanelArray(panels, row, index, rows, edits, problems, focus)
        }
    }
    AppOutlinedButton(onClick = { edits.make { focus.next = panels.add() } }) {
        Text(stringResource(R.string.panel_add))
    }
}

@Composable
private fun PanelArray(panels: PanelsForm, row: Int, index: Int, rows: List<Int>, edits: Edits,
    problems: ProblemFocus, focus: RowFocus<Int>) {
    val count = rows.size
    val number = index + 1
    val name = edits.shown { panels.text(row, PanelsForm.Field.NAME) }.trim()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RowHeading(if (name.isEmpty()) stringResource(R.string.panel_array, number)
            else stringResource(R.string.panel_array_named, number, name), MaterialTheme.typography.titleMedium, focus,
            row)
        PanelArrayFields(panels, row, edits, problems, 10 * index, last = number == count) { label ->
            stringResource(R.string.panel_in_array, label, number)
        }
        RemoveArray(number, count > 1) {
            edits.make {
                focus.removing(rows, row)
                panels.remove(row)
            }
        }
    }
}

/** Remove, off for the last array with the reason, which a screen reader reads as the button's state. */
@Composable
private fun RemoveArray(number: Int, removable: Boolean, onRemove: () -> Unit) {
    val reason = stringResource(R.string.panel_keep_one)
    AppTextButton(onClick = onRemove, enabled = removable,
        modifier = if (removable) Modifier else Modifier.semantics { stateDescription = reason }) {
        Text(stringResource(R.string.panel_remove, number))
    }
    if (!removable) {
        Text(reason, style = MaterialTheme.typography.bodySmall, modifier = Modifier.clearAndSetSemantics { })
    }
}

/**
 * Where the home is: the place search of the stored primary provider, or a line that it has none; the coordinates,
 * always shown; and the time zone under "Advanced", which typed coordinates leave alone, as a stored zone counts as
 * chosen (#315).
 *
 * @param problems the page's fields in order, for the focus after Save
 */
@Composable
internal fun LocationPage(session: SettingsSession, edits: Edits, problems: ProblemFocus) {
    val config by session.config.collectAsStateWithLifecycle()
    val location = session.forms.location
    val places = session.places
    val primary = config.forecast().primary()
    LocationFields(location, places, session.providerName(primary), session.hasPlaceSearch(primary), edits,
        problems, Coordinates.SHOWN, { result ->
            location.choose(result.place)
            places.choose(result)
        }, { field: LocationForm.Field, text ->
            location.edit(field, text)
            places.forgetChosen()
        })
}

/**
 * The car with "I don't know", then the house's baseload with its own.
 *
 * @param problems the page's fields in order, for the focus after Save
 */
@Composable
internal fun CarAndHousePage(car: CarForm, house: HouseForm, edits: Edits, problems: ProblemFocus) {
    SectionHeading(stringResource(R.string.settings_car_heading))
    CarStep(car, edits, problems, ImeAction.Next)
    SectionHeading(stringResource(R.string.house_heading))
    HouseFields(house, edits, problems, 10, ImeAction.Done)
}

/**
 * The wall charger, as in set-up.
 *
 * @param problems the page's fields in order, for the focus after Save
 */
@Composable
internal fun ChargerPage(charger: ChargerForm, edits: Edits, problems: ProblemFocus) {
    ChargerStep(charger, edits, problems)
}

/**
 * The weekly away periods, as in set-up; one-off away periods and trips have screens of their own (#276, #278).
 *
 * @param problems the page's fields in order, for the focus after Save
 */
@Composable
internal fun AwayPage(away: WeeklyAwayForm, edits: Edits, problems: ProblemFocus) {
    Text(stringResource(R.string.settings_away_intro))
    AwayPeriods(away, edits, problems)
}

/**
 * The planning preferences: the charge levels and the margin in %, the import tolerance in kWh, the shortest charge
 * window and the notification's lead in minutes, and the charging hours as times typed, not on a dial.
 *
 * @param problems the page's fields in order, for the focus after Save
 */
@Composable
internal fun PlanningPage(planning: PlanningForm, edits: Edits, problems: ProblemFocus) {
    val shown by planning.problems.collectAsStateWithLifecycle()
    listOf(
        Triple(PlanningForm.Field.MINIMUM_CHARGE, R.string.planning_minimum to R.string.planning_minimum_hint,
            FieldKind.DECIMAL),
        Triple(PlanningForm.Field.TARGET_CHARGE, R.string.planning_target to R.string.planning_target_hint,
            FieldKind.DECIMAL),
        Triple(PlanningForm.Field.MARGIN, R.string.planning_margin to R.string.planning_margin_hint, FieldKind.DECIMAL),
        Triple(PlanningForm.Field.IMPORT_TOLERANCE, R.string.planning_tolerance to R.string.planning_tolerance_hint,
            FieldKind.DECIMAL),
        Triple(PlanningForm.Field.MINIMUM_WINDOW, R.string.planning_min_window to R.string.planning_min_window_hint,
            FieldKind.WHOLE),
        Triple(PlanningForm.Field.NOTIFY_LEAD, R.string.planning_lead to R.string.planning_lead_hint,
            FieldKind.WHOLE),
    ).forEachIndexed { order, (field, texts, kind) ->
        NumberField(stringResource(texts.first), stringResource(texts.second), edits.shown { planning.text(field) },
            { text -> edits.make { planning.edit(field, text) } }, kind, shown[field],
            focus = rememberFieldFocus(problems, order, field in shown))
    }
    SectionHeading(stringResource(R.string.planning_hours_heading))
    Text(stringResource(R.string.planning_hours_intro))
    listOf(PlanningForm.Field.CHARGING_START to R.string.planning_hours_from,
        PlanningForm.Field.CHARGING_END to R.string.planning_hours_until).forEachIndexed { place, (field, label) ->
        TimeField(stringResource(label), edits.shown { planning.text(field) },
            { text -> edits.make { planning.edit(field, text) } }, shown[field],
            rememberFieldFocus(problems, 10 + place, field in shown),
            if (field == PlanningForm.Field.CHARGING_END) ImeAction.Done else ImeAction.Next)
    }
}
