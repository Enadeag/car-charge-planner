package io.github.enadeag.carchargeplanner.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AwayFileTest {

    private static final ZoneId TZ = ZoneId.of("Europe/Amsterdam");
    private static final LocalDate DAY = LocalDate.of(2026, 7, 3);
    private static final Instant NOW = DAY.atTime(7, 0).atZone(TZ).toInstant();

    @TempDir
    Path folder;

    private AwayFile file() {
        return new AwayFile(folder.resolve("data").resolve("away.json"));
    }

    @Test
    void periodsAreKeptInOrderOfTheirStartAndEachOnce() throws IOException {
        var later = AwayPeriod.allDay(LocalDate.of(2026, 7, 5), TZ);
        var sooner = AwayPeriod.on(LocalDate.of(2026, 7, 4), LocalTime.of(10, 0), LocalTime.of(16, 0), TZ);
        file().add(later, NOW);
        file().add(sooner, NOW);
        file().add(sooner, NOW);

        assertThat(file().periods()).containsExactly(sooner, later);
    }

    @Test
    void aRemovedPeriodIsGone() throws IOException {
        var period = AwayPeriod.allDay(LocalDate.of(2026, 7, 4), TZ);
        file().add(period, NOW);

        assertThat(file().remove(period)).isEmpty();
        assertThat(file().periods()).isEmpty();
    }

    @Test
    void periodsThatEndedMoreThanADayAgoAreDroppedWhenOneIsAdded() throws IOException {
        var old = AwayPeriod.allDay(LocalDate.of(2026, 6, 20), TZ);
        file().add(old, LocalDate.of(2026, 6, 19).atTime(7, 0).atZone(TZ).toInstant());
        var yesterday = AwayPeriod.on(DAY.minusDays(1), LocalTime.of(9, 0), LocalTime.of(17, 0), TZ);
        file().add(yesterday, NOW);
        var next = AwayPeriod.allDay(LocalDate.of(2026, 7, 4), TZ);

        assertThat(file().add(next, NOW)).containsExactly(yesterday, next);
    }

    @Test
    void aMissingOrDamagedFileHoldsNoPeriods() throws IOException {
        assertThat(file().periods()).isEmpty();
        var damaged = Files.writeString(folder.resolve("damaged.json"), "{oops");
        assertThat(new AwayFile(damaged).periods()).isEmpty();
    }

    @Test
    void aDamagedEntryIsLeftOutAndTheRestKept() throws IOException {
        var path = Files.writeString(folder.resolve("away.json"), """
                {"periods": [{"start": 1}, "junk",
                  {"start": "2026-07-04T08:00:00Z", "end": "2026-07-04T06:00:00Z"},
                  {"start": "2026-07-04T06:00:00Z", "end": "2026-07-04T08:00:00Z"}]}
                """);
        assertThat(new AwayFile(path).periods()).containsExactly(
                new AwayPeriod(Instant.parse("2026-07-04T06:00:00Z"), Instant.parse("2026-07-04T08:00:00Z")));
    }

    @Test
    void aPeriodThatCannotBeSavedIsReported() throws IOException {
        Files.writeString(folder.resolve("data"), "a file where the folder should be");
        var period = AwayPeriod.allDay(DAY, TZ);

        assertThatThrownBy(() -> file().add(period, NOW)).isInstanceOf(IOException.class);
    }
}
