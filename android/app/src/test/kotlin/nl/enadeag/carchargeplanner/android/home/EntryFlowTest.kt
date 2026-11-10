package nl.enadeag.carchargeplanner.android.home

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nl.enadeag.carchargeplanner.android.afterTheSystemEndedTheApp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EntryFlowTest {

    private val handle = SavedStateHandle()
    private var open = true
    private var edited = false
    private var shut = 0
    private val stored = mutableListOf<String>()
    private lateinit var changes: ChangeQueue<String>

    private fun TestScope.flow(kept: SavedStateHandle = handle, scope: CoroutineScope = backgroundScope): EntryFlow {
        changes = ChangeQueue(scope)
        return EntryFlow(kept, KEY, changes, { open }, { edited }) {
            open = false
            shut++
        }
    }

    /** A save of "level" that waits for [gate], then stores it, or fails with [fails]; it never closes itself. */
    private fun EntryFlow.saveLevel(gate: CompletableDeferred<Unit>? = null, fails: String? = null,
        entered: () -> String? = { "level" }) = save(entered) { value ->
        gate?.await()
        if (fails == null) {
            stored += value
        }
        fails
    }

    @Test
    fun anOpenedEntryIsIdleWithoutAQuestionOrARefusal() = runTest {
        val flow = flow()

        assertThat(flow.saving.value).isEqualTo(SaveState.Idle)
        assertThat(flow.discardAsked.value).isFalse()
        assertThat(flow.refusals.value).isZero()
        assertThat(flow.takesEdits).isTrue()
    }

    @Test
    fun backWithAChangeAsksAndKeepsTheQuestionInTheHandle() = runTest {
        val flow = flow()
        edited = true

        flow.back()

        assertThat(flow.discardAsked.value).isTrue()
        assertThat(handle.get<Boolean>(KEY)).isTrue()
        assertThat(shut).isZero()
    }

    @Test
    fun backWithoutAChangeClosesWithoutAsking() = runTest {
        val flow = flow()

        flow.back()

        assertThat(shut).isEqualTo(1)
        assertThat(flow.discardAsked.value).isFalse()
    }

    @Test
    fun backOfAClosedEntryNeitherAsksNorCloses() = runTest {
        val flow = flow()
        open = false
        edited = true

        flow.back()

        assertThat(flow.discardAsked.value).isFalse()
        assertThat(handle.keys()).isEmpty()
        assertThat(shut).isZero()
    }

    @Test
    fun keepEditingForgetsTheQuestionAndStaysOpen() = runTest {
        val flow = flow()
        edited = true
        flow.back()

        flow.keepEditing()

        assertThat(flow.discardAsked.value).isFalse()
        assertThat(handle.keys()).isEmpty()
        assertThat(shut).isZero()
    }

    @Test
    fun discardClosesAndTheQuestionGoes() = runTest {
        val flow = flow()
        edited = true
        flow.back()

        flow.discard()

        assertThat(shut).isEqualTo(1)
        assertThat(flow.discardAsked.value).isFalse()
    }

    @Test
    fun discardOfAClosedEntryDoesNotCloseItAgain() = runTest {
        val flow = flow()
        open = false

        flow.discard()

        assertThat(shut).isZero()
    }

    @Test
    fun theQuestionComesBackWithAnEntryThatCameBackOpen() = runTest {
        edited = true
        flow().back()

        val again = flow(handle.afterTheSystemEndedTheApp())

        assertThat(again.discardAsked.value).isTrue()
    }

    @Test
    fun aKeptQuestionWithoutAnOpenEntryDoesNotShow() = runTest {
        handle[KEY] = true
        open = false

        val flow = flow()

        assertThat(flow.discardAsked.value).isFalse()
        assertThat(handle.contains(KEY)).isFalse()
    }

    @Test
    fun aKeptQuestionWithoutAnOpenEntryDoesNotComeBackWithTheNextEntry() = runTest {
        handle[KEY] = true
        open = false
        flow()
        open = true

        val again = flow(handle.afterTheSystemEndedTheApp())

        assertThat(again.discardAsked.value).isFalse()
    }

    @Test
    fun aKeptValueThatIsNoYesIsNoQuestion() = runTest {
        handle[KEY] = "yes"

        val flow = flow()

        assertThat(flow.discardAsked.value).isFalse()
    }

    @Test
    fun aSaveThatWorkedRunsAsAChangeAndClosesTheEntry() = runTest {
        val flow = flow()
        val gate = CompletableDeferred<Unit>()

        flow.saveLevel(gate)
        runCurrent()

        assertThat(flow.saving.value).isEqualTo(SaveState.Saving)
        assertThat(changes.busy.value).isTrue()
        gate.complete(Unit)
        runCurrent()
        assertThat(stored).containsExactly("level")
        assertThat(shut).isEqualTo(1)
        assertThat(flow.saving.value).isEqualTo(SaveState.Idle)
        assertThat(changes.busy.value).isFalse()
    }

    @Test
    fun aSaveThatWorkedClosesTheEntryAfterWhatTheStoreDoes() = runTest {
        val flow = flow()
        var shutWhileStoring = -1

        flow.save({ "level" }) {
            shutWhileStoring = shut
            null
        }
        runCurrent()

        assertThat(shutWhileStoring).isZero()
        assertThat(shut).isEqualTo(1)
    }

    @Test
    fun aStoreThatThrowsLeavesTheEntryOpenAndItsSaveIdle() = runTest {
        val thrown = mutableListOf<Throwable>()
        val flow = flow(scope = CoroutineScope(backgroundScope.coroutineContext +
            SupervisorJob(backgroundScope.coroutineContext.job) + CoroutineExceptionHandler { _, e -> thrown += e }))

        flow.save({ "level" }) { throw IllegalStateException("disk gone") }
        runCurrent()

        assertThat(thrown).hasSize(1)
        assertThat(flow.saving.value).isEqualTo(SaveState.Idle)
        assertThat(flow.takesEdits).isTrue()
        assertThat(changes.busy.value).isFalse()
        assertThat(shut).isZero()
    }

    @Test
    fun aSaveThatFailedKeepsTheEntryOpenWithTheWords() = runTest {
        val flow = flow()

        flow.saveLevel(fails = "No space left on device")
        runCurrent()

        assertThat(flow.saving.value).isEqualTo(SaveState.Failed("No space left on device"))
        assertThat(shut).isZero()
        assertThat(changes.busy.value).isFalse()
        assertThat(flow.takesEdits).isTrue()
    }

    @Test
    fun eachRefusedSaveCountsARefusalAndStartsNothing() = runTest {
        val flow = flow()

        flow.saveLevel { null }
        flow.saveLevel { null }
        runCurrent()

        assertThat(flow.refusals.value).isEqualTo(2)
        assertThat(flow.saving.value).isEqualTo(SaveState.Idle)
        assertThat(changes.busy.value).isFalse()
        assertThat(stored).isEmpty()
    }

    @Test
    fun whileAnotherChangeRunsSaveNeitherChecksNorStarts() = runTest {
        val flow = flow()
        val other = CompletableDeferred<Unit>()
        changes.run({ other.await() })
        runCurrent()
        var checked = 0

        flow.saveLevel { checked++; "level" }
        other.complete(Unit)
        runCurrent()

        assertThat(checked).isZero()
        assertThat(flow.saving.value).isEqualTo(SaveState.Idle)
        assertThat(stored).isEmpty()
    }

    @Test
    fun aSaveOfAClosedEntryNeitherChecksNorStarts() = runTest {
        val flow = flow()
        open = false
        var checked = 0

        flow.saveLevel { checked++; "level" }
        runCurrent()

        assertThat(checked).isZero()
        assertThat(flow.refusals.value).isZero()
        assertThat(stored).isEmpty()
    }

    @Test
    fun whileASaveRunsEditsBackDiscardAndAnotherSaveDoNothing() = runTest {
        val flow = flow()
        edited = true
        val gate = CompletableDeferred<Unit>()
        flow.saveLevel(gate)
        runCurrent()

        val takesEdits = flow.takesEdits
        flow.back()
        flow.discard()
        flow.saveLevel()
        gate.complete(Unit)
        runCurrent()

        assertThat(takesEdits).isFalse()
        assertThat(flow.discardAsked.value).isFalse()
        assertThat(stored).containsExactly("level")
        assertThat(shut).isEqualTo(1)
    }

    @Test
    fun closingForgetsAFailedSaveAndTheQuestion() = runTest {
        val flow = flow()
        edited = true
        flow.saveLevel(fails = "No space left on device")
        runCurrent()
        flow.back()

        flow.discard()

        assertThat(flow.saving.value).isEqualTo(SaveState.Idle)
        assertThat(flow.discardAsked.value).isFalse()
        assertThat(handle.contains(KEY)).isFalse()
        assertThat(shut).isEqualTo(1)
    }

    private companion object {
        const val KEY = "entry.discard"
    }
}
