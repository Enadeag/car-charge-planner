package nl.enadeag.carchargeplanner.desktop.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.engine.store.StoredJson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AppSettingsFileTest {

    private static final AppSettings FOLLOWING = AppSettings.following(Units.IMPERIAL);

    @TempDir
    Path folder;

    private AppSettingsFile file() {
        return new AppSettingsFile(folder.resolve("app.json"), Units.IMPERIAL);
    }

    private AppSettings read(String content) throws IOException {
        Files.writeString(folder.resolve("app.json"), content);
        return file().read();
    }

    @Test
    void chosenSettingsAreKept() throws IOException {
        assertThat(read("{\"language\": \"nl\", \"units\": \"metric\", \"mode\": \"window\"}"))
                .isEqualTo(new AppSettings(Optional.of(Language.DUTCH), Units.METRIC, DisplayMode.WINDOW, Appearance.SYSTEM));
    }

    @Test
    void withoutAFileTheAppFollowsTheSystem() {
        assertThat(file().read()).isEqualTo(FOLLOWING);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"language\": \"system\"}", "{}", "{\"language\": \"tlh\"}", "not json",
        "{\"units\": \"furlongs\", \"mode\": \"hologram\"}", "{\"units\": 3}", "{\"appearance\": \"sepia\"}",
        "{\"appearance\": \"system\"}", "{\"version\": 2, \"language\": \"nl\"}"})
    void aFileThatChoosesNothingTheAppHasFollowsTheSystem(String content) throws IOException {
        assertThat(read(content)).isEqualTo(FOLLOWING);
    }

    @Test
    void oneUnknownValueLeavesTheOthersAsChosen() throws IOException {
        assertThat(read("{\"language\": \"tlh\", \"units\": \"metric\", \"mode\": \"tray\"}"))
                .isEqualTo(new AppSettings(Optional.empty(), Units.METRIC, DisplayMode.TRAY, Appearance.SYSTEM));
    }

    @Test
    void anAppearanceChosenIsKept() throws IOException {
        assertThat(read("{\"appearance\": \"light\"}").appearance()).isEqualTo(Appearance.LIGHT);
    }

    @Test
    void writtenSettingsAreReadBack() throws IOException {
        var chosen = new AppSettings(Optional.of(Language.ENGLISH), Units.METRIC, DisplayMode.WINDOW, Appearance.DARK);
        var file = new AppSettingsFile(folder.resolve("new", "app.json"), Units.IMPERIAL);

        file.write(chosen);

        assertThat(file.read()).isEqualTo(chosen);
    }

    @Test
    void followingTheSystemsLanguageIsReadBackAsSuch() throws IOException {
        file().write(new AppSettings(Optional.empty(), Units.METRIC, DisplayMode.TRAY, Appearance.SYSTEM));

        assertThat(new AppSettingsFile(folder.resolve("app.json"), Units.METRIC).read().language()).isEmpty();
    }

    @Test
    void aFileThatCannotBeWrittenIsLeftAsItWas() throws IOException {
        Files.writeString(folder.resolve("app.json"), "{\"language\": \"nl\"}");
        Files.createDirectory(folder.resolve("app.json.tmp"));

        assertThatThrownBy(() -> file().write(FOLLOWING)).isInstanceOf(IOException.class);
        assertThat(file().read().language()).contains(Language.DUTCH);
    }

    @Test
    void theCountryDecidesTheUnitsOfAnOwnerWhoDidNotChoose() {
        assertThat(Units.ofCountry("GB")).isEqualTo(Units.IMPERIAL);
        assertThat(Units.ofCountry("NL")).isEqualTo(Units.METRIC);
        assertThat(Units.ofCountry("")).isEqualTo(Units.METRIC);
    }

    @Test
    void aWrittenFileHasVersionOneAndAFileWithoutOneIsReadAsBefore() throws IOException {
        assertThat(read("{\"language\": \"nl\"}").language()).contains(Language.DUTCH);

        file().write(FOLLOWING);

        assertThat(StoredJson.parse(Files.readString(folder.resolve("app.json"))).path("version").intValue())
                .isEqualTo(1);
    }

    @Test
    void aFileOfANewerVersionIsKeptAsideWhenTheSettingsAreSaved() throws IOException {
        var newer = "{\"version\": 2, \"language\": \"nl\"}";
        Files.writeString(folder.resolve("app.json"), newer);

        file().write(FOLLOWING);

        assertThat(file().read()).isEqualTo(FOLLOWING);
        try (var kept = Files.list(folder)) {
            assertThat(kept.filter(path -> path.getFileName().toString().startsWith("app.json.damaged-")))
                    .singleElement().satisfies(path -> assertThat(path).hasContent(newer));
        }
    }
}
