package nl.enadeag.carchargeplanner.android.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.home.AwayEntry
import nl.enadeag.carchargeplanner.android.home.BatteryEntry
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.ui.home.AwayEntryDialog
import nl.enadeag.carchargeplanner.android.ui.home.BatteryEntryDialog
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Save of the away and battery entry dialogs, which is held while it saves, so it stays focusable, on
 * Robolectric; the rest of each dialog is in `AwayEntryScreenTest` and `BatteryEntryScreenTest`.
 */
@RunWith(AndroidJUnit4::class)
class EntryDialogsScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val lisbon = ZoneId.of("Europe/Lisbon")
    private val today = LocalDate.of(2026, 6, 1)
    private val clock = Clock.fixed(Instant.parse("2026-06-01T08:00:00Z"), ZoneOffset.UTC)
    private val access = FakeEngineAccess()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handle = SavedStateHandle()

    @After
    fun stop() {
        scope.cancel()
    }

    private fun button(id: Int) = compose.onNode(hasText(text(id)) and hasAnyAncestor(isDialog()))

    /**
     * Presses Save: while the save runs it is held, focusable yet marked off, with its progress; once the save
     * failed it is on again.
     */
    private fun saveIsHeldWhileItRuns(gate: CompletableDeferred<Unit>) {
        button(R.string.action_save).performClick()
        compose.waitForIdle()

        button(R.string.saving).assertIsNotEnabled()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.RequestFocus))
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo) and
            hasAnyAncestor(isDialog()), useUnmergedTree = true).assertExists()
        gate.complete(Unit)
        compose.waitForIdle()
        button(R.string.action_save).assertIsEnabled()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.RequestFocus))
    }

    @Test
    fun whileAnAwayPeriodIsSavedSaveIsHeldAndStaysFocusable() {
        val gate = CompletableDeferred<Unit>()
        access.awayGate = gate
        access.awayFails = "No space left on device"
        val away = AwayEntry(handle, access, scope, clock) { }
        away.open(today.plusDays(1), lisbon)
        compose.setContent { AppTheme { AwayEntryDialog(away) } }
        compose.waitForIdle()

        saveIsHeldWhileItRuns(gate)

        assertThat(access.awayChanges).hasSize(1)
    }

    @Test
    fun whileABatteryLevelIsSavedSaveIsHeldAndStaysFocusable() {
        val gate = CompletableDeferred<Unit>()
        access.batteryGate = gate
        access.batteryFails = "No space left on device"
        val battery = BatteryEntry(handle, NumberText(Locale.ENGLISH), access, scope) { }
        battery.open()
        battery.edit("64")
        compose.setContent { AppTheme { BatteryEntryDialog(battery) } }
        compose.waitForIdle()

        saveIsHeldWhileItRuns(gate)

        assertThat(battery.text.value).isEqualTo("64")
    }
}
