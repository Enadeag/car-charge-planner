package nl.enadeag.carchargeplanner;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.host.AppName;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The Flatpak's files in {@code src/packaging/flatpak/} (ADR 0008): the sandbox asks for no more than the app uses,
 * one app ID throughout, the latest release in the AppStream metadata, and the same downloads as the other packages.
 */
class FlatpakPackagingTest {

    private static final Path FLATPAK = Path.of("src", "packaging", "flatpak");
    private static final Path MANIFEST = FLATPAK.resolve(AppName.FLATPAK_ID + ".yml");
    private static final Path METAINFO = FLATPAK.resolve(AppName.FLATPAK_ID + ".metainfo.xml");
    private static final Path DESKTOP = FLATPAK.resolve(AppName.FLATPAK_ID + ".desktop");
    private static final Pattern FINISH_ARG = Pattern.compile("^  - (--\\S+)$", Pattern.MULTILINE);
    private static final Pattern RELEASE = Pattern.compile("^## \\[(\\d+\\.\\d+\\.\\d+)] - (\\d{4}-\\d{2}-\\d{2})$",
            Pattern.MULTILINE);
    private static final Pattern SKIPPED = Pattern.compile("\\G          - (\\S+)\\n");
    private static final Pattern MODULE = Pattern.compile("<module>([^<]+)</module>");
    private static final Pattern METAINFO_RELEASE =
            Pattern.compile("<release version=\"([^\"]+)\" date=\"([^\"]+)\"");

    private static String read(Path file) throws IOException {
        return Files.readString(file);
    }

    /** The manifest's lines from {@code finish-args:} up to the modules. */
    private static String finishArgs() throws IOException {
        var manifest = read(MANIFEST);
        return manifest.substring(manifest.indexOf("\nfinish-args:\n"), manifest.indexOf("\nmodules:\n"));
    }

    @Test
    void theSandboxAsksForWhatTheAppUsesAndNoMore() throws IOException {
        // each one is explained in the manifest; a new one needs its reason there, and in ADR 0008
        assertThat(FINISH_ARG.matcher(finishArgs()).results().map(match -> match.group(1)).toList())
                .containsExactlyInAnyOrder("--socket=x11", "--share=ipc", "--device=dri", "--share=network",
                        "--talk-name=org.kde.StatusNotifierWatcher", "--talk-name=org.freedesktop.Notifications",
                        "--talk-name=org.freedesktop.secrets");
    }

    @Test
    void everyFileNamesTheSameAppId() throws IOException {
        var metainfo = read(METAINFO);
        var desktop = read(DESKTOP);

        assertThat(read(MANIFEST)).contains("\nid: " + AppName.FLATPAK_ID + "\n")
                .contains("\ncommand: " + AppName.ID + "\n");
        assertThat(metainfo).contains("<id>" + AppName.FLATPAK_ID + "</id>",
                "<launchable type=\"desktop-id\">" + AppName.FLATPAK_ID + ".desktop</launchable>",
                "<binary>" + AppName.ID + "</binary>");
        assertThat(desktop).contains("\nIcon=" + AppName.FLATPAK_ID + "\n", "\nExec=" + AppName.ID + "\n");
        try (var files = Files.list(FLATPAK)) {
            assertThat(files.map(file -> file.getFileName().toString())
                    .filter(name -> name.startsWith("io.") || name.startsWith("nl."))).containsExactlyInAnyOrder(
                    MANIFEST.getFileName().toString(), METAINFO.getFileName().toString(),
                    DESKTOP.getFileName().toString());
        }
    }

    @Test
    void theNewestReleaseInTheMetadataIsTheChangelogsLatest() throws IOException {
        var changelog = RELEASE.matcher(read(Path.of("CHANGELOG.md"))).results().findFirst().orElseThrow();
        var metainfo = METAINFO_RELEASE.matcher(read(METAINFO)).results().findFirst().orElseThrow();

        assertThat(List.of(metainfo.group(1), metainfo.group(2)))
                .as("the first <release> in %s, as the latest section of CHANGELOG.md", METAINFO)
                .containsExactly(changelog.group(1), changelog.group(2));
    }

    @Test
    void itBuildsWithTheMavenOfTheWrapperAndTheJavaFxOfTheOtherPackages() throws IOException {
        var manifest = read(MANIFEST);
        var wrapper = read(Path.of(".mvn", "wrapper", "maven-wrapper.properties"));
        var linux = read(Path.of("tools", "package-linux.sh"));

        assertThat(manifest).contains("url: " + value(wrapper, "distributionUrl=(\\S+)"))
                .contains("sha256: " + value(wrapper, "distributionSha256Sum=(\\S+)"));
        assertThat(manifest).contains("url: https://download2.gluonhq.com/openjfx/"
                + value(linux, "gluonhq\\.com/openjfx/(\\S+?)\\$jmods") + value(linux, "\\njmods=(\\S+)"));
        assertThat(manifest).contains("sha256: " + value(linux, "echo \"([0-9a-f]{64})  target/\\$jmods\""));
    }

    @Test
    void theMavenSourcesComeFromMavenCentralWithTheirChecksum() throws IOException {
        var sources = read(FLATPAK.resolve("maven-sources.json"));

        assertThat(Pattern.compile("\"url\": \"https://repo\\.maven\\.apache\\.org/maven2/").matcher(sources)
                .results().count())
                .isPositive()
                .isEqualTo(Pattern.compile("\"url\":").matcher(sources).results().count())
                .isEqualTo(Pattern.compile("\"sha256\": \"[0-9a-f]{64}\"").matcher(sources).results().count());
        assertThat(sources).contains("/org/openjfx/javafx-controls/", "/com/github/hypfvieh/dbus-java-core/",
                "/org/apache/maven/plugins/maven-compiler-plugin/");
    }

    @Test
    void theSourceFolderLeavesOutEveryBuildsOutput() throws IOException {
        var manifest = read(MANIFEST);
        var skip = "        skip:\n";
        var skipped = SKIPPED.matcher(manifest.substring(manifest.indexOf(skip) + skip.length())).results()
                .map(match -> match.group(1)).toList();
        var modules = MODULE.matcher(read(Path.of("pom.xml"))).results().map(match -> match.group(1) + "/target");

        assertThat(skipped).as("the skip list of the manifest's own folder")
                .contains("target", "android").containsAll(modules.toList());
    }

    private static String value(String text, String pattern) {
        return Pattern.compile(pattern).matcher(text).results().findFirst().orElseThrow().group(1);
    }
}
