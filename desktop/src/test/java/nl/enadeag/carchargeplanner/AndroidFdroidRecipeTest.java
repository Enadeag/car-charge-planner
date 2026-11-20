package nl.enadeag.carchargeplanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The copy of F-Droid's recipe, {@code tools/fdroid/}, still fits the Android build: its file is named by the app ID,
 * it allows the release key, its folders exist, and it never names a version after the build's. A tripwire against a
 * mistake, read as plain lines; {@code tools/fdroid-check.sh} is what runs the recipe.
 */
class AndroidFdroidRecipeTest {

    private static final Path ANDROID = Path.of("android");
    private static final Path BUILD = ANDROID.resolve("app").resolve("build.gradle.kts");
    private static final Path CERTIFICATE = ANDROID.resolve("release-certificate.sha256");
    private static final Path RECIPES = Path.of("tools", "fdroid");

    private static final Pattern APPLICATION_ID =
            Pattern.compile("^\\s*applicationId = \"([^\"]+)\"$", Pattern.MULTILINE);
    private static final Pattern BUILD_CODE = Pattern.compile("^\\s*versionCode = (\\d+)$", Pattern.MULTILINE);

    @Test
    void theRecipeIsNamedByTheApplicationId() throws IOException {
        var applicationId = single(APPLICATION_ID, Files.readString(BUILD), "applicationId in " + BUILD);

        try (var recipes = Files.list(RECIPES)) {
            assertThat(recipes.map(path -> path.getFileName().toString()))
                    .containsExactly(applicationId + ".yml");
        }
    }

    @Test
    void theRecipeAllowsOnlyTheReleaseKey() throws IOException {
        var fingerprint = Files.readAllLines(CERTIFICATE).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#")).toList();
        assertThat(fingerprint).as(CERTIFICATE.toString()).hasSize(1);

        assertThat(values(recipe(), "AllowedAPKSigningKeys:"))
                .containsExactly(fingerprint.getFirst().replace(":", "").toLowerCase(Locale.ROOT));
    }

    @Test
    void theRecipeBuildsAModuleOfTheAndroidBuild() throws IOException {
        var recipe = recipe();

        for (var subdir : values(recipe, "subdir:")) {
            assertThat(Path.of(subdir)).isEqualTo(ANDROID);
        }
        for (var output : values(recipe, "output:")) {
            var module = ANDROID.resolve(output.substring(0, output.indexOf('/')));
            assertThat(module.resolve("build.gradle.kts")).as(output).isRegularFile();
        }
        assertThat(values(recipe, "subdir:")).isNotEmpty().hasSameSizeAs(values(recipe, "output:"));
    }

    @Test
    void noVersionCodeOfTheRecipeIsAfterTheBuilds() throws IOException {
        var buildCode = Integer.parseInt(single(BUILD_CODE, Files.readString(BUILD), "versionCode in " + BUILD));
        var recipe = recipe();

        var codes = values(recipe, "versionCode:").stream().map(Integer::parseInt).toList();
        assertThat(codes).isNotEmpty().isSorted().allSatisfy(code -> assertThat(code).isLessThanOrEqualTo(buildCode));
        assertThat(values(recipe, "CurrentVersionCode:")).containsExactly(String.valueOf(codes.getLast()));
    }

    private static List<String> recipe() throws IOException {
        try (var recipes = Files.list(RECIPES)) {
            return Files.readAllLines(recipes.findFirst().orElseThrow());
        }
    }

    /** The values of every line that starts with the key, apart from its indent or a list's dash. */
    private static List<String> values(List<String> lines, String key) {
        return lines.stream().map(String::strip).map(line -> line.startsWith("- ") ? line.substring(2) : line)
                .filter(line -> line.startsWith(key)).map(line -> line.substring(key.length()).strip()).toList();
    }

    private static String single(Pattern pattern, String text, String what) {
        Matcher match = pattern.matcher(text);
        assertThat(match.find()).as(what).isTrue();
        var value = match.group(1);
        assertThat(match.find()).as("a second " + what).isFalse();
        return value;
    }
}
