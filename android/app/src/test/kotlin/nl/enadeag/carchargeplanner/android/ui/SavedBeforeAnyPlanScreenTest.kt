package nl.enadeag.carchargeplanner.android.ui

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.afterTheSystemEndedTheApp
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.home.AwayEntry
import nl.enadeag.carchargeplanner.android.home.BatteryEntry
import nl.enadeag.carchargeplanner.android.home.HomeNotice
import nl.enadeag.carchargeplanner.android.home.HomeOutlook
import nl.enadeag.carchargeplanner.android.home.TripEntry
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.store.Saves
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.android.ui.home.HomeScreen
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowViewRootImpl

/** The Saved snackbar of a one-off away period saved before any plan was shown, on Robolectric. */
@RunWith(AndroidJUnit4::class)
class SavedBeforeAnyPlanScreenTest {

    /** A clock that stands still. */
    private class TestClock(val now: Instant) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
    }

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    // nine hours ahead of UTC, so a time late in the evening is told the same day in UTC but nine hours earlier
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val today = LocalDate.of(2026, 6, 1)
    private val clock = TestClock(today.atTime(22, 0).atZone(tokyo).toInstant())
    private val access = FakeEngineAccess(StartState.Ready(EngineConfig.setUpDraft(tokyo, ProviderId("open-meteo"))))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handle = SavedStateHandle()

    @After
    fun stop() {
        scope.cancel()
    }

    /** Shows the home with its plan still asked, so no dashboard was shown yet. */
    private fun show(kept: SavedStateHandle) {
        Settings.System.putString(compose.activity.contentResolver, Settings.System.TIME_12_24, "24")
        val outlook = HomeOutlook(access, scope, clock) { }
        val battery = BatteryEntry(kept, NumberText(Locale.ENGLISH), access, scope) { }
        val away = AwayEntry(kept, access, scope, clock, outlook::awayChanged)
        val trips = TripEntry(SavedStateHandle(), NumberText(Locale.ENGLISH), access, scope, clock) { }
        val notice = HomeNotice(Saves(access, scope), scope) { }
        compose.setContent {
            AppTheme { HomeScreen(outlook, notice, battery, away, trips, { tokyo }, allowedCard(scope)) { } }
        }
        compose.waitForIdle()
    }

    @Test
    fun aPeriodSavedBeforeAnyPlanIsToldInTheHomesZoneNotInUtc() {
        AwayEntry(handle, access, scope, clock) { }.apply {
            open(today, tokyo)
            choose(AwayEntry.Kind.PART_OF_DAY)
            start(LocalTime.of(23, 0))
            end(LocalTime.of(23, 45))
        }
        show(handle.afterTheSystemEndedTheApp())
        val dialog = ShadowDialog.getLatestDialog() as ComponentDialog
        compose.runOnIdle {
            Shadow.extract<ShadowViewRootImpl>(dialog.window!!.decorView.parent).callWindowFocusChanged(true)
        }

        compose.onNodeWithText(text(R.string.action_save)).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.one_off_saved,
            text(R.string.one_off_part, "Today", text(R.string.time_span, "23:00", "23:45")))).assertIsDisplayed()
    }
}
