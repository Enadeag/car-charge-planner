package nl.enadeag.carchargeplanner.engine.away;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.engine.store.StoredJson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(strings = {"{oops", "{\"version\": 2, \"periods\": []}",
        "{\"periods\": [{\"start\": 1}, {\"start\": \"2026-07-04T06:00:00Z\", \"end\": \"2026-07-04T08:00:00Z\"}]}"})
    void aFileThatCannotBeReadWholeIsNeverOverwritten(String content) throws IOException {
        var path = Files.writeString(folder.resolve("away.json"), content);
        var period = AwayPeriod.allDay(DAY, TZ);

        assertThatThrownBy(() -> new AwayFile(path).add(period, NOW)).isInstanceOf(IOException.class)
                .hasMessageContaining("left as it is");
        assertThatThrownBy(() -> new AwayFile(path).remove(period)).isInstanceOf(IOException.class);
        assertThat(path).hasContent(content);
    }

    @Test
    void aFileWithoutAVersionIsReadAndAWrittenOneHasVersionOne() throws IOException {
        var path = Files.writeString(folder.resolve("away.json"),
                "{\"periods\": [{\"start\": \"2026-07-04T06:00:00Z\", \"end\": \"2026-07-04T08:00:00Z\"}]}");
        var stored = new AwayPeriod(Instant.parse("2026-07-04T06:00:00Z"), Instant.parse("2026-07-04T08:00:00Z"));
        assertThat(new AwayFile(path).periods()).containsExactly(stored);

        var added = AwayPeriod.allDay(LocalDate.of(2026, 7, 5), TZ);
        new AwayFile(path).add(added, NOW);

        assertThat(StoredJson.parse(Files.readString(path)).path("version").intValue()).isEqualTo(1);
        assertThat(new AwayFile(path).periods()).containsExactly(stored, added);
    }

    @Test
    void addingGivesThePeriodsInOrderOfTheirStart() throws IOException {
        var later = AwayPeriod.allDay(LocalDate.of(2026, 7, 5), TZ);
        var sooner = AwayPeriod.allDay(LocalDate.of(2026, 7, 4), TZ);
        file().add(later, NOW);

        assertThat(file().add(sooner, NOW)).containsExactly(sooner, later);
    }

    @Test
    void periodsWrittenOutOfOrderAreReadInOrderOfTheirStart() throws IOException {
        var path = Files.writeString(folder.resolve("away.json"), """
                {"periods": [{"start": "2026-07-05T06:00:00Z", "end": "2026-07-05T08:00:00Z"},
                  {"start": "2026-07-04T06:00:00Z", "end": "2026-07-04T08:00:00Z"}]}
                """);

        assertThat(new AwayFile(path).periods()).containsExactly(
                new AwayPeriod(Instant.parse("2026-07-04T06:00:00Z"), Instant.parse("2026-07-04T08:00:00Z")),
                new AwayPeriod(Instant.parse("2026-07-05T06:00:00Z"), Instant.parse("2026-07-05T08:00:00Z")));
    }

    @Test
    void removingGivesThePeriodsThatAreLeftAndAnUnknownOneChangesNothing() throws IOException {
        var first = AwayPeriod.allDay(LocalDate.of(2026, 7, 4), TZ);
        var second = AwayPeriod.allDay(LocalDate.of(2026, 7, 5), TZ);
        file().add(first, NOW);
        file().add(second, NOW);

        assertThat(file().remove(first)).containsExactly(second);
        assertThat(file().remove(first)).containsExactly(second);
        assertThat(file().periods()).containsExactly(second);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0})
    void aPeriodIsKeptUntilItEndedADayAgo(long secondsLater) throws IOException {
        var period = AwayPeriod.on(DAY.minusDays(1), LocalTime.of(9, 0), LocalTime.of(17, 0), TZ);
        file().add(period, NOW);
        var now = period.end().plus(AwayFile.KEEP_AFTER_END).plusSeconds(secondsLater);
        var next = AwayPeriod.allDay(DAY.plusDays(1), TZ);

        assertThat(file().add(next, now)).isEqualTo(secondsLater < 0 ? List.of(period, next) : List.of(next));
    }
}
