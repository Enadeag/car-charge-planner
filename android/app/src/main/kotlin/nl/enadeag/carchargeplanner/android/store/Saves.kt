package nl.enadeag.carchargeplanner.android.store

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.secrets.KeyChanges
import nl.enadeag.carchargeplanner.engine.secrets.SecretsProblem
import org.slf4j.LoggerFactory

/**
 * Saves the settings in the app's own scope, so turning the phone or leaving the screen does not cancel a save, and
 * keeps each save's outcome until a screen has shown it, oldest first, so a failed save is never silent: a later
 * success never takes a failure's place. Only an outcome that no screen has shown yet and that equals a later one,
 * such as two [SaveOutcome.Saved] or two failures with the same detail, is replaced by that later one, as they say the
 * same. One save at a time: each reads the file again and changes only its own part under one lock, so two screens'
 * saves never undo each other.
 *
 * @param access the engine's reads and writes
 * @param scope the app's scope, which outlives every screen
 */
class Saves(private val access: EngineAccess, private val scope: CoroutineScope) {

    private companion object {
        val LOG = LoggerFactory.getLogger(Saves::class.java)
    }

    private val lock = Mutex()

    /** The outcomes no screen has shown yet, oldest first; changed only while holding itself. */
    private val unshown = ArrayDeque<SaveOutcome>()
    private val outcome = MutableStateFlow<SaveOutcome?>(null)

    /**
     * The oldest outcome that no screen has shown yet, which a screen shows and then passes to [shown]; null when there
     * is none.
     */
    val lastOutcome: StateFlow<SaveOutcome?> = outcome.asStateFlow()

    /**
     * Saves one Settings screen: the keys first, then the screen's part laid over the settings file as it is now,
     * written only when that changes it.
     *
     * @param keys what the owner entered and emptied in the API keys; [KeyChanges.NONE] for a screen without keys
     * @param change lays the screen's part over the whole config read again from the file, on the thread it chooses;
     *     may throw the engine's [IllegalArgumentException] when the part no longer fits it
     * @return the save, which keeps its outcome for [lastOutcome] when it ends
     */
    fun update(keys: KeyChanges, change: suspend (EngineConfig) -> EngineConfig): Job = save {
        if (!keys.isEmpty) {
            try {
                access.applyKeyChanges(keys)
            } catch (problem: SecretsProblem) {
                LOG.warn("The API keys could not be saved: " + problem.message)
                return@save SaveOutcome.KeysNotSaved
            }
        }
        when (val now = access.readSettings()) {
            is StartState.Ready -> {
                val changed = change(now.config)
                if (changed != now.config) {
                    access.saveSettings(changed)
                }
                SaveOutcome.Saved
            }
            is StartState.WithoutSettings -> SaveOutcome.SettingsUnusable(now)
            StartState.Reading -> throw IllegalStateException("a read of the settings file never gives Reading")
        }
    }

    /**
     * Saves set-up's whole config at Finish, over no settings file or one that cannot be used, which the engine keeps
     * aside; never over a file of a newer version.
     *
     * @param config the whole config as entered in set-up
     * @return the save, which keeps its outcome for [lastOutcome] when it ends
     */
    fun finishSetUp(config: EngineConfig): Job = save {
        when (val now = access.readSettings()) {
            is StartState.NewerVersion -> SaveOutcome.SettingsUnusable(now)
            else -> {
                access.saveSettings(config)
                SaveOutcome.Saved
            }
        }
    }

    /**
     * Forgets the outcome a screen has shown, so [lastOutcome] gives the next one; an outcome that is not the oldest
     * unshown one, such as one another screen showed already, changes nothing.
     *
     * @param shown the outcome the screen showed, as [lastOutcome] gave it
     */
    fun shown(shown: SaveOutcome) {
        synchronized(unshown) {
            if (unshown.firstOrNull() == shown) {
                unshown.removeFirst()
                outcome.value = unshown.firstOrNull()
            }
        }
    }

    private fun keep(result: SaveOutcome) {
        synchronized(unshown) {
            // never two equal outcomes, which lastOutcome, a StateFlow, would show as one
            unshown.remove(result)
            unshown.addLast(result)
            outcome.value = unshown.first()
        }
    }

    private fun save(work: suspend () -> SaveOutcome): Job = scope.launch {
        lock.withLock {
            val result = try {
                work()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: IOException) {
                SaveOutcome.Failed(failure.message ?: failure.javaClass.simpleName)
            } catch (failure: RuntimeException) {
                LOG.warn("A save failed", failure)
                SaveOutcome.Failed(failure.message ?: failure.javaClass.simpleName)
            }
            keep(result)
        }
    }
}
