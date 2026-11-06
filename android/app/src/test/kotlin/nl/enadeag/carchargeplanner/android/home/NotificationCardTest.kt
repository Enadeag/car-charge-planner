package nl.enadeag.carchargeplanner.android.home

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationCardTest {

    /** The card's memory in memory; a read may be held until the test lets it go. */
    private class Memory(var closed: Boolean = false, var refused: Boolean = false) : CardMemory {
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun closed(): Boolean {
            gate?.await()
            return closed
        }

        override suspend fun close() {
            closed = true
        }

        override suspend fun refused() = refused

        override suspend fun refuse() {
            refused = true
        }
    }

    private val memory = Memory()
    private var allowed = false

    private fun TestScope.card(canAsk: Boolean = true) =
        NotificationCard(memory, { allowed }, canAsk, backgroundScope).also {
            it.recheck()
            runCurrent()
        }

    @Test
    fun nothingShowsUntilTheCardIsLookedAt() = runTest {
        assertThat(NotificationCard(memory, { allowed }, true, backgroundScope).shown.value)
            .isEqualTo(NotificationCard.Shown.NONE)
    }

    @Test
    fun whileNotificationsAreNotAllowedTheCardAsksAndroidOnAndroid13AndLater() = runTest {
        assertThat(card().shown.value).isEqualTo(NotificationCard.Shown.ASK)
    }

    @Test
    fun whereAndroidCannotAskTheCardLeadsToTheSettings() = runTest {
        assertThat(card(canAsk = false).shown.value).isEqualTo(NotificationCard.Shown.OPEN_SETTINGS)
    }

    @Test
    fun whileNotificationsAreAllowedThereIsNoCard() = runTest {
        allowed = true

        assertThat(card().shown.value).isEqualTo(NotificationCard.Shown.NONE)
    }

    @Test
    fun aRefusalIsRememberedAndTheCardLeadsToTheSettingsFromThenOn() = runTest {
        val card = card()

        card.answered(false)
        runCurrent()

        assertThat(card.shown.value).isEqualTo(NotificationCard.Shown.OPEN_SETTINGS)
        assertThat(memory.refused).isTrue()
        assertThat(card().shown.value).isEqualTo(NotificationCard.Shown.OPEN_SETTINGS)
    }

    @Test
    fun theCardIsLookedAtAgainAfterAnAnswer() = runTest {
        val card = card()
        card.answered(false)
        runCurrent()
        allowed = true

        card.recheck()
        runCurrent()

        assertThat(card.shown.value).isEqualTo(NotificationCard.Shown.NONE)
    }

    @Test
    fun aYesTakesTheCardAway() = runTest {
        val card = card()
        allowed = true

        card.answered(true)
        runCurrent()

        assertThat(card.shown.value).isEqualTo(NotificationCard.Shown.NONE)
        assertThat(memory.refused).isFalse()
    }

    @Test
    fun closedTheCardStaysAwayAlsoInALaterStart() = runTest {
        val card = card()

        card.close()
        assertThat(card.shown.value).isEqualTo(NotificationCard.Shown.NONE)
        runCurrent()

        assertThat(memory.closed).isTrue()
        card.recheck()
        runCurrent()
        assertThat(card.shown.value).isEqualTo(NotificationCard.Shown.NONE)
        assertThat(card().shown.value).isEqualTo(NotificationCard.Shown.NONE)
    }

    @Test
    fun aCloseWhileTheMemoryIsReadIsNotUndoneByThatRead() = runTest {
        val card = card()
        memory.gate = CompletableDeferred()
        card.recheck()
        runCurrent()

        card.close()
        memory.gate!!.complete(Unit)
        runCurrent()

        assertThat(card.shown.value).isEqualTo(NotificationCard.Shown.NONE)
    }

    @Test
    fun notificationsAllowedInAndroidsSettingsTakeTheCardAwayWhenTheOwnerComesBack() = runTest {
        val card = card(canAsk = false)
        allowed = true

        card.recheck()
        runCurrent()

        assertThat(card.shown.value).isEqualTo(NotificationCard.Shown.NONE)
    }

    @Test
    fun notificationsBlockedLaterBringTheCardBack() = runTest {
        allowed = true
        val card = card()
        allowed = false

        card.recheck()
        runCurrent()

        assertThat(card.shown.value).isEqualTo(NotificationCard.Shown.ASK)
    }
}
