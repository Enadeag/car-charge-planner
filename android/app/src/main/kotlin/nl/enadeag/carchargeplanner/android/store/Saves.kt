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
 * keeps the last save's outcome until a screen has shown it, so a failed save is never silent. One save at a time:
 * each reads the file again and changes only its own part under one lock, so two screens' saves never undo each other.
 *
 * @param access the engine's reads and writes
 * @param scope the app's scope, which outlives every screen
 */
class Saves(private val access: EngineAccess, private val scope: CoroutineScope) {

    private companion object {
        val LOG = LoggerFactory.getLogger(Saves::class.java)
    }

    private val lock = Mutex()
    private val outcome = MutableStateFlow<SaveOutcome?>(null)

    /** The outcome of the last save that no screen has shown yet; null when there is none. */
    val lastOutcome: StateFlow<SaveOutcome?> = outcome.asStateFlow()

    /**
     * Saves one Settings screen: the keys first, then the screen's part laid over the settings file as it is now,
     * written only when that changes it.
     *
     * @param keys what the owner entered and emptied in the API keys; [KeyChanges.NONE] for a screen without keys
     * @param change lays the screen's part over the whole config read again from the file, on the thread it chooses;
     *     may throw the engine's [IllegalArgumentException] when the part no longer fits it
     * @return the save, which sets [lastOutcome] when it ends
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
     * @return the save, which sets [lastOutcome] when it ends
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
     * Forgets an outcome once a screen has shown it; a later save's outcome stays.
     *
     * @param shown the outcome the screen showed
     */
    fun shown(shown: SaveOutcome) {
        outcome.compareAndSet(shown, null)
    }

    private fun save(work: suspend () -> SaveOutcome): Job = scope.launch {
        val result = lock.withLock {
            try {
                work()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: IOException) {
                SaveOutcome.Failed(failure.message ?: failure.javaClass.simpleName)
            } catch (failure: RuntimeException) {
                LOG.warn("A save failed", failure)
                SaveOutcome.Failed(failure.message ?: failure.javaClass.simpleName)
            }
        }
        outcome.value = result
    }
}
