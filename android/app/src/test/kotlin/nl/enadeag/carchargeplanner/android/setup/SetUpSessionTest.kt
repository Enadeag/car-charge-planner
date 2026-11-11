package nl.enadeag.carchargeplanner.android.setup

import androidx.lifecycle.SavedStateHandle
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nl.enadeag.carchargeplanner.android.afterTheSystemEndedTheApp
import nl.enadeag.carchargeplanner.android.form.CarForm
import nl.enadeag.carchargeplanner.android.form.LocationForm
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.form.fillIn
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.store.SaveOutcome
import nl.enadeag.carchargeplanner.android.store.Saves
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.engine.config.Coordinates
import nl.enadeag.carchargeplanner.engine.config.PlaceName
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.forecast.ConnectionResult
import nl.enadeag.carchargeplanner.engine.forecast.Place
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SetUpSessionTest {

    private val handle = SavedStateHandle()
    private val access = FakeEngineAccess(StartState.NotSetUp)
    private val amsterdam = ZoneId.of("Europe/Amsterdam")
    private val utrecht = Place("Utrecht", Coordinates(52.0907, 5.1214), amsterdam, "", "Netherlands", "")
    private val ended = mutableListOf<SaveOutcome>()

    private fun TestScope.session(on: SavedStateHandle = handle, saves: Saves = Saves(access, backgroundScope)) =
        SetUpSession(on, NumberText(Locale.ENGLISH), { amsterdam }, { "en" }, access, saves, backgroundScope) {
            ended += it
        }

    /** Goes from the welcome to the check, Utrecht chosen and every other value as drafted. */
    private fun TestScope.toTheCheck(session: SetUpSession) {
        session.next()
        session.choose(PlaceSearch.Result(utrecht, "Utrecht, Netherlands"))
        session.flow.panels.fillIn()
        repeat(5) { assertThat(session.next()).isTrue() }
        assertThat(session.flow.step.value).isEqualTo(SetUpStep.CHECK)
        runCurrent()
    }

    @Test
    fun theWelcomeNamesTheProviderSetUpAsks() = runTest {
        assertThat(session().providerName()).isEqualTo("Open-Meteo")
    }

    @Test
    fun setUpDraftsTheFirstProviderOfferedAsThePrimary() = runTest {
        access.offered = access.providers().reversed()

        assertThat(session().flow.config().forecast().primary()).isEqualTo(ProviderId("forecast-solar"))
    }

    @Test
    fun theCheckStartsOnEnteringTheLastStepWithThePlaceChosen() = runTest {
        val session = session()

        toTheCheck(session)

        assertThat(session.check.state.value).isEqualTo(SetUpCheck.State.Asking)
        assertThat(access.checks).hasSize(1)
    }

    @Test
    fun backDuringTheCheckStopsItAndComingBackAsksAgain() = runTest {
        val session = session()
        toTheCheck(session)

        assertThat(session.back()).isTrue()
        access.checks[0].complete(ConnectionResult(true, 7, "Received a forecast for 7 days."))
        runCurrent()
        assertThat(session.flow.step.value).isEqualTo(SetUpStep.MORE)
        assertThat(session.check.state.value).isEqualTo(SetUpCheck.State.NotAsked)

        session.next()
        runCurrent()
        assertThat(access.checks).hasSize(2)
        assertThat(session.check.state.value).isEqualTo(SetUpCheck.State.Asking)
    }

    @Test
    fun afterTheSystemEndedTheAppOnTheLastStepTheCheckAsksAgain() = runTest {
        toTheCheck(session())

        val again = session(handle.afterTheSystemEndedTheApp())
        runCurrent()

        assertThat(again.flow.step.value).isEqualTo(SetUpStep.CHECK)
        assertThat(again.check.state.value).isEqualTo(SetUpCheck.State.Asking)
        assertThat(access.checks).hasSize(2)
    }

    @Test
    fun aRefusedNextCountsSoTheStepCanMoveTheFocus() = runTest {
        val session = session()
        session.next()

        assertThat(session.next()).isFalse()

        assertThat(session.problemsShown.value).isEqualTo(1)
    }

    @Test
    fun backKeepsWhatWasEnteredAndLeavesSetUpOnlyOnTheWelcome() = runTest {
        val session = session()
        toTheCheck(session)
        repeat(3) { session.back() }
        session.flow.car.edit(CarForm.Field.BATTERY, "75")

        repeat(3) { assertThat(session.back()).isTrue() }
        assertThat(session.back()).isFalse()

        assertThat(session.flow.step.value).isEqualTo(SetUpStep.WELCOME)
        assertThat(session.flow.car.text(CarForm.Field.BATTERY)).isEqualTo("75")
    }

    @Test
    fun aFinishThatSavesEndsSetUpAndIsShown() = runTest {
        val saves = Saves(access, backgroundScope)
        val session = session(saves = saves)
        toTheCheck(session)

        session.finish()
        assertThat(session.finishing.value).isEqualTo(SetUpSession.Finishing.Saving)
        runCurrent()

        assertThat(ended).containsExactly(SaveOutcome.Saved)
        assertThat(saves.lastOutcome.value).isNull()
        assertThat(session.finishing.value).isEqualTo(SetUpSession.Finishing.Idle)
        assertThat((access.settings as StartState.Ready).config.system().location()).isEqualTo(utrecht.location())
    }

    @Test
    fun aFinishThatFailsStaysOnTheCheckWithItsDetailsAndMayBeTriedAgain() = runTest {
        val saves = Saves(access, backgroundScope)
        val session = session(saves = saves)
        toTheCheck(session)
        access.writeFails = true

        session.finish()
        runCurrent()

        assertThat(session.finishing.value).isEqualTo(SetUpSession.Finishing.Failed("No space left on device"))
        assertThat(session.flow.step.value).isEqualTo(SetUpStep.CHECK)
        assertThat(saves.lastOutcome.value).isNull()
        assertThat(ended).isEmpty()

        access.writeFails = false
        session.finish()
        runCurrent()
        assertThat(ended).containsExactly(SaveOutcome.Saved)
    }

    @Test
    fun aFailureThatComesAtOnceStaysShown() = runTest {
        val immediate = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        val session = SetUpSession(handle, NumberText(Locale.ENGLISH), { amsterdam }, { "en" }, access,
            Saves(access, immediate), immediate) { ended += it }
        toTheCheck(session)
        access.writeFails = true

        session.finish()

        assertThat(session.finishing.value).isEqualTo(SetUpSession.Finishing.Failed("No space left on device"))
    }

    @Test
    fun backWaitsWhileFinishSaves() = runTest {
        val session = session()
        toTheCheck(session)
        val gate = CompletableDeferred<Unit>()
        access.readGate = gate

        session.finish()
        runCurrent()

        assertThat(session.back()).isTrue()
        assertThat(session.flow.step.value).isEqualTo(SetUpStep.CHECK)
        gate.complete(Unit)
        runCurrent()
        assertThat(ended).containsExactly(SaveOutcome.Saved)
    }

    @Test
    fun aFileOfANewerVersionEndsSetUpWithIt() = runTest {
        val newer = StartState.NewerVersion("written by a newer version of the app")
        val session = session()
        toTheCheck(session)
        access.settings = newer

        session.finish()
        runCurrent()

        assertThat(ended).containsExactly(SaveOutcome.SettingsUnusable(newer))
        assertThat(access.written).isEmpty()
    }

    @Test
    fun aRefusedValueAtFinishGoesBackToItsStepAndSaysWhyUntilTheOwnerLeavesIt() = runTest {
        val session = session()
        toTheCheck(session)
        session.flow.car.edit(CarForm.Field.CONSUMPTION, "0")

        session.finish()
        runCurrent()

        assertThat(session.flow.step.value).isEqualTo(SetUpStep.CAR)
        assertThat(session.refused.value).isTrue()
        assertThat(session.problemsShown.value).isEqualTo(1)
        assertThat(session.finishing.value).isEqualTo(SetUpSession.Finishing.Idle)
        assertThat(access.written).isEmpty()
        session.flow.car.edit(CarForm.Field.CONSUMPTION, "18")
        session.next()
        assertThat(session.refused.value).isFalse()
    }

    @Test
    fun afterARefusedValueAtFinishBackWorksAndTakesTheLineAway() = runTest {
        val session = session()
        toTheCheck(session)
        session.flow.car.edit(CarForm.Field.CONSUMPTION, "0")
        session.finish()
        runCurrent()

        assertThat(session.back()).isTrue()

        assertThat(session.flow.step.value).isEqualTo(SetUpStep.PANELS)
        assertThat(session.refused.value).isFalse()
    }

    @Test
    fun backFromAFailedFinishForgetsTheFailure() = runTest {
        val session = session()
        toTheCheck(session)
        access.writeFails = true
        session.finish()
        runCurrent()
        assertThat(session.finishing.value).isInstanceOf(SetUpSession.Finishing.Failed::class.java)

        session.back()
        session.next()

        assertThat(session.flow.step.value).isEqualTo(SetUpStep.CHECK)
        assertThat(session.finishing.value).isEqualTo(SetUpSession.Finishing.Idle)
    }

    @Test
    fun aSecondFinishWhileSavingDoesNotSaveAgain() = runTest {
        val session = session()
        toTheCheck(session)
        val gate = CompletableDeferred<Unit>()
        access.readGate = gate
        session.finish()
        runCurrent()

        session.finish()
        gate.complete(Unit)
        runCurrent()

        assertThat(access.written).containsExactly("settings")
        assertThat(ended).containsExactly(SaveOutcome.Saved)
    }

    @Test
    fun typedCoordinatesThatDifferDropTheNameOfThePlaceChosen() = runTest {
        val session = session()
        session.next()
        session.choose(PlaceSearch.Result(utrecht, "Utrecht, Netherlands"))
        assertThat(session.flow.location.placeName()).isEqualTo(PlaceName("Utrecht, Netherlands"))
        assertThat(session.places.chosenCount.value).isEqualTo(1)

        session.typeCoordinate(LocationForm.Field.LATITUDE, "52.1")

        assertThat(session.flow.location.placeName()).isNull()
        assertThat(session.flow.location.text(LocationForm.Field.LATITUDE)).isEqualTo("52.1")
        assertThat(session.flow.config().system().place()).isEmpty()
    }
}
