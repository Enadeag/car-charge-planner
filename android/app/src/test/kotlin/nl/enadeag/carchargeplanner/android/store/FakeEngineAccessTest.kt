package nl.enadeag.carchargeplanner.android.store

import java.time.Instant
import kotlinx.coroutines.test.runTest
import nl.enadeag.carchargeplanner.android.background.WorkThatPlansARound
import nl.enadeag.carchargeplanner.engine.ForecastUse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The round and the next due notice of the [EngineAccess] in memory, which the work in the background is tested on. */
class FakeEngineAccessTest {

    @Test
    fun aRoundGivesTheOutlookSetAndTheNextDueNoticeIsAskedAboutThatOutlook() = runTest {
        val access = FakeEngineAccess()
        val due = Instant.parse("2026-07-03T07:45:00Z")
        access.noticeDue = due

        val answer = WorkThatPlansARound(access).plan()

        assertThat(answer).isEqualTo(due)
        assertThat(access.rounds).containsExactly(ForecastUse.STORED_ONLY)
        assertThat(access.dueAskedFor).containsExactly(access.roundOutlook)
    }
}
