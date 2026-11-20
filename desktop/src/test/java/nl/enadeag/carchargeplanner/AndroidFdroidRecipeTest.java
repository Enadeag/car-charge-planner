package nl.enadeag.carchargeplanner;

import static nl.enadeag.carchargeplanner.AndroidListingTest.BUILD;
import static nl.enadeag.carchargeplanner.AndroidListingTest.VERSION_CODE;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The copy of F-Droid's recipe, {@code tools/fdroid/}, still fits the Android build: its file is named by the app ID,
 * it allows the release key and downloads the released APK, its folders exist, its update check reads the build file
 * as it is, and it never names a version after the build's. A tripwire against a mistake, read as plain lines;
 * {@code tools/fdroid-check.sh} is what runs the recipe.
 */
class AndroidFdroidRecipeTest {

    private static final Path ANDROID = Path.of("android");
    private static final Path CERTIFICATE = ANDROID.resolve("release-certificate.sha256");
    private static final Path WORKFLOW = Path.of(".github", "workflows", "android-release.yml");
    private static final Path RECIPES = Path.of("tools", "fdroid");

    private static final Pattern APPLICATION_ID =
            Pattern.compile("^\\s*applicationId = \"([^\"]+)\"$", Pattern.MULTILINE);
    private static final Pattern RELEASE_APK =
            Pattern.compile("apk=\"(car-charge-planner-android-)\\$VERSION(\\.apk)\"");

    @Test
    void theRecipeIsNamedByTheApplicationId() throws IOException {
        try (var recipes = Files.list(RECIPES)) {
            assertThat(recipes.map(path -> path.getFileName().toString())).containsExactly(applicationId() + ".yml");
        }
    }

    @Test
    void theRecipeAllowsOnlyTheReleaseKey() throws IOException {
        var fingerprint = Files.readAllLines(CERTIFICATE).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#")).toList();
        assertThat(fingerprint).as(CERTIFICATE.toString()).hasSize(1);

        assertThat(field(recipe(), "AllowedAPKSigningKeys"))
                .isEqualTo(fingerprint.getFirst().replace(":", "").toLowerCase(Locale.ROOT));
    }

    @Test
    void theRecipeDownloadsTheApkTheReleaseAttaches() throws IOException {
        var apk = RELEASE_APK.matcher(Files.readString(WORKFLOW));
        assertThat(apk.find()).as("the APK's name in " + WORKFLOW).isTrue();

        assertThat(field(recipe(), "Binaries"))
                .startsWith("https://github.com/Enadeag/car-charge-planner/releases/download/android-v%v/")
                .endsWith("/" + apk.group(1) + "%v" + apk.group(2));
    }

    @Test
    void theRecipeBuildsAModuleOfTheAndroidBuild() throws IOException {
        var recipe = recipe();
        var subdirs = values(recipe, "subdir:");
        var outputs = values(recipe, "output:");

        assertThat(subdirs).isNotEmpty().hasSameSizeAs(outputs);
        for (var subdir : subdirs) {
            assertThat(Path.of(subdir)).isEqualTo(ANDROID);
        }
        for (var output : outputs) {
            assertThat(output).as("output names a module's folder first").contains("/");
            var module = ANDROID.resolve(output.substring(0, output.indexOf('/')));
            assertThat(module.resolve("build.gradle.kts")).as(output).isRegularFile();
        }
    }

    @Test
    void eachBuildsVersionCodeIsTheOneItsVersionNameGives() throws IOException {
        var names = values(recipe(), "versionName:");
        var codes = values(recipe(), "versionCode:");

        assertThat(names).isNotEmpty().hasSameSizeAs(codes);
        for (int i = 0; i < names.size(); i++) {
            var parts = names.get(i).split("\\.");
            var code = Integer.parseInt(parts[0]) * 10_000 + Integer.parseInt(parts[1]) * 100
                    + Integer.parseInt(parts[2]);
            assertThat(codes.get(i)).as(names.get(i)).isEqualTo(String.valueOf(code));
        }
    }

    @Test
    void noVersionCodeOfTheRecipeIsAfterTheBuilds() throws IOException {
        var recipe = recipe();

        var codes = values(recipe, "versionCode:").stream().map(Integer::parseInt).toList();
        var buildCode = buildCode();
        assertThat(codes).isNotEmpty().isSorted().allSatisfy(code -> assertThat(code).isLessThanOrEqualTo(buildCode));
        assertThat(values(recipe, "CurrentVersionCode:")).containsExactly(String.valueOf(codes.getLast()));
    }

    /** F-Droid's update check reads a release tag's build file with the recipe's regular expressions. */
    @Test
    void theUpdateCheckReadsTheBuildFilesCodeAndNameAndOnlyTheAndroidTags() throws IOException {
        var recipe = recipe();
        var data = field(recipe, "UpdateCheckData").split("\\|", -1);
        assertThat(data).as("UpdateCheckData").hasSize(4);
        assertThat(Path.of(data[0])).isEqualTo(BUILD);
        assertThat(data[2]).as("the version name from the same file").isEqualTo(".");
        var build = Files.readString(BUILD);
        var release = build.replace("-SNAPSHOT\"", "\"");
        var snapshot = release.replaceFirst("(versionName = \"[^\"]+)\"", "$1-SNAPSHOT\"");
        var versionName = Pattern.compile("versionName = \"([^\"-]+)").matcher(build);
        assertThat(versionName.find()).isTrue();

        var code = Pattern.compile(data[1]).matcher(build);
        assertThat(code.find()).isTrue();
        assertThat(Integer.parseInt(code.group(1))).isEqualTo(buildCode());
        var name = Pattern.compile(data[3]).matcher(release);
        assertThat(name.find()).as("the version name of a release commit").isTrue();
        assertThat(name.group(1)).isEqualTo(versionName.group(1));
        // the name's expression takes X.Y.Z alone, so a snapshot's tag gives no version and F-Droid passes over it
        assertThat(Pattern.compile(data[3]).matcher(snapshot).find()).as("the name of a snapshot").isFalse();

        var tags = Pattern.compile(field(recipe, "UpdateCheckMode").replaceFirst("^Tags ", ""));
        assertThat(tags.matcher("android-v0.2.0").find()).isTrue();
        assertThat(tags.matcher("v0.7.0").find()).isFalse();
        assertThat(tags.matcher("android-v0.2.0-rc1").find()).isFalse();
        assertThat(tags.matcher("android-v1").find()).isFalse();
    }

    @Test
    void theBuildChecksTheVersionCodeAgainstTheName() throws IOException {
        var lines = Files.readAllLines(BUILD);
        var at = 0;
        while (at < lines.size() && !VERSION_CODE.matcher(lines.get(at)).matches()) {
            at++;
        }
        var after = at + 1 < lines.size() ? lines.get(at + 1) : "";

        assertThat(after.strip()).as("the line after versionCode in build.gradle.kts must call AppVersion.check")
                .startsWith("AppVersion.check(");
    }

    private static String applicationId() throws IOException {
        var match = APPLICATION_ID.matcher(Files.readString(BUILD));
        assertThat(match.find()).as("applicationId in " + BUILD).isTrue();
        return match.group(1);
    }

    private static int buildCode() throws IOException {
        var match = VERSION_CODE.matcher(Files.readString(BUILD));
        assertThat(match.find()).as("versionCode in " + BUILD).isTrue();
        return Integer.parseInt(match.group(1));
    }

    private static List<String> recipe() throws IOException {
        return Files.readAllLines(RECIPES.resolve(applicationId() + ".yml"));
    }

    /** The values of every line that starts with the key, apart from its indent or a list's dash. */
    private static List<String> values(List<String> lines, String key) {
        return lines.stream().map(String::strip).map(line -> line.startsWith("- ") ? line.substring(2) : line)
                .filter(line -> line.startsWith(key)).map(line -> line.substring(key.length()).strip()).toList();
    }

    /** A top-level field's value, with the lines rewritemeta folds it onto joined by a space, as YAML reads them. */
    private static String field(List<String> lines, String key) {
        var parts = new ArrayList<String>();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(key + ":")) {
                parts.add(lines.get(i).substring(key.length() + 1).strip());
                for (int j = i + 1; j < lines.size() && lines.get(j).startsWith("  "); j++) {
                    parts.add(lines.get(j).strip());
                }
            }
        }
        assertThat(parts).as(key).isNotEmpty();
        return String.join(" ", parts).strip();
    }
}
