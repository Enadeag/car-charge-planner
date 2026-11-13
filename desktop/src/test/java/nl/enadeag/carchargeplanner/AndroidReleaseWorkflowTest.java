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
        assertThat(linesContaining(workflow, "concurrency:")).containsExactly("concurrency:");
        assertThat(linesContaining(workflow, "cancel-in-progress:")).containsExactly("  cancel-in-progress: false");
    }

    @Test
    void theKeyIsAskedForOnlyAfterEveryCheckOfTheTag() {
        assertThat(linesStarting(jobs.get("sign"), "    needs:"))
                .containsExactly("    needs: [prepare, build, tag-checks, no-release-yet]");
        for (var job : List.of("tag-checks", "no-release-yet")) {
            assertThat(linesStarting(jobs.get(job), "    if:")).as(job)
                    .containsExactly("    if: github.event_name == 'push'");
        }
        assertThat(linesStarting(jobs.get("release"), "    needs:"))
                .containsExactly("    needs: [prepare, build, sign, sign-dry-run]");
        assertThat(jobs.get("release")).contains("\n    if: >-\n"
                + "      ${{ !cancelled() && needs.build.result == 'success'\n"
                + "      && (needs.sign.result == 'success' || needs.sign-dry-run.result == 'success') }}\n");
    }

    @Test
    void onlyTheTagRunsSigningJobNamesTheEnvironment() {
        assertThat(linesContaining(workflow, "environment:")).containsExactly("    environment: android-release");
        assertThat(linesContaining(jobs.get("sign"), "environment:")).hasSize(1);
        assertThat(linesStarting(jobs.get("sign"), "    if:")).containsExactly("    if: github.event_name == 'push'");
        assertThat(linesStarting(jobs.get("sign-dry-run"), "    if:"))
                .containsExactly("    if: github.event_name != 'push'");
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
        assertThat(linesContaining(workflow, "cache:")).isEmpty();
        var gradleSteps = jobs.values().stream().flatMap(job -> steps(job).stream())
                .filter(step -> step.contains("gradle/actions/setup-gradle@")).toList();

        assertThat(gradleSteps).isNotEmpty().allSatisfy(step -> assertThat(step).contains("cache-disabled: true"));
    }

    @Test
    void noCheckoutKeepsTheToken() {
        var checkouts = stepsUsing("actions/checkout@");

        assertThat(checkouts).hasSize(4).allSatisfy(step -> assertThat(step).contains("persist-credentials: false\n"));
    }

    @Test
    void javaIsTemurinAndNeverTheLatest() {
        var setups = stepsUsing("actions/setup-java@");

        assertThat(setups).hasSize(2).allSatisfy(step -> assertThat(step).contains("distribution: temurin\n"));
        assertThat(linesContaining(workflow, "distribution:")).hasSize(2);
        assertThat(workflow).doesNotContain("check-latest");
    }

    @Test
    void theSigningJobsRunOnlyTheScriptAndNothingFetchedOrEvaluated() {
        for (var job : SIGNING_JOBS) {
            var text = jobs.get(job);

            assertThat(text).as(job).doesNotContainIgnoringCase("curl").doesNotContainIgnoringCase("wget")
                    .doesNotContain("eval", "bash -c", "sh -c", "| sh", "| bash");
            assertThat(text.replace("cmdline-tools/", "").lines().filter(line -> line.contains("tools/")).toList())
                    .as(job)
                    .allSatisfy(line -> assertThat(line).contains("sh tools/android-sign.sh "));
        }
        var tag = jobs.get("sign").replaceAll("\\\\\n\\s*", " ").replaceAll("[ ]+", " ");
        assertThat(tag).contains("sh tools/android-sign.sh sign ", "sh tools/android-sign.sh check-unsigned ",
                "sh tools/android-sign.sh check-signed \"$apk\" \"$APP_ID\" \"$VERSION_NAME\" \"$VERSION_CODE\" "
                        + "android/app/debug.keystore android/release-certificate.sha256\n");
    }

    @Test
    void nothingIsTurnedOffOrAllowedToFail() {
        assertThat(workflow).doesNotContain("continue-on-error", "if: false", "if: true", "if: ${{ false");
    }

    @Test
    void theChecksOfTheTagKeepTheirComparisonAndTheirStop() {
        var form = step(jobs.get("prepare"), "Check the tag's form");
        assertThat(form).contains("        if: github.event_name == 'push'\n",
                "if ! printf '%s\\n' \"$GITHUB_REF_NAME\" | grep -Eqx \"android-v$number\\.$number\\.$number\"; then",
                "tag $GITHUB_REF_NAME is not android-vX.Y.Z", "exit 1");

        var version = step(jobs.get("prepare"), "Read the version and compare it with the tag");
        assertThat(version).contains("if [ \"$GITHUB_REF_NAME\" != \"android-v$name\" ]; then",
                "[ \"$GITHUB_EVENT_NAME\" = \"push\" ] || exit 0", "*-SNAPSHOT)");

        var branch = step(jobs.get("tag-checks"), "Check that release/android-X.Y.Z is at the tag");
        assertThat(branch).contains("if ! at=$(gh api \"repos/$GITHUB_REPOSITORY/git/ref/heads/$branch\"",
                "if [ \"$at\" != \"$COMMIT\" ]; then",
                "echo \"::error::$branch is at $at, not at this tag's commit $COMMIT\"; exit 1");

        var emulator = step(jobs.get("tag-checks"), "Check that the emulator job was green for this commit");
        assertThat(emulator).contains("head_sha=$COMMIT",
                "select(.name == \"android-emulator\" and .conclusion == \"success\")",
                "if [ -n \"$green\" ]; then",
                "exit 0", "\n          exit 1\n");

        var existing = step(jobs.get("no-release-yet"),
                "Check that no release, draft or published, exists for the tag");
        assertThat(existing).contains("grep -Fqx \"$GITHUB_REF_NAME\"", "exit 1");
    }

    @Test
    void theTagRunsReleaseNeedsTheTagToExist() {
        var creates = jobs.get("release").replace("\\\n", " ").lines()
                .filter(line -> line.contains("gh release create")).toList();

        assertThat(creates).hasSize(2);
        assertThat(creates.stream().filter(line -> line.contains("\"$GITHUB_REF_NAME\"")).toList())
                .singleElement().asString().contains(" --verify-tag ");
    }

    @Test
    void theAttestationIsNotMadeInADryRun() {
        var attest = step(jobs.get("release"), "Attest where the APK was built");

        assertThat(attest).startsWith("      - name: Attest where the APK was built\n"
                + "        if: env.DRY_RUN != 'true'\n        uses: actions/attest-build-provenance@");
    }

    @Test
    void bothSigningJobsCheckTheirCheckoutIsTheCommitThatWasBuiltBeforeAnythingUsesIt() {
        for (var job : SIGNING_JOBS) {
            var steps = steps(jobs.get(job));

            assertThat(steps.get(0)).as(job).contains("actions/checkout@");
            assertThat(steps.get(1)).as(job).startsWith("      - name: Check that this is the commit that was built\n")
                    .contains("COMMIT: ${{ needs.prepare.outputs.commit }}\n", "git rev-parse 'HEAD^{commit}'",
                            "if [ \"$head\" != \"$COMMIT\" ]; then", "exit 1");
        }
    }

    @Test
    void theReleaseNotesMustNameTheCertificateOfTheSignedApkBeforeTheDraftIsMade() {
        assertThat(jobs.get("sign")).contains("certificate: ${{ steps.signed.outputs.certificate }}",
                "certificate=$(sh tools/android-sign.sh certificate \"$apk\")");
        var release = jobs.get("release");
        assertThat(release).contains("SIGNED_CERTIFICATE: ${{ needs.sign.outputs.certificate }}");
        var check = step(release, "Check that the notes name the signed APK's certificate");
        assertThat(check).contains("        if: env.DRY_RUN != 'true'\n", "[ -z \"$SIGNED_CERTIFICATE\" ]",
                "grep -Fq -- \"$SIGNED_CERTIFICATE\"", "exit 1");
        assertThat(release.indexOf(check)).isLessThan(release.indexOf("      - name: Create the draft release"));
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
        assertThat(linesContaining(jobs.get("release"), ": write")).containsExactly(
                "      contents: write  # create the draft release",
                "      id-token: write  # the attestation: a token that says which workflow run built the APK",
                "      attestations: write");
        // only a token that can write lists draft releases; this job runs no code of the repository
        assertThat(linesContaining(jobs.get("no-release-yet"), ": write")).containsExactly(
                "      contents: write  # to list draft releases too; nothing is written here");
        assertThat(jobs.get("no-release-yet")).doesNotContain("uses:", "sh tools/", "gradlew");
        for (var job : List.of("prepare", "tag-checks", "build", "sign", "sign-dry-run")) {
            assertThat(linesContaining(jobs.get(job), ": write")).as(job).isEmpty();
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

    /** The lines of {@code text} that start with {@code prefix}, indentation included. */
    private static List<String> linesStarting(String text, String prefix) {
        return text.lines().filter(line -> line.startsWith(prefix)).toList();
    }

    /** The lines of {@code text} that hold {@code part} anywhere. */
    private static List<String> linesContaining(String text, String part) {
        return text.lines().filter(line -> line.contains(part)).toList();
    }

    /** The one step of {@code job} that is named {@code name}. */
    private static String step(String job, String name) {
        return steps(job).stream().filter(step -> step.startsWith("      - name: " + name + "\n")).reduce((a, b) -> {
            throw new AssertionError("two steps are named " + name);
        }).orElseThrow(() -> new AssertionError("no step is named " + name));
    }

    private static int count(String text, String part) {
        return text.split(Pattern.quote(part), -1).length - 1;
    }
}
