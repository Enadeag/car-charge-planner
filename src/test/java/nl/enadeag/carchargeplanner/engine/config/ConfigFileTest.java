package nl.enadeag.carchargeplanner.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.engine.away.WeeklyAwayPeriod;
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.store.StoredJson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

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
        assertThat(config.planning()).isEqualTo(new PlanningPreferences(new Percent(30), new Percent(80), 0.85, 0.2,
                Duration.ofMinutes(45), Duration.ofMinutes(15)));
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
        "\"phases\": 1,         | \"phases\": \"ONE\",     | phases",
        "\"minimumPct\": 30,    | \"minimumPct\": \"\",    | minimumPct",
        "\"name\": \"South roof\" | \"name\": 5          | system.panels[0].name",
        "\"house\": { \"baseloadKw\": 0.5 } | \"house\": 0.5 | system.house: must be a JSON object",
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
    void aNumberInQuotesIsReadAsTheNumber() throws IOException, ConfigProblem {
        var file = write(Files.readString(EXAMPLE).replace("\"minimumPct\": 30", "\"minimumPct\": \"30\"")
                .replace("\"phases\": 1", "\"phases\": \"3\""));

        var config = new ConfigFile(file).read();

        assertThat(config.planning().minimumPct()).isEqualTo(new Percent(30));
        assertThat(config.system().charger().phases()).isEqualTo(Phases.THREE);
    }

    @Test
    void aWrongValueIsNamedByItsWholePlaceInTheFile() throws IOException {
        var file = write(Files.readString(EXAMPLE).replace("\"kwp\": 5.0", "\"kwp\": 0"));

        assertThatThrownBy(() -> new ConfigFile(file).read()).isInstanceOf(ConfigProblem.class)
                .hasMessage("engine.json: system.panels[0]: kwp must be more than 0");
    }

    @Test
    void anUnknownNameIsRefusedWithTheNamesThatAreAllowed() throws IOException {
        var file = write(Files.readString(EXAMPLE).replace("\"kmPerDay\"", "\"kmPerWeek\""));

        assertThatThrownBy(() -> new ConfigFile(file).read()).isInstanceOf(ConfigProblem.class)
                .hasMessage("engine.json: system.car: unknown setting 'kmPerWeek'; allowed are 'usableBatteryKwh', "
                        + "'consumptionKwhPer100km', 'kmPerDay'");
    }

    @Test
    void theAwaySectionGivesTheWeeklyAwayPeriods() throws ConfigProblem {
        assertThat(new ConfigFile(EXAMPLE).read().away().weekly())
                .containsExactly(new WeeklyAwayPeriod(DayOfWeek.THURSDAY, LocalTime.of(6, 0), LocalTime.of(19, 0)));
    }

    @Test
    void severalWeeklyAwayPeriodsAreReadInTheirOrderAndTheWeekdayInAnyCase() throws IOException, ConfigProblem {
        var file = write(Files.readString(EXAMPLE).replace(
                "{ \"weekday\": \"THURSDAY\", \"start\": \"06:00\", \"end\": \"19:00\" }",
                "{\"weekday\": \"monday\", \"start\": \"07:30\", \"end\": \"17:45\"},"
                        + "{\"weekday\": \"Thursday\", \"start\": \"06:00\", \"end\": \"19:00\"},"
                        + "{\"weekday\": \"SUNDAY\", \"start\": \"00:00\", \"end\": \"12:15\"}"));

        assertThat(new ConfigFile(file).read().away().weekly()).containsExactly(
                new WeeklyAwayPeriod(DayOfWeek.MONDAY, LocalTime.of(7, 30), LocalTime.of(17, 45)),
                new WeeklyAwayPeriod(DayOfWeek.THURSDAY, LocalTime.of(6, 0), LocalTime.of(19, 0)),
                new WeeklyAwayPeriod(DayOfWeek.SUNDAY, LocalTime.of(0, 0), LocalTime.of(12, 15)));
    }

    @Test
    void anEmptyListMeansNoWeeklyAwayPeriods() throws IOException, ConfigProblem {
        var example = Files.readString(EXAMPLE);
        var file = write(example.substring(0, example.indexOf("\"recurring\"")) + "\"recurring\": []}}");
        assertThat(new ConfigFile(file).read().away()).isEqualTo(WeeklyAwaySchedule.NONE);
    }

    /** A config file from before away periods existed must keep working. */
    @Test
    void aFileWithoutTheAwaySectionHasNoWeeklyAwayPeriods() throws IOException, ConfigProblem {
        var example = Files.readString(EXAMPLE);
        var file = write(example.substring(0, example.indexOf(",\n  \"away\"")) + "\n}");
        assertThat(new ConfigFile(file).read().away()).isEqualTo(WeeklyAwaySchedule.NONE);
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
                new PlanningPreferences(new Percent(20), new Percent(90), 0.7, 0.1,
                        Duration.ofMinutes(30), Duration.ofMinutes(0)),
                new ForecastSelection(new ProviderId("forecast-solar"), Optional.empty(), Map.of()), WeeklyAwaySchedule.NONE,
                TypicalValues.NONE);

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

    @Test
    void typicalValuesAreWrittenAndReadBack() throws IOException, ConfigProblem {
        var example = new ConfigFile(EXAMPLE).read();
        var typical = new TypicalValues(Set.of(ConfigSetting.CAR_BATTERY, ConfigSetting.CHARGER_PHASES),
                List.of(Set.of(ConfigSetting.PANEL_SIZE, ConfigSetting.PANEL_TILT)));
        var file = new ConfigFile(folder.resolve("engine.json"));

        file.write(example.withSystem(example.system(), typical));

        assertThat(file.read().typical()).isEqualTo(typical);
    }

    @Test
    void aFileWithoutTypicalValuesHasNone() throws ConfigProblem {
        assertThat(new ConfigFile(EXAMPLE).read().typical()).isEqualTo(TypicalValues.NONE);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "{\"system\": [\"MINIMUM_CHARGE\"], \"panels\": []}     | typical.system",
        "{\"system\": [], \"panels\": [[\"CAR_BATTERY\"]]}       | typical.panels",
        "{\"system\": [], \"panels\": [[], []]}                   | more panel arrays",
        "{\"system\": [\"BATTERY\"], \"panels\": []}            | BATTERY",
    })
    void typicalValuesThatCannotBeAreRejected(String typical, String named) throws IOException {
        var example = Files.readString(EXAMPLE);
        var file = write(example.substring(0, example.lastIndexOf('}')) + ", \"typical\": " + typical + "}");

        assertThatThrownBy(() -> new ConfigFile(file).read()).isInstanceOf(ConfigProblem.class)
                .hasMessageContaining(named);
    }

    @Test
    void aWrittenFileHasVersionOneFirst() throws IOException, ConfigProblem {
        var path = folder.resolve("engine.json");
        new ConfigFile(path).write(new ConfigFile(EXAMPLE).read());

        var written = StoredJson.parse(Files.readString(path));
        assertThat(List.copyOf(written.propertyNames())).startsWith("version", "system");
        assertThat(written.path("version").intValue()).isEqualTo(1);
        assertThat(new ConfigFile(path).read()).isEqualTo(new ConfigFile(EXAMPLE).read());
    }

    @Test
    void aFileOfANewerVersionIsRefusedAsWrittenByANewerApp() throws IOException {
        var file = write(Files.readString(EXAMPLE).replaceFirst("\\{", "{\"version\": 2,"));

        assertThatThrownBy(() -> new ConfigFile(file).read()).isInstanceOfSatisfying(ConfigProblem.class,
                problem -> {
                    assertThat(problem.kind()).isEqualTo(ConfigProblem.Kind.INVALID);
                    assertThat(problem.detail()).contains("written by a newer version of the app");
                });
    }

    @Test
    void anUnknownNameIsStillRefusedBesideTheVersion() throws IOException {
        var file = write(Files.readString(EXAMPLE).replaceFirst("\\{", "{\"version\": 1, \"extra\": 1,"));

        assertThatThrownBy(() -> new ConfigFile(file).read()).isInstanceOf(ConfigProblem.class)
                .hasMessageContaining("unknown setting 'extra'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"system\": oops", "{\"version\": 2}", "{\"planning\": {}}"})
    void writingOverAFileThatCannotBeUsedKeepsItAside(String unusable) throws IOException, ConfigProblem {
        var path = write(unusable);
        var config = new ConfigFile(EXAMPLE).read();

        new ConfigFile(path).write(config);

        assertThat(new ConfigFile(path).read()).isEqualTo(config);
        try (var kept = Files.list(folder)) {
            assertThat(kept.filter(file -> file.getFileName().toString().startsWith("engine.json.damaged-")))
                    .singleElement().satisfies(file -> assertThat(file).hasContent(unusable));
        }
    }

    @Test
    void aChangedFileIsReadAgainByTheSameReader() throws IOException, ConfigProblem {
        var path = write(Files.readString(EXAMPLE));
        var file = new ConfigFile(path);
        assertThat(file.read().planning().margin()).isEqualTo(0.85);

        write(Files.readString(EXAMPLE).replace("\"margin\": 0.85", "\"margin\": 0.5"));

        assertThat(file.read().planning().margin()).isEqualTo(0.5);
    }

    @Test
    void aWrongValueInTheSecondPanelArrayIsNamedByItsPlace() throws IOException {
        var example = Files.readString(EXAMPLE);
        var roof = example.substring(example.indexOf("{", example.indexOf("\"panels\"")),
                example.indexOf("}", example.indexOf("\"panels\"")) + 1);
        var file = write(example.replace(roof, roof + ", " + roof.replace("\"tiltDeg\": 35", "\"tiltDeg\": 91")));

        assertThatThrownBy(() -> new ConfigFile(file).read()).isInstanceOf(ConfigProblem.class)
                .hasMessage("engine.json: system.panels[1]: tiltDeg must be between 0 and 90");
    }

    @Test
    void aWrongTypicalValueIsNamedByItsPlaceInItsList() throws IOException {
        var example = Files.readString(EXAMPLE);
        var file = write(example.substring(0, example.lastIndexOf('}'))
                + ", \"typical\": {\"system\": [], \"panels\": [[\"PANEL_SIZE\", 5]]}}");

        assertThatThrownBy(() -> new ConfigFile(file).read()).isInstanceOf(ConfigProblem.class)
                .hasMessageContaining("typical.panels[0][1]");
    }

    @Test
    void typicalValuesAreInTheFileForTheNextReader() throws IOException, ConfigProblem {
        var example = new ConfigFile(EXAMPLE).read();
        var typical = new TypicalValues(Set.of(ConfigSetting.CHARGER_PHASES, ConfigSetting.CAR_BATTERY),
                List.of(Set.of(ConfigSetting.PANEL_TILT, ConfigSetting.PANEL_SIZE)));
        var path = folder.resolve("engine.json");

        new ConfigFile(path).write(example.withSystem(example.system(), typical));

        var written = StoredJson.parse(Files.readString(path)).path("typical");
        assertThat(written.path("system").values()).extracting(node -> node.stringValue())
                .containsExactly("CAR_BATTERY", "CHARGER_PHASES");
        assertThat(written.path("panels").get(0).values()).extracting(node -> node.stringValue())
                .containsExactly("PANEL_SIZE", "PANEL_TILT");
        assertThat(new ConfigFile(path).read().typical()).isEqualTo(typical);
    }

    @Test
    void theProvidersSettingsAreWrittenInTheOrderOfTheirIds() throws IOException, ConfigProblem {
        var example = new ConfigFile(EXAMPLE).read();
        var settings = new LinkedHashMap<ProviderId, Map<String, String>>();
        for (var id : List.of("zz-last", "open-meteo", "aa-first", "forecast-solar")) {
            settings.put(new ProviderId(id), Map.of("note", id));
        }
        var path = folder.resolve("engine.json");

        new ConfigFile(path).write(example.withForecast(new ForecastSelection(new ProviderId("open-meteo"),
                Optional.empty(), settings)));

        var written = StoredJson.parse(Files.readString(path)).path("forecast").path("settings");
        assertThat(List.copyOf(written.propertyNames())).containsExactly("aa-first", "forecast-solar", "open-meteo", "zz-last");
    }
}
