package nl.enadeag.carchargeplanner.android.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import nl.enadeag.carchargeplanner.android.home.CardMemory
import nl.enadeag.carchargeplanner.android.home.NotificationCard
import nl.enadeag.carchargeplanner.android.prefs.WifiOnly

/** What a card remembers when nothing was ever closed or refused, for a test of something else. */
private object NothingRemembered : CardMemory {
    override suspend fun closed() = false
    override suspend fun close() {}
    override suspend fun refused() = false
    override suspend fun refuse() {}
}

/**
 * A notification card that never shows, as notifications are allowed, for a test of another part of the screen.
 *
 * @param scope where the card would read its memory
 */
internal fun allowedCard(scope: CoroutineScope) = NotificationCard(NothingRemembered, { true }, true, scope)

/** A "Wi-Fi only" switch that stays off and ignores a switch, for a test of another part of Settings. */
internal object OffWifiOnly : WifiOnly {
    override val on: Flow<Boolean> = flowOf(false)
    override fun switch(on: Boolean) {}
}
