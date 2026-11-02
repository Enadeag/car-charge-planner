package nl.enadeag.carchargeplanner.android.ui

import androidx.lifecycle.SavedStateHandle
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nl.enadeag.carchargeplanner.android.form.LocationForm
import nl.enadeag.carchargeplanner.android.start.AppFlow
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.store.Saves
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private val zoneAtStart: TimeZone = TimeZone.getDefault()

    @BeforeEach
    fun useTheTestDispatcherAsMain() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun putEverythingBack() {
        TimeZone.setDefault(zoneAtStart)
        Dispatchers.resetMain()
    }

    private fun TestScope.model(): AppViewModel {
        val access = FakeEngineAccess()
        return AppViewModel(access, Saves(access, backgroundScope), backgroundScope, SavedStateHandle(),
            Locale.ENGLISH, "en")
    }

    @Test
    fun thePhonesTimeZoneIsTheOneItHasWhenAsked() = runTest {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Amsterdam"))
        val model = model()

        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))

        assertThat(model.flow.phoneZone()).isEqualTo(ZoneId.of("Asia/Tokyo"))
    }

    @Test
    fun typedCoordinatesGetThePhonesTimeZoneAsItChangedAfterTheModelWasMade() = runTest {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Amsterdam"))
        val model = model()
        runCurrent()
        val session = (model.flow.screen.value as AppFlow.Screen.SetUp).session

        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        session.flow.location.edit(LocationForm.Field.LATITUDE, "35.68")

        assertThat(session.flow.location.timeZone()).isEqualTo(ZoneId.of("Asia/Tokyo"))
    }
}
