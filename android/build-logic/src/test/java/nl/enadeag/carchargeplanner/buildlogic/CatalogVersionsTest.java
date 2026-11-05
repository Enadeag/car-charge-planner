package nl.enadeag.carchargeplanner.buildlogic;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

/** The catalog of the Android build: what the poms give it must not also be written in its file. */
class CatalogVersionsTest {

    private static final Path CATALOG = Path.of("..", "gradle", "libs.versions.toml");

    @Test
    void theCatalogFileGivesNoVersionThatThePomsGive() throws IOException {
        var keys = new ArrayList<String>();
        boolean inVersions = false;
        for (var line : Files.readAllLines(CATALOG)) {
            var trimmed = line.strip();
            if (trimmed.startsWith("[")) {
                inVersions = trimmed.equals("[versions]");
            } else if (inVersions && trimmed.contains("=")) {
                keys.add(trimmed.substring(0, trimmed.indexOf('=')).strip());
            }
        }

        assertThat(keys).isNotEmpty().doesNotContainAnyElementsOf(MavenVersions.versionNames());
    }

    @Test
    void theVersionsThePomsGiveAreThoseOfTheLibrariesAndOfPit() {
        assertThat(MavenVersions.versionNames()).containsExactlyInAnyOrder("jackson", "slf4j", "junit", "assertj",
                "archunit", "pitest", "pitest-junit5");
    }
}
