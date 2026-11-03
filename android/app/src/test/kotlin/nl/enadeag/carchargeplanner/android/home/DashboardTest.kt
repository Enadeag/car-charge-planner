package nl.enadeag.carchargeplanner.android.home

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Optional
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.forecast.Freshness
import nl.enadeag.carchargeplanner.engine.forecast.ProviderRole
import nl.enadeag.carchargeplanner.engine.plan.GridAdvice
import nl.enadeag.carchargeplanner.engine.plan.Window
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class DashboardTest {

    private val amsterdam = ZoneId.of("Europe/Amsterdam")
    private val today = LocalDate.of(2026, 6, 1)
    private val madeAt = today.atTime(10, 0).atZone(amsterdam)

    private fun dashboard(away: List<OneOffAwayPeriod> = listOf(), gridAdvice: GridAdvice? = null) =
        Dashboard.of(Plans.planned(madeAt, listOf(listOf(), listOf(), listOf()), gridAdvice = gridAdvice), away)

    @Test
    fun aPlanWithoutDaysIsNoDashboard() {
        assertThatThrownBy { Dashboard.of(Plans.planned(madeAt, listOf()), listOf()) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun theFooterCreditsThePrimaryProviderWithTheTimeItsForecastWasFetched() {
        val fetched = madeAt.minusMinutes(20).toInstant()

        val footer = Footer.of(Plans.planned(madeAt, listOf(listOf()), source = Plans.source(fetched)))

        assertThat(footer).isEqualTo(Footer(Plans.CREDIT, Footer.Fallback.No, fetched, stale = false, busyUntil = null))
    }

    @Test
    fun aStaleForecastFromTheFallbackSaysForWhichProviderItStoodIn() {
        val fetched = madeAt.minusHours(3).toInstant()
        val source = Plans.source(fetched, Freshness.STALE, ProviderRole.FALLBACK, "Forecast.Solar")

        val footer = Footer.of(Plans.planned(madeAt, listOf(listOf()), source = source))

        assertThat(footer.fallback).isEqualTo(Footer.Fallback.For("Forecast.Solar"))
        assertThat(footer.stale).isTrue()
    }

    @Test
    fun aFallbackForAPrimaryWithoutANameSaysSo() {
        val source = Plans.source(madeAt.toInstant(), role = ProviderRole.FALLBACK, primaryName = "")

        assertThat(Footer.of(Plans.planned(madeAt, listOf(listOf()), source = source)).fallback)
            .isEqualTo(Footer.Fallback.ForUnknown)
    }

    @ParameterizedTest
    @CsvSource("-1, false", "0, false", "1, true")
    fun theProviderIsBusyOnlyWhileItsCoolDownLastsPastThePlan(minutesAfterThePlan: Long, busy: Boolean) {
        val until = madeAt.plusMinutes(minutesAfterThePlan).toInstant()
        val source = Plans.source(madeAt.toInstant(), busyUntil = until)

        assertThat(Footer.of(Plans.planned(madeAt, listOf(listOf()), source = source)).busyUntil)
            .isEqualTo(if (busy) until else null)
    }

    @Test
    fun gridAdviceWithAWindowNamesItsDayInTheHomesZoneWithTheMinimumItKeeps() {
        // 22:30 in UTC on the 2nd is past midnight in Amsterdam: the window is on the 3rd, not tomorrow
        val window = Window(Instant.parse("2026-06-02T22:30:00Z"), Instant.parse("2026-06-02T23:30:00Z"), 16, 3.7,
            0.0, 3.7)
        val advice = GridAdvice(today.plusDays(2), 17.0, 13.0, 3.7, Optional.of(window), Optional.of(Percent(55.0)))

        assertThat(dashboard(gridAdvice = advice).gridAdvice).isEqualTo(GridAdviceModel(3.7,
            DayName.Other(today.plusDays(2)), window, DayName.Other(today.plusDays(2)), Percent(55.0), 17.0,
            Percent(30.0)))
    }

    @ParameterizedTest
    @CsvSource("0.0, true", "-4.0, true", "0.5, false")
    fun gridAdviceWithoutRoomRunsEmptyOnlyWhenItEndsAtOrBelowNothing(endLevelPct: Double, runsEmpty: Boolean) {
        val advice = GridAdvice(today.plusDays(1), endLevelPct, 30.0, 6.0, Optional.empty(), Optional.empty())

        val model = dashboard(gridAdvice = advice).gridAdvice!!

        assertThat(model.window).isNull()
        assertThat(model.windowDay).isNull()
        assertThat(model.lowDay).isEqualTo(DayName.Tomorrow)
        assertThat(model.runsEmpty).isEqualTo(runsEmpty)
    }

    @Test
    fun withoutGridAdviceThereIsNone() {
        assertThat(dashboard().gridAdvice).isNull()
    }

    @Test
    fun theOneOffAwayPeriodsThatEndedAreLeftOutAndTheOthersListedByKind() {
        val ended = OneOffAwayPeriod.on(today, LocalTime.of(7, 0), LocalTime.of(10, 0), amsterdam)
        val running = OneOffAwayPeriod.on(today, LocalTime.of(9, 0), LocalTime.of(11, 0), amsterdam)
        val allDay = OneOffAwayPeriod.allDay(today.plusDays(1), amsterdam)
        val days = OneOffAwayPeriod(today.plusDays(2).atTime(18, 0).atZone(amsterdam).toInstant(),
            today.plusDays(4).atTime(9, 0).atZone(amsterdam).toInstant())

        assertThat(dashboard(listOf(ended, running, allDay, days)).away).containsExactly(
            ComingAway.PartOfDay(DayName.Today, running.start(), running.end()),
            ComingAway.AllDay(DayName.Tomorrow),
            ComingAway.Days(DayName.Other(today.plusDays(2)), days.start(), DayName.Other(today.plusDays(4)),
                days.end()))
    }

    @ParameterizedTest
    @CsvSource("2026-03-29", "2026-10-25")
    fun aWholeShortOrLongDayIsAllDayAndTwentyFourHoursOfItAreNot(day: LocalDate) {
        val start = day.atStartOfDay(amsterdam).toInstant()
        val whole = OneOffAwayPeriod.allDay(day, amsterdam)
        val twentyFourHours = OneOffAwayPeriod(start, start.plusSeconds(24 * 3600))

        val listed = ComingAway.of(listOf(whole, twentyFourHours), amsterdam, start)

        assertThat(listed[0]).isEqualTo(ComingAway.AllDay(DayName.Today))
        assertThat(listed[1]).isNotInstanceOf(ComingAway.AllDay::class.java)
    }

    @Test
    fun gridAdvicesLowDayIsNamedFromTheDayThePlanWasMadeInTheHomesZone() {
        // 22:30 in UTC on the 1st is 00:30 on the 2nd in Amsterdam: the 3rd is tomorrow, not the day after
        val lateAtNight = Instant.parse("2026-06-01T22:30:00Z").atZone(amsterdam)
        val advice = GridAdvice(today.plusDays(2), 12.0, 18.0, 7.4, Optional.empty(), Optional.empty())

        val shown = Dashboard.of(Plans.planned(lateAtNight, listOf(listOf(), listOf()), gridAdvice = advice), listOf())

        assertThat(shown.gridAdvice?.lowDay).isEqualTo(DayName.Tomorrow)
    }

    @Test
    fun gridAdviceShowsOnTodaysTabAndNoOther() {
        val advice = GridAdvice(today.plusDays(1), 12.0, 18.0, 7.4, Optional.empty(), Optional.empty())
        val shown = dashboard(gridAdvice = advice)

        assertThat(shown.days.map { shown.adviceOn(it) }).containsExactly(shown.gridAdvice, null, null)
    }

    @Test
    fun aPlanWhoseFirstDayIsNotTodayShowsNoGridAdvice() {
        val advice = GridAdvice(today.plusDays(2), 12.0, 18.0, 7.4, Optional.empty(), Optional.empty())
        val shown = dashboard(gridAdvice = advice)
        val later = shown.copy(days = shown.days.drop(1))

        assertThat(later.days.map { later.adviceOn(it) }).containsOnlyNulls()
    }

    @Test
    fun theBatteryBarsMarkThePlansMinimumAndTargetChargeLevels() {
        assertThat(dashboard().levels).isEqualTo(ChargeLevels(Percent(20.0), Percent(80.0)))
    }

    @Test
    fun gridAdvicesMinimumIsTheEndLevelAndTheDeficitWithinAPercentagesRange() {
        // the engine's deficit is the minimum less the end level; a rounding must not make the sum refused
        val advice = GridAdvice(today.plusDays(1), -3.3, 103.30000000000001, 6.0, Optional.empty(), Optional.empty())

        assertThat(dashboard(gridAdvice = advice).gridAdvice?.minimum).isEqualTo(Percent(100.0))
    }

    @Test
    fun gridAdvicesMinimumStaysAtNothingWhenARoundingPutsTheSumJustBelow() {
        val advice = GridAdvice(today.plusDays(1), -4.0, 3.9999999, 6.0, Optional.empty(), Optional.empty())

        assertThat(dashboard(gridAdvice = advice).gridAdvice?.minimum).isEqualTo(Percent(0.0))
    }
}
