package nl.enadeag.carchargeplanner.android.home

import androidx.lifecycle.SavedStateHandle
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nl.enadeag.carchargeplanner.android.afterTheSystemEndedTheApp
import nl.enadeag.carchargeplanner.android.form.FieldProblem
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.engine.config.Percent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

@OptIn(ExperimentalCoroutinesApi::class)
class BatteryEntryTest {

    private val access = FakeEngineAccess()
    private val handle = SavedStateHandle()
    private var plannedAgain = 0

    private fun TestScope.entry(kept: SavedStateHandle = handle, locale: Locale = Locale.ENGLISH) =
        BatteryEntry(kept, NumberText(locale), access, backgroundScope) { plannedAgain++ }

    private fun TestScope.typed(text: String, locale: Locale = Locale.ENGLISH): BatteryEntry =
        entry(locale = locale).also {
            it.open()
            it.edit(text)
        }

    private fun batteryKeys() = handle.keys().filter { it == "battery" || it.startsWith("battery.") }

    @Test
    fun theEntryOpensEmptyWithNothingWrong() = runTest {
        val entry = entry()
        assertThat(entry.open.value).isFalse()

        entry.open()

        assertThat(entry.open.value).isTrue()
        assertThat(entry.text.value).isEmpty()
        assertThat(entry.problems.value).isEmpty()
        assertThat(entry.saving.value).isEqualTo(SaveState.Idle)
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = ["0;0.0", "1;1.0", "64;64.0", "99;99.0", "100;100.0", "-0;0.0", "064;64.0"])
    fun aWholeLevelFrom0To100IsSaved(text: String, level: Double) = runTest {
        val entry = typed(text)

        entry.save()
        runCurrent()

        assertThat(access.batteryLevels).containsExactly(Percent(level))
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = ["64,5;en", "64.5;en", "64,5;nl", "64.5;nl", "100.0;en", "0,0;nl", "99,9;nl",
        "0.1;en", "-0.1;en", "100.1;en"])
    fun aLevelWithDecimalsIsRefusedNeverRoundedAndNothingIsSaved(text: String, language: String) = runTest {
        val entry = typed(text, Locale.forLanguageTag(language))

        entry.save()
        runCurrent()

        assertThat(entry.problems.value)
            .containsExactlyEntriesOf(mapOf(BatteryEntry.Field.LEVEL to FieldProblem.NotAWholeNumber))
        assertThat(entry.refusals.value).isEqualTo(1)
        assertThat(access.batteryLevels).isEmpty()
        assertThat(entry.open.value).isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["-1", "101", "1000", "99999999999"])
    fun aLevelOutside0To100IsRefusedAndNothingIsSaved(text: String) = runTest {
        val entry = typed(text)

        entry.save()
        runCurrent()

        assertThat(entry.problems.value)
            .containsExactlyEntriesOf(mapOf(BatteryEntry.Field.LEVEL to FieldProblem.NotFrom0To100))
        assertThat(entry.refusals.value).isEqualTo(1)
        assertThat(access.batteryLevels).isEmpty()
        assertThat(entry.open.value).isTrue()
        assertThat(entry.saving.value).isEqualTo(SaveState.Idle)
        assertThat(plannedAgain).isZero()
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "abc", "64%", "6 4", "1e2", "+64", "64,", ".5", "1,000.5"])
    fun aTextThatIsNoNumberIsRefusedAndNothingIsSaved(text: String) = runTest {
        val entry = typed(text)

        entry.save()
        runCurrent()

        assertThat(entry.problems.value)
            .containsExactlyEntriesOf(mapOf(BatteryEntry.Field.LEVEL to FieldProblem.NotANumber))
        assertThat(access.batteryLevels).isEmpty()
        assertThat(entry.open.value).isTrue()
    }

    @Test
    fun anEmptyEntryThatWasNeverTypedInIsRefusedAsNoNumber() = runTest {
        val entry = entry()
        entry.open()

        entry.save()
        runCurrent()

        assertThat(entry.problems.value)
            .containsExactlyEntriesOf(mapOf(BatteryEntry.Field.LEVEL to FieldProblem.NotANumber))
        assertThat(access.batteryLevels).isEmpty()
    }

    @Test
    fun eachRefusedSaveCountsAsAProblemShownAgainSoTheFieldTakesTheFocusAgain() = runTest {
        val entry = typed("101")

        entry.save()
        entry.save()
        runCurrent()

        assertThat(entry.refusals.value).isEqualTo(2)
    }

    @Test
    fun aRefusedLevelIsForgottenWhenTheEntryIsCancelledAndOpenedAgain() = runTest {
        val entry = typed("101")
        entry.save()
        runCurrent()

        entry.cancel()
        entry.open()

        assertThat(entry.problems.value).isEmpty()
        assertThat(entry.text.value).isEmpty()
    }

    @Test
    fun theProblemGoesWhenTheFieldIsEdited() = runTest {
        val entry = typed("101")
        entry.save()
        runCurrent()

        entry.edit("100")

        assertThat(entry.problems.value).isEmpty()
    }

    @Test
    fun aSaveThatWorkedClosesTheEntrySaysSoOnceAndHasThePlanMadeAgain() = runTest {
        val entry = typed("64")

        entry.save()
        runCurrent()

        assertThat(entry.open.value).isFalse()
        assertThat(entry.notice.value).isEqualTo(BatteryEntry.Notice.Saved)
        assertThat(plannedAgain).isEqualTo(1)
        entry.noticeShown()
        assertThat(entry.notice.value).isNull()
    }

    @Test
    fun aSaveThatWorkedForgetsTheEntryInTheSavedState() = runTest {
        val entry = typed("64")
        assertThat(batteryKeys()).isNotEmpty()

        entry.save()
        runCurrent()

        assertThat(batteryKeys()).isEmpty()
        entry.open()
        assertThat(entry.text.value).isEmpty()
    }

    @Test
    fun aSaveThatFailedKeepsTheEntryOpenWithWhatWasTypedAndWhy() = runTest {
        access.batteryFails = "battery.json is not written: written by a newer version of the app"
        val entry = typed("64")

        entry.save()
        runCurrent()

        assertThat(entry.open.value).isTrue()
        assertThat(entry.text.value).isEqualTo("64")
        assertThat(entry.saving.value).isEqualTo(SaveState.Failed(
            "battery.json is not written: written by a newer version of the app"))
        assertThat(entry.notice.value).isNull()
        assertThat(plannedAgain).isZero()
    }

    @Test
    fun aFailedSaveIsForgottenWhenTheEntryIsCancelledAndOpenedAgain() = runTest {
        access.batteryFails = "No space left on device"
        val entry = typed("64")
        entry.save()
        runCurrent()

        entry.cancel()
        entry.open()

        assertThat(entry.saving.value).isEqualTo(SaveState.Idle)
    }

    @Test
    fun aSaveAfterAFailureThatWorksClosesTheEntry() = runTest {
        access.batteryFails = "No space left on device"
        val entry = typed("64")
        entry.save()
        runCurrent()

        access.batteryFails = null
        entry.save()
        runCurrent()

        assertThat(entry.open.value).isFalse()
        assertThat(access.batteryLevels).containsExactly(Percent(64.0))
    }

    @Test
    fun whileASaveRunsNothingClosesTheEntryAndASecondSaveIsNotStarted() = runTest {
        val gate = CompletableDeferred<Unit>()
        access.batteryGate = gate
        val entry = typed("64")
        entry.save()
        runCurrent()
        assertThat(entry.saving.value).isEqualTo(SaveState.Saving)

        entry.save()
        entry.cancel()
        entry.back()
        runCurrent()

        assertThat(entry.open.value).isTrue()
        assertThat(entry.discardAsked.value).isFalse()
        gate.complete(Unit)
        runCurrent()
        assertThat(access.batteryLevels).containsExactly(Percent(64.0))
        assertThat(plannedAgain).isEqualTo(1)
    }

    @Test
    fun cancelClosesAtOnceAndForgetsWhatWasTyped() = runTest {
        val entry = typed("64")

        entry.cancel()

        assertThat(entry.open.value).isFalse()
        assertThat(entry.discardAsked.value).isFalse()
        assertThat(batteryKeys()).isEmpty()
        assertThat(access.batteryLevels).isEmpty()
        assertThat(entry.notice.value).isNull()
    }

    @Test
    fun backWithALevelTypedAsksAndKeepEditingKeepsIt() = runTest {
        val entry = typed("64")

        entry.back()
        assertThat(entry.discardAsked.value).isTrue()
        assertThat(entry.open.value).isTrue()

        entry.keepEditing()
        assertThat(entry.discardAsked.value).isFalse()
        assertThat(entry.open.value).isTrue()
        assertThat(entry.text.value).isEqualTo("64")
    }

    @Test
    fun discardClosesAndForgetsWhatWasTyped() = runTest {
        val entry = typed("64")
        entry.back()

        entry.discard()

        assertThat(entry.open.value).isFalse()
        assertThat(entry.discardAsked.value).isFalse()
        assertThat(batteryKeys()).isEmpty()
        assertThat(access.batteryLevels).isEmpty()
    }

    @Test
    fun backWithOnlyASpaceTypedAsksAsTheFieldIsNotEmpty() = runTest {
        val entry = typed(" ")

        entry.back()

        assertThat(entry.discardAsked.value).isTrue()
        assertThat(entry.open.value).isTrue()
    }

    @Test
    fun backWithNothingTypedClosesWithoutAsking() = runTest {
        val entry = typed("6")
        entry.edit("")

        entry.back()

        assertThat(entry.discardAsked.value).isFalse()
        assertThat(entry.open.value).isFalse()
        assertThat(batteryKeys()).isEmpty()
    }

    @Test
    fun theOpenEntryWhatWasTypedAndTheQuestionSurviveTheSystemEndingTheApp() = runTest {
        val entry = typed("64")
        entry.back()

        val again = entry(handle.afterTheSystemEndedTheApp())

        assertThat(again.open.value).isTrue()
        assertThat(again.text.value).isEqualTo("64")
        assertThat(again.discardAsked.value).isTrue()
        again.keepEditing()
        again.save()
        runCurrent()
        assertThat(access.batteryLevels).containsExactly(Percent(64.0))
    }

    @Test
    fun aClosedEntryLeavesNothingToComeBackAfterTheSystemEndedTheApp() = runTest {
        val entry = typed("64")
        entry.cancel()

        val again = entry(handle.afterTheSystemEndedTheApp())

        assertThat(again.open.value).isFalse()
        assertThat(again.text.value).isEmpty()
        assertThat(again.discardAsked.value).isFalse()
    }

    @Test
    fun openingAnOpenEntryKeepsWhatWasTyped() = runTest {
        val entry = typed("64")

        entry.open()

        assertThat(entry.text.value).isEqualTo("64")
    }

    @Test
    fun aSaveOfAClosedEntrySavesNothing() = runTest {
        val entry = entry()

        entry.save()
        runCurrent()

        assertThat(access.batteryLevels).isEmpty()
        assertThat(entry.problems.value).isEmpty()
    }

    @Test
    fun forgettingLeavesNoKeyOfTheEntryAndNoneOfAnotherForm() = runTest {
        typed("64").back()
        handle["batteryish"] = "another's"
        handle["car.text.BATTERY"] = "75"

        BatteryEntry.forget(handle)

        assertThat(batteryKeys()).isEmpty()
        assertThat(handle.keys()).containsExactlyInAnyOrder("batteryish", "car.text.BATTERY")
    }

    @Test
    fun whileASaveRunsTheEntryIsBusyAndASecondSaveStartsNothing() = runTest {
        val gate = CompletableDeferred<Unit>()
        access.batteryGate = gate
        val entry = typed("64")
        entry.save()
        runCurrent()

        val busy = entry.busy.value
        entry.save()
        gate.complete(Unit)
        runCurrent()

        assertThat(busy).isTrue()
        assertThat(entry.busy.value).isFalse()
        assertThat(access.batteryLevels).containsExactly(Percent(64.0))
        assertThat(entry.refusals.value).isZero()
    }

    @Test
    fun whileASaveRunsTypingIsIgnored() = runTest {
        val gate = CompletableDeferred<Unit>()
        access.batteryGate = gate
        access.batteryFails = "No space left on device"
        val entry = typed("64")
        entry.save()
        runCurrent()

        entry.edit("70")
        gate.complete(Unit)
        runCurrent()

        assertThat(entry.text.value).isEqualTo("64")
        assertThat(entry(handle.afterTheSystemEndedTheApp()).text.value).isEqualTo("64")
    }

    @Test
    fun whileASaveRunsDiscardDoesNothing() = runTest {
        val gate = CompletableDeferred<Unit>()
        access.batteryGate = gate
        access.batteryFails = "No space left on device"
        val entry = typed("64")
        entry.save()
        runCurrent()

        entry.discard()
        gate.complete(Unit)
        runCurrent()

        assertThat(entry.open.value).isTrue()
        assertThat(entry.text.value).isEqualTo("64")
    }

    @Test
    fun aClosedEntryHoldsNoTypedLevelOrProblem() = runTest {
        val entry = typed("101")
        entry.save()

        entry.cancel()

        assertThat(entry.text.value).isEmpty()
        assertThat(entry.problems.value).isEmpty()
    }

    @Test
    fun anEntryOpensEmptyAlsoWhenTheSavedStateHeldATypedLevelWithoutTheEntryOpen() = runTest {
        handle["battery.text.LEVEL"] = "64"
        val entry = entry()

        entry.open()

        assertThat(entry.text.value).isEmpty()
    }

    @Test
    fun aKeptQuestionWithoutAnOpenEntryDoesNotShow() = runTest {
        handle["battery.discard"] = true

        val entry = entry()

        assertThat(entry.discardAsked.value).isFalse()
    }

    @Test
    fun theSavedNoticeComesWithTheEndOfTheSave() = runTest {
        val gate = CompletableDeferred<Unit>()
        access.batteryGate = gate
        val entry = typed("64")
        entry.save()
        runCurrent()
        val whileSaving = entry.notice.value

        gate.complete(Unit)
        runCurrent()

        assertThat(whileSaving).isNull()
        assertThat(entry.notice.value).isEqualTo(BatteryEntry.Notice.Saved)
    }

    @Test
    fun aSaveThatFailedSaysNothingThroughTheNotice() = runTest {
        access.batteryFails = "No space left on device"
        val entry = typed("70")

        entry.save()
        runCurrent()

        assertThat(entry.saving.value).isEqualTo(SaveState.Failed("No space left on device"))
        assertThat(entry.notice.value).isNull()
    }

    /** The field's text is put in the handle directly, as typing is ignored while the save runs. */
    @Test
    fun whileASaveRunsSaveCountsNoRefusalAlsoForATextItWouldRefuse() = runTest {
        val gate = CompletableDeferred<Unit>()
        access.batteryGate = gate
        val entry = typed("64")
        entry.save()
        runCurrent()
        handle["battery.text.LEVEL"] = "no number"

        entry.save()
        gate.complete(Unit)
        runCurrent()

        assertThat(entry.refusals.value).isZero()
        assertThat(entry.problems.value).isEmpty()
    }
}
