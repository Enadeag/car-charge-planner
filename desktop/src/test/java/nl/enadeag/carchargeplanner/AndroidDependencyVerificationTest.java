package nl.enadeag.carchargeplanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Gradle's dependency verification stays strict, its default, in every workflow and every {@code gradle.properties}
 * of the Android build (#484): none names {@code --dependency-verification}, whose only use is to weaken it, or the
 * words {@code lenient} and {@code off} outside a comment, nor writes the checksums in place of checking them. Here,
 * with the Maven build, so every pull request that changes a workflow runs it.
 */
class AndroidDependencyVerificationTest {

    private static final Pattern WEAKER = Pattern.compile("(?i)\\b(lenient|off)\\b");
    private static final List<String> FLAGS = List.of("--dependency-verification", "--write-verification-metadata");

    @Test
    void theFilesAreTheWorkflowsAndTheAndroidBuildsProperties() throws IOException {
        assertThat(files()).contains(Path.of(".github", "workflows", "android.yml"),
                Path.of(".github", "workflows", "android-release.yml"), Path.of(".github", "workflows", "mutation.yml"),
                Path.of("android", "gradle.properties"));
    }

    @Test
    void noWorkflowAndNoPropertiesFileWeakensTheVerification() throws IOException {
        for (var file : files()) {
            var text = Files.readString(file);

            assertThat(text).as(file.toString()).doesNotContain(FLAGS);
            assertThat(withoutComments(text).lines().filter(line -> WEAKER.matcher(line).find()))
                    .as("lines of %s that name lenient or off outside a comment, as a weaker verification mode", file)
                    .isEmpty();
        }
    }

    @Test
    void aWeakerModeIsFoundInEveryFormButNotInAComment() {
        for (var weaker : List.of("org.gradle.dependency.verification=lenient",
                "run: ./gradlew -Dorg.gradle.dependency.verification=off check", "  MODE: \"Off\"")) {
            assertThat(withoutComments(weaker).lines().filter(line -> WEAKER.matcher(line).find())).as(weaker)
                    .hasSize(1);
        }
        assertThat(withoutComments("# the cache is off for a release\n  # lenient\nkey=value  # off")
                .lines().filter(line -> WEAKER.matcher(line).find())).isEmpty();
    }

    /** Every workflow, and every {@code gradle.properties} of the Android build and its included builds. */
    private static List<Path> files() throws IOException {
        var files = new ArrayList<Path>();
        try (Stream<Path> workflows = Files.list(Path.of(".github", "workflows"))) {
            workflows.filter(file -> file.toString().endsWith(".yml") || file.toString().endsWith(".yaml"))
                    .sorted().forEach(files::add);
        }
        // android/ and the folders of its projects and included builds
        try (Stream<Path> android = Files.find(Path.of("android"), 2,
                (file, attributes) -> file.getFileName().toString().equals("gradle.properties"))) {
            android.sorted().forEach(files::add);
        }
        return files;
    }

    /** {@code text} without its comments: from a {@code #} at the start of a line or after a space to the line's end. */
    private static String withoutComments(String text) {
        return text.lines().map(line -> line.replaceAll("(^|\\s)#.*$", "")).reduce("", (a, b) -> a + b + "\n");
    }
}
