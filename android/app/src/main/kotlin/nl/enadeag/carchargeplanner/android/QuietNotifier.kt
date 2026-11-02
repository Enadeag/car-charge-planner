package nl.enadeag.carchargeplanner.android

import nl.enadeag.carchargeplanner.engine.notify.Notice
import nl.enadeag.carchargeplanner.engine.notify.Notifier

/**
 * Tells the owner nothing: the app has no notifications until #277, which replaces this. The engine is not started
 * before then either, so nothing is announced and nothing is marked as told.
 */
internal object QuietNotifier : Notifier {

    override fun announce(notice: Notice) = Unit
}
