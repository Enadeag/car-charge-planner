package nl.enadeag.carchargeplanner.engine.battery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.store.StoredJson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BatteryFileTest {

    private static final Instant NOW = Instant.parse("2026-07-03T07:00:00Z");

    @TempDir
    Path folder;

    private Path path() {
        return folder.resolve("data").resolve("battery.json");
    }

    @Test
    void theLevelEnteredLastIsKeptWithItsMoment() throws IOException {
        new BatteryFile(path()).save(new BatteryReading(new Percent(40), NOW));
        new BatteryFile(path()).save(new BatteryReading(new Percent(64.5), NOW.plusSeconds(60)));

        assertThat(new BatteryFile(path()).reading())
                .contains(new BatteryReading(new Percent(64.5), NOW.plusSeconds(60)));
    }

    @Test
    void withoutAFileThereIsNoReading() {
        assertThat(new BatteryFile(path()).reading()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "not json",
        "{}",
        "{\"levelPct\": 140, \"enteredAt\": \"2026-07-03T07:00:00Z\"}",
        "{\"levelPct\": \"40\", \"enteredAt\": \"2026-07-03T07:00:00Z\"}",
        "{\"levelPct\": 40, \"enteredAt\": \"yesterday\"}",
        "{\"levelPct\": 40}"})
    void aDamagedFileCountsAsNoReading(String content) throws IOException {
        Files.createDirectories(path().getParent());
        Files.writeString(path(), content);

        assertThat(new BatteryFile(path()).reading()).isEmpty();
    }

    @Test
    void aFileThatCannotBeWrittenIsLeftAsItWas() throws IOException {
        var file = new BatteryFile(path());
        file.save(new BatteryReading(new Percent(40), NOW));
        Files.createDirectories(path().resolveSibling("battery.json.tmp"));

        assertThatThrownBy(() -> file.save(new BatteryReading(new Percent(70), NOW))).isInstanceOf(IOException.class);
        assertThat(file.reading()).contains(new BatteryReading(new Percent(40), NOW));
    }

    @Test
    void aReadingNeedsALevelAndAMoment() {
        assertThatThrownBy(() -> new BatteryReading(null, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BatteryReading(new Percent(40), null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aFileWithoutAVersionIsReadAndAWrittenOneHasVersionOne() throws IOException {
        Files.createDirectories(path().getParent());
        Files.writeString(path(), "{\"levelPct\": 40, \"enteredAt\": \"2026-07-03T07:00:00Z\"}");
        assertThat(new BatteryFile(path()).reading()).contains(new BatteryReading(new Percent(40), NOW));

        new BatteryFile(path()).save(new BatteryReading(new Percent(70), NOW));

        assertThat(StoredJson.parse(Files.readString(path())).path("version").intValue()).isEqualTo(1);
    }

    @Test
    void aFileOfANewerVersionCountsAsNoReadingAndIsKeptAsideWhenALevelIsSaved() throws IOException {
        Files.createDirectories(path().getParent());
        var newer = "{\"version\": 2, \"levelPct\": 40, \"enteredAt\": \"2026-07-03T07:00:00Z\"}";
        Files.writeString(path(), newer);
        assertThat(new BatteryFile(path()).reading()).isEmpty();

        new BatteryFile(path()).save(new BatteryReading(new Percent(70), NOW));

        assertThat(new BatteryFile(path()).reading()).contains(new BatteryReading(new Percent(70), NOW));
        try (var kept = Files.list(path().getParent())) {
            assertThat(kept.filter(file -> file.getFileName().toString().startsWith("battery.json.damaged-")))
                    .singleElement().satisfies(file -> assertThat(file).hasContent(newer));
        }
    }
}
