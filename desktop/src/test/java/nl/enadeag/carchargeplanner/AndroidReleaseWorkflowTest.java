package nl.enadeag.carchargeplanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * What {@code android-release.yml} keeps apart (#272): the release key reaches one job and one step of it, which runs
 * no Gradle; only the release job writes and attests; nothing caches; and the build runs on one exact Java 21. Here,
 * with the Maven build, so every pull request that changes the workflow runs it.
 */
class AndroidReleaseWorkflowTest {

    private static final Path WORKFLOW = Path.of(".github", "workflows", "android-release.yml");
    private static final Pattern JOB = Pattern.compile("^  ([a-z][a-z0-9-]*):$", Pattern.MULTILINE);
    private static final Pattern STEP = Pattern.compile("^      - ", Pattern.MULTILINE);
    private static final Pattern USES = Pattern.compile("^\\s+(?:- )?uses:\\s*(\\S+)(.*)$", Pattern.MULTILINE);
    private static final Pattern JAVA_VERSION =
            Pattern.compile("^\\s+java-version:\\s*\"?([^\"\\s]+)\"?", Pattern.MULTILINE);
    private static final List<String> SIGNING_JOBS = List.of("sign", "sign-dry-run");

    private static String workflow;
    private static Map<String, String> jobs;

    @BeforeAll
    static void read() throws IOException {
        workflow = Files.readString(WORKFLOW).replace("\r\n", "\n");
        jobs = jobs(workflow);
    }

    @Test
    void theJobsAreTheOnesTheChecksBelowKnow() {
        assertThat(jobs).containsOnlyKeys("prepare", "tag-checks", "no-release-yet", "build", "sign", "sign-dry-run",
                "release");
    }

    @Test
    void onlyAnAndroidTagAndARunByHandStartIt() {
        assertThat(workflow)
                .contains("\non:\n  push:\n    tags: [\"android-v*\"]\n  workflow_dispatch:\n\npermissions:\n");
        assertThat(workflow.lines().filter(line -> line.matches("[^#\\s].*")))
                .containsExactly("name: Android release", "on:", "permissions:", "concurrency:", "env:", "jobs:");
    }

    @Test
    void runsForOneRefWaitInOrderAndAreNeverCancelled() {
        assertThat(workflow).contains("\nconcurrency:\n  group: android-release-${{ github.ref }}\n  queue: max\n"
                + "  cancel-in-progress: false\n");
        assertThat(lines(workflow, "concurrency:")).containsExactly("concurrency:");
        assertThat(lines(workflow, "cancel-in-progress:")).containsExactly("  cancel-in-progress: false");
    }

    @Test
    void theKeyIsAskedForOnlyAfterEveryCheckOfTheTag() {
        assertThat(lines(jobs.get("sign"), "    needs:"))
                .containsExactly("    needs: [prepare, build, tag-checks, no-release-yet]");
        for (var job : List.of("tag-checks", "no-release-yet")) {
            assertThat(lines(jobs.get(job), "    if:")).as(job).containsExactly("    if: github.event_name == 'push'");
        }
        assertThat(lines(jobs.get("release"), "    needs:"))
                .containsExactly("    needs: [prepare, build, sign, sign-dry-run]");
        assertThat(jobs.get("release")).contains("\n    if: >-\n"
                + "      ${{ !cancelled() && needs.build.result == 'success'\n"
                + "      && (needs.sign.result == 'success' || needs.sign-dry-run.result == 'success') }}\n");
    }

    @Test
    void onlyTheTagRunsSigningJobUsesTheEnvironment() {
        assertThat(lines(workflow, "environment:")).containsExactly("    environment: android-release");
        assertThat(lines(jobs.get("sign"), "environment:")).hasSize(1);
        assertThat(lines(jobs.get("sign"), "    if:")).containsExactly("    if: github.event_name == 'push'");
        assertThat(lines(jobs.get("sign-dry-run"), "    if:")).containsExactly("    if: github.event_name != 'push'");
    }

    @Test
    void theKeysSecretsAreInTheEnvOfTheSigningStepAlone() {
        var signing = steps(jobs.get("sign")).stream().filter(step -> step.contains("secrets.")).toList();

        assertThat(signing).hasSize(1);
        var step = signing.getFirst();
        assertThat(step).startsWith("      - name: Sign the APK\n        env:\n"
                + "          ANDROID_KEYSTORE_BASE64: ${{ secrets.ANDROID_KEYSTORE_BASE64 }}\n"
                + "          ANDROID_KEYSTORE_PASSWORD: ${{ secrets.ANDROID_KEYSTORE_PASSWORD }}\n        run: |\n");
        assertThat(step).contains("umask 077", "\"$RUNNER_TEMP/release-keystore.p12\"")
                .doesNotContain("uses:", "gradle", "set -x", "echo \"$ANDROID_KEYSTORE");
        assertThat(workflow).doesNotContain("set -x");
        // in any form, such as secrets['NAME'] or secrets: inherit; comments aside
        var code = workflow.lines().filter(line -> !line.strip().startsWith("#"))
                .collect(Collectors.joining("\n")).toLowerCase(Locale.ROOT);
        assertThat(count(code, "secrets")).isEqualTo(2);
        assertThat(count(step.toLowerCase(Locale.ROOT), "secrets")).isEqualTo(2);
    }

    @Test
    void theReleaseIsAlwaysADraftThatIsNotTheLatest() {
        var joined = workflow.replace("\\\n", " ");
        var creates = joined.lines().filter(line -> line.contains("gh release create")).toList();

        assertThat(creates).hasSize(2).allSatisfy(line -> assertThat(line).contains(" --draft ", " --latest=false "));
        assertThat(workflow).doesNotContain("gh release edit", "--draft=false", "--latest=true");
    }

    @Test
    void theKeystoreIsRemovedInAStepThatAlwaysRuns() {
        for (var job : SIGNING_JOBS) {
            var removal = steps(jobs.get(job)).stream().filter(step -> step.contains("name: Remove the keystore"))
                    .toList();

            assertThat(removal).as(job).singleElement().asString().contains("if: always()",
                    "rm -f \"${RUNNER_TEMP:?}/");
        }
    }

    @Test
    void nothingCaches() {
        assertThat(workflow).doesNotContain("actions/cache", "cache-provider", "cache-read-only", "cache-encryption");
        var gradleSteps = jobs.values().stream().flatMap(job -> steps(job).stream())
                .filter(step -> step.contains("gradle/actions/setup-gradle@")).toList();

        assertThat(gradleSteps).isNotEmpty().allSatisfy(step -> assertThat(step).contains("cache-disabled: true"));
    }

    @Test
    void noActionIsGivenACache() {
        assertThat(lines(workflow, "cache:")).isEmpty();
    }

    @Test
    void noCheckoutKeepsTheToken() {
        var checkouts = stepsUsing("actions/checkout@");

        assertThat(checkouts).hasSize(4).allSatisfy(step -> assertThat(step).contains("persist-credentials: false\n"));
    }

    @Test
    void javaIsTemurinOfTheVersionNamedAndNoNewer() {
        var setups = stepsUsing("actions/setup-java@");

        assertThat(setups).hasSize(2).allSatisfy(step -> assertThat(step).contains("distribution: temurin\n"));
        assertThat(lines(workflow, "distribution:")).hasSize(2);
        assertThat(workflow).doesNotContain("check-latest");
    }

    @Test
    void theSigningJobsRunNoGradleAndOnlyGitHubsOwnActions() {
        for (var job : SIGNING_JOBS) {
            var text = jobs.get(job);

            assertThat(text).as(job).doesNotContainIgnoringCase("gradle").doesNotContain("setup-java");
            assertThat(USES.matcher(text).results().map(match -> match.group(1).replaceAll("@.*", "")))
                    .as(job).containsOnly("actions/checkout", "actions/download-artifact", "actions/upload-artifact");
        }
    }

    @Test
    void onlyTheReleaseJobWritesAndAttests() {
        assertThat(workflow).contains("\npermissions:\n  contents: read\n\n");
        assertThat(lines(jobs.get("release"), ": write")).containsExactly(
                "      contents: write  # create the draft release",
                "      id-token: write  # the attestation: a token that says which workflow run built the APK",
                "      attestations: write");
        // only a token that can write lists draft releases; this job runs no code of the repository
        assertThat(lines(jobs.get("no-release-yet"), ": write")).containsExactly(
                "      contents: write  # to list draft releases too; nothing is written here");
        assertThat(jobs.get("no-release-yet")).doesNotContain("uses:", "sh tools/", "gradlew");
        for (var job : List.of("prepare", "tag-checks", "build", "sign", "sign-dry-run")) {
            assertThat(lines(jobs.get(job), ": write")).as(job).isEmpty();
            assertThat(jobs.get(job)).as(job).doesNotContain("id-token", "attestations");
        }
    }

    @Test
    void theBuildUsesOneExactJava21() {
        var versions = JAVA_VERSION.matcher(workflow).results().map(match -> match.group(1)).toList();

        assertThat(versions).hasSize(2).allMatch(version -> version.matches("21(\\.\\d+){2,}\\+\\d+"),
                "an exact Java 21 version with its build number, such as 21.0.13+9");
        assertThat(versions).containsOnly(versions.getFirst());
    }

    @Test
    void everyActionIsPinnedToACommit() {
        var uses = USES.matcher(workflow).results().toList();

        assertThat(uses).isNotEmpty().allSatisfy(match -> {
            assertThat(match.group(1)).matches("[a-z-]+/[a-z-]+(/[a-z-]+)?@[0-9a-f]{40}");
            assertThat(match.group(2)).matches(" # v\\d+(\\.\\d+)*");
        });
    }

    /** Every step of every job that uses {@code action}. */
    private static List<String> stepsUsing(String action) {
        return jobs.values().stream().flatMap(job -> steps(job).stream()).filter(step -> step.contains(action))
                .toList();
    }

    /** Each job's text, from its id's line up to the next job, by id. */
    private static Map<String, String> jobs(String text) {
        var start = text.indexOf("\njobs:\n");
        assertThat(start).as("the workflow has jobs").isPositive();
        var body = text.substring(start + "\njobs:\n".length());
        var ids = JOB.matcher(body).results().toList();
        var result = new LinkedHashMap<String, String>();
        for (var i = 0; i < ids.size(); i++) {
            var end = i + 1 < ids.size() ? ids.get(i + 1).start() : body.length();
            result.put(ids.get(i).group(1), body.substring(ids.get(i).start(), end));
        }
        return result;
    }

    /** A job's steps, each from its {@code - } line up to the next step. */
    private static List<String> steps(String job) {
        var starts = STEP.matcher(job).results().map(match -> match.start()).toList();
        var result = new ArrayList<String>();
        for (var i = 0; i < starts.size(); i++) {
            result.add(job.substring(starts.get(i), i + 1 < starts.size() ? starts.get(i + 1) : job.length()));
        }
        return result;
    }

    /** The lines of {@code text} that hold {@code part}; one that starts with spaces must start the line. */
    private static List<String> lines(String text, String part) {
        return text.lines().filter(line -> part.startsWith(" ") ? line.startsWith(part) : line.contains(part))
                .toList();
    }

    private static int count(String text, String part) {
        return text.split(Pattern.quote(part), -1).length - 1;
    }
}
