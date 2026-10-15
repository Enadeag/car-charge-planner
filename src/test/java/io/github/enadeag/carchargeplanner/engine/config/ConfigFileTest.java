package io.github.enadeag.carchargeplanner.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.enadeag.carchargeplanner.engine.away.RecurringAwayPeriod;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
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

        assertThat(config.system().location()).isEqualTo(new Coordinates(51.0, 5.0));
        assertThat(config.system().timezone()).isEqualTo(ZoneId.of("Europe/Brussels"));
        assertThat(config.system().panels()).containsExactly(new PanelArray("South roof", 5.0, 35, 0, 0.14));
        assertThat(config.system().charger()).isEqualTo(new Charger(Phases.ONE, 6, 16, 230, 0.9));
        assertThat(config.planning()).isEqualTo(new PlanningPreferences(new Percent(30), new Percent(80), 0.85, 0.2, 45, 15));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "\"kwp\": 5.0           | \"kwhp\": 5.0          | kwp",
        "\"phases\": 1,         | \"phases\": 1, \"phasse\": 3, | phasse",
        "\"phases\": 1,         | \"phases\": 2,         | phases must be 1 or 3",
        "\"phases\": 1,         | \"phases\": 0,         | phases must be 1 or 3",
        "\"phases\": 1,         | \"phases\": 4,         | phases must be 1 or 3",
        "\"phases\": 1,         | \"phases\": -1,        | phases must be 1 or 3",
        "\"phases\": 1,         | \"phases\": 1.5,       | phases",
        "\"phases\": 1,         | \"phases\": true,      | phases",
        "\"margin\": 0.85,      | \"margin\": 1.5,       | margin",
        "\"timezone\": \"Europe/Brussels\" | \"timezone\": \"Mars/Base\" | Mars/Base",
        "\"latitude\": 51.0,    | \"latitude\": 120,     | latitude must be between -90 and 90",
        "\"latitude\": 51.0,    | \"latitude\": null,    | latitude",
        "\"latitude\": 51.0,    | ''                     | latitude",
        "\"longitude\": 5.0,    | \"longitude\": -181,   | longitude must be between -180 and 180",
        "\"longitude\": 5.0,    | \"longitude\": \"east\", | longitude",
        "\"longitude\": 5.0,    | \"longitude\": 5.0, \"location\": {\"latitude\": 1, \"longitude\": 2}, | location",
        "\"targetPct\": 80,     | ''                     | targetPct",
        "\"minimumPct\": 30,    | \"minimumPct\": 90,    | minimumPct",
        "\"minimumPct\": 30,    | \"minimumPct\": -1,    | minimumPct",
        "\"minimumPct\": 30,    | \"minimumPct\": null,  | minimumPct",
        "\"minimumPct\": 30,    | \"minimumPct\": \"thirty\", | minimumPct",
        "\"targetPct\": 80,     | \"targetPct\": 100.5,  | targetPct",
        "\"notifyLeadMinutes\": 15 | \"notifyLeadMinutes\": -5 | notifyLeadMinutes",
        "\"volts\": 230,        | \"volts\": null,       | volts",
        "\"primary\": \"open-meteo\" | \"primary\": \"\"  | primary",
        "\"primary\": \"open-meteo\" | \"primary\": \"Open Meteo\" | primary",
        "\"fallback\": \"forecast-solar\" | \"fallback\": \"x y\" | fallback",
        "\"fallback\": \"forecast-solar\" | \"fallback\": \"open-meteo\" | different",
        "\"open-meteo\": { | \"Open Meteo\": { | not a provider id",
        "\"base_url\": \"\" | \"Base URL\": \"x\" | not a setting name",
        "\"base_url\": \"\" | \"base_url\": 5 | base_url",
        "\"forecast\": {  | \"forecst\": { | 'forecast'",
        "\"fallback\": \"forecast-solar\", | \"fallback\": \"forecast-solar\", \"fallbak\": \"\", | fallbak",
        "\"weekday\": \"THURSDAY\" | \"weekday\": \"FUNDAY\" | FUNDAY",
        "\"weekday\": \"THURSDAY\" | \"weekday\": 3     | weekday",
        "\"start\": \"06:00\"     | \"start\": \"22:00\" | no periods over midnight",
        "\"start\": \"06:00\"     | \"start\": \"19:00\" | no periods over midnight",
        "\"start\": \"06:00\"     | \"start\": \"6am\"   | 6am",
        "\"end\": \"19:00\"       | \"end\": \"24:00\"   | 24:00",
        "', \"end\": \"19:00\"'     | ''                  | end",
        "\"recurring\": [          | \"recuring\": [     | recurring",
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
    void theAwaySectionGivesTheWeeklyAwayPeriods() throws ConfigProblem {
        assertThat(new ConfigFile(EXAMPLE).read().away().recurring())
                .containsExactly(new RecurringAwayPeriod(DayOfWeek.THURSDAY, LocalTime.of(6, 0), LocalTime.of(19, 0)));
    }

    @Test
    void severalWeeklyAwayPeriodsAreReadInTheirOrderAndTheWeekdayInAnyCase() throws IOException, ConfigProblem {
        var file = write(Files.readString(EXAMPLE).replace(
                "{ \"weekday\": \"THURSDAY\", \"start\": \"06:00\", \"end\": \"19:00\" }",
                "{\"weekday\": \"monday\", \"start\": \"07:30\", \"end\": \"17:45\"},"
                        + "{\"weekday\": \"Thursday\", \"start\": \"06:00\", \"end\": \"19:00\"},"
                        + "{\"weekday\": \"SUNDAY\", \"start\": \"00:00\", \"end\": \"12:15\"}"));

        assertThat(new ConfigFile(file).read().away().recurring()).containsExactly(
                new RecurringAwayPeriod(DayOfWeek.MONDAY, LocalTime.of(7, 30), LocalTime.of(17, 45)),
                new RecurringAwayPeriod(DayOfWeek.THURSDAY, LocalTime.of(6, 0), LocalTime.of(19, 0)),
                new RecurringAwayPeriod(DayOfWeek.SUNDAY, LocalTime.of(0, 0), LocalTime.of(12, 15)));
    }

    @Test
    void anEmptyListMeansNoWeeklyAwayPeriods() throws IOException, ConfigProblem {
        var example = Files.readString(EXAMPLE);
        var file = write(example.substring(0, example.indexOf("\"recurring\"")) + "\"recurring\": []}}");
        assertThat(new ConfigFile(file).read().away()).isEqualTo(AwaySchedule.NONE);
    }

    /** A config file from before away periods existed must keep working. */
    @Test
    void aFileWithoutTheAwaySectionHasNoWeeklyAwayPeriods() throws IOException, ConfigProblem {
        var example = Files.readString(EXAMPLE);
        var file = write(example.substring(0, example.indexOf(",\n  \"away\"")) + "\n}");
        assertThat(new ConfigFile(file).read().away()).isEqualTo(AwaySchedule.NONE);
    }

    @Test
    void aFileThatHoldsNoObjectIsRejected() throws IOException {
        var file = write("[]");
        assertThatThrownBy(() -> new ConfigFile(file).read())
                .isInstanceOf(ConfigProblem.class)
                .hasMessageContaining("engine.json");
    }

    @Test
    void theForecastSectionNamesTheProvidersAndTheirPlainSettings() throws ConfigProblem {
        var forecast = new ConfigFile(EXAMPLE).read().forecast();

        var openMeteo = new ProviderId("open-meteo");
        var forecastSolar = new ProviderId("forecast-solar");
        assertThat(forecast).isEqualTo(new ForecastSelection(openMeteo, Optional.of(forecastSolar),
                Map.of(openMeteo, Map.of("base_url", ""))));
        assertThat(forecast.settingsOf(forecastSolar)).isEmpty();
    }

    @Test
    void anEmptyFallbackMeansThereIsNone() throws IOException, ConfigProblem {
        var file = write(Files.readString(EXAMPLE).replace("\"fallback\": \"forecast-solar\"", "\"fallback\": \"\""));
        var forecast = new ConfigFile(file).read().forecast();

        assertThat(forecast.fallback()).isEmpty();
        assertThat(forecast.order()).containsExactly(new ProviderId("open-meteo"));
    }

    /** It may come from a newer version: the forecast service says the provider is not known when it is asked. */
    @Test
    void anUnknownProviderIdIsNotAConfigProblem() throws IOException, ConfigProblem {
        var file = write(Files.readString(EXAMPLE).replace("\"primary\": \"open-meteo\"", "\"primary\": \"solcast\""));
        assertThat(new ConfigFile(file).read().forecast().primary()).isEqualTo(new ProviderId("solcast"));
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

    @Test
    void aWrittenConfigIsReadBackTheSame() throws IOException, ConfigProblem {
        var example = new ConfigFile(EXAMPLE).read();
        var file = new ConfigFile(folder.resolve("new", "engine.json"));

        file.write(example);

        assertThat(file.read()).isEqualTo(example);
        assertThat(folder.resolve("new")).isDirectoryContaining(path -> path.getFileName().toString().equals("engine.json"))
                .isDirectoryNotContaining(path -> path.getFileName().toString().endsWith(".tmp"));
    }

    @Test
    void writingReplacesTheWholeFile() throws IOException, ConfigProblem {
        var example = new ConfigFile(EXAMPLE).read();
        var file = new ConfigFile(write(Files.readString(EXAMPLE)));
        var fewer = new EngineConfig(example.system(),
                new PlanningPreferences(new Percent(20), new Percent(90), 0.7, 0.1, 30, 0),
                new ForecastSelection(new ProviderId("forecast-solar"), Optional.empty(), Map.of()), AwaySchedule.NONE);

        file.write(fewer);

        assertThat(file.read()).isEqualTo(fewer);
    }

    @Test
    void aFileThatCannotBeWrittenIsLeftAsItWas() throws IOException {
        var original = Files.readString(EXAMPLE);
        var path = write(original);
        Files.createDirectory(folder.resolve("engine.json.tmp"));

        assertThatThrownBy(() -> new ConfigFile(path).write(new ConfigFile(EXAMPLE).read()))
                .isInstanceOf(IOException.class);
        assertThat(path).hasContent(original);
    }
}
