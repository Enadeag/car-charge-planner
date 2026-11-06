package nl.enadeag.carchargeplanner.android.background

import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import nl.enadeag.carchargeplanner.android.form.DRAFT
import nl.enadeag.carchargeplanner.android.store.AwaySave
import nl.enadeag.carchargeplanner.android.store.BatteryLevelSave
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.engine.ForecastUse
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.secrets.KeyChanges
import nl.enadeag.carchargeplanner.engine.secrets.SecretsProblem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ReschedulingAccessTest {

    private val engine = FakeEngineAccess(StartState.Ready(DRAFT))
    private var told = 0
    private val access = ReschedulingAccess(engine) { told++ }
    private val period =
        OneOffAwayPeriod(Instant.parse("2026-06-08T06:00:00Z"), Instant.parse("2026-06-08T18:00:00Z"))

    @Test
    fun aWrittenSettingsFileIsTold() = runTest {
        access.saveSettings(DRAFT)

        assertThat(engine.written).containsExactly("settings")
        assertThat(told).isEqualTo(1)
    }

    @Test
    fun aSettingsFileThatCouldNotBeWrittenIsNotTold() = runTest {
        engine.writeFails = true

        assertThat(runCatching { access.saveSettings(DRAFT) }.exceptionOrNull())
            .isInstanceOf(IOException::class.java)
        assertThat(told).isZero()
    }

    @Test
    fun aSettingsFileSetAsideIsTold() = runTest {
        access.setSettingsAside()

        assertThat(engine.settings).isEqualTo(StartState.NotSetUp)
        assertThat(told).isEqualTo(1)
    }

    @Test
    fun changedKeysAreToldAndKeysThatCouldNotBeStoredAreNot() = runTest {
        access.applyKeyChanges(KeyChanges.NONE)
        assertThat(told).isEqualTo(1)

        engine.keysFail = true
        assertThat(runCatching { access.applyKeyChanges(KeyChanges.NONE) }.exceptionOrNull())
            .isInstanceOf(SecretsProblem::class.java)
        assertThat(told).isEqualTo(1)
    }

    @Test
    fun aSavedBatteryLevelIsToldAndOneThatFailedIsNot() = runTest {
        assertThat(access.saveBatteryLevel(Percent(40.0))).isEqualTo(BatteryLevelSave.Saved)
        assertThat(told).isEqualTo(1)

        engine.batteryFails = "No space left on device"
        assertThat(access.saveBatteryLevel(Percent(50.0))).isInstanceOf(BatteryLevelSave.Failed::class.java)
        assertThat(told).isEqualTo(1)
    }

    @Test
    fun anAwayPeriodAddedOrRemovedIsToldAndOneThatFailedIsNot() = runTest {
        assertThat(access.addAwayPeriod(period)).isEqualTo(AwaySave.Saved(listOf(period)))
        assertThat(access.removeAwayPeriod(period)).isEqualTo(AwaySave.Saved(listOf()))
        assertThat(told).isEqualTo(2)

        engine.awayFails = "No space left on device"
        access.addAwayPeriod(period)
        access.removeAwayPeriod(period)
        assertThat(told).isEqualTo(2)
    }

    @Test
    fun readsAndRoundsAreNotToldAndReachTheEngine() = runTest {
        assertThat(access.readSettings()).isEqualTo(StartState.Ready(DRAFT))
        access.round(ForecastUse.STORED_ONLY)
        access.nextNoticeDue(engine.roundOutlook)
        access.awayPeriods()
        access.storedKeys()

        assertThat(engine.rounds).containsExactly(ForecastUse.STORED_ONLY)
        assertThat(engine.dueAskedFor).containsExactly(engine.roundOutlook)
        assertThat(told).isZero()
    }
}
