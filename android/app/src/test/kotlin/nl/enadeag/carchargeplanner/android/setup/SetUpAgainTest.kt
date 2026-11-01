package nl.enadeag.carchargeplanner.android.setup

import java.nio.file.Path
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
}
