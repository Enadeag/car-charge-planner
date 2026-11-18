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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * What {@code android-release.yml} keeps apart (#272): the release key reaches one job and one step of it, which runs
 * no Gradle; only the release job writes and attests; nothing caches; the build runs on one exact Java 21; and no key
 * is asked for before a second build has given the same unsigned APK (#424). Here, with the Maven build, so every pull
 * request that changes the workflow runs it.
 */
class AndroidReleaseWorkflowTest {

    private static final Path WORKFLOW = Path.of(".github", "workflows", "android-release.yml");
    private static final Pattern JOB = Pattern.compile("^  ([a-z][a-z0-9-]*):$", Pattern.MULTILINE);
    private static final Pattern STEP = Pattern.compile("^      - ", Pattern.MULTILINE);
    private static final Pattern USES = Pattern.compile("^\\s+(?:- )?uses:\\s*(\\S+)(.*)$", Pattern.MULTILINE);
    private static final Pattern JAVA_VERSION =
            Pattern.compile("^\\s+java-version:\\s*\"?([^\"\\s]+)\"?", Pattern.MULTILINE);
    private static final List<String> SIGNING_JOBS = List.of("sign", "sign-dry-run");

    /** Every command line a run body of a signing job may start with; anything else, such as a sourced file, fails. */
    private static final List<String> SIGNING_COMMANDS = List.of(
            "head=$(git rev-parse 'HEAD^{commit}')",
            "if [ \"$head\" != \"$COMMIT\" ]; then",
            "fi",
            "sdkmanager=\"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager\"",
            "(yes || true) | \"$sdkmanager\" --licenses > /dev/null",
            "\"$sdkmanager\" --install \"build-tools;36.0.0\"",
            "umask 077",
            "keystore=\"$RUNNER_TEMP/release-keystore.p12\"",
            "keystore=\"$RUNNER_TEMP/dry-run-keystore.p12\"",
            "printf '%s' \"$ANDROID_KEYSTORE_BASE64\" | base64 -d > \"$keystore\"",
            "unset ANDROID_KEYSTORE_BASE64",
            "ANDROID_KEYSTORE_PASSWORD=$(openssl rand -hex 24)",
            "export ANDROID_KEYSTORE_PASSWORD",
            "mkdir \"$RUNNER_TEMP/signed\"",
            "rm -f \"${RUNNER_TEMP:?}/release-keystore.p12\"",
            "rm -f \"${RUNNER_TEMP:?}/dry-run-keystore.p12\"",
            "apk=\"$RUNNER_TEMP/signed/car-charge-planner-android-$VERSION_NAME.apk\"",
            "apk=\"$RUNNER_TEMP/signed/car-charge-planner-android-$VERSION_NAME-dry-run.apk\"",
            "certificate=$(sh tools/android-sign.sh certificate \"$apk\")",
            "sum=$(sha256sum \"$apk\")",
            "echo \"sha256=${sum%% *}\" >> \"$GITHUB_OUTPUT\"",
            "echo \"certificate=$certificate\" >> \"$GITHUB_OUTPUT\"");
    private static final List<Pattern> SIGNING_COMMAND_FORMS = List.of(
            Pattern.compile("sh tools/android-sign\\.sh (check-unsigned|sign|check-signed|throwaway-key) "
                    + "(?!.*\\$\\()[^;|&<>`]+"),
            Pattern.compile("echo \"::error::(?!.*\\$\\()[^\"`]*\"; exit 1"));

    private static final String TAG_FORM = """
            number='(0|[1-9][0-9]*)'
            if ! printf '%s\\n' "$GITHUB_REF_NAME" | grep -Eqx "android-v$number\\.$number\\.$number"; then
              echo "::error::tag $GITHUB_REF_NAME is not android-vX.Y.Z; an Android release has no other tags"
              exit 1
            fi
            """;

    private static final String VERSION_AGAINST_TAG = """
            out=$(./gradlew -q --console=plain :app:printReleaseVersion)
            name=$(printf '%s\\n' "$out" | sed -n 's/^versionName=//p')
            code=$(printf '%s\\n' "$out" | sed -n 's/^versionCode=//p')
            if [ -z "$name" ] || [ -z "$code" ]; then
              echo "::error::printReleaseVersion gave no version name or code: $out"; exit 1
            fi
            echo "versionName $name, versionCode $code"
            echo "version-name=$name" >> "$GITHUB_OUTPUT"
            echo "version-code=$code" >> "$GITHUB_OUTPUT"
            [ "$GITHUB_EVENT_NAME" = "push" ] || exit 0
            case "$name" in
              *-SNAPSHOT)
                echo "::error::the build says $name: a release commit sets the versionName without -SNAPSHOT"
                exit 1 ;;
            esac
            if [ "$GITHUB_REF_NAME" != "android-v$name" ]; then
              echo "::error::tag $GITHUB_REF_NAME does not match the versionName $name (expected tag android-v$name)"
              exit 1
            fi
            """;

    private static final String RELEASE_BRANCH = """
            branch="release/android-$VERSION"
            if ! at=$(gh api "repos/$GITHUB_REPOSITORY/git/ref/heads/$branch" --jq .object.sha 2> /dev/null); then
              echo "::error::$branch does not exist: create it at the tag's commit before pushing the tag"; exit 1
            fi
            if [ "$at" != "$COMMIT" ]; then
              echo "::error::$branch is at $at, not at this tag's commit $COMMIT"; exit 1
            fi
            echo "$branch is at $COMMIT"
            """;

    private static final String EMULATOR_GREEN = """
            workflow="repos/$GITHUB_REPOSITORY/actions/workflows/android.yml"
            runs=$(gh api "$workflow/runs?head_sha=$COMMIT&per_page=100" \\
              --jq '.workflow_runs[] | select(.event == "push" or .event == "workflow_dispatch") | .id')
            for run in $runs; do
              green=$(gh api "repos/$GITHUB_REPOSITORY/actions/runs/$run/jobs?per_page=100" \\
                --jq '.jobs[] | select(.name == "android-emulator" and .conclusion == "success") | .html_url')
              if [ -n "$green" ]; then
                echo "android-emulator was green for $COMMIT: $green"; exit 0
              fi
            done
            echo "::error::no green android-emulator job of android.yml for $COMMIT: a push to" \\
              "release/android-$VERSION runs it, or run android.yml by hand on that branch"
            exit 1
            """;

    private static final String NO_RELEASE_YET = """
            tags=$(gh api --paginate "repos/$GITHUB_REPOSITORY/releases?per_page=100" --jq '.[].tag_name')
            if printf '%s\\n' "$tags" | grep -Fqx "$GITHUB_REF_NAME"; then
              echo "::error::a release (draft or published) exists for $GITHUB_REF_NAME; \
            this workflow never replaces one"
              exit 1
            fi
            echo "no release exists for $GITHUB_REF_NAME"
            """;

    private static final String CHECKSUMS = """
            cd release
            if [ "$DRY_RUN" = "true" ]; then apk="car-charge-planner-android-$VERSION-dry-run.apk"
            else apk="car-charge-planner-android-$VERSION.apk"; fi
            if [ "$(ls)" != "$apk" ]; then
              echo "::error::expected $apk only"; ls -l; exit 1
            fi
            sum=$(sha256sum "$apk")
            if [ "${sum%% *}" != "$SIGNED_SHA256" ]; then
              echo "::error::$apk is not the APK the signing job checked (SHA-256 $SIGNED_SHA256)"; exit 1
            fi
            sha256sum -- "$apk" > SHA256SUMS
            cat SHA256SUMS
            """;

    private static final String COMPARE = """
            first="$RUNNER_TEMP/first/app-release-unsigned.apk"
            second=reproduce/car-charge-planner/android/app/build/outputs/apk/release/app-release-unsigned.apk
            if ! sum=$(sha256sum "$first"); then
              echo "::error::the build job's APK $first cannot be read, so nothing is signed"; exit 1
            fi
            if [ "${sum%% *}" != "$APK_SHA256" ]; then
              echo "::error::$first is not the APK the build job checked (SHA-256 $APK_SHA256), so nothing is signed"
              exit 1
            fi
            echo "The build job's versions:"; cat "$RUNNER_TEMP/first/build-versions.txt"
            echo "This job's versions:"; cat "$RUNNER_TEMP/versions/build-versions.txt"
            if ! cmp -s "$RUNNER_TEMP/first/build-versions.txt" "$RUNNER_TEMP/versions/build-versions.txt"; then
              echo "::warning::the two builds used other versions of the build-tools, the platform or Java (above)"
            fi
            status=0
            cmp "$first" "$second" || status=$?
            if [ "$status" -eq 0 ]; then
              echo "The unsigned APK built again is byte for byte the build job's (SHA-256 $APK_SHA256)."; exit 0
            fi
            if [ "$status" -ne 1 ]; then
              echo "::error::cmp cannot compare the two APKs (exit $status: one is missing or unreadable)," \\
                "so nothing is signed"
              exit 1
            fi
            mkdir "$RUNNER_TEMP/difference"
            cp "$first" "$RUNNER_TEMP/difference/build-job.apk"
            cp "$second" "$RUNNER_TEMP/difference/reproduce-job.apk"
            unzip -v "$first" > "$RUNNER_TEMP/difference/build-job.txt"
            unzip -v "$second" > "$RUNNER_TEMP/difference/reproduce-job.txt"
            echo "::error::the unsigned APK built again differs from the build job's, so nothing is signed; the" \\
              "artifact android-reproduce-difference-$GITHUB_RUN_NUMBER-$GITHUB_RUN_ATTEMPT holds both APKs and" \\
              "their unzip -v listings"
            exit 1
            """;

    private static String workflow;
    private static String code;
    private static Map<String, String> jobs;
    private static Map<String, String> jobCode;

    @BeforeAll
    static void read() throws IOException {
        workflow = Files.readString(WORKFLOW).replace("\r\n", "\n");
        code = withoutComments(workflow);
        jobs = jobs(workflow);
        jobCode = jobs(code);
    }

    @Test
    void theJobsAreTheOnesTheChecksBelowKnow() {
        assertThat(jobs).containsOnlyKeys("prepare", "tag-checks", "no-release-yet", "build", "reproduce", "sign",
                "sign-dry-run", "release");
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
                .containsExactly("    needs: [prepare, build, reproduce, tag-checks, no-release-yet]");
        assertThat(linesStarting(jobs.get("sign-dry-run"), "    needs:"))
                .containsExactly("    needs: [prepare, build, reproduce]");
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
        assertThat(withoutComments(step)).contains("umask 077", "\"$RUNNER_TEMP/release-keystore.p12\"")
                .doesNotContain("uses:", "gradle", "set -x", "echo \"$ANDROID_KEYSTORE");
        assertThat(code).doesNotContain("set -x");
        // in any form, such as secrets['NAME'] or secrets: inherit
        assertThat(count(code.toLowerCase(Locale.ROOT), "secrets")).isEqualTo(2);
        assertThat(count(withoutComments(step).toLowerCase(Locale.ROOT), "secrets")).isEqualTo(2);
    }

    @Test
    void theReleaseIsAlwaysADraftThatIsNotTheLatest() {
        var joined = workflow.replace("\\\n", " ");
        var creates = joined.lines().filter(line -> line.contains("gh release create")).toList();

        assertThat(creates).hasSize(2).allSatisfy(line -> assertThat(line).contains(" --draft ", " --latest=false "));
        assertThat(code).doesNotContain("gh release edit", "--draft=false", "--latest=true");
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
        assertThat(code).doesNotContain("actions/cache", "cache-provider", "cache-read-only", "cache-encryption");
        assertThat(linesContaining(code, "cache:")).isEmpty();
        var gradleSteps = jobs.values().stream().flatMap(job -> steps(job).stream())
                .filter(step -> step.contains("gradle/actions/setup-gradle@")).toList();

        assertThat(gradleSteps).isNotEmpty().allSatisfy(step -> assertThat(step).contains("cache-disabled: true"));
    }

    @Test
    void noCheckoutKeepsTheToken() {
        var checkouts = stepsUsing("actions/checkout@");

        assertThat(checkouts).hasSize(5).allSatisfy(step -> assertThat(step).contains("persist-credentials: false\n"));
    }

    @Test
    void javaIsTemurinAndNeverTheLatest() {
        var setups = stepsUsing("actions/setup-java@");

        assertThat(setups).hasSize(3).allSatisfy(step -> assertThat(step).contains("distribution: temurin\n"));
        assertThat(linesContaining(workflow, "distribution:")).hasSize(3);
        assertThat(code).doesNotContain("check-latest");
    }

    @Test
    void theSigningJobsRunOnlyTheScriptAndTheCommandsOfTheirOwnList() {
        for (var job : SIGNING_JOBS) {
            var commands = steps(jobs.get(job)).stream().filter(step -> step.contains("\n        run:"))
                    .map(step -> runBody(step).replaceAll("\\\\\n\\s*", " ").replaceAll("[ ]+", " "))
                    .flatMap(body -> body.lines().map(String::strip))
                    .toList();

            assertThat(commands).as(job).isNotEmpty().allSatisfy(command -> assertThat(
                    SIGNING_COMMANDS.contains(command)
                            || SIGNING_COMMAND_FORMS.stream().anyMatch(form -> form.matcher(command).matches()))
                    .as("a command that is not on the list: " + command).isTrue());
        }
        var tag = jobCode.get("sign").replaceAll("\\\\\n\\s*", " ").replaceAll("[ ]+", " ");
        assertThat(tag).contains("sh tools/android-sign.sh sign ", "sh tools/android-sign.sh check-unsigned ",
                "sh tools/android-sign.sh check-signed \"$apk\" \"$APP_ID\" \"$VERSION_NAME\" \"$VERSION_CODE\" "
                        + "android/app/debug.keystore android/release-certificate.sha256\n");
    }

    @Test
    void nothingIsTurnedOffOrAllowedToFail() {
        assertThat(code).doesNotContain("continue-on-error", "if: false", "if: true", "if: ${{ false");
    }

    @Test
    void theChecksOfTheTagAndOfTheSignedApkAreExactlyTheseTexts() {
        var form = step(jobs.get("prepare"), "Check the tag's form");
        assertThat(form).contains("        if: github.event_name == 'push'\n");
        assertThat(runBody(form)).isEqualTo(TAG_FORM.strip());
        assertThat(runBody(step(jobs.get("prepare"), "Read the version and compare it with the tag")))
                .isEqualTo(VERSION_AGAINST_TAG.strip());
        assertThat(runBody(step(jobs.get("tag-checks"), "Check that release/android-X.Y.Z is at the tag")))
                .isEqualTo(RELEASE_BRANCH.strip());
        assertThat(runBody(step(jobs.get("tag-checks"), "Check that the emulator job was green for this commit")))
                .isEqualTo(EMULATOR_GREEN.strip());
        assertThat(runBody(step(jobs.get("no-release-yet"),
                "Check that no release, draft or published, exists for the tag"))).isEqualTo(NO_RELEASE_YET.strip());
        assertThat(runBody(step(jobs.get("release"), "Checksums"))).isEqualTo(CHECKSUMS.strip());
    }

    @Test
    void theSignedApkIsKeptForOneDayOnly() {
        for (var job : SIGNING_JOBS) {
            var uploads = steps(jobs.get(job)).stream().filter(step -> step.contains("android-signed-apk"))
                    .filter(step -> step.contains("actions/upload-artifact@")).toList();

            assertThat(uploads).as(job).singleElement().asString().contains("\n          retention-days: 1\n");
        }
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
    void theReleaseNotesMustNameTheCertificateOfTheSignedApkBeforeTheAttestationAndTheDraft() {
        assertThat(jobs.get("sign")).contains("certificate: ${{ steps.signed.outputs.certificate }}",
                "certificate=$(sh tools/android-sign.sh certificate \"$apk\")");
        var release = jobs.get("release");
        assertThat(release).contains("SIGNED_CERTIFICATE: ${{ needs.sign.outputs.certificate }}");
        var check = step(release, "Check that the notes name the signed APK's certificate");
        assertThat(check).contains("        if: env.DRY_RUN != 'true'\n",
                "sentence=\"SHA-256 fingerprint \\`$SIGNED_CERTIFICATE\\`\"", "[ -z \"$SIGNED_CERTIFICATE\" ]",
                "grep -Fq -- \"$sentence\"", "exit 1");
        assertThat(release.indexOf(check)).isLessThan(release.indexOf("      - name: Attest where the APK was built"));
        assertThat(release.indexOf("      - name: Attest where the APK was built"))
                .isLessThan(release.indexOf("      - name: Create the draft release"));
    }

    @Test
    void theSigningJobsRunNoGradleAndOnlyGitHubsOwnActions() {
        for (var job : SIGNING_JOBS) {
            var text = jobs.get(job);

            assertThat(withoutComments(text)).as(job).doesNotContainIgnoringCase("gradle")
                    .doesNotContain("setup-java");
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
        assertThat(jobCode.get("no-release-yet")).doesNotContain("uses:", "sh tools/", "gradlew");
        for (var job : List.of("prepare", "tag-checks", "build", "reproduce", "sign", "sign-dry-run")) {
            assertThat(linesContaining(jobs.get(job), ": write")).as(job).isEmpty();
            assertThat(jobCode.get(job)).as(job).doesNotContain("id-token", "attestations");
        }
    }

    @Test
    void theBuildUsesOneExactJava21() {
        var versions = JAVA_VERSION.matcher(workflow).results().map(match -> match.group(1)).toList();

        assertThat(versions).hasSize(3).allMatch(version -> version.matches("21(\\.\\d+){2,}\\+\\d+"),
                "an exact Java 21 version with its build number, such as 21.0.13+9");
        assertThat(versions).containsOnly(versions.getFirst());
    }

    @Test
    void reproduceRunsOnEveryRunAndOnlyGitHubsOwnActionsAndGradlesSetUp() {
        var reproduce = jobs.get("reproduce");

        assertThat(linesStarting(reproduce, "    needs:")).containsExactly("    needs: [prepare, build]");
        assertThat(linesStarting(reproduce, "    if:")).isEmpty();
        assertThat(USES.matcher(reproduce).results().map(match -> match.group(1).replaceAll("@.*", "")))
                .containsOnly("actions/checkout", "actions/setup-java", "gradle/actions/setup-gradle",
                        "actions/download-artifact", "actions/upload-artifact");
        assertThat(jobCode.get("reproduce").toLowerCase(Locale.ROOT)).doesNotContain("secrets", "environment:",
                "github.token", "gh_token");
    }

    @Test
    void reproduceBuildsTheSameCommitTheSameWayAsTheBuildJob() {
        var build = jobs.get("build");
        var reproduce = jobs.get("reproduce");

        assertThat(steps(reproduce).get(1)).startsWith("      - name: Check that this is the commit that was built\n")
                .contains("COMMIT: ${{ needs.prepare.outputs.commit }}\n", "if [ \"$head\" != \"$COMMIT\" ]; then",
                        "exit 1");
        for (var name : List.of("Android SDK platform 37 and build-tools 36.0.0",
                "Android Lint, the unit tests and the build's checks, and the unsigned release APK",
                "Record the versions of the build-tools, the platform and Java")) {
            assertThat(withoutComments(withoutEnv(step(reproduce, name)))).as(name)
                    .isEqualTo(withoutComments(step(build, name)));
        }
    }

    @Test
    void reproduceDiffersFromTheBuildJobInFolderGradleHomeTimeZoneAndLocale() {
        var reproduce = jobs.get("reproduce");

        assertThat(reproduce).contains("\n          path: reproduce/car-charge-planner\n",
                "\n        working-directory: reproduce/car-charge-planner/android\n",
                "run: sudo locale-gen tr_TR.UTF-8\n",
                "run: echo \"GRADLE_USER_HOME=$RUNNER_TEMP/gradle-home\" >> \"$GITHUB_ENV\"\n",
                "grep -Fqx '    user.language = tr'");
        // on the Gradle step and on the step that checks Java sees them, so the build runs under what was checked
        for (var name : List.of("Check that Java sees the other time zone and locale",
                "Android Lint, the unit tests and the build's checks, and the unsigned release APK")) {
            var step = step(reproduce, name);
            assertThat(step).as(name).contains("\n        env:\n");
            for (var line : List.of("          TZ: Pacific/Auckland", "          LANG: tr_TR.UTF-8",
                    "          LC_ALL: tr_TR.UTF-8")) {
                assertThat(step.lines()).as(name + ": " + line.strip()).containsOnlyOnce(line);
            }
        }
        assertThat(code.replace(jobCode.get("reproduce"), "")).as("the build job has none of these")
                .doesNotContain("TZ:", "LANG", "LC_ALL", "GRADLE_USER_HOME", "path: reproduce");
    }

    @Test
    void reproduceComparesTheApksAndKeepsBothWhenTheyDiffer() {
        var reproduce = jobs.get("reproduce");
        var compare = step(reproduce, "Compare the APK with the build job's");

        assertThat(compare).contains("        id: compare\n");
        assertThat(runBody(compare)).isEqualTo(COMPARE.strip());
        var steps = steps(reproduce);
        assertThat(steps.getLast()).contains("actions/upload-artifact@",
                "        if: failure() && steps.compare.outcome == 'failure'\n",
                "          name: android-reproduce-difference-${{ github.run_number }}-${{ github.run_attempt }}\n",
                "          path: ${{ runner.temp }}/difference/\n", "          if-no-files-found: error\n",
                "          retention-days: 14\n");
        assertThat(steps.indexOf(compare)).as("the comparison is the step before the upload of a difference")
                .isEqualTo(steps.size() - 2);
    }

    @Test
    void reproduceChecksTheFirstApkIsTheOneTheBuildJobReportedAndSignChecks() {
        var compare = step(jobs.get("reproduce"), "Compare the APK with the build job's");

        assertThat(compare).as("the build job's SHA-256 comes through env, never into the script itself")
                .contains("\n        env:\n          APK_SHA256: ${{ needs.build.outputs.apk-sha256 }}\n");
        assertThat(runBody(compare)).doesNotContain("${{");
        assertThat(jobs.get("build")).contains("      apk-sha256: ${{ steps.apk.outputs.sha256 }}\n");
        for (var job : SIGNING_JOBS) {
            assertThat(step(jobs.get(job), "Check the unsigned APK")).as(job)
                    .contains("APK_SHA256: ${{ needs.build.outputs.apk-sha256 }}\n");
        }
    }

    @Test
    void noStepOfReproduceIsSkippedButTheUploadOfADifference() {
        var steps = steps(jobs.get("reproduce"));

        // a skipped step counts as success, so sign would run without the comparison
        for (var step : steps.subList(0, steps.size() - 1)) {
            assertThat(step.lines().filter(line -> line.matches(" {6}(- | {2})if:.*")))
                    .as("a step of reproduce with an if:, which may skip it and let sign run without the comparison: "
                            + step.lines().findFirst().orElseThrow())
                    .isEmpty();
        }
    }

    @Test
    void reproduceComparesWithTheBuildJobsApkAndVersions() {
        var steps = steps(jobs.get("reproduce"));
        var compare = steps.indexOf(step(jobs.get("reproduce"), "Compare the APK with the build job's"));

        for (var artifact : List.of("android-unsigned-apk", "android-build-versions")) {
            var downloads = steps.stream().filter(step -> step.contains("actions/download-artifact@"))
                    .filter(step -> step.contains("\n          name: " + artifact + "\n")).toList();

            assertThat(downloads).as("the download of " + artifact + " into the folder the comparison reads")
                    .singleElement().asString()
                    .contains("\n          name: " + artifact + "\n          path: ${{ runner.temp }}/first\n");
            assertThat(steps.indexOf(downloads.getFirst())).as(artifact + " is downloaded before the comparison")
                    .isLessThan(compare);
        }
        assertThat(COMPARE).contains("first=\"$RUNNER_TEMP/first/app-release-unsigned.apk\"",
                "\"$RUNNER_TEMP/first/build-versions.txt\"");
    }

    @Test
    void theArtifactThatIsSignedHoldsTheUnsignedApkAlone() {
        var uploads = steps(jobs.get("build")).stream().filter(step -> step.contains("actions/upload-artifact@"))
                .toList();

        assertThat(uploads.stream().filter(step -> step.contains("name: android-unsigned-apk")).toList())
                .as("one upload of android-unsigned-apk, with the APK as its one path").singleElement().asString()
                .contains("\n          name: android-unsigned-apk\n",
                        "\n          path: android/app/build/outputs/apk/release/app-release-unsigned.apk\n");
        assertThat(uploads.stream().filter(step -> step.contains("build-versions.txt")).toList())
                .as("the versions go into an artifact of their own").singleElement().asString()
                .contains("\n          name: android-build-versions\n");
    }

    @Test
    void theSigningScriptKeepsTheBuildsZipAlignment() throws IOException {
        var script = Files.readString(Path.of("tools", "android-sign.sh")).replace("\r\n", "\n")
                .replaceAll("\\\\\n\\s*", " ");
        var signs = script.lines().map(String::strip).filter(line -> line.startsWith("\"$apksigner\" sign ")).toList();

        var why = "without --alignment-preserved true apksigner 35 and later pads the entries anew, so the release's "
                + "signature can no longer be copied onto an APK rebuilt from the tag, as F-Droid does (#424)";

        assertThat(signs).as("one apksigner sign command").hasSize(1);
        assertThat(count(signs.getFirst(), "--alignment-preserved")).as(why).isEqualTo(1);
        assertThat(signs.getFirst()).as(why).matches(".* --alignment-preserved true( .*)?");
    }

    @Test
    void everyActionIsPinnedToACommit() {
        var uses = USES.matcher(workflow).results().toList();

        assertThat(uses).isNotEmpty().allSatisfy(match -> {
            assertThat(match.group(1)).matches("[a-z-]+/[a-z-]+(/[a-z-]+)?@[0-9a-f]{40}");
            assertThat(match.group(2)).matches(" # v\\d+(\\.\\d+)*");
        });
    }

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

    private static List<String> linesStarting(String text, String prefix) {
        return text.lines().filter(line -> line.startsWith(prefix)).toList();
    }

    private static List<String> linesContaining(String text, String part) {
        return text.lines().filter(line -> line.contains(part)).toList();
    }

    private static String step(String job, String name) {
        return steps(job).stream().filter(step -> step.startsWith("      - name: " + name + "\n")).reduce((a, b) -> {
            throw new AssertionError("two steps are named " + name);
        }).orElseThrow(() -> new AssertionError("no step is named " + name));
    }

    /** The commands of a step's {@code run:}, a block or a single line, without indentation and comments. */
    private static String runBody(String step) {
        var lines = step.lines().toList();
        var inline = lines.stream().filter(line -> line.startsWith("        run: ") && !line.equals("        run: |"))
                .findFirst();
        if (inline.isPresent()) {
            return withoutComment(inline.get().substring("        run: ".length())).strip();
        }
        var start = lines.indexOf("        run: |");
        assertThat(start).as("a step with a run").isNotNegative();
        var body = new ArrayList<String>();
        for (var line : lines.subList(start + 1, lines.size())) {
            if (!line.isBlank() && !line.startsWith("          ")) {
                break;
            }
            body.add(line.isBlank() ? "" : line.substring(10));
        }
        return withoutComments(String.join("\n", body)).strip();
    }

    /** A step without its {@code env:} block, for comparing steps that differ only in it. */
    private static String withoutEnv(String step) {
        return step.replaceAll("(?m)^        env:\n(?: {10}.*\n)*", "");
    }

    /** {@code text} without whole comment lines and trailing {@code  #} comments, which a quoted {@code #} is not. */
    private static String withoutComments(String text) {
        var kept = new ArrayList<String>();
        for (var line : text.lines().toList()) {
            var bare = withoutComment(line);
            if (!bare.isBlank() || line.isBlank()) {
                kept.add(bare);
            }
        }
        return String.join("\n", kept);
    }

    private static String withoutComment(String line) {
        var quote = '\0';
        for (var i = 0; i < line.length(); i++) {
            var c = line.charAt(i);
            if (quote != '\0') {
                if (c == '\\' && quote == '"') {
                    i++;
                } else if (c == quote) {
                    quote = '\0';
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '#' && (i == 0 || Character.isWhitespace(line.charAt(i - 1)))) {
                return line.substring(0, i).stripTrailing();
            }
        }
        return line;
    }

    private static int count(String text, String part) {
        return text.split(Pattern.quote(part), -1).length - 1;
    }
}
