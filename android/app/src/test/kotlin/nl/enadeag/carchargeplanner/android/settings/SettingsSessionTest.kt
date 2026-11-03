package nl.enadeag.carchargeplanner.android.settings

import androidx.lifecycle.SavedStateHandle
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nl.enadeag.carchargeplanner.android.afterTheSystemEndedTheApp
import nl.enadeag.carchargeplanner.android.form.CarForm
import nl.enadeag.carchargeplanner.android.form.FieldProblem
import nl.enadeag.carchargeplanner.android.form.LocationForm
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.form.PanelsForm
import nl.enadeag.carchargeplanner.android.form.PlanningForm
import nl.enadeag.carchargeplanner.android.setup.PlaceSearch
import nl.enadeag.carchargeplanner.android.setup.SetUpCheck
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.store.Saves
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.android.store.StoredKeys
import nl.enadeag.carchargeplanner.engine.config.Coordinates
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.forecast.ConnectionResult
import nl.enadeag.carchargeplanner.engine.forecast.PlaceResults
import nl.enadeag.carchargeplanner.engine.secrets.KeyChanges
import nl.enadeag.carchargeplanner.engine.secrets.Secrets
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsSessionTest {

    private val amsterdam = ZoneId.of("Europe/Amsterdam")
    private val openMeteo = ProviderId("open-meteo")
    private val forecastSolar = ProviderId("forecast-solar")
    private val draft = EngineConfig.setUpDraft(amsterdam, openMeteo)
    private val stored = draft.withSystem(draft.system().withLocation(Coordinates(52.0907, 5.1214), amsterdam),
        draft.typical())
    private val access = FakeEngineAccess(StartState.Ready(stored))
    private val handle = SavedStateHandle()
    private var closed = 0
    private val unusable = mutableListOf<StartState.WithoutSettings>()

    private fun TestScope.session(on: SavedStateHandle = handle, saves: Saves = Saves(access, backgroundScope)) =
        SettingsSession(on, NumberText(Locale.ENGLISH), { amsterdam }, { "en" }, access, saves, backgroundScope,
            StandardTestDispatcher(testScheduler), stored, { closed++ }) { unusable += it }.also { runCurrent() }

    private fun storedNow() = (access.settings as StartState.Ready).config

    @Test
    fun backFromAPageWithoutEditsGoesToTheListAndFromTheListLeavesSettings() = runTest {
        val session = session()
        session.open(SettingsPage.CHARGER)
        assertThat(session.page.value).isEqualTo(SettingsPage.CHARGER)

        session.back()
        assertThat(session.page.value).isNull()
        assertThat(closed).isZero()
        session.back()

        assertThat(closed).isEqualTo(1)
    }

    @Test
    fun backWithEditsAsksAndKeepEditingStaysWithTheEdits() = runTest {
        val session = session()
        session.open(SettingsPage.CAR_AND_HOUSE)
        session.forms.car.edit(CarForm.Field.BATTERY, "75")

        session.back()
        assertThat(session.discardAsked.value).isTrue()
        assertThat(session.page.value).isEqualTo(SettingsPage.CAR_AND_HOUSE)
        session.keepEditing()

        assertThat(session.discardAsked.value).isFalse()
        assertThat(session.page.value).isEqualTo(SettingsPage.CAR_AND_HOUSE)
        assertThat(session.forms.car.text(CarForm.Field.BATTERY)).isEqualTo("75")
        assertThat(access.written).isEmpty()
    }

    @Test
    fun discardGoesToTheListAndThePageOpensAgainWithTheStoredValuesAndNoProblems() = runTest {
        val session = session()
        session.open(SettingsPage.CAR_AND_HOUSE)
        session.forms.car.edit(CarForm.Field.BATTERY, "lots")
        session.save()
        assertThat(session.forms.car.problems.value).isNotEmpty()
        session.back()

        session.discard()
        assertThat(session.page.value).isNull()
        session.open(SettingsPage.CAR_AND_HOUSE)

        assertThat(session.forms.car.text(CarForm.Field.BATTERY)).isEqualTo("60.0")
        assertThat(session.forms.car.problems.value).isEmpty()
        assertThat(session.refused.value).isFalse()
        assertThat(access.written).isEmpty()
    }

    @Test
    fun saveStoresOnlyThePageAndKeepsItOpenWithoutEdits() = runTest {
        val saves = Saves(access, backgroundScope)
        val session = session(saves = saves)
        session.open(SettingsPage.CAR_AND_HOUSE)
        session.forms.car.edit(CarForm.Field.BATTERY, "75")

        session.save()
        runCurrent()

        assertThat(storedNow().system().car().usableBatteryKwh()).isEqualTo(75.0)
        assertThat(storedNow().planning()).isEqualTo(stored.planning())
        assertThat(session.page.value).isEqualTo(SettingsPage.CAR_AND_HOUSE)
        assertThat(session.saving.value).isEqualTo(SettingsSession.SaveState.Saved)
        assertThat(session.forms.car.isEdited()).isFalse()
        assertThat(session.config.value).isEqualTo(storedNow())
        assertThat(saves.lastOutcome.value).isNull()
        assertThat(saves.changes.value).isEqualTo(1)
        session.back()
        assertThat(session.page.value).isNull()
    }

    @Test
    fun aRefusedValueShowsItsProblemSavesNothingAndCountsAsShown() = runTest {
        val session = session()
        session.open(SettingsPage.CAR_AND_HOUSE)
        session.forms.car.edit(CarForm.Field.BATTERY, "lots")

        session.save()
        runCurrent()

        assertThat(access.written).isEmpty()
        assertThat(session.refused.value).isTrue()
        assertThat(session.problemsShown.value).isEqualTo(1)
        assertThat(session.saving.value).isEqualTo(SettingsSession.SaveState.Idle)
        assertThat(session.forms.car.problems.value).containsEntry(CarForm.Field.BATTERY, FieldProblem.NotANumber)
    }

    @Test
    fun backWaitsWhileThePageSaves() = runTest {
        val session = session()
        session.open(SettingsPage.CAR_AND_HOUSE)
        session.forms.car.edit(CarForm.Field.BATTERY, "75")
        val gate = CompletableDeferred<Unit>()
        access.readGate = gate

        session.save()
        runCurrent()
        session.back()

        assertThat(session.page.value).isEqualTo(SettingsPage.CAR_AND_HOUSE)
        assertThat(session.discardAsked.value).isFalse()
        gate.complete(Unit)
        runCurrent()
        assertThat(session.saving.value).isEqualTo(SettingsSession.SaveState.Saved)
    }

    @Test
    fun aFailedWriteSaysWhyAndKeepsTheEdits() = runTest {
        val session = session()
        session.open(SettingsPage.CAR_AND_HOUSE)
        session.forms.car.edit(CarForm.Field.BATTERY, "75")
        access.writeFails = true

        session.save()
        runCurrent()

        assertThat(session.saving.value).isEqualTo(SettingsSession.SaveState.Failed("No space left on device"))
        assertThat(session.forms.car.text(CarForm.Field.BATTERY)).isEqualTo("75")
        assertThat(session.forms.car.isEdited()).isTrue()
    }

    @Test
    fun keysThatCouldNotBeStoredSaySoAndTheTypedKeyStays() = runTest {
        val session = session()
        session.open(SettingsPage.FORECAST)
        runCurrent()
        session.forms.forecast.edit(openMeteo, "api_key", "K1")
        access.keysFail = true

        session.save()
        runCurrent()

        assertThat(session.saving.value).isEqualTo(SettingsSession.SaveState.KeysNotSaved)
        assertThat(session.forms.forecast.text(openMeteo, "api_key")).isEqualTo("K1")
        assertThat(access.written).isEmpty()
    }

    @Test
    fun theForecastPageWaitsForTheKeysBeforeItSaves() = runTest {
        val session = session()
        session.open(SettingsPage.FORECAST)
        assertThat(session.keys.value).isNull()

        session.save()
        runCurrent()

        assertThat(session.saving.value).isEqualTo(SettingsSession.SaveState.Idle)
        assertThat(session.keys.value).isEqualTo(StoredKeys.Read(Secrets.NONE))
    }

    @Test
    fun aStoredKeyFillsItsFieldAndAKeyTypedIsNeverInTheSavedState() = runTest {
        access.keys = Secrets(mapOf(openMeteo to mapOf("api_key" to "STORED1")))
        val session = session()
        session.open(SettingsPage.FORECAST)
        runCurrent()
        assertThat(session.forms.forecast.text(openMeteo, "api_key")).isEqualTo("STORED1")

        session.forms.forecast.edit(openMeteo, "api_key", "TYPED2")

        val kept = handle.keys().map { handle.get<Any>(it).toString() }
        assertThat(kept).noneMatch { it.contains("TYPED2") || it.contains("STORED1") }
        val again = session(handle.afterTheSystemEndedTheApp())
        assertThat(again.page.value).isEqualTo(SettingsPage.FORECAST)
        assertThat(again.forms.forecast.text(openMeteo, "api_key")).isEqualTo("STORED1")
    }

    @Test
    fun keysThatCouldNotBeReadAreNotAskedForAndOneLeftEmptyStays() = runTest {
        access.keysUnreadable = true
        val session = session()
        session.open(SettingsPage.FORECAST)
        runCurrent()
        assertThat(session.keys.value).isEqualTo(StoredKeys.Unreadable)
        assertThat(session.forms.forecast.keysReadable()).isFalse()
        session.forms.forecast.chooseFallback(forecastSolar)

        session.save()
        runCurrent()

        assertThat(access.written).containsExactly("settings")
        assertThat(storedNow().forecast().fallback()).contains(forecastSolar)
    }

    @Test
    fun theOpenPageAndTheTypedTextsSurviveTheSystemEndingTheAppButNotTheProblems() = runTest {
        val session = session()
        session.open(SettingsPage.SOLAR)
        val row = session.forms.panels.rowIds.value.single()
        session.forms.panels.edit(row, PanelsForm.Field.TILT, "steep")
        session.save()
        assertThat(session.forms.panels.problems(row).value).isNotEmpty()

        val again = session(handle.afterTheSystemEndedTheApp())

        assertThat(again.page.value).isEqualTo(SettingsPage.SOLAR)
        val kept = again.forms.panels.rowIds.value.single()
        assertThat(again.forms.panels.text(kept, PanelsForm.Field.TILT)).isEqualTo("steep")
        assertThat(again.forms.panels.problems(kept).value).isEmpty()
        assertThat(again.forms.panels.isEdited()).isTrue()
    }

    @Test
    fun theDiscardQuestionSurvivesTheSystemEndingTheApp() = runTest {
        val session = session()
        session.open(SettingsPage.PLANNING)
        session.forms.planning.edit(PlanningForm.Field.MARGIN, "50")
        session.back()

        val again = session(handle.afterTheSystemEndedTheApp())

        assertThat(again.discardAsked.value).isTrue()
    }

    @Test
    fun theLastArrayStaysAndRemovingAnotherActsAtOnce() = runTest {
        val session = session()
        session.open(SettingsPage.SOLAR)
        val panels = session.forms.panels
        val first = panels.rowIds.value.single()
        assertThat(panels.remove(first)).isFalse()

        val second = panels.add()
        assertThat(panels.remove(first)).isTrue()

        assertThat(panels.rowIds.value).containsExactly(second)
    }

    @Test
    fun inSettingsTypedCoordinatesLeaveTheStoredZone() = runTest {
        val lisbon = ZoneId.of("Europe/Lisbon")
        val stored = stored.withSystem(stored.system().withLocation(Coordinates(38.72, -9.14), lisbon),
            stored.typical())
        access.settings = StartState.Ready(stored)
        val session = SettingsSession(handle, NumberText(Locale.ENGLISH), { amsterdam }, { "en" }, access,
            Saves(access, backgroundScope), backgroundScope, StandardTestDispatcher(testScheduler), stored, { }) { }
        session.open(SettingsPage.LOCATION)

        session.forms.location.edit(LocationForm.Field.LATITUDE, "38.8")

        assertThat(session.forms.location.timeZone()).isEqualTo(lisbon)
    }

    @Test
    fun thePlaceSearchUsesTheStoredPrimaryProvider() = runTest {
        access.settings = StartState.Ready(stored)
        val session = session()
        session.open(SettingsPage.LOCATION)

        session.places.edit("Utrecht")
        session.places.search()
        runCurrent()

        assertThat(access.searchedWith).containsExactly(openMeteo)
        assertThat(session.hasPlaceSearch(openMeteo)).isTrue()
        assertThat(session.hasPlaceSearch(forecastSolar)).isFalse()
    }

    @Test
    fun checkConnectionAsksWithTheValuesAsTypedNothingSaved() = runTest {
        val session = session()
        session.open(SettingsPage.FORECAST)
        runCurrent()
        val forecast = session.forms.forecast
        forecast.edit(openMeteo, "base_url", "http://localhost:8080")
        forecast.edit(openMeteo, "api_key", "K1")
        forecast.chooseFallback(forecastSolar)

        session.checkConnection(SettingsSession.Role.PRIMARY)
        session.checkConnection(SettingsSession.Role.FALLBACK)
        runCurrent()

        assertThat(access.checked).containsExactly(
            openMeteo to mapOf("base_url" to "http://localhost:8080", "api_key" to "K1"),
            forecastSolar to mapOf())
        assertThat(access.written).isEmpty()
    }

    @Test
    fun aSaveOverAFileOfANewerVersionHandsItOn() = runTest {
        val session = session()
        session.open(SettingsPage.CAR_AND_HOUSE)
        session.forms.car.edit(CarForm.Field.BATTERY, "75")
        access.settings = StartState.NewerVersion("version 99")

        session.save()
        runCurrent()

        assertThat(unusable).containsExactly(StartState.NewerVersion("version 99"))
    }

    @Test
    fun checkConnectionWithAnEmptiedKeyDoesNotUseTheStoredOne() = runTest {
        access.keys = Secrets(mapOf(openMeteo to mapOf("api_key" to "STORED1")))
        val session = session()
        session.open(SettingsPage.FORECAST)
        runCurrent()
        session.forms.forecast.edit(openMeteo, "api_key", "")

        session.checkConnection(SettingsSession.Role.PRIMARY)
        runCurrent()

        assertThat(access.checked).containsExactly(openMeteo to mapOf("api_key" to ""))
    }

    @Test
    fun checkConnectionWithAKeyLeftEmptyWhileTheKeysCouldNotBeReadLeavesItToTheStoredOne() = runTest {
        access.keysUnreadable = true
        val session = session()
        session.open(SettingsPage.FORECAST)
        runCurrent()
        session.forms.forecast.edit(openMeteo, "api_key", "")

        session.checkConnection(SettingsSession.Role.PRIMARY)
        runCurrent()

        assertThat(access.checked).containsExactly(openMeteo to mapOf())
    }

    @Test
    fun anEmptiedKeyIsRemovedOnSave() = runTest {
        access.keys = Secrets(mapOf(openMeteo to mapOf("api_key" to "STORED1")))
        val session = session()
        session.open(SettingsPage.FORECAST)
        runCurrent()
        session.forms.forecast.edit(openMeteo, "api_key", "")

        session.save()
        runCurrent()

        assertThat(access.keys.of(openMeteo)).doesNotContainKey("api_key")
        assertThat(session.saving.value).isEqualTo(SettingsSession.SaveState.Saved)
    }

    @Test
    fun anotherSavesOutcomeLeavesThePagesEditsAlone() = runTest {
        val saves = Saves(access, backgroundScope)
        val session = session(saves = saves)
        session.open(SettingsPage.CAR_AND_HOUSE)
        session.forms.car.edit(CarForm.Field.BATTERY, "75")

        val lisbon = Coordinates(38.72, -9.14)
        saves.update(KeyChanges.NONE) { it.withSystem(it.system().withLocation(lisbon, amsterdam), it.typical()) }
        runCurrent()

        assertThat(session.config.value.system().location()).isEqualTo(lisbon)
        assertThat(session.forms.car.text(CarForm.Field.BATTERY)).isEqualTo("75")
        assertThat(session.forms.car.isEdited()).isTrue()
        assertThat(session.saving.value).isEqualTo(SettingsSession.SaveState.Idle)
    }

    @Test
    fun aSavedValueShowsAsStored() = runTest {
        val session = session()
        session.open(SettingsPage.CAR_AND_HOUSE)
        session.forms.car.edit(CarForm.Field.BATTERY, "75")

        session.save()
        runCurrent()

        assertThat(session.forms.car.text(CarForm.Field.BATTERY)).isEqualTo("75.0")
    }

    @Test
    fun leavingAPageStopsItsPlaceSearchAndItsChecks() = runTest {
        val session = session()
        session.open(SettingsPage.LOCATION)
        val places = session.places
        places.edit("Utrecht")
        places.search()
        runCurrent()
        session.back()
        session.open(SettingsPage.FORECAST)
        runCurrent()
        val check = session.primaryCheck
        session.checkConnection(SettingsSession.Role.PRIMARY)
        runCurrent()

        session.back()
        access.searches.single().complete(PlaceResults.Found(listOf()))
        access.checks.single().complete(ConnectionResult(true, 2, "Works"))
        runCurrent()

        assertThat(places.state.value).isEqualTo(PlaceSearch.State.Idle)
        assertThat(check.state.value).isNotInstanceOf(SetUpCheck.State.Works::class.java)
    }
}
