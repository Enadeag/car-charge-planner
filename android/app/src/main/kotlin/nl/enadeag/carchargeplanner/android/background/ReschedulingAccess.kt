package nl.enadeag.carchargeplanner.android.background

import java.nio.file.Path
import nl.enadeag.carchargeplanner.android.store.AwaySave
import nl.enadeag.carchargeplanner.android.store.BatteryLevelSave
import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.secrets.KeyChanges

/**
 * The engine's reads and writes, which tell the work in the background each time the owner stored a change that
 * the plan depends on: the settings file written (at Finish, or by a Settings save that changed it) or set aside, the
 * API keys changed, a battery level or a one-off away period saved. The work then makes a round at once, so the alarm
 * follows the new plan and a changed place gets its forecast. A write that fails tells nothing.
 *
 * @param engine the engine's reads and writes
 * @param changed told after each such change, on the caller's thread; it must not block
 */
class ReschedulingAccess(private val engine: EngineAccess, private val changed: () -> Unit) :
    EngineAccess by engine {

    override suspend fun saveSettings(config: EngineConfig) {
        engine.saveSettings(config)
        changed()
    }

    override suspend fun setSettingsAside(): Path = engine.setSettingsAside().also { changed() }

    override suspend fun applyKeyChanges(changes: KeyChanges) {
        engine.applyKeyChanges(changes)
        changed()
    }

    override suspend fun saveBatteryLevel(level: Percent): BatteryLevelSave =
        engine.saveBatteryLevel(level).also { if (it is BatteryLevelSave.Saved) changed() }

    override suspend fun addAwayPeriod(period: OneOffAwayPeriod): AwaySave =
        engine.addAwayPeriod(period).also(::told)

    override suspend fun removeAwayPeriod(period: OneOffAwayPeriod): AwaySave =
        engine.removeAwayPeriod(period).also(::told)

    private fun told(save: AwaySave) {
        if (save is AwaySave.Saved) {
            changed()
        }
    }
}
