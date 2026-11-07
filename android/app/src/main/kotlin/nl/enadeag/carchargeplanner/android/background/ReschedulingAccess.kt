package nl.enadeag.carchargeplanner.android.background

import java.nio.file.Path
import nl.enadeag.carchargeplanner.android.store.AwaySave
import nl.enadeag.carchargeplanner.android.store.BatteryLevelSave
import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.android.store.TripSave
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.secrets.KeyChanges
import nl.enadeag.carchargeplanner.engine.trip.Trip

/**
 * The engine's reads and writes, which tell the work in the background each time the owner stored a change that
 * the plan depends on: the settings file written (at Finish, or by a Settings save that changed it) or set aside, the
 * API keys changed, a battery level, a one-off away period or a trip saved. The work then schedules a round now:
 * while one only waits, that one plans from the change when it runs; while one runs, which may have read the files
 * before the change, exactly one more follows it; never more than one waits ([RoundWork.once]). So the alarm follows
 * the new plan, such as one at a trip's charge start, and a changed place gets its forecast. A write that fails tells
 * nothing.
 *
 * @param engine the engine's reads and writes
 * @param changed told after each such change, on the caller's thread; it must not block
 */
class ReschedulingAccess(private val engine: EngineAccess, private val changed: () -> Unit) :
    EngineAccess by engine {

    override suspend fun saveSettings(config: EngineConfig): Unit = engine.saveSettings(config).also { changed() }

    override suspend fun setSettingsAside(): Path = engine.setSettingsAside().also { changed() }

    override suspend fun applyKeyChanges(changes: KeyChanges): Unit =
        engine.applyKeyChanges(changes).also { changed() }

    override suspend fun saveBatteryLevel(level: Percent): BatteryLevelSave =
        engine.saveBatteryLevel(level).also { tellIf(it is BatteryLevelSave.Saved) }

    override suspend fun addAwayPeriod(period: OneOffAwayPeriod): AwaySave =
        engine.addAwayPeriod(period).also { tellIf(it is AwaySave.Saved) }

    override suspend fun removeAwayPeriod(period: OneOffAwayPeriod): AwaySave =
        engine.removeAwayPeriod(period).also { tellIf(it is AwaySave.Saved) }

    override suspend fun addTrip(trip: Trip): TripSave =
        engine.addTrip(trip).also { tellIf(it is TripSave.Saved) }

    override suspend fun changeTrip(trip: Trip, changed: Trip): TripSave =
        engine.changeTrip(trip, changed).also { tellIf(it is TripSave.Saved) }

    override suspend fun removeTrip(trip: Trip): TripSave =
        engine.removeTrip(trip).also { tellIf(it is TripSave.Saved) }

    private fun tellIf(saved: Boolean) {
        if (saved) {
            changed()
        }
    }
}
