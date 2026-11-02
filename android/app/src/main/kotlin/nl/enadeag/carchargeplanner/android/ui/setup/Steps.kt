package nl.enadeag.carchargeplanner.android.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.format.TextStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.form.CarForm
import nl.enadeag.carchargeplanner.android.form.ChargerForm
import nl.enadeag.carchargeplanner.android.form.HouseForm
import nl.enadeag.carchargeplanner.android.form.PanelsForm
import nl.enadeag.carchargeplanner.android.form.WeeklyAwayForm
import nl.enadeag.carchargeplanner.android.ui.components.ChoiceRow
import nl.enadeag.carchargeplanner.android.ui.components.FieldKind
import nl.enadeag.carchargeplanner.android.ui.components.NumberField
import nl.enadeag.carchargeplanner.android.ui.components.ProblemFocus
import nl.enadeag.carchargeplanner.android.ui.components.SectionHeading
import nl.enadeag.carchargeplanner.android.ui.components.TimeField
import nl.enadeag.carchargeplanner.android.ui.components.rememberFieldFocus
import nl.enadeag.carchargeplanner.engine.config.Phases

/**
 * What the app does, what leaves the phone and to whom, and what to do when a value is not known; read only.
 *
 * @param provider the forecast provider's name, such as Open-Meteo
 */
@Composable
internal fun WelcomeStep(provider: String) {
    Text(stringResource(R.string.setup_welcome_intro))
    SectionHeading(stringResource(R.string.setup_welcome_privacy_title))
    Text(stringResource(R.string.setup_welcome_privacy, provider))
    Text(stringResource(R.string.setup_welcome_nothing_else))
    Text(stringResource(R.string.setup_welcome_settings))
    SectionHeading(stringResource(R.string.setup_welcome_typical_title))
    Text(stringResource(R.string.setup_welcome_typical))
}

/**
 * The one panel array of set-up: its size, tilt, direction and losses, and "I don't know".
 *
 * @param panels the panels' form, which holds one array in set-up
 * @param edits counts each edit, so the fields show it
 * @param problems the step's fields in order, for the focus after Next
 */
@Composable
internal fun PanelsStep(panels: PanelsForm, edits: Edits, problems: ProblemFocus) {
    val rows by panels.rowIds.collectAsStateWithLifecycle()
    val row = rows.firstOrNull() ?: return
    val shown by panels.problems(row).collectAsStateWithLifecycle()
    val typical = edits.shown { panels.typical(row) }
    Text(stringResource(R.string.setup_panels_intro))
    listOf(
        Triple(PanelsForm.Field.SIZE, R.string.panel_size to R.string.panel_size_hint, FieldKind.DECIMAL),
        Triple(PanelsForm.Field.TILT, R.string.panel_tilt to R.string.panel_tilt_hint, FieldKind.DECIMAL),
        Triple(PanelsForm.Field.DIRECTION, R.string.panel_direction to R.string.panel_direction_hint, FieldKind.SIGNED),
        Triple(PanelsForm.Field.LOSSES, R.string.panel_losses to R.string.panel_losses_hint, FieldKind.DECIMAL),
    ).forEachIndexed { order, (field, texts, kind) ->
        NumberField(stringResource(texts.first), stringResource(texts.second), edits.shown { panels.text(row, field) },
            { text -> edits.make { panels.edit(row, field, text) } }, kind, shown[field], field in typical,
            rememberFieldFocus(problems, order, field in shown))
    }
    UseTypical { edits.make { panels.useTypical(row) } }
}

/**
 * The car: its usable battery, consumption, other driving and trip surcharge, and "I don't know".
 *
 * @param car the car's form
 * @param edits counts each edit, so the fields show it
 * @param problems the step's fields in order, for the focus after Next
 */
@Composable
internal fun CarStep(car: CarForm, edits: Edits, problems: ProblemFocus) {
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
            rememberFieldFocus(problems, order, field in shown))
    }
    UseTypical { edits.make { car.useTypical() } }
}

/**
 * The wall charger: its phases, lowest and highest current, voltage and efficiency, and "I don't know".
 *
 * @param charger the charger's form
 * @param edits counts each edit, so the fields show it
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
            rememberFieldFocus(problems, order, field in shown))
    }
    UseTypical { edits.make { charger.useTypical() } }
}

/**
 * What makes the plan better and may be skipped: the house's baseload with "I don't know", and the weekly away
 * periods, which start empty.
 *
 * @param house the house's form
 * @param away the weekly away periods' form
 * @param edits counts each edit, so the fields show it
 * @param problems the step's fields in order, for the focus after Next
 */
@Composable
internal fun MoreStep(house: HouseForm, away: WeeklyAwayForm, edits: Edits, problems: ProblemFocus) {
    val houseProblems by house.problems.collectAsStateWithLifecycle()
    val rows by away.rowIds.collectAsStateWithLifecycle()
    Text(stringResource(R.string.setup_more_intro))
    SectionHeading(stringResource(R.string.house_heading))
    val baseload = HouseForm.Field.BASELOAD
    NumberField(stringResource(R.string.baseload), stringResource(R.string.baseload_hint),
        edits.shown { house.text(baseload) }, { text -> edits.make { house.edit(baseload, text) } },
        FieldKind.DECIMAL, houseProblems[baseload], baseload in edits.shown { house.typical() },
        rememberFieldFocus(problems, 0, baseload in houseProblems))
    UseTypical { edits.make { house.useTypical() } }
    SectionHeading(stringResource(R.string.away_heading))
    if (rows.isEmpty()) {
        Text(stringResource(R.string.away_none))
    }
    rows.forEachIndexed { index, row -> AwayPeriod(away, row, index, edits, problems) }
    OutlinedButton(onClick = { edits.make { away.add() } }) {
        Text(stringResource(R.string.away_add))
    }
}

/** One weekly away period: its place in the list, the weekday, the times, and Remove. */
@Composable
private fun AwayPeriod(away: WeeklyAwayForm, row: Int, index: Int, edits: Edits, problems: ProblemFocus) {
    val shown by away.problems(row).collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    val number = index + 1
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.away_period, number), style = MaterialTheme.typography.titleSmall)
        ChoiceRow(stringResource(R.string.away_weekday), null, DayOfWeek.entries,
            edits.shown { DayOfWeek.entries.firstOrNull { it.name == away.text(row, WeeklyAwayForm.Field.WEEKDAY) } },
            { it.getDisplayName(TextStyle.FULL, locale) }, { day -> edits.make { away.choose(row, day) } })
        listOf(WeeklyAwayForm.Field.START to R.string.away_from, WeeklyAwayForm.Field.END to R.string.away_until)
            .forEachIndexed { place, (field, label) ->
                TimeField(stringResource(label), edits.shown { away.text(row, field) },
                    { text -> edits.make { away.edit(row, field, text) } }, shown[field],
                    rememberFieldFocus(problems, 100 + 2 * index + place, field in shown))
            }
        TextButton(onClick = { edits.make { away.remove(row) } }) {
            Text(stringResource(R.string.away_remove, number))
        }
    }
}

/** "I don't know": fills in the typical values and marks them. */
@Composable
private fun UseTypical(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Text(stringResource(R.string.use_typical))
    }
}
