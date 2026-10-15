package io.github.enadeag.carchargeplanner.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AppSettingsFileTest {

    @TempDir
    Path folder;

    private AppSettings read(String content) throws IOException {
        var file = Files.writeString(folder.resolve("app.json"), content);
        return new AppSettingsFile(file).read();
    }

    @Test
    void aChosenLanguageIsKept() throws IOException {
        assertThat(read("{\"language\": \"nl\"}").language()).contains(Language.DUTCH);
    }

    @Test
    void withoutAFileTheAppFollowsTheSystem() {
        assertThat(new AppSettingsFile(folder.resolve("app.json")).read()).isEqualTo(AppSettings.SYSTEM);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"language\": \"system\"}", "{}", "{\"language\": \"tlh\"}", "not json"})
    void aFileThatChoosesNoLanguageTheAppHasFollowsTheSystem(String content) throws IOException {
        assertThat(read(content)).isEqualTo(AppSettings.SYSTEM);
    }
}
