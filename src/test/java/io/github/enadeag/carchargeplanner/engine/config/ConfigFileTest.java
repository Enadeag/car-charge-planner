package io.github.enadeag.carchargeplanner.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ConfigFileTest {

    private static final Path EXAMPLE = Path.of("docs", "example-engine.json");

    @TempDir
    Path folder;

    private Path write(String json) throws IOException {
        return Files.writeString(folder.resolve("engine.json"), json);
    }

    @Test
    void theExampleConfigDescribesAWholeSystem() throws ConfigProblem {
        var config = new ConfigFile(EXAMPLE).read();

        assertThat(config.system().timezone()).isEqualTo(ZoneId.of("Europe/Brussels"));
        assertThat(config.system().panels()).containsExactly(new PanelArray("South roof", 5.0, 35, 0, 0.14));
        assertThat(config.system().charger()).isEqualTo(new Charger(1, 6, 16, 230, 0.9));
        assertThat(config.planning()).isEqualTo(new PlanningPreferences(30, 80, 0.85, 0.2, 45, 15));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "\"kwp\": 5.0           | \"kwhp\": 5.0          | kwp",
        "\"phases\": 1,         | \"phases\": 1, \"phasse\": 3, | phasse",
        "\"phases\": 1,         | \"phases\": 2,         | phases must be 1 or 3",
        "\"phases\": 1,         | \"phases\": 0,         | phases must be 1 or 3",
        "\"phases\": 1,         | \"phases\": 4,         | phases must be 1 or 3",
        "\"phases\": 1,         | \"phases\": -1,        | phases must be 1 or 3",
        "\"phases\": 1,         | \"phases\": true,      | phases",
        "\"margin\": 0.85,      | \"margin\": 1.5,       | margin",
        "\"timezone\": \"Europe/Brussels\" | \"timezone\": \"Mars/Base\" | Mars/Base",
        "\"latitude\": 51.0,    | \"latitude\": 120,     | latitude",
        "\"targetPct\": 80,     | ''                     | targetPct",
        "\"minimumPct\": 30,    | \"minimumPct\": 90,    | minimumPct",
        "\"minimumPct\": 30,    | \"minimumPct\": -1,    | minimumPct",
        "\"notifyLeadMinutes\": 15 | \"notifyLeadMinutes\": -5 | notifyLeadMinutes",
        "\"volts\": 230,        | \"volts\": null,       | volts",
    })
    void aWrongOrMissingValueIsRejectedWithAMessageThatNamesIt(String original, String replacement, String named)
            throws IOException {
        var example = Files.readString(EXAMPLE);
        assertThat(example).contains(original);
        var file = write(example.replace(original, replacement));

        assertThatThrownBy(() -> new ConfigFile(file).read())
                .isInstanceOf(ConfigProblem.class)
                .hasMessageContaining(named);
    }

    @Test
    void aFileThatIsNotJsonIsRejected() throws IOException {
        var file = write("[system\nlatitude = 1");
        assertThatThrownBy(() -> new ConfigFile(file).read()).isInstanceOf(ConfigProblem.class);
    }

    @Test
    void aMissingFileIsAProblemThatSaysWhereTheFileShouldBe() {
        var missing = folder.resolve("nothing-here.json");
        assertThatThrownBy(() -> new ConfigFile(missing).read())
                .isInstanceOf(ConfigProblem.class)
                .hasMessageContaining(missing.toString());
    }
}
