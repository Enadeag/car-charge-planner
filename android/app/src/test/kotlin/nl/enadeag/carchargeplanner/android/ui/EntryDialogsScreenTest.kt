package nl.enadeag.carchargeplanner.android.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Save of the away and battery entry dialogs, which is held while it saves, so it keeps the focus, on Robolectric
 * out of touch mode, where a control can take the focus; the rest of each dialog is in `AwayEntryScreenTest` and
 * `BatteryEntryScreenTest`.
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

    /** Out of touch mode, as after a key pressed on a keyboard; the dialog's window opens so as well. */
    @Before
    fun keyboard() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    @After
    fun stop() {
        scope.cancel()
    }

    private fun button(id: Int) = compose.onNode(hasText(text(id)) and hasAnyAncestor(isDialog()))

    /** Presses Enter on a keyboard, on the control with the focus. */
    private fun SemanticsNodeInteraction.enter() {
        performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
    }

    /**
     * Gives Save the focus and presses it with Enter: while the save runs it is held, marked off with its progress,
     * and keeps the focus; once the save failed it is on again, still with the focus.
     */
    private fun saveIsHeldWhileItRuns(gate: CompletableDeferred<Unit>) {
        button(R.string.action_save).requestFocus()
        compose.waitForIdle()
        button(R.string.action_save).assertIsFocused().enter()

        button(R.string.saving).assertIsNotEnabled().assertIsFocused()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo) and
            hasAnyAncestor(isDialog()), useUnmergedTree = true).assertExists()
        gate.complete(Unit)
        compose.waitForIdle()
        button(R.string.action_save).assertIsEnabled().assertIsFocused()
    }

    @Test
    fun whileAnAwayPeriodIsSavedSaveIsHeldAndKeepsTheFocus() {
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
    fun whileABatteryLevelIsSavedSaveIsHeldAndKeepsTheFocus() {
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

    @Test
    fun whileABatteryLevelIsSavedTheFieldTakesNoTyping() {
        val gate = CompletableDeferred<Unit>()
        access.batteryGate = gate
        access.batteryFails = "No space left on device"
        val battery = BatteryEntry(handle, NumberText(Locale.ENGLISH), access, scope) { }
        battery.open()
        battery.edit("64")
        compose.setContent { AppTheme { BatteryEntryDialog(battery) } }
        compose.waitForIdle()
        val field = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.IsEditable) and
            hasAnyAncestor(isDialog()))

        button(R.string.action_save).performClick()
        compose.waitForIdle()

        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.IsEditable, false))
        gate.complete(Unit)
        compose.waitForIdle()
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.IsEditable, true))
    }

    @Test
    fun whileAnUndoThatWaitedRunsOverTheOpenAwayEntrySaveIsHeldWithoutProgress() {
        val period = OneOffAwayPeriod.allDay(today.plusDays(2), lisbon)
        access.away = listOf(period)
        val away = AwayEntry(handle, access, scope, clock) { }
        val removing = CompletableDeferred<Unit>()
        access.awayGate = removing
        away.remove(period)
        away.undo(period)
        removing.complete(Unit)
        compose.waitForIdle()
        away.open(today.plusDays(1), lisbon)
        compose.setContent { AppTheme { AwayEntryDialog(away) } }
        compose.waitForIdle()
        button(R.string.action_save).requestFocus()
        compose.waitForIdle()
        val restoring = CompletableDeferred<Unit>()
        access.awayGate = restoring

        compose.runOnIdle { away.noticeShown() }
        compose.waitForIdle()

        button(R.string.action_save).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
            .assertIsFocused().enter()
        button(R.string.saving).assertDoesNotExist()
        restoring.complete(Unit)
        compose.waitForIdle()
        assertThat(access.awayChanges.map { it.first }).containsExactly("remove", "add")
        button(R.string.action_save).assertIsEnabled().assertIsFocused()
    }
}
