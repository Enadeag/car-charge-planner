package nl.enadeag.carchargeplanner.android.setup

import java.nio.file.Path
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.store.StartState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class SetUpAgainTest {

    private val damaged = StartState.Unusable("engine.json", "not valid JSON (line 3, column 7)")
    private val access = FakeEngineAccess(damaged)

    @Test
    fun theFileIsMovedAsideOnlyAfterTheOwnerConfirms() = runTest {
        val again = SetUpAgain(damaged, access, backgroundScope)

        assertThat(again.confirm()).isNull()
        again.ask()
        runCurrent()
        assertThat(access.settings).isEqualTo(damaged)

        again.confirm()
        runCurrent()

        assertThat(again.state.value)
            .isEqualTo(SetUpAgain.State.Moved(Path.of("files", "engine.json.damaged-20261101-120000")))
        assertThat(access.settings).isEqualTo(StartState.NotSetUp)
    }

    @Test
    fun cancellingMovesNothing() = runTest {
        val again = SetUpAgain(damaged, access, backgroundScope)

        again.ask()
        again.cancel()

        assertThat(again.state.value).isEqualTo(SetUpAgain.State.Offered)
        assertThat(again.confirm()).isNull()
        runCurrent()
        assertThat(access.settings).isEqualTo(damaged)
    }

    @Test
    fun aMoveThatFailsSaysWhyAndMayBeTriedAgain() = runTest {
        access.moveFails = true
        val again = SetUpAgain(damaged, access, backgroundScope)
        again.ask()

        again.confirm()
        runCurrent()

        assertThat(again.state.value).isEqualTo(SetUpAgain.State.Failed("Permission denied"))
        access.moveFails = false
        again.ask()
        again.confirm()
        runCurrent()
        assertThat(again.state.value).isInstanceOf(SetUpAgain.State.Moved::class.java)
    }

    @Test
    fun aMoveThatFailsInTheEngineIsAFailureTooAndMayBeTriedAgain() = runTest {
        access.moveThrows = IllegalStateException("the engine is closed")
        val again = SetUpAgain(damaged, access, backgroundScope)
        again.ask()

        again.confirm()
        runCurrent()

        assertThat(again.state.value).isEqualTo(SetUpAgain.State.Failed("the engine is closed"))
        access.moveThrows = null
        again.ask()
        again.confirm()
        runCurrent()
        assertThat(again.state.value).isInstanceOf(SetUpAgain.State.Moved::class.java)
    }

    @Test
    fun whileTheFileIsBeingMovedTheOwnerCanNeitherAskCancelNorConfirmAgain() = runTest {
        val gate = CompletableDeferred<Unit>()
        access.moveGate = gate
        val again = SetUpAgain(damaged, access, backgroundScope)
        again.ask()
        again.confirm()
        runCurrent()

        again.ask()
        again.cancel()
        assertThat(again.confirm()).isNull()
        assertThat(again.state.value).isEqualTo(SetUpAgain.State.Moving)
        gate.complete(Unit)
        runCurrent()
        assertThat(again.state.value).isInstanceOf(SetUpAgain.State.Moved::class.java)
    }

    @Test
    fun onceMovedNothingAsksOrMovesAgain() = runTest {
        val again = SetUpAgain(damaged, access, backgroundScope)
        again.ask()
        again.confirm()
        runCurrent()
        val moved = again.state.value

        again.ask()
        again.cancel()

        assertThat(again.confirm()).isNull()
        assertThat(again.state.value).isEqualTo(moved)
    }

    @Test
    fun cancelOnlyLeavesTheQuestionAndAFailedMoveStaysUntilTheOwnerAsksAgain() = runTest {
        access.moveFails = true
        val again = SetUpAgain(damaged, access, backgroundScope)
        again.cancel()
        assertThat(again.state.value).isEqualTo(SetUpAgain.State.Offered)
        again.ask()
        again.confirm()
        runCurrent()

        again.cancel()

        assertThat(again.state.value).isEqualTo(SetUpAgain.State.Failed("Permission denied"))
        assertThat(again.confirm()).isNull()
    }
}
