package nl.enadeag.carchargeplanner.android.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the notification card remembers on this phone, also after the app was closed. */
interface CardMemory {

    /**
     * Whether the owner closed the card.
     *
     * @return true once closed with ×; false when it cannot be read
     */
    suspend fun closed(): Boolean

    /** Remembers that the owner closed the card, for good. */
    suspend fun close()

    /**
     * Whether the owner refused Android's question for notifications.
     *
     * @return true once refused; false when it cannot be read
     */
    suspend fun refused(): Boolean

    /** Remembers that the owner refused Android's question, so the card leads to Android's settings from now on. */
    suspend fun refuse()
}

/**
 * The dashboard's card that asks the owner to allow notifications, while they are not allowed: Android 13's
 * permission not given, or notifications blocked for the app on any Android. It explains first and then asks Android
 * (Android 13 and later), and after a refusal, or where Android cannot ask, leads to the app's notification settings.
 * Closed with ×, it stays away; Settings keeps the way in. It is looked at again each time the owner comes back to the
 * app, as notifications may have been allowed in Android's settings meanwhile. Called from the main thread.
 *
 * @param memory what the card remembers
 * @param allowed whether notifications are allowed now, asked each time
 * @param canAsk true on Android 13 and later, where the app asks Android for the permission
 * @param scope where the memory is read and written, the screen's
 */
class NotificationCard(private val memory: CardMemory, private val allowed: () -> Boolean,
    private val canAsk: Boolean, private val scope: CoroutineScope) {

    /** What the card shows. */
    enum class Shown {

        /** No card: notifications are allowed, or the card was closed, or the memory is not read yet. */
        NONE,

        /** The card with its button that asks Android for the permission. */
        ASK,

        /** The card with its button that opens the app's notification settings, as Android will not ask again. */
        OPEN_SETTINGS,
    }

    private val shownNow = MutableStateFlow(Shown.NONE)
    private val lock = Mutex()

    /** True once closed here, so a look begun before the close was written does not bring the card back. */
    private var closedHere = false

    /** What the card shows now. */
    val shown: StateFlow<Shown> = shownNow.asStateFlow()

    /** Looks again whether the card is due and how, as when the owner comes back to the app. */
    fun recheck() {
        scope.launch { lock.withLock { update() } }
    }

    /** Takes Android's answer to the question: a refusal makes the card lead to the settings from now on. */
    fun answered(granted: Boolean) {
        scope.launch {
            lock.withLock {
                if (!granted) {
                    memory.refuse()
                }
                update()
            }
        }
    }

    /** Closes the card for good, as the owner pressed ×. */
    fun close() {
        closedHere = true
        shownNow.value = Shown.NONE
        scope.launch { memory.close() }
    }

    private suspend fun update() {
        val due = due()
        // the owner may have closed it while the memory was read
        shownNow.value = if (closedHere) Shown.NONE else due
    }

    private suspend fun due(): Shown = when {
        allowed() || memory.closed() -> Shown.NONE
        canAsk && !memory.refused() -> Shown.ASK
        else -> Shown.OPEN_SETTINGS
    }
}
