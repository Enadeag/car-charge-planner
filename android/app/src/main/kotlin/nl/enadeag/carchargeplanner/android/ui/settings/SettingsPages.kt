package nl.enadeag.carchargeplanner.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import java.time.Duration
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
import nl.enadeag.carchargeplanner.android.ui.components.ChoiceMenu
import nl.enadeag.carchargeplanner.android.ui.components.EntryField
import nl.enadeag.carchargeplanner.android.ui.components.FieldKind
import nl.enadeag.carchargeplanner.android.ui.components.Fold
import nl.enadeag.carchargeplanner.android.ui.components.NumberField
import nl.enadeag.carchargeplanner.android.ui.components.ProblemFocus
import nl.enadeag.carchargeplanner.android.ui.components.RowFocus
import nl.enadeag.carchargeplanner.android.ui.components.RowHeading
import nl.enadeag.carchargeplanner.android.ui.components.SectionHeading
import nl.enadeag.carchargeplanner.android.ui.components.TimeChoice
import nl.enadeag.carchargeplanner.android.ui.components.rememberFieldFocus
import nl.enadeag.carchargeplanner.android.ui.setup.AwayPeriods
import nl.enadeag.carchargeplanner.android.ui.setup.CarStep
import nl.enadeag.carchargeplanner.android.ui.setup.ChargerStep
import nl.enadeag.carchargeplanner.android.ui.setup.chargePowerName
import nl.enadeag.carchargeplanner.android.ui.setup.Edits
import nl.enadeag.carchargeplanner.android.ui.setup.LocationFields
import nl.enadeag.carchargeplanner.android.ui.setup.PanelArrayFields
import nl.enadeag.carchargeplanner.android.ui.setup.UseTypical
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.ChargePowerChoice
import nl.enadeag.carchargeplanner.engine.config.NotifyLeadChoices
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
            system.panels().size, numbers.write(system.panels().sumOf { it.kwp() }, 1))
        SettingsPage.LOCATION -> system.place().map { it.text() }.orElse(null)
            ?: stringResource(R.string.settings_location_summary, numbers.write(system.location().latitude(), 4),
                numbers.write(system.location().longitude(), 4))
        SettingsPage.CAR_AND_HOUSE -> stringResource(R.string.settings_car_house_summary,
            numbers.write(system.car().usableBatteryKwh(), 1))
        SettingsPage.CHARGER -> {
            val charger = system.charger()
            val power = ChargePowerChoice.of(charger)
            if (power != ChargePowerChoice.OTHER) stringResource(chargePowerName(power))
            else stringResource(R.string.settings_charger_summary, stringResource(phasesName(charger.phases())),
                charger.minAmps(), charger.maxAmps())
        }
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
 * Where the home is, as set-up's step shows it, with the place search of the stored primary provider; typed
 * coordinates leave the time zone alone, as a stored zone counts as chosen (#315).
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
        problems, { result ->
            location.choose(result.place, result.label)
            places.chosen()
        }, { field: LocationForm.Field, text -> location.edit(field, text) })
}

/**
 * The car as in set-up, with the house's use and its own "I don't know" after the trip surcharge under the car's "More
 * settings"; a problem in the house's use opens the fold as one in the trip surcharge does.
 *
 * @param problems the page's fields in order, for the focus after Save
 */
@Composable
internal fun CarAndHousePage(car: CarForm, house: HouseForm, edits: Edits, problems: ProblemFocus) {
    val shown by house.problems.collectAsStateWithLifecycle()
    val baseload = HouseForm.Field.BASELOAD
    val problem = baseload in shown
    SectionHeading(stringResource(R.string.settings_car_heading))
    CarStep(car, edits, problems, problem) {
        NumberField(stringResource(R.string.household_use), stringResource(R.string.household_use_hint),
            edits.shown { house.text(baseload) }, { text -> edits.make { house.edit(baseload, text) } },
            FieldKind.DECIMAL, shown[baseload], baseload in edits.shown { house.typical() },
            rememberFieldFocus(problems, 10, problem), ImeAction.Done)
        UseTypical { edits.make { house.useTypical() } }
    }
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
 * The planning preferences: the charge levels in %, the charging hours, each chosen on the time picker, and the
 * notification's lead, chosen from a few, then folded under "More settings" the margin in %, the import tolerance in
 * kWh and the shortest charge window in minutes. A stored lead that is none of the few is offered too while it is the
 * one chosen. The fold starts closed; a problem in one of its fields opens it and keeps it open, also after the problem
 * is put right, while a press on its row does nothing meanwhile. Only the page's last field has the keyboard's Done:
 * the target while the fold is closed, else the shortest window.
 *
 * @param problems the page's fields in order, for the focus after Save
 */
@Composable
internal fun PlanningPage(planning: PlanningForm, edits: Edits, problems: ProblemFocus) {
    val shown by planning.problems.collectAsStateWithLifecycle()
    val folded = listOf(PlanningForm.Field.MARGIN, PlanningForm.Field.IMPORT_TOLERANCE,
        PlanningForm.Field.MINIMUM_WINDOW)
    val problem = folded.any { it in shown }
    var closed by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(problem) {
        if (problem) {
            closed = false
        }
    }
    val open = !closed || problem
    @Composable
    fun typed(field: PlanningForm.Field, label: Int, hint: Int, kind: FieldKind, order: Int, imeAction: ImeAction) {
        NumberField(stringResource(label), stringResource(hint), edits.shown { planning.text(field) },
            { text -> edits.make { planning.edit(field, text) } }, kind, shown[field],
            focus = rememberFieldFocus(problems, order, field in shown), imeAction = imeAction)
    }
    typed(PlanningForm.Field.MINIMUM_CHARGE, R.string.planning_minimum, R.string.planning_minimum_hint,
        FieldKind.DECIMAL, 0, ImeAction.Next)
    typed(PlanningForm.Field.TARGET_CHARGE, R.string.planning_target, R.string.planning_target_hint,
        FieldKind.DECIMAL, 1, if (open) ImeAction.Next else ImeAction.Done)
    SectionHeading(stringResource(R.string.planning_hours_heading))
    Text(stringResource(R.string.planning_hours_intro))
    listOf(PlanningForm.Field.CHARGING_START to R.string.planning_hours_from,
        PlanningForm.Field.CHARGING_END to R.string.planning_hours_until).forEachIndexed { place, (field, label) ->
        TimeChoice(stringResource(label), edits.shown { planning.time(field) }, shown[field],
            rememberFieldFocus(problems, 2 + place, field in shown)) { time ->
            edits.make { planning.choose(field, time) }
        }
    }
    val lead = edits.shown { planning.lead() }
    ChoiceMenu(stringResource(R.string.planning_lead), lead?.let { NotifyLeadChoices.offered(it) }
        ?: NotifyLeadChoices.STANDARD, lead, { leadName(it) }, { chosen -> edits.make { planning.choose(chosen) } },
        shown[PlanningForm.Field.NOTIFY_LEAD],
        rememberFieldFocus(problems, 4, PlanningForm.Field.NOTIFY_LEAD in shown),
        hint = stringResource(R.string.planning_lead_hint))
    Fold(stringResource(R.string.more_settings), open, { opened -> if (!problem) closed = !opened }) {
        typed(PlanningForm.Field.MARGIN, R.string.planning_margin, R.string.planning_margin_hint, FieldKind.DECIMAL,
            5, ImeAction.Next)
        typed(PlanningForm.Field.IMPORT_TOLERANCE, R.string.planning_tolerance, R.string.planning_tolerance_hint,
            FieldKind.DECIMAL, 6, ImeAction.Next)
        typed(PlanningForm.Field.MINIMUM_WINDOW, R.string.planning_min_window, R.string.planning_min_window_hint,
            FieldKind.WHOLE, 7, ImeAction.Done)
    }
}

/**
 * @return "At the start" for 0, otherwise such as "20 minutes ahead"
 */
@Composable
internal fun leadName(lead: Duration): String = if (lead.isZero) stringResource(R.string.planning_lead_at_start)
    else lead.toMinutes().toInt().let { pluralStringResource(R.plurals.planning_lead_ahead, it, it) }
