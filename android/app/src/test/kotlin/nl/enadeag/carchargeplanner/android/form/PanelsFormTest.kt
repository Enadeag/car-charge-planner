package nl.enadeag.carchargeplanner.android.form

import androidx.lifecycle.SavedStateHandle
import java.time.Year
import java.util.Locale
import java.util.Optional
import java.util.OptionalInt
import nl.enadeag.carchargeplanner.android.afterTheSystemEndedTheApp
import nl.enadeag.carchargeplanner.engine.config.CompassPoint
import nl.enadeag.carchargeplanner.engine.config.PanelArray
import nl.enadeag.carchargeplanner.engine.config.PanelSize
import nl.enadeag.carchargeplanner.engine.config.TiltChoice
import nl.enadeag.carchargeplanner.engine.config.TypicalValues
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class PanelsFormTest {

    private val handle = SavedStateHandle()
    private val numbers = NumberText(Locale.ENGLISH)
    private val thisYear = { Year.of(2026) }
    private val form = PanelsForm(handle, numbers, thisYear)

    private val east = PanelArray("east", PanelSize.Panels(12, OptionalInt.of(300), Optional.of(Year.of(2019))),
        35.0, -90.0, 0.137)
    private val west = PanelArray("west", 2.0, 30.0, 100.0, 0.14)
    private val stored = DRAFT.withSystem(DRAFT.system().withPanels(listOf(east, west)), TypicalValues.NONE)

    private fun panels(count: Int, wattPeak: Int?, year: Int?) = PanelSize.Panels(count,
        wattPeak?.let { OptionalInt.of(it) } ?: OptionalInt.empty(), Optional.ofNullable(year?.let { Year.of(it) }))

    @Test
    fun untouchedArraysAreTheStoredOnesExactly() {
        form.load(stored)
        val (first, second) = form.rowIds.value

        assertThat(form.text(first, PanelsForm.Field.COUNT)).isEqualTo("12")
        assertThat(form.text(first, PanelsForm.Field.LOSSES)).isEqualTo("14")
        assertThat(form.text(second, PanelsForm.Field.COUNT)).isEmpty()
        assertThat(form.read(stored)).isEqualTo(stored)
        assertThat(form.isEdited()).isFalse()
    }

    @Test
    fun aStoredYearAfterThisYearIsKeptUntilTheOwnerChangesTheSize() {
        val future = PanelArray("shed", panels(10, null, 2027), 35.0, 0.0, 0.14)
        val config = DRAFT.withSystem(DRAFT.system().withPanels(listOf(future)), TypicalValues.NONE)
        form.load(config)
        val row = form.rowIds.value.single()
        form.edit(row, PanelsForm.Field.NAME, "shed roof")

        assertThat(form.check(config)!!.system().panels()).containsExactly(
            PanelArray("shed roof", future.size(), 35.0, 0.0, 0.14))
        assertThat(form.together(row)).isEqualTo(4.3)

        form.edit(row, PanelsForm.Field.COUNT, "12")

        assertThat(form.check(config)).isNull()
        assertThat(form.problems(row).value).containsExactlyEntriesOf(mapOf(
            PanelsForm.Field.YEAR to FieldProblem.Refused(InvalidSetting.Rule.YEAR_PLACED_OUT_OF_RANGE)))
    }

    @Test
    fun anArrayStoredByItsPeakPowerSaysSoAndKeepsItUntilItsPanelsAreEntered() {
        form.load(stored)
        val second = form.rowIds.value[1]
        assertThat(form.keptKwp(second)).isEqualTo(2.0)

        form.edit(second, PanelsForm.Field.COUNT, "8")
        form.edit(second, PanelsForm.Field.POWER, "410")

        assertThat(form.keptKwp(second)).isNull()
        assertThat(form.read(stored)!!.system().panels()[1])
            .isEqualTo(PanelArray("west", panels(8, 410, null), 30.0, 100.0, 0.14))
    }

    @Test
    fun aStoredDirectionAndTiltShowAsTheNearestChoiceButStayUntilChosen() {
        form.load(stored)
        val second = form.rowIds.value[1]

        assertThat(form.direction(second)).isEqualTo(CompassPoint.WEST)
        assertThat(form.tilt(second)).isEqualTo(TiltChoice.PITCHED)
        form.edit(second, PanelsForm.Field.NAME, "garage")
        assertThat(form.read(stored)!!.system().panels()[1]).isEqualTo(PanelArray("garage", 2.0, 30.0, 100.0, 0.14))

        form.choose(second, TiltChoice.STEEP)

        assertThat(form.read(stored)!!.system().panels()[1]).isEqualTo(PanelArray("garage", 2.0, 50.0, 100.0, 0.14))
    }

    @Test
    fun anAddedArrayStartsEmptyButForTheTypicalLossesAndNeedsItsPanelsAndChoices() {
        form.load(stored)

        val added = form.add()

        assertThat(form.text(added, PanelsForm.Field.LOSSES)).isEqualTo("14")
        assertThat(form.typical(added)).containsExactly(PanelsForm.Field.LOSSES)
        assertThat(form.direction(added)).isNull()
        assertThat(form.check(stored)).isNull()
        assertThat(form.problems(added).value).containsExactlyEntriesOf(mapOf(
            PanelsForm.Field.COUNT to FieldProblem.Refused(InvalidSetting.Rule.PANEL_COUNT_OUT_OF_RANGE),
            PanelsForm.Field.DIRECTION to FieldProblem.NotChosen,
            PanelsForm.Field.TILT to FieldProblem.NotChosen))
    }

    @Test
    fun anAddedArrayIsStoredWithWhatWasEnteredAndItsLossesMarkedTypical() {
        form.load(stored)
        val added = form.add()
        form.fillIn(added)

        val read = form.read(stored)!!

        assertThat(read.system().panels()).containsExactly(east, west,
            PanelArray("", panels(20, null, 2021), 35.0, 0.0, 0.14))
        assertThat(read.typical().panels()).containsExactly(setOf(), setOf(), setOf(ConfigSetting.PANEL_LOSSES))
    }

    @Test
    fun theSumAndTheEstimateFollowTheFields() {
        form.load(stored)
        val first = form.rowIds.value.first()
        form.edit(first, PanelsForm.Field.COUNT, "10")
        form.edit(first, PanelsForm.Field.POWER, "")
        form.edit(first, PanelsForm.Field.YEAR, "2021")

        assertThat(form.typicalWattPeak(first)).isEqualTo(360)
        assertThat(form.together(first)).isEqualTo(3.6)

        form.edit(first, PanelsForm.Field.YEAR, "2027")
        assertThat(form.typicalWattPeak(first)).isNull()
        assertThat(form.together(first)).isNull()
    }

    @ParameterizedTest
    @CsvSource(nullValues = ["-"], value = ["1989, -", "1990, 220", "2026, 430", "2027, -"])
    fun theEstimateIsShownOnlyForAYearTheArrayMayHave(year: String, wattPeak: Int?) {
        form.load(stored)
        val first = form.rowIds.value.first()
        form.edit(first, PanelsForm.Field.YEAR, year)

        assertThat(form.typicalWattPeak(first)).isEqualTo(wattPeak)
    }

    @ParameterizedTest
    @ValueSource(strings = ["1000000000", "-1000000000"])
    fun aYearPastWhatAYearCanBeIsRefusedAtItsField(year: String) {
        form.load(stored)
        val first = form.rowIds.value.first()
        form.edit(first, PanelsForm.Field.COUNT, "10")
        form.edit(first, PanelsForm.Field.POWER, "")
        form.edit(first, PanelsForm.Field.YEAR, year)

        assertThat(form.together(first)).isNull()
        assertThat(form.check(stored)).isNull()

        assertThat(form.problems(first).value).containsExactlyEntriesOf(mapOf(
            PanelsForm.Field.YEAR to FieldProblem.Refused(InvalidSetting.Rule.YEAR_PLACED_OUT_OF_RANGE)))
    }

    @ParameterizedTest
    @CsvSource(value = ["WEST, 90.0", "NORTH, 180.0"])
    fun aChosenDirectionIsStoredAsItsDegrees(point: CompassPoint, azimuthDeg: Double) {
        form.load(stored)
        val first = form.rowIds.value.first()

        form.choose(first, point)

        assertThat(form.read(stored)!!.system().panels()[0].azimuthDeg()).isEqualTo(azimuthDeg)
    }

    @ParameterizedTest
    @ValueSource(doubles = [-180.0, 180.0])
    fun aStoredNorthShowsAsNorthAndIsWrittenBackAsItWas(azimuthDeg: Double) {
        val north = DRAFT.withSystem(DRAFT.system().withPanels(listOf(PanelArray("shed", 2.0, 35.0, azimuthDeg, 0.14))),
            TypicalValues.NONE)
        form.load(north)
        val row = form.rowIds.value.single()
        form.edit(row, PanelsForm.Field.NAME, "shed roof")

        assertThat(form.direction(row)).isEqualTo(CompassPoint.NORTH)
        assertThat(form.read(north)!!.system().panels().single().azimuthDeg()).isEqualTo(azimuthDeg)
    }

    @ParameterizedTest
    @CsvSource(nullValues = ["-"], value = [
        "201, 2021, '',  COUNT, PANEL_COUNT_OUT_OF_RANGE",
        "10,  '',   '',  YEAR,  NO_YEAR_OR_POWER",
        "10,  1989, '',  YEAR,  YEAR_PLACED_OUT_OF_RANGE",
        "10,  2027, '',  YEAR,  YEAR_PLACED_OUT_OF_RANGE",
        "10,  2021, 40,  POWER, PANEL_POWER_OUT_OF_RANGE",
    ])
    fun aValueTheEngineRefusesShowsItsOwnRuleAtItsField(count: String, year: String, power: String,
        field: PanelsForm.Field, rule: InvalidSetting.Rule) {
        form.load(stored)
        val first = form.rowIds.value.first()
        form.edit(first, PanelsForm.Field.COUNT, count)
        form.edit(first, PanelsForm.Field.YEAR, year)
        form.edit(first, PanelsForm.Field.POWER, power)

        assertThat(form.check(stored)).isNull()

        assertThat(form.problems(first).value).containsExactlyEntriesOf(mapOf(field to FieldProblem.Refused(rule)))
    }

    @Test
    fun aNumberWithDecimalsOrLettersIsRefusedAsItIsForTheAmps() {
        form.load(stored)
        val first = form.rowIds.value.first()
        form.edit(first, PanelsForm.Field.COUNT, "12,5")
        form.edit(first, PanelsForm.Field.YEAR, "twenty")

        assertThat(form.check(stored)).isNull()

        assertThat(form.problems(first).value).containsExactlyEntriesOf(mapOf(
            PanelsForm.Field.COUNT to FieldProblem.NotAWholeNumber, PanelsForm.Field.YEAR to FieldProblem.NotANumber))
    }

    @Test
    fun setUpShowsItsDraftsTypicalArrayAsANewOne() {
        val setUp = PanelsForm(handle, numbers, thisYear, newWhenTypical = true)
        setUp.load(DRAFT)
        val row = setUp.rowIds.value.single()

        assertThat(setUp.text(row, PanelsForm.Field.COUNT)).isEmpty()
        assertThat(setUp.direction(row)).isNull()
        assertThat(setUp.keptKwp(row)).isNull()
        assertThat(setUp.read(DRAFT)).isNull()
        setUp.fillIn(row)

        val read = setUp.read(DRAFT)!!
        assertThat(read.system().panels().single()).isEqualTo(PanelArray("", panels(20, null, 2021), 35.0, 0.0, 0.14))
        assertThat(read.typical().panels().single()).containsExactly(ConfigSetting.PANEL_LOSSES)
    }

    @Test
    fun aKeptPeakPowerStaysTypicalWhereItWas() {
        form.load(DRAFT)
        val row = form.rowIds.value.single()
        form.edit(row, PanelsForm.Field.NAME, "roof")

        assertThat(form.read(DRAFT)!!.typical().panels().single()).isEqualTo(TypicalValues.PANEL_SETTINGS)
    }

    @Test
    fun removingAnArrayKeepsTheOthersExactAndTheLastOneStays() {
        form.load(stored)
        val (first, second) = form.rowIds.value

        assertThat(form.remove(first)).isTrue()
        assertThat(form.remove(second)).isFalse()

        assertThat(form.read(stored)!!.system().panels()).containsExactly(west)
        assertThat(form.isEdited()).isTrue()
    }

    @Test
    fun aRestoredDirectionOrTiltWhoseNameNoLongerFitsCountsAsNotChosen() {
        form.load(stored)
        val first = form.rowIds.value.first()
        form.choose(first, CompassPoint.WEST)
        form.choose(first, TiltChoice.STEEP)
        handle["panels.$first.text.DIRECTION"] = "WEST_SOUTH_WEST"
        handle["panels.$first.text.TILT"] = "TILTED"

        val again = PanelsForm(handle.afterTheSystemEndedTheApp(), numbers, thisYear)

        assertThat(again.direction(first)).isNull()
        assertThat(again.tilt(first)).isNull()
        assertThat(again.check(stored)).isNull()
        assertThat(again.problems(first).value).containsExactlyEntriesOf(mapOf(
            PanelsForm.Field.DIRECTION to FieldProblem.NotChosen, PanelsForm.Field.TILT to FieldProblem.NotChosen))
    }

    @ParameterizedTest
    @ValueSource(strings = ["two", "0", "NaN"])
    fun aRestoredPeakPowerThatNoLongerFitsIsNotKeptAndTheArrayNeedsItsPanels(kwp: String) {
        form.load(stored)
        val second = form.rowIds.value[1]
        handle["panels.$second.keptKwp"] = kwp

        val again = PanelsForm(handle.afterTheSystemEndedTheApp(), numbers, thisYear)

        assertThat(again.keptKwp(second)).isNull()
        assertThat(again.check(stored)).isNull()
        assertThat(again.problems(second).value).containsExactlyEntriesOf(mapOf(
            PanelsForm.Field.COUNT to FieldProblem.Refused(InvalidSetting.Rule.PANEL_COUNT_OUT_OF_RANGE)))
    }

    @Test
    fun theRowsTheirTextsAndTheKeptPeakPowerSurviveTheSystemEndingTheApp() {
        form.load(stored)
        form.add()
        form.edit(form.rowIds.value[0], PanelsForm.Field.NAME, "garage")

        val again = PanelsForm(handle.afterTheSystemEndedTheApp(), numbers, thisYear)

        assertThat(again.rowIds.value).isEqualTo(form.rowIds.value)
        assertThat(again.keptKwp(again.rowIds.value[1])).isEqualTo(2.0)
        assertThat(again.text(again.rowIds.value[0], PanelsForm.Field.NAME)).isEqualTo("garage")
        assertThat(again.read(stored)).isNull()
    }
}
