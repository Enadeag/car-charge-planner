package nl.enadeag.carchargeplanner.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.form.FieldProblem
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting

/**
 * What a field says about its problem, in the app's language: one sentence per rule, without the allowed range, which
 * the field's hint gives.
 */
@Composable
internal fun problemText(problem: FieldProblem): String = stringResource(when (problem) {
    is FieldProblem.Refused -> when (problem.rule) {
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
    }
    FieldProblem.NotANumber -> R.string.problem_not_a_number
    FieldProblem.NotAWholeNumber -> R.string.problem_not_a_whole_number
    FieldProblem.NotFrom0To100 -> R.string.problem_level_out_of_range
    FieldProblem.Required -> R.string.problem_required
    FieldProblem.ServerAddress -> R.string.problem_server_address
    FieldProblem.LettersAndDigits -> R.string.problem_letters_and_digits
    FieldProblem.OneLine -> R.string.problem_one_line
    FieldProblem.NotChosen -> R.string.problem_not_chosen
    FieldProblem.NoPlaceChosen -> R.string.problem_no_place
})
