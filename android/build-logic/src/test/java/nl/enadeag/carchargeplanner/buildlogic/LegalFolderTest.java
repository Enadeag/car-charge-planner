package nl.enadeag.carchargeplanner.buildlogic;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.api.GradleException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LegalFolderTest {

    @TempDir
    Path dir;

    private Path legal() throws IOException {
        return Files.createDirectories(dir.resolve("legal"));
    }

    @Test
    void theTextsComeByNameAsUtf8WithUnixLineEndings() throws IOException {
        Files.writeString(legal().resolve("slf4j.txt"), "SLF4J\r\nArtifact: org.slf4j:slf4j-api\r\n", UTF_8);
        Files.writeString(legal().resolve("jackson.txt"), "Jackson\nCopyright © 2023 Someone\n", UTF_8);
        Files.writeString(legal().resolve("old-mac.txt"), "Old\rMac\r", UTF_8);

        assertThat(LegalFolder.texts(legal())).containsExactly(
                entry("jackson.txt", "Jackson\nCopyright © 2023 Someone\n"),
                entry("old-mac.txt", "Old\nMac\n"),
                entry("slf4j.txt", "SLF4J\nArtifact: org.slf4j:slf4j-api\n"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"notes.md", "image.png", "LICENCE", "apache.txt.bak", "more.txt", "link.txt"})
    void anythingButAPlainTxtFileIsRefusedAsTheApkWouldCarryIt(String name) throws IOException {
        Files.writeString(legal().resolve("apache-2.0.txt"), "Apache License 2.0\n", UTF_8);
        var file = legal().resolve(name);
        switch (name) {
            case "more.txt" -> Files.createDirectory(file);
            case "link.txt" -> {
                var outside = Files.writeString(dir.resolve("outside.txt"), "Outside\n", UTF_8);
                try {
                    Files.createSymbolicLink(file, outside);
                } catch (IOException | UnsupportedOperationException e) {
                    Assumptions.abort("this system makes no symbolic links: " + e);
                }
            }
            default -> Files.writeString(file, "Not a licence text\n", UTF_8);
        }

        assertThatThrownBy(() -> LegalFolder.texts(legal())).isInstanceOf(GradleException.class)
                .hasMessageContaining("android/legal/" + name + " is no plain .txt file: remove it");
    }

    @Test
    void everyEntryThatIsRefusedIsNamedInNameOrderWithItsFix() throws IOException {
        Files.writeString(legal().resolve("apache-2.0.txt"), "Apache License 2.0\n", UTF_8);
        Files.writeString(legal().resolve("zzz.md"), "Notes\n", UTF_8);
        Files.writeString(legal().resolve("image.png"), "PNG\n", UTF_8);
        Files.writeString(legal().resolve("latin1.txt"), "Caf\u00e9", StandardCharsets.ISO_8859_1);

        assertThatThrownBy(() -> LegalFolder.texts(legal())).isInstanceOf(GradleException.class)
                .hasMessageContainingAll("android/legal/image.png is no plain .txt file: remove it",
                        "android/legal/latin1.txt is no UTF-8 text: save it as UTF-8",
                        "android/legal/zzz.md is no plain .txt file: remove it")
                .message().satisfies(message -> assertThat(message.indexOf("image.png"))
                        .isLessThan(message.indexOf("latin1.txt")).isLessThan(message.indexOf("zzz.md")));
    }
}
