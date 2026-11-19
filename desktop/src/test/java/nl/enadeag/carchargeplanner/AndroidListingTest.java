package nl.enadeag.carchargeplanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The Android app's listing for F-Droid, {@code fastlane/metadata/android/} (#486), keeps F-Droid's limits, and each
 * Android release brings its changelog in every language. A tripwire in the Maven build, so every pull request runs
 * it; F-Droid's own checks come only when its recipe runs (#280).
 */
class AndroidListingTest {

    private static final Path LISTING = Path.of("fastlane", "metadata", "android");
    private static final Path BUILD = Path.of("android", "app", "build.gradle.kts");

    /** Each text file of a language, with F-Droid's limit in characters, without the final newline. */
    private static final Map<String, Integer> TEXTS =
            Map.of("title.txt", 50, "short_description.txt", 80, "full_description.txt", 4000);
    private static final int CHANGELOG_LIMIT = 500;

    private static final Pattern CHANGELOG = Pattern.compile("[1-9][0-9]*\\.txt");
    private static final Pattern VERSION_NAME =
            Pattern.compile("^\\s*versionName = \"(\\d+)\\.(\\d+)\\.(\\d+)(-SNAPSHOT)?\"$", Pattern.MULTILINE);

    @Test
    void theListingIsInEnglishAndDutch() throws IOException {
        assertThat(languages()).containsExactly(LISTING.resolve("en-US"), LISTING.resolve("nl-NL"));
    }

    @Test
    void eachLanguageHasItsTextsWithinFdroidsLimits() throws IOException {
        for (var language : languages()) {
            for (var text : TEXTS.entrySet()) {
                assertWithin(language.resolve(text.getKey()), text.getValue());
            }
        }
    }

    @Test
    void eachChangelogIsNamedByAVersionCodeAndWithinFdroidsLimit() throws IOException {
        for (var language : languages()) {
            for (var changelog : files(language.resolve("changelogs"))) {
                assertThat(changelog.getFileName().toString()).as(changelog.toString()).matches(CHANGELOG);
                assertWithin(changelog, CHANGELOG_LIMIT);
            }
        }
    }

    @Test
    void everyLanguageHasTheSameChangelogs() throws IOException {
        var english = names(LISTING.resolve("en-US").resolve("changelogs"));

        assertThat(english).isNotEmpty();
        for (var language : languages()) {
            assertThat(names(language.resolve("changelogs"))).as(language.toString()).isEqualTo(english);
        }
    }

    @Test
    void noChangelogIsForAVersionAfterTheAppsAndAReleaseHasItsOwn() throws IOException {
        var version = VERSION_NAME.matcher(Files.readString(BUILD));
        assertThat(version.find()).as("versionName in " + BUILD).isTrue();
        // as AppVersion in android/build-logic works it out; -SNAPSHOT has the code of its base version
        var code = Integer.parseInt(version.group(1)) * 10_000 + Integer.parseInt(version.group(2)) * 100
                + Integer.parseInt(version.group(3));
        var release = version.group(4) == null;
        assertThat(version.find()).as("a second versionName in " + BUILD).isFalse();

        for (var language : languages()) {
            var changelogs = files(language.resolve("changelogs"));
            assertThat(changelogs).as(language.toString())
                    .allSatisfy(changelog -> assertThat(codeOf(changelog)).as(changelog.toString())
                            .isLessThanOrEqualTo(code));
            if (release) {
                assertThat(language.resolve("changelogs").resolve(code + ".txt"))
                        .as("the release commit of versionCode " + code + " brings its changelog").isRegularFile();
            }
        }
    }

    @Test
    void theFeatureGraphicIsAPngOf1024By500() throws IOException {
        for (var language : languages()) {
            assertPng(language.resolve("images").resolve("featureGraphic.png"), 1024, 500);
        }
    }

    /** tools/android-listing-pictures.sh writes them; F-Droid shows them in the order of their numbers. */
    @Test
    void everyLanguageHasTheSamePhoneScreenshotsNumberedWithoutGapsEachAPngOf1080By2400() throws IOException {
        var english = names(LISTING.resolve("en-US").resolve("images").resolve("phoneScreenshots"));

        assertThat(english).isNotEmpty();
        assertThat(english).containsExactlyInAnyOrderElementsOf(
                IntStream.rangeClosed(1, english.size()).mapToObj(number -> number + ".png").toList());
        for (var language : languages()) {
            var folder = language.resolve("images").resolve("phoneScreenshots");
            assertThat(names(folder)).as(language.toString()).isEqualTo(english);
            for (var name : english) {
                assertPng(folder.resolve(name), 1080, 2400);
            }
        }
    }

    private static void assertPng(Path picture, int width, int height) throws IOException {
        var bytes = Files.readAllBytes(picture);

        assertThat(HexFormat.of().formatHex(bytes, 0, 16)).as(picture.toString())
                .isEqualTo("89504e470d0a1a0a0000000d49484452");
        var header = ByteBuffer.wrap(bytes, 16, 8);
        assertThat(List.of(header.getInt(), header.getInt())).as(picture.toString()).containsExactly(width, height);
    }

    private static void assertWithin(Path file, int limit) throws IOException {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(Files.readAllBytes(file)))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new AssertionError(file + " is not UTF-8", e);
        }
        assertThat(text).as(file + " ends in one newline").endsWith("\n").doesNotEndWith("\n\n");
        var body = text.substring(0, text.length() - 1);
        assertThat(body).as(file.toString()).isNotBlank().doesNotContain("\r");
        assertThat(body.codePointCount(0, body.length())).as(file + " in characters").isLessThanOrEqualTo(limit);
    }

    private static int codeOf(Path changelog) {
        var name = changelog.getFileName().toString();
        return Integer.parseInt(name.substring(0, name.length() - ".txt".length()));
    }

    private static List<Path> languages() throws IOException {
        try (Stream<Path> paths = Files.list(LISTING)) {
            return paths.filter(Files::isDirectory).sorted().toList();
        }
    }

    private static List<Path> files(Path folder) throws IOException {
        try (Stream<Path> paths = Files.list(folder)) {
            return paths.sorted().toList();
        }
    }

    private static List<String> names(Path folder) throws IOException {
        return files(folder).stream().map(file -> file.getFileName().toString()).toList();
    }
}
