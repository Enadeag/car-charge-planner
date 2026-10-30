package nl.enadeag.carchargeplanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The release workflows build every package with the same, exact Java version, which the packages bring along, and
 * take the release's version from the parent pom, where the one version of every module is set.
 */
class ReleaseWorkflowTest {

    private static final Pattern READ_VERSION =
            Pattern.compile("^.*help:evaluate.*-Dexpression=project\\.version.*$", Pattern.MULTILINE);
    private static final Pattern JAVA_VERSION =
            Pattern.compile("^\\s+java-version:\\s*\"?([^\"\\s]+)\"?", Pattern.MULTILINE);

    @Test
    void everyReleaseBuildUsesTheSameExactJavaVersion() throws IOException {
        var versions = new ArrayList<String>();
        for (var workflow : List.of("release.yml", "windows-installer.yml")) {
            var text = Files.readString(Path.of(".github", "workflows", workflow));
            JAVA_VERSION.matcher(text).results().forEach(match -> versions.add(match.group(1)));
        }

        assertThat(versions).isNotEmpty().allMatch(version -> version.matches("\\d+(\\.\\d+){2,}\\+\\d+"),
                "an exact version with its build number, such as 25.0.4.1+1");
        assertThat(versions).containsOnly(versions.getFirst());
    }

    @Test
    void theVersionIsReadFromTheParentAlone() throws IOException {
        for (var file : List.of(Path.of(".github", "workflows", "release.yml"), Path.of("tools", "package-linux.sh"),
                Path.of("tools", "package-windows.ps1"))) {
            var reads = READ_VERSION.matcher(Files.readString(file)).results().map(match -> match.group()).toList();

            assertThat(reads).as("where %s reads the version", file).isNotEmpty()
                    .allMatch(read -> read.contains(" -N "), "with -N, so only the parent pom is read");
        }
    }
}
