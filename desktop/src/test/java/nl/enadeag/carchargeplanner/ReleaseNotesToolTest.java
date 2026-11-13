package nl.enadeag.carchargeplanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Runs {@code tools/ReleaseNotes.java} the way the release workflow does: as a source file, in a JDK of its own. */
class ReleaseNotesToolTest {

    private static final String CHANGELOG = """
            # Changelog

            ## [Unreleased]

            ### Added
            - Something new.

            ## [0.3.0] - 2026-10-20

            The Java version.

            ### Changed
            - Everything.

            ## [0.3.0-rc1] - 2026-10-18

            A candidate.

            ## [0.2.0] - 2026-10-14

            ### Added
            - Windows.

            [0.2.0]: https://example.org/v0.2.0
            """;

    private static final String WITH_ANDROID = """
            # Changelog

            ## [Unreleased]

            ### Android
            - Not released yet.

            ## [Android 0.1.0] - 2026-11-20

            The first Android release.

            ## [0.7.0] - 2026-11-18

            - The desktop.
            """;

    private static final String FINGERPRINT = "0123456789abcdef".repeat(4);

    @TempDir
    Path folder;

    private record Run(int exitCode, String out) {
    }

    @Test
    void printsTheVersionsSectionAndHowToInstallItsFiles() throws Exception {
        var run = releaseNotes("0.3.0", CHANGELOG);

        assertThat(run.exitCode()).isZero();
        assertThat(run.out()).startsWith("The Java version.\n\n### Changed\n- Everything.\n\n### Installing\n");
        assertThat(run.out()).doesNotContain("Something new", "A candidate", "Windows.\n")
                .contains("`car-charge-planner-0.3.0.exe`", "`car-charge-planner-0.3.0-linux.tar.gz`",
                        "`sudo apt install ./car-charge-planner_0.3.0_amd64.deb`",
                        "`sudo dnf install ./car-charge-planner-0.3.0-1.x86_64.rpm`",
                        "`sha256sum --ignore-missing -c SHA256SUMS`",
                        "`car-charge-planner-0.3.0-linux.cdx.json`", "`car-charge-planner-0.3.0-windows.cdx.json`",
                        "/blob/v0.3.0/CHANGELOG.md", "/blob/v0.3.0/docs/install.md");
    }

    @Test
    void namesTheJavaRuntimeOfTheJdkThatRunsIt() throws Exception {
        var run = releaseNotes("0.3.0", CHANGELOG);

        assertThat(run.out()).contains("Java " + Runtime.version() + " (" + System.getProperty("java.vendor") + ")");
    }

    @Test
    void theLastSectionEndsAtTheLinkDefinitions() throws Exception {
        var run = releaseNotes("0.2.0", CHANGELOG);

        assertThat(run.exitCode()).isZero();
        assertThat(run.out()).startsWith("### Added\n- Windows.\n\n### Installing\n").doesNotContain("example.org");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.4.0", "0.3", "0x3x0"})
    void aVersionWithoutASectionHasNoNotes(String version) throws Exception {
        var run = releaseNotes(version, CHANGELOG);

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.out()).isEmpty();
    }

    @Test
    void anEmptySectionHasNoNotes() throws Exception {
        var run = releaseNotes("0.3.0", "## [0.3.0] - 2026-10-20\n\n## [0.2.0] - 2026-10-14\n\n- Windows.\n");

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.out()).isEmpty();
    }

    @Test
    void androidPrintsItsOwnSectionAndHowToInstallAndCheckTheApk() throws Exception {
        var run = notes(WITH_ANDROID, "--android", "0.1.0", FINGERPRINT);

        assertThat(run.exitCode()).isZero();
        assertThat(run.out()).startsWith("The first Android release.\n\n### Installing\n");
        assertThat(run.out()).contains("Android 10 or newer", "`car-charge-planner-android-0.1.0.apk`",
                        "`sha256sum --ignore-missing -c SHA256SUMS`", "`" + FINGERPRINT + "`",
                        "`apksigner verify --print-certs car-charge-planner-android-0.1.0.apk`",
                        "tag `android-v0.1.0`",
                        "`gh attestation verify car-charge-planner-android-0.1.0.apk -R Enadeag/car-charge-planner`",
                        "/blob/android-v0.1.0/CHANGELOG.md")
                .doesNotContain("Not released yet", "The desktop", ".exe", "Java runtime", "/blob/v0.1.0/");
    }

    @Test
    void androidAndTheDesktopNeverTakeEachOthersSection() throws Exception {
        assertThat(notes(WITH_ANDROID, "0.1.0").exitCode()).isEqualTo(1);
        assertThat(notes(WITH_ANDROID, "--android", "0.7.0", FINGERPRINT).exitCode()).isEqualTo(1);
        assertThat(notes(WITH_ANDROID, "0.7.0").out()).startsWith("- The desktop.\n\n### Installing\n")
                .doesNotContain("Android release");
    }

    @Test
    void androidNeedsTheFingerprint() throws Exception {
        var run = notes(WITH_ANDROID, "--android", "0.1.0");

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.out()).isEmpty();
    }

    private Run releaseNotes(String version, String changelog) throws IOException, InterruptedException {
        return notes(changelog, version);
    }

    /** Runs the tool with {@code args}, the changelog's path put in after the version. */
    private Run notes(String changelog, String... args) throws IOException, InterruptedException {
        var file = Files.writeString(folder.resolve("CHANGELOG.md"), changelog, StandardCharsets.UTF_8);
        var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var command = new ArrayList<>(List.of(java, Path.of("tools", "ReleaseNotes.java").toString()));
        var android = args[0].equals("--android");
        for (var i = 0; i < args.length; i++) {
            command.add(args[i]);
            if (i == (android ? 1 : 0)) {
                command.add(file.toString());
            }
        }
        var process = new ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        var out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        return new Run(process.exitValue(), out);
    }
}
