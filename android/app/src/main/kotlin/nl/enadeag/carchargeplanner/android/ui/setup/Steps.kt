package nl.enadeag.carchargeplanner.android.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.format.TextStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.form.CarForm
import nl.enadeag.carchargeplanner.android.form.ChargerForm
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.form.PanelsForm
import nl.enadeag.carchargeplanner.android.form.WeeklyAwayForm
import nl.enadeag.carchargeplanner.android.ui.components.ChoiceMenu
import nl.enadeag.carchargeplanner.android.ui.components.ChoiceRow
import nl.enadeag.carchargeplanner.android.ui.components.EntryField
import nl.enadeag.carchargeplanner.android.ui.components.FieldKind
import nl.enadeag.carchargeplanner.android.ui.components.Fold
import nl.enadeag.carchargeplanner.android.ui.components.NoneLine
import nl.enadeag.carchargeplanner.android.ui.components.NumberField
import nl.enadeag.carchargeplanner.android.ui.components.ProblemFocus
import nl.enadeag.carchargeplanner.android.ui.components.SectionHeading
import nl.enadeag.carchargeplanner.android.ui.components.TimeField
import nl.enadeag.carchargeplanner.android.ui.components.RowFocus
import nl.enadeag.carchargeplanner.android.ui.components.RowHeading
import nl.enadeag.carchargeplanner.android.ui.components.rememberFieldFocus
import nl.enadeag.carchargeplanner.engine.config.CompassPoint
import nl.enadeag.carchargeplanner.engine.config.Phases
import nl.enadeag.carchargeplanner.engine.config.TiltChoice
import nl.enadeag.carchargeplanner.android.ui.components.AppOutlinedButton
import nl.enadeag.carchargeplanner.android.ui.components.AppTextButton

/** What the app does, what leaves the phone and to whom, and what to do when a value is not known; read only. */
@Composable
internal fun WelcomeStep(provider: String) {
    Text(stringResource(R.string.setup_welcome_intro))
    SectionHeading(stringResource(R.string.setup_welcome_privacy_title))
    Text(stringResource(R.string.setup_welcome_privacy, provider))
    Text(stringResource(R.string.setup_welcome_background, provider))
    Text(stringResource(R.string.setup_welcome_nothing_else))
    Text(stringResource(R.string.setup_welcome_settings))
    SectionHeading(stringResource(R.string.setup_welcome_typical_title))
    Text(stringResource(R.string.setup_welcome_typical))
}

/**
 * The one panel array of set-up; more are added in Settings.
 *
 * @param problems the step's fields in order, for the focus after Next
 */
@Composable
internal fun PanelsStep(panels: PanelsForm, edits: Edits, problems: ProblemFocus) {
    val rows by panels.rowIds.collectAsStateWithLifecycle()
    val row = rows.firstOrNull() ?: return
    Text(stringResource(R.string.setup_panels_intro))
    PanelArrayFields(panels, row, edits, problems, 0, last = true) { it }
}

/**
 * A panel array's fields, the same in set-up and Settings: the number of panels, the direction, the tilt, the year
 * placed and the power per panel, the peak power they give together, and the losses and the name folded under "More
 * settings", which opens by itself while one of them shows a problem. An array stored by its peak power says so above
 * them. Only the page's last field has the keyboard's Done: the power while the fold is closed, else the name.
 *
 * @param problems the page's fields in order, for the focus after Next or Save
 * @param order the place of the array's first field on the page; its others follow it
 * @param last whether the array ends the page
 * @param named the label of a field as the page names it, such as with the array's number
 */
@Composable
internal fun PanelArrayFields(panels: PanelsForm, row: Int, edits: Edits, problems: ProblemFocus, order: Int,
    last: Boolean, named: @Composable (String) -> String) {
    val shown by panels.problems(row).collectAsStateWithLifecycle()
    val typical = edits.shown { panels.typical(row) }
    val numbers = NumberText(LocalConfiguration.current.locales[0])
    var folded by rememberSaveable(row) { mutableStateOf(true) }
    val open = !folded || PanelsForm.Field.LOSSES in shown
    edits.shown { panels.keptKwp(row) }?.let { kwp ->
        Text(stringResource(R.string.panel_now_set, numbers.write(kwp, if (Math.round(kwp * 10) / 10.0 == kwp) 1 else 2)))
    }
    @Composable
    fun typed(field: PanelsForm.Field, label: Int, hint: String, place: Int, imeAction: ImeAction = ImeAction.Next,
        kind: FieldKind = FieldKind.WHOLE, placeholder: String? = null) {
        EntryField(named(stringResource(label)), hint, edits.shown { panels.text(row, field) },
            { text -> edits.make { panels.edit(row, field, text) } }, kind, shown[field], field in typical,
            rememberFieldFocus(problems, order + place, field in shown), imeAction = imeAction,
            placeholder = placeholder)
    }
    typed(PanelsForm.Field.COUNT, R.string.panel_count, "", 0)
    ChoiceMenu(named(stringResource(R.string.panel_direction)), CompassPoint.entries,
        edits.shown { panels.direction(row) }, { stringResource(directionName(it)) },
        { point -> edits.make { panels.choose(row, point) } }, shown[PanelsForm.Field.DIRECTION],
        rememberFieldFocus(problems, order + 1, PanelsForm.Field.DIRECTION in shown),
        PanelsForm.Field.DIRECTION in typical)
    ChoiceMenu(named(stringResource(R.string.panel_tilt)), TiltChoice.entries, edits.shown { panels.tilt(row) },
        { stringResource(tiltName(it)) }, { tilt -> edits.make { panels.choose(row, tilt) } },
        shown[PanelsForm.Field.TILT], rememberFieldFocus(problems, order + 2, PanelsForm.Field.TILT in shown),
        PanelsForm.Field.TILT in typical)
    typed(PanelsForm.Field.YEAR, R.string.panel_year, "", 3)
    val estimate = edits.shown { panels.typicalWattPeak(row) }
    val powerEmpty = edits.shown { panels.text(row, PanelsForm.Field.POWER) }.isBlank()
    typed(PanelsForm.Field.POWER, R.string.panel_power,
        if (estimate != null && powerEmpty) stringResource(R.string.panel_power_hint_typical, estimate,
            edits.shown { panels.text(row, PanelsForm.Field.YEAR) }.trim())
        else stringResource(R.string.panel_power_hint), 4,
        if (last && !open) ImeAction.Done else ImeAction.Next, placeholder = estimate?.toString())
    edits.shown { panels.together(row) }?.let { kwp ->
        Text(stringResource(R.string.panel_together, numbers.write(kwp, 1)), style = MaterialTheme.typography.bodyLarge)
    }
    Fold(named(stringResource(R.string.more_settings)), open, { folded = !it }) {
        typed(PanelsForm.Field.LOSSES, R.string.panel_losses, stringResource(R.string.panel_losses_hint), 5,
            kind = FieldKind.DECIMAL)
        typed(PanelsForm.Field.NAME, R.string.panel_name, stringResource(R.string.panel_name_hint), 6,
            if (last) ImeAction.Done else ImeAction.Next, FieldKind.TEXT)
    }
}

internal fun directionName(point: CompassPoint): Int = when (point) {
    CompassPoint.NORTH -> R.string.direction_north
    CompassPoint.NORTH_EAST -> R.string.direction_north_east
    CompassPoint.EAST -> R.string.direction_east
    CompassPoint.SOUTH_EAST -> R.string.direction_south_east
    CompassPoint.SOUTH -> R.string.direction_south
    CompassPoint.SOUTH_WEST -> R.string.direction_south_west
    CompassPoint.WEST -> R.string.direction_west
    CompassPoint.NORTH_WEST -> R.string.direction_north_west
}

internal fun tiltName(tilt: TiltChoice): Int = when (tilt) {
    TiltChoice.FLAT -> R.string.tilt_flat
    TiltChoice.PITCHED -> R.string.tilt_pitched
    TiltChoice.STEEP -> R.string.tilt_steep
    TiltChoice.UPRIGHT -> R.string.tilt_upright
}

/**
 * The car: its usable battery, consumption, other driving and trip surcharge, and "I don't know".
 *
 * @param problems the step's fields in order, for the focus after Next
 * @param lastAction the keyboard's action key on the trip surcharge: Done when it is the page's last field
 */
@Composable
internal fun CarStep(car: CarForm, edits: Edits, problems: ProblemFocus, lastAction: ImeAction = ImeAction.Done) {
    val shown by car.problems.collectAsStateWithLifecycle()
    val typical = edits.shown { car.typical() }
    listOf(
        CarForm.Field.BATTERY to (R.string.car_battery to R.string.car_battery_hint),
        CarForm.Field.CONSUMPTION to (R.string.car_consumption to R.string.car_consumption_hint),
        CarForm.Field.DAILY_DISTANCE to (R.string.car_other_driving to R.string.car_other_driving_hint),
        CarForm.Field.TRIP_SURCHARGE to (R.string.car_trip_surcharge to R.string.car_trip_surcharge_hint),
    ).forEachIndexed { order, (field, texts) ->
        NumberField(stringResource(texts.first), stringResource(texts.second), edits.shown { car.text(field) },
            { text -> edits.make { car.edit(field, text) } }, FieldKind.DECIMAL, shown[field], field in typical,
            rememberFieldFocus(problems, order, field in shown),
            if (field == CarForm.Field.TRIP_SURCHARGE) lastAction else ImeAction.Next)
    }
    UseTypical { edits.make { car.useTypical() } }
}

/**
 * The wall charger: its phases, lowest and highest current, voltage and efficiency, and "I don't know".
 *
 * @param problems the step's fields in order, for the focus after Next
 */
@Composable
internal fun ChargerStep(charger: ChargerForm, edits: Edits, problems: ProblemFocus) {
    val shown by charger.problems.collectAsStateWithLifecycle()
    val typical = edits.shown { charger.typical() }
    ChoiceRow(stringResource(R.string.charger_phases), stringResource(R.string.charger_phases_hint), Phases.entries,
        edits.shown { charger.phases() },
        { stringResource(if (it == Phases.ONE) R.string.phases_one else R.string.phases_three) },
        { phases -> edits.make { charger.choose(phases) } }, ChargerForm.Field.PHASES in typical)
    listOf(
        Triple(ChargerForm.Field.MIN_CURRENT, R.string.charger_min_current to R.string.charger_min_current_hint,
            FieldKind.WHOLE),
        Triple(ChargerForm.Field.MAX_CURRENT, R.string.charger_max_current to R.string.charger_max_current_hint,
            FieldKind.WHOLE),
        Triple(ChargerForm.Field.VOLTAGE, R.string.charger_voltage to R.string.charger_voltage_hint, FieldKind.DECIMAL),
        Triple(ChargerForm.Field.EFFICIENCY, R.string.charger_efficiency to R.string.charger_efficiency_hint,
            FieldKind.DECIMAL),
    ).forEachIndexed { order, (field, texts, kind) ->
        NumberField(stringResource(texts.first), stringResource(texts.second), edits.shown { charger.text(field) },
            { text -> edits.make { charger.edit(field, text) } }, kind, shown[field], field in typical,
            rememberFieldFocus(problems, order, field in shown),
            if (field == ChargerForm.Field.EFFICIENCY) ImeAction.Done else ImeAction.Next)
    }
    UseTypical { edits.make { charger.useTypical() } }
}

/**
 * The weekly away periods, which start empty and may be skipped.
 *
 * @param problems the step's fields in order, for the focus after Next
 */
@Composable
internal fun AwayStep(away: WeeklyAwayForm, edits: Edits, problems: ProblemFocus) {
    Text(stringResource(R.string.setup_away_intro))
    SectionHeading(stringResource(R.string.away_heading))
    AwayPeriods(away, edits, problems)
}

/**
 * The weekly away periods, each with its controls named by its number, "None" when there are none, and Add. They
 * end the page, so the last period's end time has the keyboard's Done. After Add the focus goes to the new period's
 * heading, after Remove to the one before, to the next when the first went, or to the line that says there are none
 * when none is left.
 *
 * @param problems the page's fields in order, for the focus after Next or Save
 */
@Composable
internal fun AwayPeriods(away: WeeklyAwayForm, edits: Edits, problems: ProblemFocus) {
    val rows by away.rowIds.collectAsStateWithLifecycle()
    val focus = remember { RowFocus<Int>() }
    if (rows.isEmpty()) {
        NoneLine(stringResource(R.string.away_none), focus)
    }
    rows.forEachIndexed { index, row ->
        key(row) {
            AwayPeriod(away, row, index, rows, edits, problems, focus)
        }
    }
    AppOutlinedButton(onClick = { edits.make { focus.next = away.add() } }) {
        Text(stringResource(R.string.away_add))
    }
}

/**
 * One weekly away period: its place in the list as a heading, the weekday, the times, and Remove. The weekday group,
 * each time field and Remove are named with the period's number, so a screen reader that lands on one, as on a problem
 * after Next, says which period; the weekday's radio buttons are named by the day, inside that group. Only the last
 * period's end time has the keyboard's Done.
 */
@Composable
private fun AwayPeriod(away: WeeklyAwayForm, row: Int, index: Int, rows: List<Int>, edits: Edits,
    problems: ProblemFocus, focus: RowFocus<Int>) {
    val last = index == rows.lastIndex
    val shown by away.problems(row).collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    val number = index + 1
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RowHeading(stringResource(R.string.away_period, number), MaterialTheme.typography.titleSmall, focus, row)
        ChoiceRow(stringResource(R.string.away_weekday, number), null, DayOfWeek.entries,
            edits.shown { DayOfWeek.entries.firstOrNull { it.name == away.text(row, WeeklyAwayForm.Field.WEEKDAY) } },
            { it.getDisplayName(TextStyle.FULL, locale) }, { day -> edits.make { away.choose(row, day) } })
        listOf(WeeklyAwayForm.Field.START to R.string.away_from, WeeklyAwayForm.Field.END to R.string.away_until)
            .forEachIndexed { place, (field, label) ->
                TimeField(stringResource(label, number), edits.shown { away.text(row, field) },
                    { text -> edits.make { away.edit(row, field, text) } }, shown[field],
                    rememberFieldFocus(problems, 100 + 2 * index + place, field in shown),
                    if (field == WeeklyAwayForm.Field.END && last) ImeAction.Done else ImeAction.Next)
            }
        AppTextButton(onClick = {
            edits.make {
                focus.removing(rows, row)
                away.remove(row)
            }
        }) {
            Text(stringResource(R.string.away_remove, number))
        }
    }
}

/**
 * "I don't know": fills in the typical values and marks them.
 *
 * @param name the button's text; the plain "I don't know" unless several on a page need telling apart
 */
@Composable
internal fun UseTypical(name: String = stringResource(R.string.use_typical), onClick: () -> Unit) {
    AppOutlinedButton(onClick = onClick) {
        Text(name)
    }
}
