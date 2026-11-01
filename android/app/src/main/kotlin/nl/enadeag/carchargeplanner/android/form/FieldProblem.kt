package nl.enadeag.carchargeplanner.android.form

import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting

/**
 * What a field says is wrong after Next or Save, until the owner edits it: a rule of the engine's records, or what the
 * form itself checks. Each is one text of the screens (#314, #315), which map it in the owner's language.
 */
sealed interface FieldProblem {

    /**
     * The engine refused the value, such as a percentage above 100 or a minimum charge level not below the target.
     *
     * @property rule the rule the value breaks
     */
    data class Refused(val rule: InvalidSetting.Rule) : FieldProblem

    /** The text is not a number: empty, letters, a thousands separator, an exponent, a space or a plus sign. */
    data object NotANumber : FieldProblem

    /** A number with decimals where the field takes a whole number, such as amps or minutes; nothing is rounded. */
    data object NotAWholeNumber : FieldProblem

    /** The text is not a time such as 07:30. */
    data object NotATime : FieldProblem

    /** A forecast provider's setting that it needs has no value. */
    data object Required : FieldProblem

    /** Not a server address an API key may be sent to: https, or plain http only to this phone. */
    data object ServerAddress : FieldProblem

    /** A value that may hold only the letters A to Z and the digits 0 to 9, such as an API key. */
    data object LettersAndDigits : FieldProblem

    /** A value with a line break or another control character, which must be on one line. */
    data object OneLine : FieldProblem

    /** Set-up's location step without a place chosen; set-up does not go on without one. */
    data object NoPlaceChosen : FieldProblem
}
