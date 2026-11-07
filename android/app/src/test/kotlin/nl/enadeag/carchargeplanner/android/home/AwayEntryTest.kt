package nl.enadeag.carchargeplanner.android.home

import androidx.lifecycle.SavedStateHandle
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nl.enadeag.carchargeplanner.android.afterTheSystemEndedTheApp
import nl.enadeag.carchargeplanner.android.home.AwayEntry.Kind
import nl.enadeag.carchargeplanner.android.home.AwayEntry.Notice
import nl.enadeag.carchargeplanner.android.home.AwayEntry.Problem
import nl.enadeag.carchargeplanner.android.home.AwayEntry.SaveState
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

@OptIn(ExperimentalCoroutinesApi::class)
class AwayEntryTest {

    /** A clock that stands still until a test moves it. */
    private class TestClock(var now: Instant) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
    }

    private val access = FakeEngineAccess()
    private val handle = SavedStateHandle()
    private val amsterdam = ZoneId.of("Europe/Amsterdam")
    private val today = LocalDate.of(2026, 6, 1)
    private val clock = TestClock(today.atTime(8, 0).atZone(amsterdam).toInstant())
    private val changes = mutableListOf<List<OneOffAwayPeriod>>()

    private fun TestScope.entry(kept: SavedStateHandle = handle) =
        AwayEntry(kept, access, backgroundScope, clock) { changes += it }

    private fun TestScope.opened(day: LocalDate = today, zone: ZoneId = amsterdam): AwayEntry =
        entry().also { it.open(day, zone) }

    private fun TestScope.partOfDay(start: String, end: String, day: LocalDate = today,
        zone: ZoneId = amsterdam): AwayEntry = opened(day, zone).also {
        it.choose(Kind.PART_OF_DAY)
        it.start(LocalTime.parse(start))
        it.end(LocalTime.parse(end))
    }

    private fun TestScope.saved(entry: AwayEntry): OneOffAwayPeriod? {
        entry.save()
        runCurrent()
        return access.away.singleOrNull()
    }

    private fun keys() = handle.keys().filter { it == "oneOffAway" || it.startsWith("oneOffAway.") }

    private fun at(day: LocalDate, time: String, zone: ZoneId = amsterdam): Instant =
        day.atTime(LocalTime.parse(time)).atZone(zone).toInstant()

    @Test
    fun theEntryOpensOnAllDayWithTheTimesReadyAtNineAndFiveAndNothingWrong() = runTest {
        val entry = entry()
        assertThat(entry.draft.value).isNull()

        entry.open(today.plusDays(1), amsterdam)

        assertThat(entry.draft.value).isEqualTo(AwayEntry.Draft(today.plusDays(1), amsterdam, Kind.ALL_DAY,
            LocalTime.of(9, 0), LocalTime.of(17, 0)))
        assertThat(entry.problem.value).isNull()
        assertThat(entry.saving.value).isEqualTo(SaveState.Idle)
    }

    @Test
    fun allDayIsSavedFromTheDaysMidnightToTheNextInTheHomesZone() = runTest {
        val entry = opened(today.plusDays(1))

        assertThat(saved(entry)).isEqualTo(OneOffAwayPeriod(at(today.plusDays(1), "00:00"),
            at(today.plusDays(2), "00:00")))
    }

    @Test
    fun partOfTheDayIsSavedFromTheStartToTheEndInTheHomesZone() = runTest {
        val entry = partOfDay("10:15", "16:30")

        assertThat(saved(entry)).isEqualTo(OneOffAwayPeriod(at(today, "10:15"), at(today, "16:30")))
    }

    @ParameterizedTest
    @CsvSource("UTC", "Europe/Lisbon", "Pacific/Auckland", "America/Los_Angeles")
    fun theHomesZoneReadsTheTimesWhateverThePhonesZone(phone: String) = runTest {
        val before = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(phone))
        try {
            val entry = partOfDay("10:00", "12:00", today.plusDays(1))

            assertThat(saved(entry)).isEqualTo(OneOffAwayPeriod(Instant.parse("2026-06-02T08:00:00Z"),
                Instant.parse("2026-06-02T10:00:00Z")))
        } finally {
            TimeZone.setDefault(before)
        }
    }

    @ParameterizedTest
    @CsvSource("UTC, 2026-06-02T10:00:00Z", "Europe/Lisbon, 2026-06-02T09:00:00Z",
        "Pacific/Auckland, 2026-06-01T22:00:00Z")
    fun theSameTimesAreAnotherMomentInAnotherHomesZone(zone: String, start: Instant) = runTest {
        val entry = partOfDay("10:00", "12:00", today.plusDays(1), ZoneId.of(zone))

        assertThat(saved(entry)).isEqualTo(OneOffAwayPeriod(start, start.plus(Duration.ofHours(2))))
    }

    @ParameterizedTest
    @CsvSource("09:00, 09:00", "17:00, 09:00", "00:01, 00:00", "23:59, 23:58")
    fun anEndThatIsNotAfterTheStartIsRefusedAndNothingIsSaved(start: String, end: String) = runTest {
        val entry = partOfDay(start, end, today.plusDays(1))

        assertThat(saved(entry)).isNull()
        assertThat(entry.problem.value).isEqualTo(Problem.END_NOT_AFTER_START)
        assertThat(entry.draft.value).isNotNull()
        assertThat(entry.busy.value).isFalse()
        assertThat(changes).isEmpty()
    }

    @Test
    fun aPeriodEndingExactlyAtTheTimeOfSaveIsRefusedAsPassed() = runTest {
        val entry = partOfDay("06:00", "08:00")

        assertThat(saved(entry)).isNull()
        assertThat(entry.problem.value).isEqualTo(Problem.PASSED)
    }

    @Test
    fun aPeriodIsCheckedByTheClockAtSaveNotWhenTheEntryOpened() = runTest {
        val entry = partOfDay("06:00", "09:00")
        clock.now = at(today, "09:00")

        assertThat(saved(entry)).isNull()
        assertThat(entry.problem.value).isEqualTo(Problem.PASSED)
    }

    @Test
    fun aPeriodThatEndsAMinuteAfterSaveIsSaved() = runTest {
        val entry = partOfDay("06:00", "08:01")

        assertThat(saved(entry)).isEqualTo(OneOffAwayPeriod(at(today, "06:00"), at(today, "08:01")))
    }

    @Test
    fun allDayOnADayThatEndedIsRefused() = runTest {
        clock.now = at(today.plusDays(1), "00:00")

        val entry = opened(today)

        assertThat(saved(entry)).isNull()
        assertThat(entry.problem.value).isEqualTo(Problem.PASSED)
    }

    @Test
    fun allDayOfTodayIsSavedThoughItStartedAlready() = runTest {
        clock.now = at(today, "23:59")

        assertThat(saved(opened(today))).isEqualTo(OneOffAwayPeriod.allDay(today, amsterdam))
    }

    @Test
    fun allDayOnTheDayTheClocksGoForwardIsTwentyThreeHoursAndOnTheDayTheyGoBackTwentyFive() = runTest {
        val spring = LocalDate.of(2026, 3, 29)
        val autumn = LocalDate.of(2026, 10, 25)
        clock.now = at(spring, "00:00").minus(Duration.ofDays(1))

        val short = saved(opened(spring))!!
        access.away = listOf()
        val long = saved(opened(autumn))!!

        assertThat(Duration.between(short.start(), short.end())).isEqualTo(Duration.ofHours(23))
        assertThat(Duration.between(long.start(), long.end())).isEqualTo(Duration.ofHours(25))
    }

    @ParameterizedTest
    @CsvSource("02:00, 03:00", "02:30, 03:00", "02:59, 03:00")
    fun timesThatTheClocksGoingForwardMakeOneMomentOrReverseAreRefused(start: String, end: String) = runTest {
        val spring = LocalDate.of(2026, 3, 29)
        clock.now = at(spring, "00:00").minus(Duration.ofDays(1))

        val entry = partOfDay(start, end, spring)

        assertThat(saved(entry)).isNull()
        assertThat(entry.problem.value).isEqualTo(Problem.END_NOT_AFTER_START)
    }

    @Test
    fun aStartInTheHourTheClocksSkipCountsFromWhenTheClockShowsItAgain() = runTest {
        val spring = LocalDate.of(2026, 3, 29)
        clock.now = at(spring, "00:00").minus(Duration.ofDays(1))

        val entry = partOfDay("02:30", "04:00", spring)

        assertThat(saved(entry)).isEqualTo(OneOffAwayPeriod(Instant.parse("2026-03-29T01:30:00Z"),
            Instant.parse("2026-03-29T02:00:00Z")))
    }

    @Test
    fun timesInTheHourTheClocksRepeatAreTheFirstTimeTheyCome() = runTest {
        val autumn = LocalDate.of(2026, 10, 25)
        clock.now = at(autumn, "00:00").minus(Duration.ofDays(1))

        val entry = partOfDay("02:30", "02:45", autumn)

        assertThat(saved(entry)).isEqualTo(OneOffAwayPeriod(Instant.parse("2026-10-25T00:30:00Z"),
            Instant.parse("2026-10-25T00:45:00Z")))
    }

    @Test
    fun aSaveThatWorkedClosesTheEntrySaysSoOnceAndHandsTheStoredPeriodsOn() = runTest {
        val before = OneOffAwayPeriod.allDay(today.plusDays(3), amsterdam)
        access.away = listOf(before)
        val entry = partOfDay("10:00", "12:00")

        entry.save()
        runCurrent()

        val period = OneOffAwayPeriod(at(today, "10:00"), at(today, "12:00"))
        assertThat(entry.draft.value).isNull()
        assertThat(entry.notice.value).isEqualTo(Notice.Saved(period, AwayEntry.Entered(amsterdam,
            ComingAway.describe(period, amsterdam, clock.instant()))))
        val entered = (entry.notice.value as Notice.Saved).entered
        assertThat(entered?.zone).isEqualTo(amsterdam)
        assertThat(entered?.away).isEqualTo(ComingAway.describe(period, amsterdam, clock.instant()))
        assertThat(changes).containsExactly(listOf(period, before))
        assertThat(entry.busy.value).isFalse()
        assertThat(keys()).isEmpty()
        entry.noticeShown()
        assertThat(entry.notice.value).isNull()
    }

    @Test
    fun aSaveThatFailedKeepsTheEntryOpenWithWhatWasEnteredAndWhyAndChangesNothing() = runTest {
        access.awayFails = "away.json is not written: written by a newer version of the app"
        val entry = partOfDay("10:00", "12:00")

        entry.save()
        runCurrent()

        assertThat(entry.draft.value).isEqualTo(AwayEntry.Draft(today, amsterdam, Kind.PART_OF_DAY,
            LocalTime.of(10, 0), LocalTime.of(12, 0)))
        assertThat(entry.saving.value)
            .isEqualTo(SaveState.Failed("away.json is not written: written by a newer version of the app"))
        assertThat(entry.notice.value).isNull()
        assertThat(entry.busy.value).isFalse()
        assertThat(changes).isEmpty()
    }

    @Test
    fun whileASaveRunsTheEntryCannotBeChangedClosedOrSavedAgainAndNothingElseChanges() = runTest {
        val gate = CompletableDeferred<Unit>()
        access.awayGate = gate
        val other = OneOffAwayPeriod.allDay(today.plusDays(2), amsterdam)
        access.away = listOf(other)
        val entry = partOfDay("10:00", "12:00")
        entry.save()
        runCurrent()
        assertThat(entry.saving.value).isEqualTo(SaveState.Saving)
        assertThat(entry.busy.value).isTrue()

        entry.choose(Kind.ALL_DAY)
        entry.start(LocalTime.of(11, 0))
        entry.end(LocalTime.of(13, 0))
        entry.cancel()
        entry.back()
        entry.save()
        entry.remove(other)
        runCurrent()

        assertThat(entry.draft.value?.kind).isEqualTo(Kind.PART_OF_DAY)
        assertThat(entry.draft.value?.start).isEqualTo(LocalTime.of(10, 0))
        assertThat(entry.draft.value?.end).isEqualTo(LocalTime.of(12, 0))
        assertThat(entry.discardAsked.value).isFalse()
        assertThat(access.awayChanges).hasSize(1)
        gate.complete(Unit)
        runCurrent()
        assertThat(entry.draft.value).isNull()
        assertThat(access.away).hasSize(2)
    }

    @ParameterizedTest
    @ValueSource(strings = ["kind", "start", "end"])
    fun aRefusalGoesWhenTheChoiceOrATimeChanges(changed: String) = runTest {
        val entry = partOfDay("12:00", "10:00", today.plusDays(1))
        entry.save()
        assertThat(entry.problem.value).isEqualTo(Problem.END_NOT_AFTER_START)

        when (changed) {
            "kind" -> entry.choose(Kind.ALL_DAY)
            "start" -> entry.start(LocalTime.of(9, 0))
            else -> entry.end(LocalTime.of(18, 0))
        }

        assertThat(entry.problem.value).isNull()
    }

    @Test
    fun aRefusalIsForgottenWhenTheEntryIsCancelledAndOpenedAgain() = runTest {
        val entry = partOfDay("12:00", "10:00", today.plusDays(1))
        entry.save()

        entry.cancel()
        entry.open(today.plusDays(1), amsterdam)

        assertThat(entry.problem.value).isNull()
    }

    @Test
    fun aFailedSaveIsForgottenWhenTheEntryIsCancelledAndOpenedAgain() = runTest {
        access.awayFails = "No space left on device"
        val entry = partOfDay("10:00", "12:00")
        entry.save()
        runCurrent()
        assertThat((entry.saving.value as SaveState.Failed).detail).isEqualTo("No space left on device")

        entry.cancel()
        entry.open(today, amsterdam)

        assertThat(entry.saving.value).isEqualTo(SaveState.Idle)
    }

    @Test
    fun cancelClosesAtOnceAndForgetsWhatWasEntered() = runTest {
        val entry = partOfDay("10:00", "12:00", today.plusDays(1))
        assertThat(keys()).isNotEmpty()

        entry.cancel()

        assertThat(entry.draft.value).isNull()
        assertThat(keys()).isEmpty()
        assertThat(access.awayChanges).isEmpty()
        entry.open(today.plusDays(1), amsterdam)
        assertThat(entry.draft.value?.kind).isEqualTo(Kind.ALL_DAY)
        assertThat(entry.draft.value?.start).isEqualTo(LocalTime.of(9, 0))
    }

    @Test
    fun backWithNothingChangedCloses() = runTest {
        val entry = opened()

        entry.back()

        assertThat(entry.draft.value).isNull()
        assertThat(entry.discardAsked.value).isFalse()
    }

    @Test
    fun backAfterChangingBackToHowItOpenedClosesAsNothingIsLost() = runTest {
        val entry = opened()
        entry.choose(Kind.PART_OF_DAY)
        entry.choose(Kind.ALL_DAY)

        entry.back()

        assertThat(entry.draft.value).isNull()
    }

    @ParameterizedTest
    @ValueSource(strings = ["kind", "start", "end"])
    fun backWithSomethingChangedAsksAndKeepEditingStays(changed: String) = runTest {
        val entry = opened()
        when (changed) {
            "kind" -> entry.choose(Kind.PART_OF_DAY)
            "start" -> entry.start(LocalTime.of(8, 0))
            else -> entry.end(LocalTime.of(18, 0))
        }

        entry.back()

        assertThat(entry.discardAsked.value).isTrue()
        assertThat(entry.draft.value).isNotNull()
        entry.keepEditing()
        assertThat(entry.discardAsked.value).isFalse()
        assertThat(entry.draft.value).isNotNull()
    }

    @Test
    fun discardThrowsWhatWasEnteredAway() = runTest {
        val entry = partOfDay("10:00", "12:00", today.plusDays(1))
        entry.back()

        entry.discard()

        assertThat(entry.draft.value).isNull()
        assertThat(entry.discardAsked.value).isFalse()
        assertThat(keys()).isEmpty()
        assertThat(access.awayChanges).isEmpty()
    }

    @Test
    fun anEntryOpenStaysAsItIsWhenOpenedAgain() = runTest {
        val entry = partOfDay("10:00", "12:00", today.plusDays(1))

        entry.open(today.plusDays(2), ZoneId.of("UTC"))

        assertThat(entry.draft.value).isEqualTo(AwayEntry.Draft(today.plusDays(1), amsterdam, Kind.PART_OF_DAY,
            LocalTime.of(10, 0), LocalTime.of(12, 0)))
    }

    @Test
    fun theEntryAndTheQuestionComeBackAfterTheSystemEndedTheApp() = runTest {
        val zone = ZoneId.of("America/Los_Angeles")
        partOfDay("10:30", "12:45", today.plusDays(2), zone).back()

        val again = entry(handle.afterTheSystemEndedTheApp())

        assertThat(again.draft.value).isEqualTo(AwayEntry.Draft(today.plusDays(2), zone, Kind.PART_OF_DAY,
            LocalTime.of(10, 30), LocalTime.of(12, 45)))
        assertThat(again.discardAsked.value).isTrue()
    }

    @ParameterizedTest
    @CsvSource(
        "Mars/Olympus,2026-06-02,32400",
        "Europe/Amsterdam,not-a-day,32400",
        "Europe/Amsterdam,2026-06-02,99999",
    )
    fun anEntryWhoseKeptZoneDayOrTimeCannotBeReadIsClosedAndForgotten(zone: String, day: String, start: Int) =
        runTest {
            val kept = SavedStateHandle(mapOf("oneOffAway.day" to day, "oneOffAway.zone" to zone,
                "oneOffAway.part" to true, "oneOffAway.start" to start, "oneOffAway.end" to 61200,
                "oneOffAway.discard" to true))

            val again = entry(kept)

            assertThat(again.draft.value).isNull()
            assertThat(again.discardAsked.value).isFalse()
            assertThat(kept.keys().filter { it.startsWith("oneOffAway") }).isEmpty()
        }

    @Test
    fun aClosedEntryStaysClosedAfterTheSystemEndedTheApp() = runTest {
        entry()

        assertThat(entry(handle.afterTheSystemEndedTheApp()).draft.value).isNull()
    }

    @Test
    fun forgetLeavesNoKeyOfTheEntry() = runTest {
        partOfDay("10:00", "12:00").back()

        AwayEntry.forget(handle)

        assertThat(keys()).isEmpty()
    }

    @Test
    fun removeActsAtOnceSaysSoWithThePeriodAndHandsTheStoredPeriodsOn() = runTest {
        val going = OneOffAwayPeriod.allDay(today.plusDays(1), amsterdam)
        val staying = OneOffAwayPeriod.allDay(today.plusDays(2), amsterdam)
        access.away = listOf(going, staying)
        val entry = entry()

        entry.remove(going)
        runCurrent()

        assertThat(access.away).containsExactly(staying)
        assertThat(entry.notice.value).isEqualTo(Notice.Removed(going))
        assertThat(changes).containsExactly(listOf(staying))
        assertThat(entry.busy.value).isFalse()
    }

    @Test
    fun aRemoveThatFailedSaysSoWithTheEnginesWordsAndChangesNothing() = runTest {
        val going = OneOffAwayPeriod.allDay(today.plusDays(1), amsterdam)
        access.away = listOf(going)
        access.awayFails = "No space left on device"
        val entry = entry()

        entry.remove(going)
        runCurrent()

        assertThat(access.away).containsExactly(going)
        assertThat((entry.notice.value as Notice.NotRemoved).detail).isEqualTo("No space left on device")
        assertThat(changes).isEmpty()
        assertThat(entry.busy.value).isFalse()
    }

    @Test
    fun undoAddsTheRemovedPeriodBackAndSaysItIsSaved() = runTest {
        val going = OneOffAwayPeriod.allDay(today.plusDays(1), amsterdam)
        access.away = listOf(going)
        val entry = entry()
        entry.remove(going)
        runCurrent()

        entry.undo(going)
        runCurrent()

        assertThat(access.away).containsExactly(going)
        assertThat(entry.notice.value).isEqualTo(Notice.Saved(going))
        assertThat(changes).containsExactly(listOf(), listOf(going))
    }

    @Test
    fun anUndoThatFailedSaysSoWithTheEnginesWordsAndChangesNothing() = runTest {
        val going = OneOffAwayPeriod.allDay(today.plusDays(1), amsterdam)
        val entry = entry()
        access.awayFails = "No space left on device"

        entry.undo(going)
        runCurrent()

        assertThat(access.away).isEmpty()
        assertThat((entry.notice.value as Notice.NotSaved).detail).isEqualTo("No space left on device")
        assertThat(changes).isEmpty()
    }

    @Test
    fun whileARemoveRunsAnotherRemoveDoesNothingAndTheEntryDoesNotOpen() = runTest {
        val gate = CompletableDeferred<Unit>()
        access.awayGate = gate
        val going = OneOffAwayPeriod.allDay(today.plusDays(1), amsterdam)
        val other = OneOffAwayPeriod.allDay(today.plusDays(2), amsterdam)
        access.away = listOf(going, other)
        val entry = entry()
        entry.remove(going)
        runCurrent()
        assertThat(entry.busy.value).isTrue()

        entry.remove(other)
        entry.open(today, amsterdam)
        runCurrent()

        assertThat(entry.draft.value).isNull()
        assertThat(access.awayChanges).containsExactly("remove" to going)
        gate.complete(Unit)
        runCurrent()
        assertThat(entry.busy.value).isFalse()
        assertThat(access.away).containsExactly(other)
    }

    @Test
    fun anUndoPressedWhileARemoveRunsWaitsForItAndForTheScreenToSayItAndThenAddsThePeriodBack() = runTest {
        val going = OneOffAwayPeriod.allDay(today.plusDays(1), amsterdam)
        val other = OneOffAwayPeriod.allDay(today.plusDays(2), amsterdam)
        access.away = listOf(going, other)
        val entry = entry()
        entry.remove(going)
        runCurrent()
        entry.noticeShown()
        val gate = CompletableDeferred<Unit>()
        access.awayGate = gate
        entry.remove(other)
        runCurrent()

        entry.undo(going)
        runCurrent()
        assertThat(access.awayChanges).containsExactly("remove" to going, "remove" to other)
        gate.complete(Unit)
        runCurrent()
        assertThat(entry.notice.value).isEqualTo(Notice.Removed(other))
        assertThat(access.awayChanges).hasSize(2)
        entry.noticeShown()
        runCurrent()

        assertThat(access.awayChanges.last()).isEqualTo("add" to going)
        assertThat(access.away).containsExactly(going)
        assertThat(entry.notice.value).isEqualTo(Notice.Saved(going))
        assertThat(entry.busy.value).isFalse()
    }

    @Test
    fun anUndoPressedWhileASaveRunsWaitsForItAndForTheScreenToSayItAndThenAddsThePeriodBack() = runTest {
        val going = OneOffAwayPeriod.allDay(today.plusDays(2), amsterdam)
        access.away = listOf(going)
        val entry = entry()
        entry.remove(going)
        runCurrent()
        entry.noticeShown()
        entry.open(today, amsterdam)
        val gate = CompletableDeferred<Unit>()
        access.awayGate = gate
        entry.save()
        runCurrent()

        entry.undo(going)
        runCurrent()
        assertThat(access.awayChanges.map { it.first }).containsExactly("remove", "add")
        gate.complete(Unit)
        runCurrent()
        assertThat((entry.notice.value as Notice.Saved).period).isNotEqualTo(going)
        assertThat(access.awayChanges).hasSize(2)
        entry.noticeShown()
        runCurrent()

        assertThat(access.away).hasSize(2).contains(going)
        assertThat(entry.notice.value).isEqualTo(Notice.Saved(going))
    }

    @Test
    fun anUndoPressedWhileARemoveThatFailsRunsWaitsUntilTheScreenSaidThatFailure() = runTest {
        val going = OneOffAwayPeriod.allDay(today.plusDays(1), amsterdam)
        val other = OneOffAwayPeriod.allDay(today.plusDays(2), amsterdam)
        access.away = listOf(going, other)
        val entry = entry()
        entry.remove(going)
        runCurrent()
        entry.noticeShown()
        val gate = CompletableDeferred<Unit>()
        access.awayGate = gate
        access.awayFails = "No space left on device"
        entry.remove(other)
        runCurrent()
        entry.undo(going)
        gate.complete(Unit)
        runCurrent()
        access.awayFails = null

        assertThat(entry.notice.value).isEqualTo(Notice.NotRemoved("No space left on device"))
        assertThat(access.awayChanges).containsExactly("remove" to going, "remove" to other)
        entry.noticeShown()
        runCurrent()

        assertThat(entry.notice.value).isEqualTo(Notice.Saved(going))
        assertThat(access.away).containsExactly(going, other)
    }

    @Test
    fun whileTheEntryIsOpenRemoveAndUndoDoNothing() = runTest {
        val going = OneOffAwayPeriod.allDay(today.plusDays(1), amsterdam)
        access.away = listOf(going)
        val entry = opened()

        entry.remove(going)
        entry.undo(OneOffAwayPeriod.allDay(today.plusDays(2), amsterdam))
        runCurrent()

        assertThat(access.awayChanges).isEmpty()
        assertThat(entry.notice.value).isNull()
    }
}
