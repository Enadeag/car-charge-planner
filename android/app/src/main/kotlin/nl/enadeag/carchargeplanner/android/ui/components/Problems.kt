package nl.enadeag.carchargeplanner.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.form.FieldProblem
import nl.enadeag.carchargeplanner.android.form.Limit
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting

/**
 * What a field says about its problem, in the app's language: one sentence per rule, and for a number outside its
 * setting's limit the limit itself, written as the field writes numbers.
 */
@Composable
internal fun problemText(problem: FieldProblem): String = when (problem) {
    is FieldProblem.OutOfRange -> limitText(problem.limit)
    is FieldProblem.Refused -> stringResource(when (problem.rule) {
        InvalidSetting.Rule.OUT_OF_RANGE -> R.string.problem_out_of_range
        InvalidSetting.Rule.NOT_BELOW_TARGET -> R.string.problem_not_below_target
        InvalidSetting.Rule.BELOW_MINIMUM_CURRENT -> R.string.problem_below_minimum_current
        InvalidSetting.Rule.SAME_AS_PRIMARY -> R.string.problem_same_as_primary
        InvalidSetting.Rule.NOT_AFTER_START -> R.string.problem_not_after_start
        InvalidSetting.Rule.SAME_AS_START -> R.string.problem_same_as_start
        InvalidSetting.Rule.PANEL_COUNT_OUT_OF_RANGE -> R.string.problem_panel_count
        InvalidSetting.Rule.PANEL_POWER_OUT_OF_RANGE -> R.string.problem_panel_power
        InvalidSetting.Rule.YEAR_PLACED_OUT_OF_RANGE -> R.string.problem_year_placed
        InvalidSetting.Rule.NO_YEAR_OR_POWER -> R.string.problem_no_year_or_power
    })
    FieldProblem.NotANumber -> stringResource(R.string.problem_not_a_number)
    FieldProblem.NotAWholeNumber -> stringResource(R.string.problem_not_a_whole_number)
    FieldProblem.NotFrom0To100 -> stringResource(R.string.problem_level_out_of_range)
    FieldProblem.Required -> stringResource(R.string.problem_required)
    FieldProblem.ServerAddress -> stringResource(R.string.problem_server_address)
    FieldProblem.LettersAndDigits -> stringResource(R.string.problem_letters_and_digits)
    FieldProblem.OneLine -> stringResource(R.string.problem_one_line)
    FieldProblem.NotChosen -> stringResource(R.string.problem_not_chosen)
    FieldProblem.NoPlaceChosen -> stringResource(R.string.problem_no_place)
}

@Composable
private fun limitText(limit: Limit): String {
    val ends = limit.written(NumberText(LocalConfiguration.current.locales[0])).toTypedArray()
    return stringResource(when (limit.shape) {
        Limit.Shape.FROM_TO -> R.string.problem_from_to
        Limit.Shape.AT_LEAST -> R.string.problem_at_least
        Limit.Shape.MORE_THAN -> R.string.problem_more_than
        Limit.Shape.MORE_THAN_AT_MOST -> R.string.problem_more_than_at_most
        Limit.Shape.AT_LEAST_BELOW -> R.string.problem_at_least_below
    }, *ends)
}
