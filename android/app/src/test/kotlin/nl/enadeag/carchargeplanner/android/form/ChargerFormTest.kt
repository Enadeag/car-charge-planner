package nl.enadeag.carchargeplanner.android.form

import androidx.lifecycle.SavedStateHandle
import java.util.Locale
import nl.enadeag.carchargeplanner.android.afterTheSystemEndedTheApp
import nl.enadeag.carchargeplanner.engine.config.ChargePowerChoice
import nl.enadeag.carchargeplanner.engine.config.Charger
import nl.enadeag.carchargeplanner.engine.config.HomeChargePower
import nl.enadeag.carchargeplanner.engine.config.Phases
import nl.enadeag.carchargeplanner.engine.config.TypicalValues
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

class ChargerFormTest {

    private val handle = SavedStateHandle()
    private val numbers = NumberText(Locale.ENGLISH)
    private val form = ChargerForm(handle, numbers)

    /** A charger whose efficiency has more decimals than its field shows. */
    private val stored = DRAFT.withSystem(DRAFT.system().withCharger(Charger(Phases.THREE, 8, 32, 231.4, 0.937)),
        TypicalValues.NONE)

    @Test
    fun theEfficiencyIsShownInPercentAndAnUntouchedOneIsTheStoredShareExactly() {
        form.load(stored)

        assertThat(form.text(ChargerForm.Field.EFFICIENCY)).isEqualTo("94")
        assertThat(form.phases()).isEqualTo(Phases.THREE)
        assertThat(form.read(stored)).isEqualTo(stored)
    }

    @Test
    fun aTypedEfficiencyInPercentIsKeptAsAShare() {
        form.load(stored)
        form.edit(ChargerForm.Field.EFFICIENCY, "85")

        assertThat(form.read(stored)!!.system().charger().efficiency()).isEqualTo(0.85)
    }

    @ParameterizedTest
    @ValueSource(strings = ["16.5", "16,0"])
    fun aCurrentWithDecimalsIsNotAWholeNumberAndIsNotRounded(text: String) {
        form.load(stored)
        form.edit(ChargerForm.Field.MAX_CURRENT, text)

        assertThat(form.check(stored)).isNull()
        assertThat(form.problems.value).containsExactlyEntriesOf(mapOf(
            ChargerForm.Field.MAX_CURRENT to FieldProblem.NotAWholeNumber))
    }

    @Test
    fun aHighestCurrentBelowTheLowestShowsAtTheHighest() {
        form.load(stored)
        form.edit(ChargerForm.Field.MIN_CURRENT, "10")
        form.edit(ChargerForm.Field.MAX_CURRENT, "8")

        assertThat(form.check(stored)).isNull()
        assertThat(form.problems.value).containsExactlyEntriesOf(mapOf(
            ChargerForm.Field.MAX_CURRENT to FieldProblem.Refused(InvalidSetting.Rule.BELOW_MINIMUM_CURRENT)))
    }

    @Test
    fun anEfficiencyAboveAHundredPercentIsOutOfRange() {
        form.load(stored)
        form.edit(ChargerForm.Field.EFFICIENCY, "101")

        assertThat(form.check(stored)).isNull()
        assertThat(form.problems.value).containsExactlyEntriesOf(mapOf(
            ChargerForm.Field.EFFICIENCY to FieldProblem.Refused(InvalidSetting.Rule.OUT_OF_RANGE)))
    }

    @Test
    fun choosingThePhasesTakesTheirTypicalMarkAway() {
        form.load(DRAFT)
        assertThat(form.typical()).containsExactlyInAnyOrder(ChargerForm.Field.PHASES, ChargerForm.Field.MIN_CURRENT,
            ChargerForm.Field.MAX_CURRENT, ChargerForm.Field.VOLTAGE, ChargerForm.Field.EFFICIENCY)

        form.choose(Phases.THREE)

        val read = form.read(DRAFT)!!
        assertThat(read.system().charger().phases()).isEqualTo(Phases.THREE)
        assertThat(read.typical().system()).doesNotContain(ConfigSetting.CHARGER_PHASES)
            .contains(ConfigSetting.CHARGER_MIN_CURRENT)
    }

    @Test
    fun askingForTypicalValuesSetsTheMinimumTheVoltageAndTheEfficiencyOnlyAndMarksThem() {
        form.load(stored)

        form.useTypical()

        assertThat(form.read(stored)!!.system().charger()).isEqualTo(Charger(Phases.THREE, 6, 32, 230.0, 0.9))
        assertThat(form.typical()).containsExactlyInAnyOrder(ChargerForm.Field.MIN_CURRENT, ChargerForm.Field.VOLTAGE,
            ChargerForm.Field.EFFICIENCY)
        assertThat(form.power()).isEqualTo(ChargePowerChoice.KW_22)
    }

    private fun withCharger(charger: Charger) = DRAFT.withSystem(DRAFT.system().withCharger(charger),
        TypicalValues.NONE)

    @Test
    fun onePhaseOf16AmpsAt230VoltsIs3Point7KilowattAndTheSameAt240IsOther() {
        form.load(withCharger(Charger(Phases.ONE, 6, 16, 230.0, 0.9)))
        assertThat(form.power()).isEqualTo(ChargePowerChoice.KW_3_7)

        form.load(withCharger(Charger(Phases.ONE, 6, 16, 240.0, 0.9)))
        assertThat(form.power()).isEqualTo(ChargePowerChoice.OTHER)
    }

    @Test
    fun aStoredVoltageJustOff230IsOtherThoughItsFieldShows230() {
        val config = withCharger(Charger(Phases.ONE, 6, 16, 230.4, 0.9))
        form.load(config)

        assertThat(form.text(ChargerForm.Field.VOLTAGE)).isEqualTo("230")
        assertThat(form.power()).isEqualTo(ChargePowerChoice.OTHER)

        form.choose(HomeChargePower.KW_3_7)

        assertThat(form.power()).isEqualTo(ChargePowerChoice.KW_3_7)
        assertThat(form.read(config)!!.system().charger().volts()).isEqualTo(230.0)
    }

    @Test
    fun aVoltageTypedBackTo230AfterAPowerSetItIs230() {
        val config = withCharger(Charger(Phases.ONE, 6, 16, 230.4, 0.9))
        form.load(config)
        form.choose(HomeChargePower.KW_3_7)

        form.edit(ChargerForm.Field.VOLTAGE, "231")
        form.edit(ChargerForm.Field.VOLTAGE, "230")

        assertThat(form.power()).isEqualTo(ChargePowerChoice.KW_3_7)
        assertThat(form.read(config)!!.system().charger().volts()).isEqualTo(230.0)
    }

    @Test
    fun iDontKnowKeepsAChosenOtherWhileTheVoltageStays230() {
        val config = withCharger(Charger(Phases.ONE, 10, 16, 230.0, 0.8))
        form.load(config)
        form.chooseOther()

        form.useTypical()

        assertThat(form.power()).isEqualTo(ChargePowerChoice.OTHER)
        assertThat(form.read(config)!!.system().charger()).isEqualTo(Charger(Phases.ONE, 6, 16, 230.0, 0.9))
    }

    @Test
    fun iDontKnowTurnsAChosenOtherIntoThePowerOnceTheVoltageBecomes230() {
        form.load(withCharger(Charger(Phases.ONE, 10, 16, 240.0, 0.8)))
        form.chooseOther()

        form.useTypical()

        assertThat(form.power()).isEqualTo(ChargePowerChoice.KW_3_7)
    }

    @ParameterizedTest
    @EnumSource(value = ChargerForm.Field::class, names = ["MAX_CURRENT", "VOLTAGE"])
    fun anUnreadableHighestCurrentOrVoltageShowsOther(field: ChargerForm.Field) {
        form.load(withCharger(Charger(Phases.ONE, 6, 16, 230.0, 0.9)))

        form.edit(field, "abc")

        assertThat(form.power()).isEqualTo(ChargePowerChoice.OTHER)
    }

    @ParameterizedTest
    @EnumSource(HomeChargePower::class)
    fun aPowerSetsThePhasesTheMaximumAnd230VoltsAndKeepsTheMinimumAndTheEfficiencyExactly(power: HomeChargePower) {
        val config = withCharger(Charger(Phases.THREE, 7, 25, 240.0, 0.453))
        form.load(config)

        form.choose(power)

        assertThat(form.power()).isEqualTo(ChargePowerChoice.of(power))
        val read = form.read(config)!!
        assertThat(read.system().charger()).isEqualTo(Charger(power.phases(), 7, power.maxAmps(), 230.0, 0.453))
        assertThat(read.typical().system()).isEmpty()
    }

    @Test
    fun otherChangesNoValueUntilAValueChangesAndThenTheChoiceFollowsTheValues() {
        val config = withCharger(Charger(Phases.THREE, 6, 16, 230.0, 0.9))
        form.load(config)

        form.chooseOther()

        assertThat(form.power()).isEqualTo(ChargePowerChoice.OTHER)
        assertThat(form.isEdited()).isFalse()
        assertThat(form.read(config)).isEqualTo(config)
        form.edit(ChargerForm.Field.MAX_CURRENT, "32")
        assertThat(form.power()).isEqualTo(ChargePowerChoice.KW_22)
    }

    @ParameterizedTest
    @CsvSource("THREE, 25", "ONE, 25", "THREE, 13", "ONE, 20")
    fun aChargerOfOtherCurrentsIsOther(phases: Phases, maxAmps: Int) {
        form.load(withCharger(Charger(Phases.ONE, 6, 16, 230.0, 0.9)))

        form.choose(phases)
        form.edit(ChargerForm.Field.MAX_CURRENT, maxAmps.toString())

        assertThat(form.power()).isEqualTo(ChargePowerChoice.OTHER)
    }

    @Test
    fun aPowerWhoseMaximumEndsBelowATypedMinimumShowsTheProblemAtTheMaximumAndChangesNothingElse() {
        val config = withCharger(Charger(Phases.ONE, 6, 32, 230.0, 0.9))
        form.load(config)
        form.edit(ChargerForm.Field.MIN_CURRENT, "20")

        form.choose(HomeChargePower.KW_11)

        assertThat(form.check(config)).isNull()
        assertThat(form.problems.value).containsExactlyEntriesOf(mapOf(
            ChargerForm.Field.MAX_CURRENT to FieldProblem.Refused(InvalidSetting.Rule.BELOW_MINIMUM_CURRENT)))
        assertThat(form.text(ChargerForm.Field.MIN_CURRENT)).isEqualTo("20")
    }

    @Test
    fun inSetUpThePowerStartsNotChosenAndIsRefusedUntilChosen() {
        val setUp = ChargerForm(handle, numbers, newWhenTypical = true)
        setUp.load(DRAFT)
        assertThat(setUp.power()).isNull()

        assertThat(setUp.check(DRAFT)).isNull()
        assertThat(setUp.problems.value).containsExactlyEntriesOf(mapOf(
            ChargerForm.Field.POWER to FieldProblem.NotChosen))

        setUp.useTypical()
        assertThat(setUp.power()).`as`("I don't know chooses no power").isNull()
        setUp.choose(HomeChargePower.KW_7_4)
        val read = setUp.check(DRAFT)!!
        assertThat(read.system().charger()).isEqualTo(Charger(Phases.ONE, 6, 32, 230.0, 0.9))
        assertThat(read.typical().system()).contains(ConfigSetting.CHARGER_MIN_CURRENT,
            ConfigSetting.CHARGER_VOLTAGE, ConfigSetting.CHARGER_EFFICIENCY)
            .doesNotContain(ConfigSetting.CHARGER_PHASES, ConfigSetting.CHARGER_MAX_CURRENT)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun aPowerOrOtherChosenAfterARefusedNextTakesTheProblemAway(other: Boolean) {
        val setUp = ChargerForm(handle, numbers, newWhenTypical = true)
        setUp.load(DRAFT)
        setUp.check(DRAFT)
        assertThat(setUp.problems.value).containsKey(ChargerForm.Field.POWER)

        if (other) setUp.chooseOther() else setUp.choose(HomeChargePower.KW_11)

        assertThat(setUp.problems.value).isEmpty()
    }

    @Test
    fun inSetUpAStoredChargerOfTheOwnersOwnShowsItsPower() {
        val setUp = ChargerForm(handle, numbers, newWhenTypical = true)

        setUp.load(withCharger(Charger(Phases.THREE, 6, 16, 230.0, 0.9)))

        assertThat(setUp.power()).isEqualTo(ChargePowerChoice.KW_11)
    }

    @Test
    fun aChosenPowerAndAChosenOtherSurviveTheSystemEndingTheApp() {
        val setUp = ChargerForm(handle, numbers, newWhenTypical = true)
        setUp.load(DRAFT)
        setUp.choose(HomeChargePower.KW_22)
        assertThat(ChargerForm(handle.afterTheSystemEndedTheApp(), numbers, newWhenTypical = true).power())
            .isEqualTo(ChargePowerChoice.KW_22)

        setUp.chooseOther()
        assertThat(ChargerForm(handle.afterTheSystemEndedTheApp(), numbers, newWhenTypical = true).power())
            .isEqualTo(ChargePowerChoice.OTHER)
    }

    @Test
    fun aKeptPowerThatNoLongerFitsCountsAsNotChosen() {
        val setUp = ChargerForm(handle, numbers, newWhenTypical = true)
        setUp.load(DRAFT)
        setUp.choose(HomeChargePower.KW_22)
        handle["charger.text.POWER"] = "KW_9_9"

        val again = ChargerForm(handle.afterTheSystemEndedTheApp(), numbers, newWhenTypical = true)

        assertThat(again.power()).isNull()
        assertThat(again.check(DRAFT)).isNull()
        assertThat(again.problems.value).containsKey(ChargerForm.Field.POWER)
    }

    @Test
    fun theChosenPhasesTheTextsAndTheTypicalMarksSurviveTheSystemEndingTheApp() {
        form.load(DRAFT)
        form.choose(Phases.THREE)
        form.edit(ChargerForm.Field.MAX_CURRENT, "25")

        val again = ChargerForm(handle.afterTheSystemEndedTheApp(), numbers)

        assertThat(again.phases()).isEqualTo(Phases.THREE)
        assertThat(again.text(ChargerForm.Field.MAX_CURRENT)).isEqualTo("25")
        assertThat(again.typical()).doesNotContain(ChargerForm.Field.PHASES, ChargerForm.Field.MAX_CURRENT)
            .contains(ChargerForm.Field.MIN_CURRENT)
        val charger = again.read(DRAFT)!!.system().charger()
        assertThat(charger.phases()).isEqualTo(Phases.THREE)
        assertThat(charger.maxAmps()).isEqualTo(25)
    }
}
