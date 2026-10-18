package io.github.enadeag.carchargeplanner.engine.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.enadeag.carchargeplanner.LogCapture;
import io.github.enadeag.carchargeplanner.engine.config.ConfigFile;
import io.github.enadeag.carchargeplanner.engine.config.ConfigProblem;
import io.github.enadeag.carchargeplanner.engine.config.EngineConfig;
import io.github.enadeag.carchargeplanner.engine.config.Percent;
import io.github.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastSource;
import io.github.enadeag.carchargeplanner.engine.forecast.Freshness;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderRole;
import io.github.enadeag.carchargeplanner.engine.plan.DayPlan;
import io.github.enadeag.carchargeplanner.engine.plan.GridAdvice;
import io.github.enadeag.carchargeplanner.engine.plan.Window;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** When the owner is told about a window; the cases of the Python version's tests/test_notifications.py. */
class AnnouncerTest {

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
            var example = new ConfigFile(Path.of("docs", "example-engine.json")).read();
            var planning = example.planning();
            return example.withPlanning(new PlanningPreferences(planning.minimumPct(), planning.targetPct(),
                    planning.margin(), planning.importToleranceKwh(), planning.minWindowMinutes(), leadMinutes));
        } catch (ConfigProblem e) {
            throw new AssertionError(e);
        }
    }

    private static ForecastSource forecast(Instant fetchedAt, Freshness freshness) {
        return new ForecastSource(new ProviderId("main"), "Main", "Forecast by Main", fetchedAt, freshness,
                ProviderRole.PRIMARY, "", Optional.empty());
    }

    private Announcer announcer() {
        return new Announcer(new AnnouncedWindows(folder.resolve("announced.json")), told::add);
    }

    /** Announces with a forecast fetched five minutes ago and the given lead time. */
    private List<Window> check(Announcer announcer, List<DayPlan> days, Instant now, int leadMinutes) {
        told.clear();
        announcer.announceDue(days, Optional.empty(), forecast(now.minus(Duration.ofMinutes(5)), Freshness.CACHED), now,
                config(leadMinutes));
        return told.stream().map(Announcement::window).toList();
    }

    /** With an announcer that has announced nothing yet. */
    private List<Window> check(List<DayPlan> days, Instant now) {
        var fresh = new Announcer(new AnnouncedWindows(folder.resolve("fresh-" + now.getEpochSecond() + ".json")), told::add);
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

        announcer().announceDue(days, Optional.empty(), forecast(now.minus(Duration.ofMinutes(30)), Freshness.STALE), now, config);
        announcer().announceDue(days, Optional.empty(), forecast(now.minus(Duration.ofHours(7)), Freshness.CACHED), now, config);
        assertThat(told).isEmpty();

        announcer().announceDue(days, Optional.empty(), forecast(now.minus(Duration.ofHours(5)), Freshness.CACHED), now, config);
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
        var advice = new GridAdvice(DAY, 8, 22, 1.5, Optional.of(grid));
        var now = at(DAY, 5, 20);

        announcer().announceDue(dayWith(solar), Optional.of(advice), forecast(now, Freshness.FRESH), now, config(60));

        assertThat(told).extracting(Announcement::kind, Announcement::window).containsExactly(
                tuple(AnnouncementKind.GRID_CHARGE, grid),
                tuple(AnnouncementKind.CHARGE_WINDOW, solar));
    }

    @Test
    void anAnnouncementCarriesWhatTheMessageNeeds() {
        check(dayWith(window(10, 15)), at(DAY, 9, 50));

        var announcement = told.getFirst();
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
    void eachAnnouncementIsLoggedWithItsKindAndStart() {
        try (var log = LogCapture.of(Announcer.class)) {
            check(dayWith(window(10, 15)), at(DAY, 9, 50));

            assertThat(log.lines()).containsExactly("INFO announced a CHARGE_WINDOW starting at 2026-07-03T10:00+02:00");
        }
    }

    @ParameterizedTest
    @CsvSource({"0, true", "-1, false"})
    void aForecastExactlyAsOldAsAllowedIsStillAnnouncedOn(long secondsLess, boolean announced) {
        var now = at(DAY, 9, 50);
        var fetchedAt = now.minus(Announcer.MAX_FORECAST_AGE).minusSeconds(-secondsLess);

        announcer().announceDue(dayWith(window(10, 15)), Optional.empty(), forecast(fetchedAt, Freshness.CACHED), now,
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
}
