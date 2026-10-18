package io.github.enadeag.carchargeplanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The release workflows build every package with the same, exact Java version, which the packages bring along. */
class ReleaseWorkflowTest {

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
}
