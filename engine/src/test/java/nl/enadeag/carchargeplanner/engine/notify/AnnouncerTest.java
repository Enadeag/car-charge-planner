package nl.enadeag.carchargeplanner.engine.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import nl.enadeag.carchargeplanner.LogCapture;
import nl.enadeag.carchargeplanner.engine.config.ConfigFile;
import nl.enadeag.carchargeplanner.engine.config.ConfigProblem;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastSource;
import nl.enadeag.carchargeplanner.engine.forecast.Freshness;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderRole;
import nl.enadeag.carchargeplanner.engine.plan.DayPlan;
import nl.enadeag.carchargeplanner.engine.plan.GridAdvice;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import nl.enadeag.carchargeplanner.engine.store.FilePrivacy;
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** When the owner is told about a window, and when not. */
class AnnouncerTest {

    private static final PrivateFiles FILES = new PrivateFiles(FilePrivacy.NONE);

    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    private static final LocalDate DAY = LocalDate.of(2026, 7, 3);

    @TempDir
    Path folder;

    private final List<Announcement> told = new ArrayList<>();

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(BRUSSELS).toInstant();
    }

    private static Window window(LocalDate day, int startHour, int startMinute, int endHour) {
        return new Window(at(day, startHour, startMinute), at(day, endHour, 0), 7, 1.6, 7.5, 0.1);
    }

    private static Window window(int startHour, int endHour) {
        return window(DAY, startHour, 0, endHour);
    }

    private static List<DayPlan> dayWith(Window... windows) {
        return List.of(day(DAY, windows));
    }

    private static DayPlan day(LocalDate date, Window... windows) {
        return new DayPlan(date, new Percent(40), List.of(windows), new Percent(55), new Percent(40), 12, List.of());
    }

    /** The example system, which charges on one phase and keeps a minimum of 30 %, with this lead time. */
    private static EngineConfig config(int leadMinutes) {
        try {
            var example = new ConfigFile(Path.of("docs", "example-engine.json"), FILES).read();
            var planning = example.planning();
            return example.withPlanning(new PlanningPreferences(planning.minimumPct(), planning.targetPct(),
                    planning.margin(), planning.importToleranceKwh(), planning.minWindow(),
                    Duration.ofMinutes(leadMinutes)));
        } catch (ConfigProblem e) {
            throw new AssertionError(e);
        }
    }

    private static ForecastSource forecast(Instant fetchedAt, Freshness freshness) {
        return new ForecastSource(new ProviderId("main"), "Main", "Forecast by Main", fetchedAt, freshness,
                ProviderRole.PRIMARY, "", Optional.empty(), List.of());
    }

    private Announcer announcer() {
        return new Announcer(new AnnouncedWindows(folder.resolve("announced.json"), FILES),
                new ToldTrips(folder.resolve("trip-notices.json"), FILES), notice -> told.add((Announcement) notice));
    }

    /** Announces with a forecast fetched five minutes ago and the given lead time. */
    private List<Window> check(Announcer announcer, List<DayPlan> days, Instant now, int leadMinutes) {
        told.clear();
        announcer.announceDue(days, forecast(now.minus(Duration.ofMinutes(5)), Freshness.CACHED), now,
                config(leadMinutes));
        return told.stream().map(Announcement::window).toList();
    }

    /** With an announcer that has announced nothing yet. */
    private List<Window> check(List<DayPlan> days, Instant now) {
        var fresh = new Announcer(
                new AnnouncedWindows(folder.resolve("fresh-" + now.getEpochSecond() + ".json"), FILES),
                new ToldTrips(folder.resolve("trips-" + now.getEpochSecond() + ".json"), FILES),
                notice -> told.add((Announcement) notice));
        return check(fresh, days, now, 15);
    }

    @Test
    void announcesInsideTheLeadTimeOnly() {
        var days = dayWith(window(10, 15));

        assertThat(check(days, at(DAY, 9, 44))).isEmpty();
        assertThat(check(days, at(DAY, 9, 45))).containsExactly(window(10, 15));
        assertThat(check(days, at(DAY, 9, 59))).containsExactly(window(10, 15));
    }

    @Test
    void doesNotAnnounceAWindowThatHasStarted() {
        var days = dayWith(window(10, 15));

        assertThat(check(days, at(DAY, 10, 0))).isEmpty();
        assertThat(check(days, at(DAY, 11, 0))).isEmpty();
    }

    @Test
    void aWindowThePlanStartsNowIsNotAnnounced() {
        var now = at(DAY, 11, 7);
        assertThat(check(dayWith(new Window(now, at(DAY, 15, 0), 7, 1.6, 6.0, 0.1)), now)).isEmpty();
    }

    @Test
    void aWindowIsAnnouncedOnceEvenWhenANewerForecastMovesItsStartALittle() {
        var announcer = announcer();
        assertThat(check(announcer, dayWith(window(10, 15)), at(DAY, 9, 50), 15)).hasSize(1);

        assertThat(check(announcer, dayWith(window(DAY, 10, 5, 15)), at(DAY, 9, 55), 15)).isEmpty();
    }

    @Test
    void anotherWindowTheSameDayIsStillAnnounced() {
        var announcer = announcer();
        check(announcer, dayWith(window(10, 11)), at(DAY, 9, 50), 15);

        assertThat(check(announcer, dayWith(window(10, 11), window(16, 18)), at(DAY, 15, 50), 15))
                .containsExactly(window(16, 18));
    }

    @Test
    void nothingIsAnnouncedOnAForecastThatCouldNotBeRefreshedOrIsOld() {
        var days = dayWith(window(10, 15));
        var now = at(DAY, 9, 50);
        var config = config(15);

        announcer().announceDue(days, forecast(now.minus(Duration.ofMinutes(30)), Freshness.STALE), now, config);
        announcer().announceDue(days, forecast(now.minus(Duration.ofHours(7)), Freshness.CACHED), now, config);
        assertThat(told).isEmpty();

        announcer().announceDue(days, forecast(now.minus(Duration.ofHours(5)), Freshness.CACHED), now, config);
        assertThat(told).hasSize(1);
    }

    @Test
    void theLeadTimeComesFromThePlanningPreferences() {
        var days = dayWith(window(10, 15));

        var announcer = announcer();
        assertThat(check(announcer, days, at(DAY, 9, 30), 15)).isEmpty();
        assertThat(check(announcer, days, at(DAY, 9, 30), 30)).containsExactly(window(10, 15));
    }

    @Test
    void theGridAdvicesWindowIsAnnouncedAsAGridChargeBesideTheSolarWindowsInTheOrderTheyStart() {
        var solar = window(6, 9);
        var grid = new Window(at(DAY, 5, 30), at(DAY, 6, 0), 13, 3.0, 0, 1.5);
        var advice = new GridAdvice(DAY, 8, 22, 1.5, Optional.of(grid), Optional.of(new Percent(32)));
        var now = at(DAY, 5, 20);

        announcer().announceDue(dayWith(solar), advice, forecast(now, Freshness.FRESH), now, config(60));

        assertThat(told).extracting(Announcement::kind, Announcement::window, Announcement::chargeLimitPct)
                .containsExactly(
                        tuple(AnnouncementKind.GRID_CHARGE, grid, Optional.of(new Percent(32))),
                        tuple(AnnouncementKind.SOLAR_WINDOW, solar, Optional.empty()));
    }

    @Test
    void anAnnouncementCarriesWhatTheMessageNeeds() {
        check(dayWith(window(10, 15)), at(DAY, 9, 50));

        var announcement = told.get(0);
        assertThat(announcement.zone()).isEqualTo(BRUSSELS);
        assertThat(announcement.phases().count()).isEqualTo(1);
        assertThat(announcement.minimumPct().value()).isEqualTo(30);
    }

    @Test
    void announcedWindowsSurviveARestartAndOldOnesAreDropped() {
        var yesterday = DAY.minusDays(2);
        check(announcer(), List.of(day(yesterday, window(yesterday, 10, 0, 15))),
                at(yesterday, 9, 50), 15);
        check(announcer(), dayWith(window(10, 15)), at(DAY, 9, 50), 15);

        var afterRestart = announcer();
        assertThat(afterRestart.announced()).containsExactly(window(10, 15));
        assertThat(check(afterRestart, dayWith(window(10, 15)), at(DAY, 9, 52), 15)).isEmpty();
    }

    @Test
    void aDamagedFileCountsAsNothingAnnounced() throws IOException {
        Files.writeString(folder.resolve("announced.json"), "{not json");

        assertThat(announcer().announced()).isEmpty();
        assertThat(check(dayWith(window(10, 15)), at(DAY, 9, 50))).hasSize(1);
    }

    @Test
    void eachAnnouncementIsLoggedWithItsKindAndItsStartOnlyAtDebug() {
        try (var log = LogCapture.of(Announcer.class)) {
            check(dayWith(window(10, 15)), at(DAY, 9, 50));

            assertThat(log.lines()).containsExactly("INFO announced a SOLAR_WINDOW",
                    "FINE that window starts at 2026-07-03T10:00+02:00");
        }
    }

    @ParameterizedTest
    @CsvSource({"0, true", "-1, false"})
    void aForecastExactlyAsOldAsAllowedIsStillAnnouncedOn(long secondsLess, boolean announced) {
        var now = at(DAY, 9, 50);
        var fetchedAt = now.minus(Announcer.MAX_FORECAST_AGE).minusSeconds(-secondsLess);

        announcer().announceDue(dayWith(window(10, 15)), forecast(fetchedAt, Freshness.CACHED), now,
                config(15));

        assertThat(told).hasSize(announced ? 1 : 0);
    }

    @Test
    void theWindowsAnnouncedEarlierAreKeptBesideTheNewOne() {
        var announcer = announcer();
        check(announcer, dayWith(window(10, 11), window(13, 15)), at(DAY, 9, 50), 15);
        check(announcer, dayWith(window(10, 11), window(13, 15)), at(DAY, 12, 50), 15);

        assertThat(announcer.announced()).containsExactly(window(10, 11), window(13, 15));
        assertThat(announcer().announced()).containsExactly(window(10, 11), window(13, 15));
    }

    @ParameterizedTest
    @CsvSource({"0, false", "1, true"})
    void anAnnouncedWindowIsKeptUntilItEndedADayBeforeTheNextAnnouncement(long secondsLater, boolean kept) {
        var announcer = announcer();
        var first = window(10, 11);
        check(announcer, dayWith(first), at(DAY, 9, 50), 15);
        var tomorrow = DAY.plusDays(1);
        var next = new Window(at(tomorrow, 11, 10), at(tomorrow, 12, 0), 7, 1.6, 1.2, 0.1);
        var now = first.end().plus(Announcer.KEEP_AFTER_END).minusSeconds(secondsLater);

        check(announcer, List.of(day(tomorrow, next)), now, 15);

        assertThat(announcer.announced()).isEqualTo(kept ? List.of(first, next) : List.of(next));
    }

    // when the next notice is due: the same windows and rules as the announcements

    /** When the next notice is due with a forecast fetched five minutes before now and the given lead time. */
    private static Optional<Instant> nextDue(Announcer announcer, List<DayPlan> days, Instant now, int leadMinutes) {
        return announcer.nextDue(days, List.of(), forecast(now.minus(Duration.ofMinutes(5)), Freshness.CACHED), now,
                config(leadMinutes));
    }

    @Test
    void theNextNoticeIsDueAtTheFirstWindowsStartLessTheLeadTime() {
        var days = dayWith(window(10, 12), window(16, 18));

        assertThat(nextDue(announcer(), days, at(DAY, 7, 0), 15)).contains(at(DAY, 9, 45));
        assertThat(nextDue(announcer(), days, at(DAY, 7, 0), 30)).contains(at(DAY, 9, 30));
    }

    @Test
    void aWindowWithinItsLeadTimeThatWasNotAnnouncedIsDueNow() {
        var now = at(DAY, 9, 50);
        assertThat(nextDue(announcer(), dayWith(window(10, 12)), now, 15)).contains(now);
    }

    @Test
    void anAnnouncedWindowIsNotDueAgainSoTheNextOneIs() {
        var announcer = announcer();
        var days = dayWith(window(10, 12), window(16, 18));
        check(announcer, days, at(DAY, 9, 50), 15);

        assertThat(nextDue(announcer, days, at(DAY, 9, 51), 15)).contains(at(DAY, 15, 45));
        // a newer forecast moved the announced window a little: it still counts as announced
        assertThat(nextDue(announcer, dayWith(window(DAY, 10, 5, 12), window(16, 18)), at(DAY, 9, 52), 15))
                .contains(at(DAY, 15, 45));
    }

    @Test
    void aWindowThatHasStartedIsNotDueAndWithoutOneToComeNothingIs() {
        var days = dayWith(window(10, 12), window(16, 18));

        assertThat(nextDue(announcer(), days, at(DAY, 10, 0), 15)).contains(at(DAY, 15, 45));
        assertThat(nextDue(announcer(), days, at(DAY, 16, 0), 15)).isEmpty();
        assertThat(nextDue(announcer(), List.of(), at(DAY, 7, 0), 15)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"9, 59, false", "10, 0, true", "10, 9, true", "10, 10, false", "11, 0, false"})
    void aLeadTimeOfZeroAnnouncesAtTheStartForTenMinutesAtMost(int hour, int minute, boolean announced) {
        var days = dayWith(window(10, 15));

        assertThat(check(announcer(), days, at(DAY, hour, minute), 0))
                .isEqualTo(announced ? List.of(window(10, 15)) : List.of());
    }

    @Test
    void aLeadTimeOfZeroNeverAnnouncesAtOrAfterTheWindowsEnd() {
        var days = dayWith(new Window(at(DAY, 10, 0), at(DAY, 10, 6), 7, 1.6, 0.2, 0.1));

        assertThat(check(announcer(), days, at(DAY, 10, 5), 0)).hasSize(1);
        assertThat(check(announcer(), days, at(DAY, 10, 6), 0)).isEmpty();
        assertThat(check(announcer(), days, at(DAY, 10, 8), 0)).isEmpty();
    }

    @Test
    void aLeadTimeOfZeroAnnouncesAWindowOnce() {
        var announcer = announcer();
        var days = dayWith(window(10, 15));

        assertThat(check(announcer, days, at(DAY, 10, 0), 0)).containsExactly(window(10, 15));
        assertThat(check(announcer, days, at(DAY, 10, 1), 0)).isEmpty();
        assertThat(check(announcer, days, at(DAY, 10, 2), 0)).isEmpty();
    }

    @Test
    void aLeadTimeOfOneMinuteAnnouncesBeforeTheStartAndNotAtIt() {
        var days = dayWith(window(10, 15));

        assertThat(check(announcer(), days, at(DAY, 9, 58), 1)).isEmpty();
        assertThat(check(announcer(), days, at(DAY, 9, 59), 1)).containsExactly(window(10, 15));
        assertThat(check(announcer(), days, at(DAY, 10, 0), 1)).isEmpty();
    }

    @Test
    void aLeadTimeOfZeroIsDueAtTheStartThenNowForTenMinutesThenNotAtAll() {
        var days = dayWith(window(10, 12), window(16, 18));

        assertThat(nextDue(announcer(), days, at(DAY, 7, 0), 0)).contains(at(DAY, 10, 0));
        assertThat(nextDue(announcer(), days, at(DAY, 10, 0), 0)).contains(at(DAY, 10, 0));
        assertThat(nextDue(announcer(), days, at(DAY, 10, 9), 0)).contains(at(DAY, 10, 9));
        assertThat(nextDue(announcer(), days, at(DAY, 10, 10), 0)).contains(at(DAY, 16, 0));
        assertThat(nextDue(announcer(), days, at(DAY, 16, 10), 0)).isEmpty();
    }

    @Test
    void aWindowAnnouncedWithALeadTimeOfZeroIsNotDueAgain() {
        var announcer = announcer();
        var days = dayWith(window(10, 12));
        check(announcer, days, at(DAY, 10, 0), 0);

        assertThat(nextDue(announcer, days, at(DAY, 10, 1), 0)).isEmpty();
    }

    @Test
    void theNextDueIsAskedWithoutAnnouncingAnything() {
        var announcer = announcer();
        var days = dayWith(window(10, 12));
        nextDue(announcer, days, at(DAY, 9, 50), 15);

        assertThat(told).isEmpty();
        assertThat(check(announcer, days, at(DAY, 9, 51), 15)).containsExactly(window(10, 12));
    }

    @Test
    void theGridAdvicesWindowIsDueLikeTheSolarWindows() {
        var grid = new Window(at(DAY, 5, 30), at(DAY, 6, 0), 13, 3.0, 0, 1.5);
        var advice = new GridAdvice(DAY, 8, 22, 1.5, Optional.of(grid), Optional.of(new Percent(32)));
        var now = at(DAY, 4, 0);

        assertThat(announcer().nextDue(dayWith(window(6, 9)), advice, List.of(), forecast(now, Freshness.FRESH), now,
                config(15))).contains(at(DAY, 5, 15));
        var withoutWindow = new GridAdvice(DAY, 8, 22, 1.5, Optional.empty(), Optional.empty());
        assertThat(announcer().nextDue(dayWith(window(6, 9)), withoutWindow, List.of(),
                forecast(now, Freshness.FRESH), now, config(15))).contains(at(DAY, 5, 45));
    }

    @Test
    void nothingIsDueOnAForecastThatCouldNotBeRefreshed() {
        var now = at(DAY, 7, 0);

        assertThat(announcer().nextDue(dayWith(window(10, 12)), List.of(), forecast(now, Freshness.STALE), now,
                config(15))).isEmpty();
    }

    /** The window's notice is due at 09:45; the forecast is 6 hours old at the given time. */
    @ParameterizedTest
    @CsvSource({"3, 45, true", "3, 44, false"})
    void aNoticeIsDueOnlyWhileTheForecastIsNotTooOldThen(int fetchedHour, int fetchedMinute, boolean due) {
        var now = at(DAY, 7, 0);
        var source = forecast(at(DAY, fetchedHour, fetchedMinute), Freshness.CACHED);

        assertThat(announcer().nextDue(dayWith(window(10, 12)), List.of(), source, now, config(15)).isPresent())
                .isEqualTo(due);
    }

    @Test
    void aLaterWindowIsNotDueOnAForecastThatIsTooOldByThenThoughAnEarlierOneIs() {
        var now = at(DAY, 7, 0);
        var source = forecast(at(DAY, 6, 0), Freshness.CACHED);

        assertThat(announcer().nextDue(dayWith(window(10, 12)), List.of(), source, now, config(15)))
                .contains(at(DAY, 9, 45));
        assertThat(announcer().nextDue(dayWith(window(13, 14)), List.of(), source, now, config(15))).isEmpty();
    }

    /** On the last Sunday of October Brussels turns its clocks back from 03:00 to 02:00. */
    @Test
    void theLeadTimeIsCountedInRealTimeOverAClockChange() {
        var day = LocalDate.of(2026, 10, 25);
        var start = day.atTime(3, 15).atZone(BRUSSELS).toInstant(); // 03:15 in winter time, 02:15 UTC
        var night = new Window(start, start.plus(Duration.ofHours(2)), 16, 3.7, 0, 7.4);
        var now = day.atStartOfDay(BRUSSELS).toInstant();

        var due = announcer().nextDue(List.of(day(day, night)), List.of(), forecast(now, Freshness.FRESH), now,
                config(90));

        // 90 minutes before 02:15 UTC is 00:45 UTC: 02:45 in summer time, before the clocks went back
        assertThat(due).contains(Instant.parse("2026-10-25T00:45:00Z"));
        assertThat(due.orElseThrow().atZone(BRUSSELS).toLocalTime()).isEqualTo(LocalTime.of(2, 45));
        assertThat(due.orElseThrow().atZone(BRUSSELS).getOffset()).isEqualTo(ZoneOffset.ofHours(2));
    }
}
