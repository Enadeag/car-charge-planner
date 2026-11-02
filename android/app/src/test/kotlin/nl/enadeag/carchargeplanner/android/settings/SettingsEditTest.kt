package nl.enadeag.carchargeplanner.android.settings

import androidx.lifecycle.SavedStateHandle
import java.time.ZoneId
import java.util.Locale
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nl.enadeag.carchargeplanner.android.AppEngine
import nl.enadeag.carchargeplanner.android.form.CarForm
import nl.enadeag.carchargeplanner.android.form.ConfigForm
import nl.enadeag.carchargeplanner.android.form.ForecastForm
import nl.enadeag.carchargeplanner.android.form.HouseForm
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.form.PlanningForm
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.store.SaveOutcome
import nl.enadeag.carchargeplanner.android.store.Saves
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.android.store.StoredKeys
import nl.enadeag.carchargeplanner.engine.config.Car
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.House
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet
import nl.enadeag.carchargeplanner.engine.secrets.Secrets
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsEditTest {

    private val numbers = NumberText(Locale.ENGLISH)
    private val shown = EngineConfig.setUpDraft(ZoneId.of("Europe/Amsterdam"), ProviderId("open-meteo"))
    private val access = FakeEngineAccess(StartState.Ready(shown))

    /** The screen's own thread, which the test runs. */
    private fun TestScope.screen() = StandardTestDispatcher(testScheduler)

    /** A screen's thread that knows when it runs work, on the test's scheduler like every other dispatcher here. */
    private class KnownScreen(private val thread: CoroutineDispatcher) : CoroutineDispatcher() {

        private val running = ThreadLocal.withInitial { false }

        /** Whether the work running now was handed to this dispatcher. */
        fun isRunning(): Boolean = running.get()

        override fun dispatch(context: CoroutineContext, block: Runnable) = thread.dispatch(context) {
            running.set(true)
            try {
                block.run()
            } finally {
                running.set(false)
            }
        }
    }

    /** A form that notes, at each read, whether it runs on the screen's thread. */
    private class Watched(private val form: ConfigForm, private val screen: KnownScreen) : ConfigForm by form {

        val readsOnTheScreen = mutableListOf<Boolean>()

        override fun read(config: EngineConfig): EngineConfig? {
            readsOnTheScreen += screen.isRunning()
            return form.read(config)
        }
    }

    @Test
    fun saveLaysOnlyTheScreensPartOverTheFileAsItIsThen() = runTest {
        val car = CarForm(SavedStateHandle(), numbers).apply { load(shown) }
        val house = HouseForm(SavedStateHandle(), numbers).apply { load(shown) }
        val edit = SettingsEdit(listOf(car, house), Saves(access, backgroundScope), screen())
        house.edit(HouseForm.Field.BASELOAD, "0.8")
        val elsewhere = shown.withSystem(shown.system().withHouseAndCar(House.TYPICAL, Car(77.0, 18.0, 40.0)),
            shown.typical())
        access.settings = StartState.Ready(elsewhere)

        edit.save(shown)
        runCurrent()

        val saved = (access.settings as StartState.Ready).config.system()
        assertThat(saved.house()).isEqualTo(House(0.8))
        assertThat(saved.car().usableBatteryKwh()).isEqualTo(77.0)
    }

    @Test
    fun aRefusedValueOnTheScreenSavesNothingAndEveryFormShowsItsProblems() = runTest {
        val car = CarForm(SavedStateHandle(), numbers).apply { load(shown) }
        val house = HouseForm(SavedStateHandle(), numbers).apply { load(shown) }
        val edit = SettingsEdit(listOf(car, house), Saves(access, backgroundScope), screen())
        car.edit(CarForm.Field.BATTERY, "x")
        house.edit(HouseForm.Field.BASELOAD, "-1")

        assertThat(edit.save(shown)).isNull()
        runCurrent()

        assertThat(car.problems.value).containsOnlyKeys(CarForm.Field.BATTERY)
        assertThat(house.problems.value).containsOnlyKeys(HouseForm.Field.BASELOAD)
        assertThat(access.written).isEmpty()
        assertThat(edit.isEdited()).isTrue()
    }

    @Test
    fun theForecastScreenSavesItsKeysFirst() = runTest {
        val providers = AppEngine.providers(HttpGet { throw java.io.IOException("no network") })
        val forecast = ForecastForm(SavedStateHandle(), providers.ids().map { providers.find(it).orElseThrow().info() })
        forecast.load(shown)
        forecast.loadKeys(StoredKeys.Read(Secrets.NONE))
        forecast.edit(ProviderId("open-meteo"), "api_key", "MADEUPKEY")
        val saves = Saves(access, backgroundScope)

        SettingsEdit(listOf(forecast), saves, screen()).save(shown)
        runCurrent()

        assertThat(access.written).containsExactly("keys")
        assertThat(access.keys).isEqualTo(Secrets(mapOf(ProviderId("open-meteo") to mapOf("api_key" to "MADEUPKEY"))))
        assertThat(saves.lastOutcome.value).isEqualTo(SaveOutcome.Saved)
    }

    @Test
    fun oneRefusedValueAmongAcceptedFormsSavesNothing() = runTest {
        val car = CarForm(SavedStateHandle(), numbers).apply { load(shown) }
        val house = HouseForm(SavedStateHandle(), numbers).apply { load(shown) }
        val planning = PlanningForm(SavedStateHandle(), numbers).apply { load(shown) }
        val saves = Saves(access, backgroundScope)
        val edit = SettingsEdit(listOf(car, house, planning), saves, screen())
        car.edit(CarForm.Field.BATTERY, "77")
        house.edit(HouseForm.Field.BASELOAD, "x")

        assertThat(edit.save(shown)).isNull()
        runCurrent()

        assertThat(house.problems.value).containsOnlyKeys(HouseForm.Field.BASELOAD)
        assertThat(access.written).isEmpty()
        assertThat(saves.lastOutcome.value).isNull()
    }

    @Test
    fun aSaveStartedFromAScreensScopeOutlivesThatScope() = runTest {
        val screen = screen()
        val screenScope = CoroutineScope(screen + Job())
        val house = HouseForm(SavedStateHandle(), numbers).apply { load(shown) }
        val saves = Saves(access, backgroundScope)
        val edit = SettingsEdit(listOf(house), saves, screen)
        house.edit(HouseForm.Field.BASELOAD, "0.8")
        val gate = CompletableDeferred<Unit>()
        access.readGate = gate

        screenScope.launch { edit.save(shown) }
        runCurrent()
        screenScope.cancel()
        gate.complete(Unit)
        runCurrent()

        assertThat((access.settings as StartState.Ready).config.system().house()).isEqualTo(House(0.8))
        assertThat(saves.lastOutcome.value).isEqualTo(SaveOutcome.Saved)
    }

    @Test
    fun theFormsAreReadOnTheScreensThreadWhileTheSaveRunsInTheAppsScope() = runTest {
        val screen = KnownScreen(screen())
        val house = Watched(HouseForm(SavedStateHandle(), numbers).apply {
            load(shown)
            edit(HouseForm.Field.BASELOAD, "0.8")
        }, screen)
        val edit = SettingsEdit(listOf(house), Saves(access, backgroundScope), screen)

        edit.save(shown)
        runCurrent()

        assertThat(house.readsOnTheScreen).containsExactly(true)
        assertThat((access.settings as StartState.Ready).config.system().house()).isEqualTo(House(0.8))
    }
}
