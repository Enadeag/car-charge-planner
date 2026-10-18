package io.github.enadeag.carchargeplanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** What the packaging scripts put in the packages: the licence files and the JavaFX the runtime is linked from. */
class PackagingTest {

    private static final Path LEGAL = Path.of("src", "packaging", "legal");
    private static final Pattern RESOLVED = Pattern.compile("^\\s+([^:\\s]+:[^:\\s]+):", Pattern.MULTILINE);
    private static final Pattern NAMED = Pattern.compile("^Artifact: (\\S+)$", Pattern.MULTILINE);
    private static final Pattern JAVAFX_VERSION = Pattern.compile("<javafx\\.version>([^<]+)</javafx\\.version>");
    private static final Pattern JMODS_FOLDER = Pattern.compile("gluonhq\\.com/openjfx/([^/]+)/");
    private static final Pattern JMODS_FILE = Pattern.compile("openjfx-([^_]+)_[a-z0-9-]+_bin-jmods\\.zip");

    @Test
    void theLegalFilesNameExactlyTheRuntimeLibraries() throws IOException {
        // written by dependency:list in the build (pom.xml, list-runtime-libraries) before the tests run
        var runtime = groups(Files.readString(Path.of("target", "runtime-libraries.txt")), RESOLVED);
        var named = new HashSet<String>();
        try (var files = Files.newDirectoryStream(LEGAL)) {
            for (var file : files) {
                named.addAll(groups(Files.readString(file), NAMED));
            }
        }

        assertThat(named).as("the libraries named by an 'Artifact:' line in %s", LEGAL)
                .containsExactlyInAnyOrderElementsOf(runtime);
    }

    @ParameterizedTest
    @ValueSource(strings = {"tools/package-linux.sh", "tools/package-windows.ps1"})
    void linksTheJavaFxVersionThePomBuildsAgainst(String script) throws IOException {
        var pom = JAVAFX_VERSION.matcher(Files.readString(Path.of("pom.xml"))).results().findFirst().orElseThrow();
        var text = Files.readString(Path.of(script));

        assertThat(groups(text, JMODS_FOLDER)).containsExactly(pom.group(1));
        assertThat(groups(text, JMODS_FILE)).containsExactly(pom.group(1));
    }

    private static Set<String> groups(String text, Pattern pattern) {
        return pattern.matcher(text).results().map(match -> match.group(1)).collect(Collectors.toSet());
    }
}
