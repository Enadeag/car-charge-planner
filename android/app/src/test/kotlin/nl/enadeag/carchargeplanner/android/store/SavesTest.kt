package nl.enadeag.carchargeplanner.android.store

import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nl.enadeag.carchargeplanner.engine.config.Car
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.House
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.secrets.KeyChanges
import nl.enadeag.carchargeplanner.engine.secrets.Secrets
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class SavesTest {

    private val draft = EngineConfig.setUpDraft(ZoneId.of("Europe/Amsterdam"), ProviderId("open-meteo"))
    private val access = FakeEngineAccess(StartState.Ready(draft))
    private val someKey = KeyChanges.entering(Secrets(mapOf(ProviderId("open-meteo") to mapOf("api_key" to "K1"))))

    private fun withHouse(config: EngineConfig, kw: Double) =
        config.withSystem(config.system().withHouseAndCar(House(kw), config.system().car()), config.typical())

    private fun withCar(config: EngineConfig, kwh: Double) =
        config.withSystem(config.system().withHouseAndCar(config.system().house(), Car(kwh, 18.0, 40.0)),
            config.typical())

    private fun TestScope.saves() = Saves(access, backgroundScope)

    @Test
    fun theKeysAreSavedFirstThenTheSettingsAndTheOutcomeIsKept() = runTest {
        val saves = saves()

        saves.update(someKey) { withHouse(it, 0.8) }
        runCurrent()

        assertThat(access.written).containsExactly("keys", "settings")
        assertThat((access.settings as StartState.Ready).config.system().house()).isEqualTo(House(0.8))
        assertThat(saves.lastOutcome.value).isEqualTo(SaveOutcome.Saved)
    }

    @Test
    fun keysThatCouldNotBeSavedStopTheSave() = runTest {
        access.keysFail = true
        val saves = saves()

        saves.update(someKey) { withHouse(it, 0.8) }
        runCurrent()

        assertThat(access.written).isEmpty()
        assertThat(saves.lastOutcome.value).isEqualTo(SaveOutcome.KeysNotSaved)
    }

    @Test
    fun aChangeThatChangesNothingWritesNothing() = runTest {
        val saves = saves()

        saves.update(KeyChanges.NONE) { it }
        runCurrent()

        assertThat(access.written).isEmpty()
        assertThat(saves.lastOutcome.value).isEqualTo(SaveOutcome.Saved)
    }

    @Test
    fun theChangeGoesOverTheFileAsItIsWhenTheSaveRuns() = runTest {
        val saves = saves()
        access.settings = StartState.Ready(withCar(draft, 77.0))

        saves.update(KeyChanges.NONE) { withHouse(it, 0.8) }
        runCurrent()

        val saved = (access.settings as StartState.Ready).config.system()
        assertThat(saved.car().usableBatteryKwh()).isEqualTo(77.0)
        assertThat(saved.house()).isEqualTo(House(0.8))
    }

    @Test
    fun twoSavesAtOnceRunOneAfterTheOtherSoNeitherUndoesTheOther() = runTest {
        val saves = saves()
        val gate = CompletableDeferred<Unit>()
        access.readGate = gate

        saves.update(KeyChanges.NONE) { withHouse(it, 0.8) }
        saves.update(KeyChanges.NONE) { withCar(it, 77.0) }
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        val saved = (access.settings as StartState.Ready).config.system()
        assertThat(saved.house()).isEqualTo(House(0.8))
        assertThat(saved.car().usableBatteryKwh()).isEqualTo(77.0)
        assertThat(access.written).containsExactly("settings", "settings")
    }

    @Test
    fun aFileThatCannotBeUsedNowIsNotWrittenOver() = runTest {
        val saves = saves()
        val damaged = StartState.Unusable("engine.json", "not valid JSON (line 1, column 1)")
        access.settings = damaged

        saves.update(KeyChanges.NONE) { withHouse(it, 0.8) }
        runCurrent()

        assertThat(access.written).isEmpty()
        assertThat(saves.lastOutcome.value).isEqualTo(SaveOutcome.SettingsUnusable(damaged))
    }

    @Test
    fun aFileThatCouldNotBeWrittenIsAFailureWithTheSystemsWords() = runTest {
        access.writeFails = true
        val saves = saves()

        saves.update(KeyChanges.NONE) { withHouse(it, 0.8) }
        runCurrent()

        assertThat(saves.lastOutcome.value).isEqualTo(SaveOutcome.Failed("No space left on device"))
    }

    @Test
    fun aChangeThatNoLongerFitsIsAFailure() = runTest {
        val saves = saves()

        saves.update(KeyChanges.NONE) { throw IllegalArgumentException("a value no longer fits") }
        runCurrent()

        assertThat(saves.lastOutcome.value).isEqualTo(SaveOutcome.Failed("a value no longer fits"))
        assertThat(access.written).isEmpty()
    }

    @Test
    fun finishWritesTheWholeConfigWhereThereWasNone() = runTest {
        access.settings = StartState.NotSetUp
        val saves = saves()

        saves.finishSetUp(withHouse(draft, 0.3))
        runCurrent()

        assertThat(access.settings).isEqualTo(StartState.Ready(withHouse(draft, 0.3)))
        assertThat(saves.lastOutcome.value).isEqualTo(SaveOutcome.Saved)
    }

    @Test
    fun finishNeverWritesOverAFileOfANewerVersion() = runTest {
        val newer = StartState.NewerVersion("written by a newer version of the app")
        access.settings = newer
        val saves = saves()

        saves.finishSetUp(draft)
        runCurrent()

        assertThat(access.written).isEmpty()
        assertThat(saves.lastOutcome.value).isEqualTo(SaveOutcome.SettingsUnusable(newer))
    }

    @Test
    fun aSaveOutlivesTheScreenThatStartedIt() = runTest {
        val saves = saves()
        val screen = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val gate = CompletableDeferred<Unit>()
        access.readGate = gate

        screen.launch { saves.update(KeyChanges.NONE) { withHouse(it, 0.8) } }
        runCurrent()
        screen.cancel()
        gate.complete(Unit)
        runCurrent()

        assertThat(access.written).containsExactly("settings")
    }

    @Test
    fun aFailureNotYetShownIsShownBeforeALaterSuccess() = runTest {
        access.writeFails = true
        val saves = saves()
        saves.update(KeyChanges.NONE) { withHouse(it, 0.8) }
        runCurrent()
        access.writeFails = false
        saves.update(KeyChanges.NONE) { withHouse(it, 0.9) }
        runCurrent()

        val failed = SaveOutcome.Failed("No space left on device")
        assertThat(saves.lastOutcome.value).isEqualTo(failed)
        saves.shown(SaveOutcome.Saved)
        assertThat(saves.lastOutcome.value).isEqualTo(failed)
        saves.shown(failed)
        assertThat(saves.lastOutcome.value).isEqualTo(SaveOutcome.Saved)
        saves.shown(SaveOutcome.Saved)
        assertThat(saves.lastOutcome.value).isNull()
    }

    @Test
    fun aSuccessNotYetShownIsReplacedByALaterSuccessAfterAFailure() = runTest {
        val saves = saves()
        saves.update(KeyChanges.NONE) { withHouse(it, 0.8) }
        runCurrent()
        saves.update(KeyChanges.NONE) { throw IllegalArgumentException("a value no longer fits") }
        runCurrent()
        saves.update(KeyChanges.NONE) { withHouse(it, 0.9) }
        runCurrent()

        assertThat(saves.lastOutcome.value).isEqualTo(SaveOutcome.Failed("a value no longer fits"))
        saves.shown(SaveOutcome.Failed("a value no longer fits"))
        assertThat(saves.lastOutcome.value).isEqualTo(SaveOutcome.Saved)
        saves.shown(SaveOutcome.Saved)
        assertThat(saves.lastOutcome.value).isNull()
    }

    @Test
    fun twoEqualFailuresNotYetShownAreShownOnceAndALaterSuccessFollows() = runTest {
        access.writeFails = true
        val saves = saves()
        saves.update(KeyChanges.NONE) { withHouse(it, 0.8) }
        runCurrent()
        saves.update(KeyChanges.NONE) { withHouse(it, 0.9) }
        runCurrent()
        access.writeFails = false
        saves.update(KeyChanges.NONE) { withHouse(it, 1.0) }
        runCurrent()

        val failed = SaveOutcome.Failed("No space left on device")
        assertThat(saves.lastOutcome.value).isEqualTo(failed)
        saves.shown(failed)
        assertThat(saves.lastOutcome.value).isEqualTo(SaveOutcome.Saved)
        saves.shown(SaveOutcome.Saved)
        assertThat(saves.lastOutcome.value).isNull()
    }
}
