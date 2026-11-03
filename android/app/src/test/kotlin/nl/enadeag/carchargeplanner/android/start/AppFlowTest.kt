package nl.enadeag.carchargeplanner.android.start

import androidx.lifecycle.SavedStateHandle
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nl.enadeag.carchargeplanner.android.afterTheSystemEndedTheApp
import nl.enadeag.carchargeplanner.android.form.CarForm
import nl.enadeag.carchargeplanner.android.form.LocationForm
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.home.HomeOutlook
import nl.enadeag.carchargeplanner.android.home.Plans
import nl.enadeag.carchargeplanner.android.home.TodayPlan
import nl.enadeag.carchargeplanner.android.settings.SettingsPage
import nl.enadeag.carchargeplanner.android.setup.PlaceSearch
import nl.enadeag.carchargeplanner.android.setup.SetUpAgain
import nl.enadeag.carchargeplanner.android.setup.SetUpSession
import nl.enadeag.carchargeplanner.android.setup.SetUpStep
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.store.SaveOutcome
import nl.enadeag.carchargeplanner.android.store.Saves
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.config.Coordinates
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.forecast.Place
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppFlowTest {

    private val amsterdam = ZoneId.of("Europe/Amsterdam")
    private val config = EngineConfig.setUpDraft(amsterdam, ProviderId("open-meteo"))
    private val utrecht = Place("Utrecht", Coordinates(52.0907, 5.1214), amsterdam, "", "Netherlands", "")

    private fun TestScope.flow(access: FakeEngineAccess, saves: Saves = Saves(access, backgroundScope),
        handle: SavedStateHandle = SavedStateHandle()) =
        AppFlow(access, saves, backgroundScope, backgroundScope, handle, NumberText(Locale.ENGLISH),
            { amsterdam }, { "en" }, StandardTestDispatcher(testScheduler)).also { runCurrent() }

    /** The home with its plan in, then Settings opened from it. */
    private fun TestScope.settingsFromThePlannedHome(access: FakeEngineAccess, saves: Saves = Saves(access,
        backgroundScope), handle: SavedStateHandle = SavedStateHandle()): AppFlow {
        val flow = flow(access, saves, handle)
        access.outlooks.single().complete(planned)
        runCurrent()
        flow.openSettings()
        runCurrent()
        return flow
    }

    /** Goes through set-up with a place chosen and the rest as it comes, and finishes it. */
    private fun TestScope.finishSetUp(session: SetUpSession, place: Place = utrecht) {
        session.next()
        session.choose(PlaceSearch.Result(place, place.label()))
        repeat(5) { session.next() }
        session.finish()
        runCurrent()
    }

    private val planned = Plans.planned(LocalDate.of(2026, 6, 1).atTime(8, 0).atZone(amsterdam), listOf(listOf()))

    @Test
    fun withoutSettingsTheAppShowsSetUp() = runTest {
        assertThat(flow(FakeEngineAccess(StartState.NotSetUp)).screen.value)
            .isInstanceOf(AppFlow.Screen.SetUp::class.java)
    }

    @Test
    fun withSettingsTheAppShowsTheHome() = runTest {
        assertThat(flow(FakeEngineAccess(StartState.Ready(config))).screen.value)
            .isInstanceOf(AppFlow.Screen.Home::class.java)
    }

    @Test
    fun aFileOfANewerVersionAsksForAnUpdate() = runTest {
        val screen = flow(FakeEngineAccess(StartState.NewerVersion("version 99"))).screen.value

        assertThat(screen).isEqualTo(AppFlow.Screen.NewerVersion("version 99"))
    }

    @Test
    fun anUnusableFileSetAsideStartsSetUp() = runTest {
        val access = FakeEngineAccess(StartState.Unusable("engine.json", "not valid JSON"))
        val flow = flow(access)
        val again = (flow.screen.value as AppFlow.Screen.Unusable).again
        assertThat(again.unusable.details).isEqualTo("not valid JSON")

        again.ask()
        again.confirm()
        runCurrent()

        assertThat(again.state.value).isInstanceOf(SetUpAgain.State.Moved::class.java)
        assertThat(flow.screen.value).isInstanceOf(AppFlow.Screen.SetUp::class.java)
    }

    @Test
    fun finishingSetUpOpensTheHomeWhichTakesTheSavesOutcomesFromThenOn() = runTest {
        val access = FakeEngineAccess(StartState.NotSetUp)
        val saves = Saves(access, backgroundScope)
        val flow = flow(access, saves)
        val session = (flow.screen.value as AppFlow.Screen.SetUp).session
        session.next()
        session.choose(PlaceSearch.Result(utrecht, "Utrecht, Netherlands"))
        repeat(5) { session.next() }

        session.finish()
        runCurrent()

        val home = flow.screen.value as AppFlow.Screen.Home
        access.writeFails = true
        saves.finishSetUp(config)
        runCurrent()
        assertThat(home.notice.notice.value).isEqualTo(SaveOutcome.Failed("No space left on device"))
        assertThat(saves.lastOutcome.value).isNull()
    }

    @Test
    fun finishOverAFileOfANewerVersionAsksForAnUpdate() = runTest {
        val access = FakeEngineAccess(StartState.NotSetUp)
        val flow = flow(access)
        val session = (flow.screen.value as AppFlow.Screen.SetUp).session
        session.next()
        session.choose(PlaceSearch.Result(utrecht, "Utrecht, Netherlands"))
        repeat(5) { session.next() }
        access.settings = StartState.NewerVersion("version 99")

        session.finish()
        runCurrent()

        assertThat(flow.screen.value).isEqualTo(AppFlow.Screen.NewerVersion("version 99"))
    }

    @Test
    fun aHomeWhoseSettingsFileWentReadsItAgain() = runTest {
        val access = FakeEngineAccess(StartState.Ready(config))
        val flow = flow(access)
        access.settings = StartState.NotSetUp

        access.outlooks.single().complete(Outlook.NotSetUp(Path.of("engine.json")))
        runCurrent()

        assertThat(flow.screen.value).isInstanceOf(AppFlow.Screen.SetUp::class.java)
    }

    @Test
    fun settingsOpensOnItsListAndBackLeadsToTheHomeWithItsPlanWithoutAskingAgain() = runTest {
        val access = FakeEngineAccess(StartState.Ready(config))
        val flow = settingsFromThePlannedHome(access)
        val settings = flow.screen.value as AppFlow.Screen.Settings
        assertThat(settings.page).isNull()

        settings.session.back()
        runCurrent()

        val home = flow.screen.value as AppFlow.Screen.Home
        assertThat(home.outlook.state.value).isEqualTo(HomeOutlook.State.Planned(TodayPlan.of(planned)))
        assertThat(access.outlooks).hasSize(1)
    }

    @Test
    fun theScreenFollowsThePageOpen() = runTest {
        val flow = settingsFromThePlannedHome(FakeEngineAccess(StartState.Ready(config)))
        val session = (flow.screen.value as AppFlow.Screen.Settings).session

        session.open(SettingsPage.CHARGER)
        runCurrent()

        assertThat(flow.screen.value).isEqualTo(AppFlow.Screen.Settings(session, SettingsPage.CHARGER))
    }

    @Test
    fun aSaveThatChangedSomethingHasTheHomeAskAgainOnItsReturn() = runTest {
        val access = FakeEngineAccess(StartState.Ready(config))
        val flow = settingsFromThePlannedHome(access)
        val session = (flow.screen.value as AppFlow.Screen.Settings).session
        session.open(SettingsPage.CAR_AND_HOUSE)
        session.forms.car.edit(CarForm.Field.BATTERY, "75")
        session.save()
        runCurrent()

        session.back()
        session.back()
        runCurrent()

        assertThat(flow.screen.value).isInstanceOf(AppFlow.Screen.Home::class.java)
        assertThat(access.outlooks).hasSize(2)
    }

    @Test
    fun settingsAndItsPageComeBackAfterTheSystemEndedTheApp() = runTest {
        val access = FakeEngineAccess(StartState.Ready(config))
        val handle = SavedStateHandle()
        val flow = settingsFromThePlannedHome(access, handle = handle)
        (flow.screen.value as AppFlow.Screen.Settings).session.open(SettingsPage.AWAY)
        runCurrent()

        val again = flow(access, handle = handle.afterTheSystemEndedTheApp())

        assertThat((again.screen.value as AppFlow.Screen.Settings).page).isEqualTo(SettingsPage.AWAY)
    }

    @Test
    fun aSaveThatFindsTheFileUnusableShowsItsScreenAndSettingsIsClosed() = runTest {
        val access = FakeEngineAccess(StartState.Ready(config))
        val handle = SavedStateHandle()
        val flow = settingsFromThePlannedHome(access, handle = handle)
        val session = (flow.screen.value as AppFlow.Screen.Settings).session
        session.open(SettingsPage.CAR_AND_HOUSE)
        session.forms.car.edit(CarForm.Field.BATTERY, "75")
        access.settings = StartState.Unusable("engine.json", "not valid JSON")

        session.save()
        runCurrent()

        assertThat(flow.screen.value).isInstanceOf(AppFlow.Screen.Unusable::class.java)
        access.settings = StartState.Ready(config)
        val again = flow(access, handle = handle.afterTheSystemEndedTheApp())
        assertThat(again.screen.value).isInstanceOf(AppFlow.Screen.Home::class.java)
    }

    @Test
    fun setUpAgainAfterASettingsSaveFoundTheFileUnusableStartsAfreshAndSettingsLaterOpensOnItsList() = runTest {
        val lisbon = ZoneId.of("Europe/Lisbon")
        val access = FakeEngineAccess(StartState.NotSetUp)
        val flow = flow(access)
        finishSetUp((flow.screen.value as AppFlow.Screen.SetUp).session,
            Place("Lisbon", Coordinates(38.72, -9.14), lisbon, "", "Portugal", ""))
        access.outlooks.single().complete(planned)
        runCurrent()
        flow.openSettings()
        runCurrent()
        val settings = (flow.screen.value as AppFlow.Screen.Settings).session
        settings.open(SettingsPage.LOCATION)
        settings.back()
        settings.open(SettingsPage.CAR_AND_HOUSE)
        settings.forms.car.edit(CarForm.Field.BATTERY, "75")
        access.settings = StartState.Unusable("engine.json", "not valid JSON")
        settings.save()
        runCurrent()
        val again = (flow.screen.value as AppFlow.Screen.Unusable).again
        again.ask()
        again.confirm()
        runCurrent()

        val setUp = (flow.screen.value as AppFlow.Screen.SetUp).session
        assertThat(setUp.flow.step.value).isEqualTo(SetUpStep.WELCOME)
        setUp.next()
        setUp.typeCoordinate(LocationForm.Field.LATITUDE, "38.8")
        assertThat(setUp.flow.location.timeZone()).isEqualTo(amsterdam)
        setUp.back()
        finishSetUp(setUp)
        access.outlooks.last().complete(planned)
        runCurrent()
        flow.openSettings()
        runCurrent()
        val later = flow.screen.value as AppFlow.Screen.Settings
        assertThat(later.page).isNull()
        later.session.open(SettingsPage.CAR_AND_HOUSE)
        assertThat(later.session.forms.car.text(CarForm.Field.BATTERY)).isEqualTo("60.0")
    }

    @Test
    fun setUpAgainAfterTheSystemEndedTheAppDuringSetUpStartsOnTheWelcome() = runTest {
        val access = FakeEngineAccess(StartState.NotSetUp)
        val handle = SavedStateHandle()
        val session = (flow(access, handle = handle).screen.value as AppFlow.Screen.SetUp).session
        session.next()
        session.choose(PlaceSearch.Result(utrecht, "Utrecht, Netherlands"))
        session.next()
        access.settings = StartState.Unusable("engine.json", "not valid JSON")

        val flow = flow(access, handle = handle.afterTheSystemEndedTheApp())
        val again = (flow.screen.value as AppFlow.Screen.Unusable).again
        again.ask()
        again.confirm()
        runCurrent()

        val setUp = (flow.screen.value as AppFlow.Screen.SetUp).session
        assertThat(setUp.flow.step.value).isEqualTo(SetUpStep.WELCOME)
        assertThat(setUp.flow.isEdited()).isFalse()
    }

    @Test
    fun leavingSettingsKeepsNothingOfItSoTheAppComesBackOnTheHome() = runTest {
        val access = FakeEngineAccess(StartState.Ready(config))
        val handle = SavedStateHandle()
        val flow = settingsFromThePlannedHome(access, handle = handle)
        val session = (flow.screen.value as AppFlow.Screen.Settings).session
        session.open(SettingsPage.CAR_AND_HOUSE)
        session.forms.car.edit(CarForm.Field.BATTERY, "75")
        session.back()
        session.discard()

        session.back()
        runCurrent()

        assertThat(flow.screen.value).isInstanceOf(AppFlow.Screen.Home::class.java)
        assertThat(handle.keys()).isEmpty()
        val again = flow(access, handle = handle.afterTheSystemEndedTheApp())
        assertThat(again.screen.value).isInstanceOf(AppFlow.Screen.Home::class.java)
    }

    @Test
    fun finishingSetUpKeepsNothingOfIt() = runTest {
        val access = FakeEngineAccess(StartState.NotSetUp)
        val handle = SavedStateHandle()
        val flow = flow(access, handle = handle)

        finishSetUp((flow.screen.value as AppFlow.Screen.SetUp).session)

        assertThat(flow.screen.value).isInstanceOf(AppFlow.Screen.Home::class.java)
        assertThat(handle.keys()).isEmpty()
    }
}
