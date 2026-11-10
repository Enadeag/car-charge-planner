package nl.enadeag.carchargeplanner.android.home

import androidx.lifecycle.SavedStateHandle
import java.time.Clock
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.home.TripEntry.Notice
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.engine.trip.Trip
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

@OptIn(ExperimentalCoroutinesApi::class)
class TripEntryTest {

    private val access = FakeEngineAccess()
    private val changes = mutableListOf<List<Trip>>()
    private val trip = Trip(Instant.parse("2026-06-02T05:30:00Z"), Instant.parse("2026-06-02T16:00:00Z"), 300.0, false)
    private val other = Trip(Instant.parse("2026-06-03T05:30:00Z"), Instant.parse("2026-06-03T16:00:00Z"), 80.0, true)

    private fun TestScope.entry() =
        TripEntry(SavedStateHandle(), NumberText(Locale.ENGLISH), access, backgroundScope, Clock.systemUTC()) {
            changes += it
        }

    @Test
    fun notWithThisCarSavesTheTripWithThatChoiceAloneChangedAndHandsTheStoredTripsOn() = runTest {
        access.trips = listOf(trip, other)
        val entry = entry()

        entry.choose(trip, TripChoice.NOT_WITH_THIS_CAR, true)
        runCurrent()

        val changed = Trip(trip.departure(), trip.returnTime(), 300.0, false, true)
        assertThat(access.tripChanges).containsExactly("change" to changed)
        assertThat(access.trips).containsExactly(changed, other)
        assertThat(changes).containsExactly(listOf(changed, other))
        assertThat(entry.notice.value).isNull()
    }

    @Test
    fun canChargeSavesTheTripWithThatChoiceAloneChanged() = runTest {
        access.trips = listOf(other)
        val entry = entry()

        entry.choose(other, TripChoice.CAN_CHARGE, false)
        runCurrent()

        assertThat(access.trips).containsExactly(Trip(other.departure(), other.returnTime(), 80.0, false, false))
    }

    @ParameterizedTest
    @EnumSource(TripChoice::class)
    fun eachChoiceTellsAndPutsOnlyItsOwnFlag(choice: TripChoice) {
        val on = choice.put(trip, true)

        assertThat(choice.of(on)).isTrue()
        assertThat(choice.of(choice.put(on, false))).isFalse()
        assertThat(TripChoice.entries.filter { it != choice }.map { it.of(on) }).containsOnly(false)
        assertThat(listOf(on.departure(), on.returnTime(), on.distanceKm()))
            .containsExactly(trip.departure(), trip.returnTime(), trip.distanceKm())
    }

    @Test
    fun whileAChoiceIsSavedItShowsInItsNewStateAndAnotherChoiceOrARemoveDoesNothing() = runTest {
        access.trips = listOf(trip, other)
        val gate = CompletableDeferred<Unit>()
        access.tripGate = gate
        val entry = entry()

        entry.choose(trip, TripChoice.CAN_CHARGE, true)
        runCurrent()
        assertThat(entry.pending.value).isEqualTo(TripEntry.Pending(trip, TripChoice.CAN_CHARGE, true))
        assertThat(entry.busy.value).isTrue()
        entry.choose(other, TripChoice.CAN_CHARGE, false)
        entry.remove(other)
        runCurrent()
        assertThat(access.tripChanges).hasSize(1)

        gate.complete(Unit)
        runCurrent()
        assertThat(entry.pending.value).isNull()
        assertThat(entry.busy.value).isFalse()
    }

    @Test
    fun aChoiceThatFailedSpringsBackSaysNotSavedWithTheEnginesWordsAndHandsNothingOn() = runTest {
        access.trips = listOf(trip)
        access.tripFails = "written by a newer version of the app"
        val entry = entry()

        entry.choose(trip, TripChoice.NOT_WITH_THIS_CAR, true)
        runCurrent()

        assertThat(entry.pending.value).isNull()
        assertThat(entry.notice.value).isEqualTo(Notice.NotSaved("written by a newer version of the app"))
        assertThat(access.trips).containsExactly(trip)
        assertThat(changes).isEmpty()
        assertThat(entry.busy.value).isFalse()
    }

    @Test
    fun removeActsAtOnceSaysSoWithTheTripAndUndoAddsItBack() = runTest {
        access.trips = listOf(trip, other)
        val entry = entry()

        entry.remove(trip)
        runCurrent()
        assertThat(entry.notice.value).isEqualTo(Notice.Removed(trip))
        assertThat(changes).containsExactly(listOf(other))
        entry.noticeShown()
        assertThat(entry.notice.value).isNull()

        entry.undo(trip)
        runCurrent()
        assertThat(entry.notice.value).isEqualTo(Notice.Saved(trip))
        assertThat(changes.last()).containsExactly(trip, other)
        assertThat(access.tripChanges).containsExactly("remove" to trip, "add" to trip)
    }

    @Test
    fun anUndoPressedWhileAChangeRunsWaitsForItAndThenAddsTheTripBack() = runTest {
        access.trips = listOf(trip, other)
        val entry = entry()
        entry.remove(trip)
        runCurrent()
        entry.noticeShown()
        val gate = CompletableDeferred<Unit>()
        access.tripGate = gate
        entry.choose(other, TripChoice.NOT_WITH_THIS_CAR, true)
        runCurrent()

        entry.undo(trip)
        runCurrent()
        assertThat(access.tripChanges).containsExactly("remove" to trip, "change" to TripChoice.NOT_WITH_THIS_CAR.put(
            other, true))
        gate.complete(Unit)
        runCurrent()

        assertThat(access.tripChanges.last()).isEqualTo("add" to trip)
        assertThat(access.trips).contains(trip)
        assertThat(entry.notice.value).isEqualTo(Notice.Saved(trip))
        assertThat(entry.busy.value).isFalse()
    }

    @Test
    fun anUndoPressedWhileARemoveRunsWaitsUntilTheScreenSaidThatRemoveSoBothAreSaid() = runTest {
        access.trips = listOf(trip, other)
        val entry = entry()
        entry.remove(trip)
        runCurrent()
        entry.noticeShown()
        val gate = CompletableDeferred<Unit>()
        access.tripGate = gate
        entry.remove(other)
        runCurrent()
        entry.undo(trip)
        gate.complete(Unit)
        runCurrent()

        assertThat(entry.notice.value).isEqualTo(Notice.Removed(other))
        assertThat(access.tripChanges).containsExactly("remove" to trip, "remove" to other)
        entry.noticeShown()
        runCurrent()

        assertThat(access.tripChanges.last()).isEqualTo("add" to trip)
        assertThat(entry.notice.value).isEqualTo(Notice.Saved(trip))
        assertThat(access.trips).containsExactly(trip)
    }

    @Test
    fun anUndoPressedWhileARemoveThatFailsRunsWaitsUntilTheScreenSaidThatFailure() = runTest {
        access.trips = listOf(trip, other)
        val entry = entry()
        entry.remove(trip)
        runCurrent()
        entry.noticeShown()
        val gate = CompletableDeferred<Unit>()
        access.tripGate = gate
        access.tripFails = "No space left on device"
        entry.remove(other)
        runCurrent()
        entry.undo(trip)
        gate.complete(Unit)
        runCurrent()
        access.tripFails = null

        assertThat(entry.notice.value).isEqualTo(Notice.NotRemoved("No space left on device"))
        assertThat(access.tripChanges).containsExactly("remove" to trip, "remove" to other)
        entry.noticeShown()
        runCurrent()

        assertThat(entry.notice.value).isEqualTo(Notice.Saved(trip))
        assertThat(access.trips).containsExactly(trip, other)
    }

    @Test
    fun aRemoveThatFailedSaysNotRemovedAndAnUndoThatFailedSaysNotSaved() = runTest {
        access.trips = listOf(trip)
        access.tripFails = "No space left on device"
        val entry = entry()

        entry.remove(trip)
        runCurrent()
        assertThat(entry.notice.value).isEqualTo(Notice.NotRemoved("No space left on device"))

        entry.undo(trip)
        runCurrent()
        assertThat(entry.notice.value).isEqualTo(Notice.NotSaved("No space left on device"))
        assertThat(changes).isEmpty()
    }

    @Test
    fun whileARemoveRunsAChoiceDoesNothing() = runTest {
        access.trips = listOf(trip)
        access.tripGate = CompletableDeferred()
        val entry = entry()

        entry.remove(trip)
        runCurrent()
        entry.choose(trip, TripChoice.CAN_CHARGE, true)

        assertThat(entry.pending.value).isNull()
        assertThat(access.tripChanges).containsExactly("remove" to trip)
    }
}
