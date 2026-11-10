package nl.enadeag.carchargeplanner.android.home

import androidx.lifecycle.SavedStateHandle
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nl.enadeag.carchargeplanner.android.afterTheSystemEndedTheApp
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.home.TripDraft.Fields
import nl.enadeag.carchargeplanner.android.home.TripDraft.Mode
import nl.enadeag.carchargeplanner.android.home.TripDraft.Problem
import nl.enadeag.carchargeplanner.android.home.TripEntry.Field
import nl.enadeag.carchargeplanner.android.home.TripEntry.Notice
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.ui.components.PickerDays
import nl.enadeag.carchargeplanner.engine.trip.Trip
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

/** The trip entry's Add and Change: what it opens on, the days it offers, its checks, its save and its saved state. */
@OptIn(ExperimentalCoroutinesApi::class)
class TripEntryAddAndChangeTest {

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
    private val clock = TestClock(at(today, "09:00"))
    private val changes = mutableListOf<List<Trip>>()

    private fun at(day: LocalDate, time: String, zone: ZoneId = amsterdam): Instant =
        day.atTime(LocalTime.parse(time)).atZone(zone).toInstant()

    private fun trip(from: LocalDate, departure: String, to: LocalDate, back: String, km: Double,
        canCharge: Boolean = false, notWithThisCar: Boolean = false) =
        Trip(at(from, departure), at(to, back), km, canCharge, notWithThisCar)

    private fun TestScope.entry(kept: SavedStateHandle = handle, numbers: NumberText = NumberText(Locale.ENGLISH)) =
        TripEntry(kept, numbers, access, backgroundScope, clock) { changes += it }

    private fun TestScope.added(day: LocalDate = today, zone: ZoneId = amsterdam): TripEntry =
        entry().also { it.add(day, zone) }

    private fun TripEntry.fill(km: String, from: LocalDate = today, departure: String = "08:00",
        to: LocalDate = today, back: String = "18:00") {
        departureDay(from)
        departureTime(LocalTime.parse(departure))
        returnDay(to)
        returnTime(LocalTime.parse(back))
        distance(km)
    }

    private fun TestScope.saved(entry: TripEntry): Trip? {
        entry.save()
        runCurrent()
        return access.tripChanges.lastOrNull()?.second
    }

    @Test
    fun addOpensOnThePagesDayFromEightToSixWithoutADistanceOrCharging() = runTest {
        val entry = added(today.plusDays(2))

        val draft = entry.draft.value!!
        assertThat(draft.mode).isEqualTo(Mode.ADD)
        assertThat(draft.fields).isEqualTo(Fields(today.plusDays(2), LocalTime.of(8, 0), today.plusDays(2),
            LocalTime.of(18, 0), "", false))
        assertThat(draft.edited).isFalse()
        assertThat(draft.opened).isNull()
    }

    @Test
    fun aNewTripIsSavedInTheHomesZoneSaysSoWithHowTheEntryReadsItAndClosesTheEntry() = runTest {
        val entry = added()
        entry.fill("120", back = "17:30")
        entry.canCharge(true)

        val trip = saved(entry)

        val expected = Trip(at(today, "08:00"), at(today, "17:30"), 120.0, true, false)
        assertThat(trip).isEqualTo(expected)
        assertThat(access.tripChanges).containsExactly("add" to expected)
        assertThat(changes).containsExactly(listOf(expected))
        assertThat(entry.draft.value).isNull()
        assertThat(entry.notice.value).isEqualTo(Notice.Saved(expected,
            TripEntry.Entered(amsterdam, ListedTrip.of(expected, amsterdam, clock.now))))
        assertThat(entry.closedOn).isNull()
        assertThat(handle.keys()).isEmpty()
    }

    @Test
    fun changeOpensOnTheTripInTheHomesZoneToTheMinuteWithItsDistanceInWholeKm() = runTest {
        val stored = Trip(Instant.parse("2026-06-02T05:30:45Z"), Instant.parse("2026-06-03T16:00:00Z"), 120.6, true,
            true)
        val entry = entry()

        entry.change(stored, amsterdam)

        val draft = entry.draft.value!!
        assertThat(draft.mode).isEqualTo(Mode.CHANGE)
        assertThat(draft.fields).isEqualTo(Fields(today.plusDays(1), LocalTime.of(7, 30), today.plusDays(2),
            LocalTime.of(18, 0), "121", true))
        assertThat(draft.opened).isEqualTo(stored)
        assertThat(draft.edited).isFalse()
    }

    @Test
    fun anUnchangedChangeKeepsTheExactDistanceAndNotWithThisCarAndDropsTheSeconds() = runTest {
        val stored = Trip(Instant.parse("2026-06-02T05:30:45Z"), Instant.parse("2026-06-02T16:00:00Z"), 120.6, false,
            true)
        access.trips = listOf(stored)
        val entry = entry()
        entry.change(stored, amsterdam)
        // spaces around the distance as it first showed leave it unchanged
        entry.distance(" 121 ")

        val trip = saved(entry)

        val expected = Trip(Instant.parse("2026-06-02T05:30:00Z"), stored.returnTime(), 120.6, false, true)
        assertThat(trip).isEqualTo(expected)
        assertThat(access.trips).containsExactly(expected)
        assertThat(entry.closedOn).isEqualTo(expected)
    }

    @Test
    fun aChangeDiscardedAfterAnotherChangeWasSavedClosesOnItsOwnTripNotTheSavedOne() = runTest {
        val first = trip(today.plusDays(1), "07:30", today.plusDays(1), "18:00", 300.0)
        val second = trip(today.plusDays(2), "08:00", today.plusDays(2), "17:00", 150.0)
        access.trips = listOf(first, second)
        val entry = entry()
        entry.change(first, amsterdam)
        entry.distance("310")
        saved(entry)
        assertThat(entry.closedOn).isNotEqualTo(second)

        entry.change(second, amsterdam)
        entry.distance("160")
        entry.discard()

        assertThat(entry.closedOn).isEqualTo(second)
    }

    @Test
    fun aDistanceTypedOverTheShownOneIsTheDistanceTyped() = runTest {
        val stored = trip(today.plusDays(1), "07:30", today.plusDays(1), "18:00", 120.6)
        access.trips = listOf(stored)
        val entry = entry()
        entry.change(stored, amsterdam)
        entry.distance("122")

        assertThat(saved(entry)?.distanceKm()).isEqualTo(122.0)
    }

    @Test
    fun aChangeOfATripThatIsGoneByThenAddsTheChangedOne() = runTest {
        val stored = trip(today.plusDays(1), "07:30", today.plusDays(1), "18:00", 300.0)
        val other = trip(today.plusDays(3), "07:30", today.plusDays(3), "18:00", 80.0)
        access.trips = listOf(stored, other)
        val entry = entry()
        entry.change(stored, amsterdam)
        access.trips = listOf(other)
        entry.returnTime(LocalTime.of(19, 0))

        val changed = saved(entry)!!

        assertThat(access.tripChanges).containsExactly("change" to changed)
        assertThat(access.trips).containsExactly(changed, other)
        assertThat(changes.single()).containsExactly(changed, other)
    }

    @Test
    fun aChangeOfATripWhoseReturnHasPassedIsRefusedAtSave() = runTest {
        val stored = trip(today, "06:00", today, "08:30", 40.0)
        val entry = entry()
        entry.change(stored, amsterdam)

        entry.save()

        assertThat(entry.problem.value).isEqualTo(Problem.PASSED)
        assertThat(access.tripChanges).isEmpty()
    }

    @Test
    fun theDaysOfferedRunFromTodayInTheHomesZoneTo20DaysAfterIt() = runTest {
        val entry = added()

        assertThat(entry.draft.value!!.days).isEqualTo(today..LocalDate.of(2026, 6, 21))
    }

    @ParameterizedTest
    @CsvSource(
        // the spring change, 28 March 2027, and the autumn change, 31 October 2027, in the window and at its end
        "2027-03-27T23:30, 2027-03-27, 2027-04-16",
        "2027-03-28T00:30, 2027-03-28, 2027-04-17",
        "2027-03-08T00:10, 2027-03-08, 2027-03-28",
        "2027-03-07T23:50, 2027-03-07, 2027-03-27",
        "2027-10-30T23:30, 2027-10-30, 2027-11-19",
        "2027-10-11T00:10, 2027-10-11, 2027-10-31",
        "2027-10-10T23:50, 2027-10-10, 2027-10-30",
    )
    fun acrossTheChangesOfTheClocksTheWindowEndsOnTheDate20DaysAfterToday(opened: LocalDateTime, first: LocalDate,
        last: LocalDate) = runTest {
        clock.now = opened.atZone(amsterdam).toInstant()

        val entry = added(first)

        assertThat(entry.draft.value!!.days).isEqualTo(first..last)
    }

    @Test
    fun todayIsTheHomesDayAlsoWhenItIsADayAheadOfUtc() = runTest {
        val auckland = ZoneId.of("Pacific/Auckland")
        clock.now = Instant.parse("2026-06-01T13:00:00Z")

        val entry = added(today.plusDays(1), auckland)

        val draft = entry.draft.value!!
        assertThat(draft.today).isEqualTo(today.plusDays(1))
        assertThat(draft.days.start).isEqualTo(today.plusDays(1))
    }

    @Test
    fun todayIsTheHomesDayAlsoWhenItIsADayBehindUtcAndThePickerHoldsThatDaysUtcMidnight() = runTest {
        val losAngeles = ZoneId.of("America/Los_Angeles")
        clock.now = Instant.parse("2026-06-02T03:00:00Z")

        val entry = added(today, losAngeles)

        val days = entry.draft.value!!.days
        assertThat(days.start).isEqualTo(today)
        assertThat(PickerDays.millis(days.start)).isEqualTo(Instant.parse("2026-06-01T00:00:00Z").toEpochMilli())
        assertThat(PickerDays.day(Instant.parse("2026-06-01T00:00:00Z").toEpochMilli())).isEqualTo(today)
    }

    @Test
    fun theDatePickersUtcMidnightIsTheSameDayForAHomeThirteenHoursAheadOfUtc() = runTest {
        val tonga = ZoneId.of("Pacific/Tongatapu")
        clock.now = Instant.parse("2026-06-01T12:00:00Z")
        val entry = added(LocalDate.of(2026, 6, 2), tonga)
        val days = entry.draft.value!!.days

        assertThat(days.start).isEqualTo(LocalDate.of(2026, 6, 2))
        assertThat(PickerDays.millis(days.start)).isEqualTo(Instant.parse("2026-06-02T00:00:00Z").toEpochMilli())
        assertThat(PickerDays.day(Instant.parse("2026-06-22T00:00:00Z").toEpochMilli())).isEqualTo(days.endInclusive)
        generateSequence(days.start) { it.plusDays(1) }.takeWhile { it <= days.endInclusive }.forEach {
            assertThat(PickerDays.day(PickerDays.millis(it))).isEqualTo(it)
        }
    }

    @Test
    fun anEntryOpenAcrossMidnightKeepsItsWindowAndItsToday() = runTest {
        clock.now = at(today, "23:50")
        val entry = added()
        val before = entry.draft.value!!

        clock.now = at(today.plusDays(1), "00:10")
        entry.distance("10")

        assertThat(entry.draft.value!!.days).isEqualTo(before.days)
        assertThat(entry.draft.value!!.today).isEqualTo(today)
    }

    @Test
    fun theWindowWidensToAChangedTripsOwnDaysAndAddsTheTabsDay() = runTest {
        val long = trip(today.minusDays(3), "07:00", today.plusDays(25), "18:00", 900.0)
        val entry = entry()

        entry.change(long, amsterdam)

        assertThat(entry.draft.value!!.days).isEqualTo(today.minusDays(3)..today.plusDays(25))
        entry.back()
        assertThat(added(today.plusDays(30)).draft.value!!.days).isEqualTo(today..today.plusDays(30))
    }

    @Test
    fun aNewTripLeavesOnTheDepartureDayPut() = runTest {
        val entry = added()
        entry.departureDay(today.plusDays(3))
        assertThat(entry.draft.value!!.fields.departureDay).isEqualTo(today.plusDays(3))
        entry.fill("120", from = today.plusDays(3), to = today.plusDays(4))

        assertThat(saved(entry)?.departure()).isEqualTo(at(today.plusDays(3), "08:00"))
    }

    @Test
    fun aChangeSavesTheDepartureDayMoved() = runTest {
        val stored = trip(today.plusDays(1), "07:30", today.plusDays(5), "18:00", 300.0)
        access.trips = listOf(stored)
        val entry = entry()
        entry.change(stored, amsterdam)

        entry.departureDay(today.plusDays(2))

        assertThat(entry.draft.value!!.fields.departureDay).isEqualTo(today.plusDays(2))
        assertThat(saved(entry)).isEqualTo(trip(today.plusDays(2), "07:30", today.plusDays(5), "18:00", 300.0))
    }

    @Test
    fun theReturnDayIsNeverMovedByTheEntryItself() = runTest {
        val entry = added()

        entry.departureDay(today.plusDays(3))

        assertThat(entry.draft.value!!.fields.returnDay).isEqualTo(today)
    }

    @Test
    fun aReturnNotAfterTheDepartureIsRefusedFirstAndFixedAtTheReturn() = runTest {
        val entry = added()
        entry.fill("abc", back = "08:00")

        entry.save()

        assertThat(entry.problem.value).isEqualTo(Problem.RETURN_NOT_AFTER_DEPARTURE)
        assertThat(entry.problem.value!!.field).isEqualTo(Field.RETURN)
        assertThat(entry.refusals.value).isEqualTo(1)
        assertThat(access.tripChanges).isEmpty()
    }

    @Test
    fun aReturnThatHasPassedIsRefusedBeforeTheDistanceAndADepartureInThePastIsAllowed() = runTest {
        val entry = added()
        entry.fill("abc", departure = "06:00", back = "08:59")

        entry.save()
        assertThat(entry.problem.value).isEqualTo(Problem.PASSED)
        assertThat(entry.problem.value!!.field).isEqualTo(Field.RETURN)

        entry.fill("40", departure = "06:00", back = "09:01")
        assertThat(saved(entry)).isEqualTo(trip(today, "06:00", today, "09:01", 40.0))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "abc", ".5", "1e3", "12 km", "1,000,000", "+5"])
    fun aDistanceThatIsNoNumberIsRefusedAtTheDistance(typed: String) = runTest {
        val entry = added()
        entry.fill(typed)

        entry.save()

        assertThat(entry.problem.value).isEqualTo(Problem.DISTANCE_NOT_A_NUMBER)
        assertThat(entry.problem.value!!.field).isEqualTo(Field.DISTANCE)
    }

    @Test
    fun aDistanceTooLargeToBeANumberIsNoNumber() {
        val fields = Fields(today, LocalTime.of(8, 0), today, LocalTime.of(18, 0), "1" + "0".repeat(400), false)
        val draft = TripDraft(Mode.ADD, amsterdam, today, fields, null, fields)

        assertThat(draft.check(clock.now, NumberText(Locale.ENGLISH)))
            .isEqualTo(TripDraft.Checked.Refused(Problem.DISTANCE_NOT_A_NUMBER))
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "-5", "0.0", "-0.1"])
    fun aDistanceOfZeroOrLessIsRefused(typed: String) = runTest {
        val entry = added()
        entry.fill(typed)

        entry.save()

        assertThat(entry.problem.value).isEqualTo(Problem.DISTANCE_NOT_POSITIVE)
    }

    @Test
    fun aTinyDistanceIsSaved() = runTest {
        val entry = added()
        entry.fill("0.001")

        assertThat(saved(entry)?.distanceKm()).isEqualTo(0.001)
    }

    @ParameterizedTest
    @CsvSource("1.000, 1.0", "'12,5', 12.5", "' 80 ', 80.0")
    fun theDistanceIsReadAsANumberFieldReadsItWithSpacesAroundItTrimmed(typed: String, km: Double) = runTest {
        val entry = entry(numbers = NumberText(Locale.forLanguageTag("nl")))
        entry.add(today, amsterdam)
        entry.fill(typed)

        assertThat(saved(entry)?.distanceKm()).isEqualTo(km)
    }

    @Test
    fun theDistanceTakesAtMostSixteenCharacters() = runTest {
        val entry = added()

        entry.distance("12345678901234567890")

        assertThat(entry.draft.value!!.fields.distance).isEqualTo("1234567890123456")
    }

    @Test
    fun aProblemGoesAtAnyChange() = runTest {
        val entry = added()
        entry.save()
        assertThat(entry.problem.value).isEqualTo(Problem.DISTANCE_NOT_A_NUMBER)

        entry.canCharge(true)

        assertThat(entry.problem.value).isNull()
    }

    @Test
    fun aDepartureInTheSpringGapCountsAsTheTimeAfterItSoALaterLookingReturnInTheGapsHourIsRefused() = runTest {
        // 28 March 2027 in Amsterdam: 02:00 to 03:00 does not exist, so 02:30 is 03:30
        val spring = LocalDate.of(2027, 3, 28)
        clock.now = at(spring, "00:30")
        val entry = added(spring)
        entry.fill("50", from = spring, departure = "02:30", to = spring, back = "03:15")

        entry.save()
        assertThat(entry.problem.value).isEqualTo(Problem.RETURN_NOT_AFTER_DEPARTURE)

        entry.returnTime(LocalTime.of(3, 45))
        assertThat(saved(entry)?.departure()).isEqualTo(Instant.parse("2027-03-28T01:30:00Z"))
    }

    @Test
    fun aTimeInTheRepeatedAutumnHourIsItsFirst() = runTest {
        val autumn = LocalDate.of(2026, 10, 25)
        clock.now = at(autumn, "00:30")
        val entry = added(autumn)
        entry.fill("50", from = autumn, departure = "02:30", to = autumn, back = "12:00")

        assertThat(saved(entry)?.departure()).isEqualTo(Instant.parse("2026-10-25T00:30:00Z"))
    }

    @Test
    fun whileAnotherChangeOfTheTripsRunsSaveDoesNothing() = runTest {
        val stored = trip(today.plusDays(1), "07:30", today.plusDays(1), "18:00", 300.0)
        access.trips = listOf(stored)
        access.tripGate = CompletableDeferred()
        val entry = added()
        entry.remove(stored)
        runCurrent()
        entry.fill("120")

        entry.save()
        runCurrent()

        assertThat(access.tripChanges).containsExactly("remove" to stored)
        assertThat(entry.saving.value).isEqualTo(SaveState.Idle)
        assertThat(entry.draft.value).isNotNull()
    }

    @Test
    fun aSaveThatFailedKeepsTheEntryWithWhatWasEnteredAndTheEnginesWords() = runTest {
        access.tripFails = "No space left on device"
        val entry = added()
        entry.fill("120")

        entry.save()
        runCurrent()

        assertThat(entry.saving.value).isEqualTo(SaveState.Failed("No space left on device"))
        assertThat(entry.draft.value!!.fields.distance).isEqualTo("120")
        assertThat(changes).isEmpty()
        assertThat(entry.notice.value).isNull()
    }

    @Test
    fun anEntryOpenedAfterAFailedSaveWasDiscardedStartsWithoutTheFailure() = runTest {
        access.tripFails = "No space left on device"
        val entry = added()
        entry.fill("120")
        entry.save()
        runCurrent()
        entry.back()
        entry.discard()

        entry.add(today, amsterdam)

        assertThat(entry.saving.value).isEqualTo(SaveState.Idle)
    }

    @Test
    fun anEntryOpenedAfterARefusalWasDiscardedStartsWithoutTheProblem() = runTest {
        val entry = added()
        entry.fill("abc")
        entry.save()
        entry.back()
        entry.discard()

        entry.add(today, amsterdam)

        assertThat(entry.problem.value).isNull()
    }

    @Test
    fun whileTheTripIsSavedNothingChangesOrClosesTheEntry() = runTest {
        access.tripGate = CompletableDeferred()
        val entry = added()
        entry.fill("120")
        entry.save()
        runCurrent()

        entry.distance("99")
        entry.back()
        entry.discard()

        assertThat(entry.saving.value).isEqualTo(SaveState.Saving)
        assertThat(entry.draft.value!!.fields.distance).isEqualTo("120")
        assertThat(entry.discardAsked.value).isFalse()
    }

    @Test
    fun backWithNothingChangedClosesAndAChangeUndoneDoesNotAsk() = runTest {
        val entry = added()
        entry.back()
        assertThat(entry.draft.value).isNull()

        entry.add(today, amsterdam)
        entry.departureTime(LocalTime.of(9, 0))
        entry.departureTime(LocalTime.of(8, 0))
        entry.distance("  ")
        entry.back()

        assertThat(entry.draft.value).isNull()
        assertThat(entry.discardAsked.value).isFalse()
    }

    @Test
    fun backWithAChangeAsksKeepEditingStaysAndDiscardCloses() = runTest {
        val entry = added()
        entry.distance("12")

        entry.back()
        assertThat(entry.discardAsked.value).isTrue()
        entry.keepEditing()
        assertThat(entry.discardAsked.value).isFalse()
        assertThat(entry.draft.value!!.fields.distance).isEqualTo("12")

        entry.back()
        entry.discard()
        assertThat(entry.draft.value).isNull()
        assertThat(entry.discardAsked.value).isFalse()
        assertThat(handle.keys()).isEmpty()
    }

    @Test
    fun aChangeAsksOnlyWhenSomethingDiffersFromTheTripAsItOpened() = runTest {
        val stored = trip(today.plusDays(1), "07:30", today.plusDays(1), "18:00", 300.0, canCharge = true)
        val entry = entry()
        entry.change(stored, amsterdam)
        entry.canCharge(false)
        entry.canCharge(true)

        entry.back()
        assertThat(entry.draft.value).isNull()
        assertThat(entry.closedOn).isEqualTo(stored)

        entry.change(stored, amsterdam)
        entry.returnDay(today.plusDays(2))
        entry.back()
        assertThat(entry.discardAsked.value).isTrue()
        entry.discard()
        assertThat(entry.closedOn).isEqualTo(stored)
    }

    @Test
    fun aSecondOpenLeavesTheOpenEntryAsItIs() = runTest {
        val entry = added()
        entry.distance("5")

        entry.add(today.plusDays(1), amsterdam)
        entry.change(trip(today.plusDays(1), "07:30", today.plusDays(1), "18:00", 300.0), amsterdam)

        assertThat(entry.draft.value!!.mode).isEqualTo(Mode.ADD)
        assertThat(entry.draft.value!!.fields.distance).isEqualTo("5")
    }

    @Test
    fun aChangeComesBackWithWhatWasEnteredHowItOpenedAndTheQuestionAfterTheSystemEndedTheApp() = runTest {
        val stored = Trip(Instant.parse("2026-06-02T05:30:45.5Z"), Instant.parse("2026-06-03T16:00:00Z"), 120.6,
            true, true)
        val entry = entry()
        entry.change(stored, amsterdam)
        entry.departureTime(LocalTime.of(6, 15))
        entry.returnDay(today.plusDays(3))
        entry.distance("130")
        entry.canCharge(false)
        entry.back()

        val again = entry(handle.afterTheSystemEndedTheApp())

        assertThat(again.draft.value).isEqualTo(entry.draft.value)
        assertThat(again.draft.value!!.opened).isEqualTo(stored)
        assertThat(again.discardAsked.value).isTrue()
    }

    @Test
    fun aNewTripComesBackWithItsTodayAndThePagesDayAfterTheSystemEndedTheApp() = runTest {
        clock.now = at(today, "23:50")
        val entry = added(today.plusDays(1))
        entry.distance("12,5")

        clock.now = at(today.plusDays(1), "00:30")
        val again = entry(handle.afterTheSystemEndedTheApp())

        assertThat(again.draft.value).isEqualTo(entry.draft.value)
        assertThat(again.draft.value!!.today).isEqualTo(today)
        assertThat(again.discardAsked.value).isFalse()
    }

    @ParameterizedTest
    @CsvSource(
        "tripEntry.zone, Mars/Olympus_Mons",
        "tripEntry.opened.km, many",
        "tripEntry.departureDay, someday",
        "tripEntry.mode, SOMETIMES",
        "tripEntry.opened.departure, ",
        // a day outside the window, from today, 1 June, to 21 June, and a distance longer than the entry takes
        "tripEntry.departureDay, 2026-05-31",
        "tripEntry.returnDay, 2026-06-22",
        "tripEntry.distance, 12345678901234567",
        // a text where a number or a flag belongs
        "tripEntry.departureTime, 08:00",
        "tripEntry.canCharge, yes",
        "tripEntry.opened.notWithThisCar, no",
    )
    fun anEntryThatCannotBeReadBackIsClosedAndForgotten(key: String, value: String?) = runTest {
        entry().change(trip(today.plusDays(1), "07:30", today.plusDays(1), "18:00", 300.0), amsterdam)
        if (value == null) handle.remove<String>(key) else handle[key] = value

        assertForgotten(handle.afterTheSystemEndedTheApp())
    }

    @ParameterizedTest
    @ValueSource(strings = ["tripEntry.mode", "tripEntry.zone", "tripEntry.distance", "tripEntry.firstDay"])
    fun anEntryWithANumberWhereATextBelongsIsClosedAndForgotten(key: String) = runTest {
        added().distance("12")
        handle[key] = 7

        assertForgotten(handle.afterTheSystemEndedTheApp())
    }

    private fun TestScope.assertForgotten(kept: SavedStateHandle) {
        val again = entry(kept)

        assertThat(again.draft.value).isNull()
        assertThat(again.discardAsked.value).isFalse()
        assertThat(kept.keys()).isEmpty()
    }

    @Test
    fun anEntryKeptWithItsDaysAtTheWindowsEndsAndSixteenCharactersComesBack() = runTest {
        val entry = added()
        entry.returnDay(today.plusDays(20))
        entry.distance("1234567890123456")

        val again = entry(handle.afterTheSystemEndedTheApp())

        assertThat(again.draft.value).isEqualTo(entry.draft.value)
    }

    @Test
    fun forgetLeavesNoKeyOfTheEntry() = runTest {
        entry().change(trip(today.plusDays(1), "07:30", today.plusDays(1), "18:00", 300.0), amsterdam)

        TripEntry.forget(handle)

        assertThat(handle.keys()).isEmpty()
    }

    @Test
    fun anEntryOpenedAndNotChangedComesBackAfterTheSystemEndedTheApp() = runTest {
        val entry = added()

        val again = entry(handle.afterTheSystemEndedTheApp())

        assertThat(again.draft.value).isEqualTo(entry.draft.value)
    }

    @Test
    fun aKeptQuestionWithoutAKeptEntryDoesNotShow() = runTest {
        handle["tripEntry.discard"] = true

        val entry = entry()

        assertThat(entry.discardAsked.value).isFalse()
    }
}
