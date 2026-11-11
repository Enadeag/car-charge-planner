package nl.enadeag.carchargeplanner.android.setup

import androidx.lifecycle.SavedStateHandle
import java.time.Year
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nl.enadeag.carchargeplanner.android.afterTheSystemEndedTheApp
import nl.enadeag.carchargeplanner.android.form.CarForm
import nl.enadeag.carchargeplanner.android.form.FieldProblem
import nl.enadeag.carchargeplanner.android.form.fillIn
import nl.enadeag.carchargeplanner.android.form.LocationForm
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.form.PanelsForm
import nl.enadeag.carchargeplanner.android.form.WeeklyAwayForm
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.store.SaveOutcome
import nl.enadeag.carchargeplanner.android.store.Saves
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.engine.config.Coordinates
import nl.enadeag.carchargeplanner.engine.config.HomeChargePower
import nl.enadeag.carchargeplanner.engine.config.House
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.forecast.Place
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class SetUpFlowTest {

    private val handle = SavedStateHandle()
    private val numbers = NumberText(Locale.ENGLISH)
    private val amsterdam = ZoneId.of("Europe/Amsterdam")
    private val draft = SetUpFlow.draft(amsterdam, ProviderId("open-meteo"))
    private val flow = SetUpFlow(handle, numbers, draft, { Year.of(2026) }) { amsterdam }
    private val utrecht = Place("Utrecht", Coordinates(52.0907, 5.1214), ZoneId.of("Europe/Amsterdam"), "", "", "")

    /** Goes from the welcome to the step after the location, with Utrecht chosen. */
    private fun throughTheLocation() {
        flow.next()
        flow.location.choose(utrecht, "Utrecht")
        assertThat(flow.next()).isTrue()
        flow.panels.fillIn()
        flow.charger.choose(HomeChargePower.KW_11)
    }

    @Test
    fun setUpStartsWithTheWelcomeOfSevenSteps() {
        assertThat(flow.step.value).isEqualTo(SetUpStep.WELCOME)
        assertThat(SetUpStep.CHECK.number()).isEqualTo(7)
    }

    @Test
    fun theDraftIsOpenMeteoWithoutAFallbackInThePhonesTimeZoneWithTheTypicalValuesMarked() {
        assertThat(draft.forecast().primary()).isEqualTo(ProviderId("open-meteo"))
        assertThat(draft.forecast().fallback()).isEmpty()
        assertThat(draft.system().timezone()).isEqualTo(ZoneId.of("Europe/Amsterdam"))
        assertThat(draft.system().location().isChosen).isFalse()
        assertThat(flow.car.typical()).hasSize(3)
        assertThat(flow.charger.typical()).hasSize(5)
        assertThat(flow.panels.typical(flow.panels.rowIds.value.single())).containsExactly(PanelsForm.Field.LOSSES)
    }

    @Test
    fun setUpPlansWithTheTypicalHouseUseMarkedAsTypical() {
        val config = flow.config()

        assertThat(config.system().house()).isEqualTo(House.TYPICAL)
        assertThat(config.typical().system()).contains(ConfigSetting.HOUSE_BASELOAD)
    }

    @Test
    fun aSetUpComingBackWithAHouseUseTypedInItDropsIt() {
        throughTheLocation()
        handle["house.text.BASELOAD"] = "0.8"
        handle["house.filled"] = true
        handle["house.edited"] = arrayListOf("BASELOAD")
        handle["house.typical"] = arrayListOf<String>()

        val restored = handle.afterTheSystemEndedTheApp()
        val again = SetUpFlow(restored, numbers, draft, { Year.of(2026) }) { amsterdam }

        assertThat(again.step.value).isEqualTo(SetUpStep.PANELS)
        assertThat(again.config().system().house()).isEqualTo(House.TYPICAL)
        assertThat(again.config().typical().system()).contains(ConfigSetting.HOUSE_BASELOAD)
        assertThat(restored.keys()).noneMatch { it.startsWith("house.") }
    }

    @Test
    fun withoutAPlaceChosenNextStaysOnTheLocation() {
        flow.next()

        assertThat(flow.next()).isFalse()

        assertThat(flow.step.value).isEqualTo(SetUpStep.LOCATION)
        assertThat(flow.location.problems.value).containsExactlyEntriesOf(mapOf(
            LocationForm.Field.PLACE to FieldProblem.NoPlaceChosen))
    }

    @Test
    fun aRefusedValueKeepsTheStepAndShowsItsProblem() {
        throughTheLocation()
        flow.next()
        flow.car.edit(CarForm.Field.BATTERY, "60.5.1")

        assertThat(flow.next()).isFalse()

        assertThat(flow.step.value).isEqualTo(SetUpStep.CAR)
        assertThat(flow.car.problems.value).containsExactlyEntriesOf(mapOf(
            CarForm.Field.BATTERY to FieldProblem.NotANumber))
    }

    @Test
    fun backGoesToTheStepBeforeAndLeavesSetUpOnTheFirst() {
        throughTheLocation()

        assertThat(flow.back()).isTrue()
        assertThat(flow.step.value).isEqualTo(SetUpStep.LOCATION)
        assertThat(flow.back()).isTrue()
        assertThat(flow.back()).isFalse()
        assertThat(flow.step.value).isEqualTo(SetUpStep.WELCOME)
    }

    @Test
    fun theStepTheTextsAndTheTypicalMarksSurviveTheSystemEndingTheApp() {
        throughTheLocation()
        flow.next()
        flow.car.edit(CarForm.Field.BATTERY, "75")

        val again = SetUpFlow(handle.afterTheSystemEndedTheApp(), numbers, draft, { Year.of(2026) }) { amsterdam }

        assertThat(again.step.value).isEqualTo(SetUpStep.CAR)
        assertThat(again.car.text(CarForm.Field.BATTERY)).isEqualTo("75")
        assertThat(again.car.typical()).doesNotContain(CarForm.Field.BATTERY).contains(CarForm.Field.CONSUMPTION)
        assertThat(again.config().system().location()).isEqualTo(utrecht.location())
    }

    @Test
    fun nothingIsSavedBeforeFinishWhichSavesTheWholeConfig() = runTest {
        val access = FakeEngineAccess(StartState.NotSetUp)
        val saves = Saves(access, backgroundScope)
        throughTheLocation()
        flow.next()
        flow.car.edit(CarForm.Field.BATTERY, "75")
        repeat(4) { flow.next() }
        assertThat(flow.step.value).isEqualTo(SetUpStep.CHECK)
        assertThat(access.written).isEmpty()

        flow.finish(saves)
        runCurrent()

        val saved = (access.settings as StartState.Ready).config
        assertThat(saved).isEqualTo(flow.config())
        assertThat(saved.system().location()).isEqualTo(utrecht.location())
        assertThat(saved.system().car().usableBatteryKwh()).isEqualTo(75.0)
        assertThat(saved.typical().system()).doesNotContain(ConfigSetting.CAR_BATTERY)
            .contains(ConfigSetting.CAR_CONSUMPTION, ConfigSetting.HOUSE_BASELOAD)
        assertThat(saves.lastOutcome.value).isEqualTo(SaveOutcome.Saved)
    }

    @Test
    fun finishGoesBackToTheFirstStepWithARefusedValueAndSavesNothing() = runTest {
        val access = FakeEngineAccess(StartState.NotSetUp)
        throughTheLocation()
        repeat(5) { flow.next() }
        assertThat(flow.step.value).isEqualTo(SetUpStep.CHECK)
        flow.car.edit(CarForm.Field.CONSUMPTION, "0")

        assertThat(flow.finish(Saves(access, backgroundScope))).isNull()
        runCurrent()

        assertThat(flow.step.value).isEqualTo(SetUpStep.CAR)
        assertThat(access.written).isEmpty()
    }

    /** Goes from the welcome to the step with the away periods, every value before it accepted. */
    private fun toTheAwayStep() {
        throughTheLocation()
        repeat(3) { flow.next() }
        assertThat(flow.step.value).isEqualTo(SetUpStep.AWAY)
    }

    @Test
    fun aRefusedAwayPeriodKeepsTheStepAndShowsItsProblem() {
        toTheAwayStep()
        val row = flow.away.add()
        flow.away.edit(row, WeeklyAwayForm.Field.END, "07:00")

        assertThat(flow.next()).isFalse()

        assertThat(flow.step.value).isEqualTo(SetUpStep.AWAY)
        assertThat(flow.away.problems(row).value).containsExactlyEntriesOf(mapOf(
            WeeklyAwayForm.Field.END to FieldProblem.Refused(InvalidSetting.Rule.NOT_AFTER_START)))
    }
}
