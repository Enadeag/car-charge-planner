package nl.enadeag.carchargeplanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Gradle's dependency verification of the Android build stays strict, its default, everywhere (#484). Gradle's strict
 * default is the real check; this test is a tripwire beside it, here in the Maven build, so every pull request that
 * changes a workflow runs it.
 *
 * <p>The Gradle calls of the workflows and composite actions are held to an allowlist, as deny patterns kept having
 * ways around: a call is the wrapper written plainly as {@code ./gradlew}, followed only by words of {@link #OPTIONS}
 * and tasks the refresh script runs too, up to the end of the line or the {@code )} of a {@code $(...)}. Any other word
 * fails, and so does any other way of naming the wrapper or Gradle as a command, and a call YAML would join with
 * another line, or that does not start a command: it stands on its {@code run:} line or in a literal block. A file
 * that calls Gradle uses only the actions of {@link #ACTIONS}, and no step names the check's inputs to change them (the
 * metadata file, a Gradle user home's folder, {@code BASH_ENV}, {@code user.home}). In a file that calls Gradle, every
 * {@code working-directory} and every {@code cd} goes to a folder of {@link #DIRECTORIES}. Beside that, no workflow,
 * composite action or {@code gradle.properties} names the verification flags or the mode's property in any spelling,
 * another Gradle user home, an init script, or the refresh script, which writes the checksums instead of checking them;
 * no build file, source, properties file or wrapper script of the Android build sets the mode; and
 * {@code verification-metadata.xml} checks poms too, SHA-256 only, without keys, and trusts only an IDE's sources and
 * Javadoc. Comment lines, and a line that only echoes plain double-quoted text, are left out. Not seen: a Gradle call
 * through a shell variable, or from a script file that a workflow runs.
 */
class AndroidDependencyVerificationTest {

    private static final Path METADATA = Path.of("android", "gradle", "verification-metadata.xml");
    private static final Path REFRESH_SCRIPT = Path.of("tools", "refresh-android-verification.sh");

    /** The options a workflow's Gradle call may carry: those the workflows use, none with a value of its own. */
    private static final Set<String> OPTIONS = Set.of("--continue", "--stacktrace", "-q", "--console=plain");
    /**
     * The folders a step of a file that calls Gradle may run in or {@code cd} to: the root build's, the repository's
     * root, and {@code android-release.yml}'s folder of the signed APK.
     */
    private static final Set<String> DIRECTORIES = Set.of("android", ".", "release");

    /** The wrapper written plainly, as a command of its own: not quoted, not after a path, no more name after it. */
    private static final Pattern CALL = Pattern.compile("(?<![^\\s(;&|])\\./gradlew(?![^\\s)])");
    /** The wrapper in any form, and Gradle as a command, but not a path through a folder such as {@code gradle/}. */
    private static final Pattern GRADLE = Pattern.compile("(?i:gradlew)|(?<![\\w.-])gradle(?:\\.bat)?(?![\\w/.-])");
    /** A {@code cd} or {@code pushd} and its target, up to the end of its command. */
    private static final Pattern CD = Pattern.compile("(?<![^\\s;&|(])(?:cd|pushd)(?![^\\s;&|)])\\s*([^\\s;&|)]*)");
    /** The key as YAML reads it: plain, in quotes, with or without a space before its colon. */
    private static final Pattern WORKING_DIRECTORY =
            Pattern.compile("(?<![\\w-])working-directory[\"']?\\s*:(.*)");
    /** A {@code run:} with its command on the same line, which YAML joins with a deeper line below it. */
    private static final Pattern RUN = Pattern.compile("\\s*(?:- )?run: (?![|>]).*");
    /** A key that opens a literal block, whose lines YAML keeps apart; a folded block ({@code >}) joins them. */
    private static final Pattern LITERAL_BLOCK = Pattern.compile("\\s*(?:- )?(?:run|script):\\s+\\|[+-]?\\s*");
    /** A value in quotes that goes on on the next line, where YAML joins its lines; a key written with a {@code ?}. */
    private static final Pattern HIDES_STRUCTURE = Pattern.compile(":\\s+[\"'][^\"']*$|^\\s*(?:-\\s+)?\\?\\s");

    /**
     * The long flags, and the property {@code org.gradle.dependency.verification} however it is given: {@code -D},
     * {@code -P}, {@code GRADLE_OPTS}, {@code ORG_GRADLE_PROJECT_}, {@code gradle.properties}.
     */
    private static final Pattern WEAKENS = Pattern.compile(
            "--dependency-verification|--write-verification-metadata|(?i:dependency[._]?verification)");
    /**
     * Another root build, another Gradle user home, or an init script: the long flags, the variable and the property of
     * the user home, an {@code init.d} folder or {@code init.gradle} file, but not the system's {@code /etc/init.d}.
     * And Gradle run without its name as a command: the wrapper's jar, and an action's inputs for Gradle's arguments.
     */
    private static final Pattern ELSEWHERE = Pattern.compile("--project-dir|--init-script|--gradle-user-home"
            + "|(?<![\\w-])(?:arguments|build-root-directory)[\"']?\\s*:|gradle-wrapper[.]jar"
            + "|GRADLE_USER_HOME|gradle[.]user[.]home|(?<![\\w-])(?<!/etc/)init[.](?:d|gradle)\\b");
    /** The refresh script, which runs Gradle in write mode. */
    private static final Pattern REFRESH = Pattern.compile("refresh-android-verification");
    /** An {@code echo} of plain double-quoted text only: a message, which runs nothing it names. */
    private static final Pattern MESSAGE = Pattern.compile("\\s*(-\\s+)?(run:\\s*)?echo(\\s+\"[^\"$`\\\\]*\")+\\s*");
    /** A task name, with or without a project path. */
    private static final Pattern TASK = Pattern.compile(":?[A-Za-z][\\w:-]*");
    /** The options of the refresh script's Gradle call whose value is the next word. */
    private static final Set<String> SCRIPT_OPTIONS_WITH_VALUE =
            Set.of("--write-verification-metadata", "--init-script");

    /** The words after which a call starts a command, so nothing before it hands it arguments. */
    private static final Set<String> BEFORE_A_COMMAND = Set.of("&&", "||");
    /** The actions a file that calls Gradle may use: those the workflows use; another one may run Gradle itself. */
    private static final Set<String> ACTIONS = Set.of("actions/checkout", "actions/setup-java",
            "actions/upload-artifact", "actions/download-artifact", "actions/attest-build-provenance",
            "gradle/actions/setup-gradle", "reactivecircus/android-emulator-runner");
    private static final Pattern USES = Pattern.compile("(?<![\\w-])uses[\"']?\\s*:\\s*[\"']?([^@\\s\"']*)");
    /**
     * What a step would name to change the check's inputs before a call: the metadata file, a folder of a Gradle user
     * home other than its caches, a script the shell reads first, the JVM's user home.
     */
    private static final Pattern INPUTS = Pattern.compile("(?<!--write-)verification-metadata"
            + "|(?<![\\w.*-])[.]gradle(?![\\w.-]|/caches(?![/\\w.-]))|BASH_ENV|(?<!gradle[.])user[.]home");

    private static final Set<String> SOME_TASKS = Set.of("check", "lint", ":app:printReleaseVersion");

    @Test
    void theWorkflowsAndTheAndroidBuildsPropertiesAreRead() throws IOException {
        assertThat(commandFiles(Path.of(""))).contains(Path.of(".github", "workflows", "android.yml"),
                Path.of(".github", "workflows", "android-release.yml"), Path.of(".github", "workflows", "mutation.yml"),
                Path.of("android", "gradle.properties"));
    }

    @Test
    void aCompositeActionIsReadToo(@TempDir Path root) throws IOException {
        var action = root.resolve(Path.of(".github", "actions", "build", "action.yml"));
        Files.createDirectories(action.getParent());
        Files.writeString(action, "runs:\n");
        Files.createDirectories(root.resolve(Path.of(".github", "workflows")));
        Files.createDirectories(root.resolve("android"));

        assertThat(commandFiles(root)).contains(action);
    }

    @Test
    void everyWorkflowCompositeActionAndPropertiesFileKeepsTheRules() throws IOException {
        var tasks = scriptTasks(Files.readString(REFRESH_SCRIPT));
        for (var file : commandFiles(Path.of(""))) {
            assertThat(problems(file.toString(), Files.readString(file), tasks)).as(file.toString()).isEmpty();
        }
    }

    /** With no task allowed, each Gradle call of the workflows shows up by its tasks: the calls are seen. */
    @Test
    void theWorkflowsGradleCallsAreSeen() throws IOException {
        var workflows = Path.of(".github", "workflows");
        for (var expected : Map.of("android.yml", List.of("assembleDebugAndroidTest", "lint", "check",
                ":app:connectedDebugAndroidTest"), "android-release.yml", List.of(":app:printReleaseVersion",
                        "assembleRelease"), "mutation.yml", List.of(":app:pitestDebug")).entrySet()) {
            var file = workflows.resolve(expected.getKey());
            var problems = problems(file.toString(), Files.readString(file), Set.of());
            for (var task : expected.getValue()) {
                assertThat(problems).as(file.toString()).anySatisfy(problem -> assertThat(problem)
                        .contains("is followed by " + task + ","));
            }
        }
    }

    @Test
    void theRefreshScriptsTasksAreTheWordsOfItsGradleCallThatNameATask() throws IOException {
        assertThat(scriptTasks(Files.readString(REFRESH_SCRIPT))).contains("check", "assembleRelease",
                ":app:pitestDebug", ":app:connectedTestInputs", ":app:connectedDebugAndroidTest")
                .doesNotContain("sha256", "--continue");
        var script = "GRADLE_USER_HOME=\"$home\" ./gradlew --stop > /dev/null 2>&1 || true\n"
                + "# ./gradlew --write-verification-metadata sha256 bundleRelease, in a comment\n"
                + "./gradlew --no-daemon --write-verification-metadata sha256 --init-script initScript \\\n"
                + "  assembleDebug lint \\\n  -PpitClasses='x*'\n";
        assertThat(scriptTasks(script)).containsExactlyInAnyOrder("assembleDebug", "lint");
        assertThat(scriptTasks(script.replace(" lint ", " :app:connectedTestInputs "))).containsExactlyInAnyOrder(
                "assembleDebug", ":app:connectedTestInputs", ":app:connectedDebugAndroidTest");
    }

    @Test
    void aGradleCallHasOnlyTheAllowedOptionsAndTheScriptsTasks() {
        assertRefused("run: ./gradlew -p$DIR check", 1, "-p$DIR");
        assertRefused("run: ./gradlew -F${MODE} check", 1, "-F${MODE}");
        assertRefused("run: ./gradlew -pbuild-logic check", 1, "-pbuild-logic");
        assertRefused("run: ./gradlew -Iweaker.gradle check", 1, "-Iweaker.gradle");
        assertRefused("run: ./gradlew -I weaker.gradle check", 1, "-I", "weaker.gradle");
        assertRefused("run: ./gradlew -F off check", 1, "-F", "off");
        assertRefused("run: ./gradlew -M sha256 help", 1, "-M", "sha256", "help");
        assertRefused("run: ./gradlew -Dorg.gradle.caching=false check", 1, "-Dorg.gradle.caching=false");
        assertRefused("run: ./gradlew -PpitClasses=x check", 1, "-PpitClasses=x");
        assertRefused("run: ./gradlew --console plain check", 1, "--console", "plain");
        assertRefused("run: ./gradlew -x test check", 1, "-x", "test");
        assertRefused("run: ./gradlew --offline check", 1, "--offline");
        assertRefused("run: ./gradlew \"check\"", 1, "\"check\"");
        assertRefused("run: ./gradlew 'lint'", 1, "'lint'");
        assertRefused("run: ./gradlew ${{ matrix.task }}", 1, "${{", "matrix.task", "}}");
        assertRefused("run: ./gradlew $TASKS", 1, "$TASKS");
        assertRefused("run: ./gradlew :app:lint", 1, ":app:lint");
        assertRefused("run: ./gradlew check # -F off", 1, "#", "-F", "off");
        assertRefused("run: ./gradlew check || true", 1, "||", "true");
        assertRefused("  run: ./gradlew check \\\n    -F off", 1, "-F", "off");
        assertRefused("  - uses: actions/checkout@v6\n    run: out=$(./gradlew -q --info :app:printReleaseVersion)", 2,
                "--info");
        assertThat(problems("a.yml", "run: ./gradlew --stacktrace assembleRelease", SOME_TASKS)).singleElement()
                .asString().contains("assembleRelease", REFRESH_SCRIPT.toString(), "refresh the checksums");

        assertThat(problems("a.yml", """
                - run: ./gradlew --continue --stacktrace check
                  if: failure()
                - run: |
                    out=$(./gradlew -q --console=plain :app:printReleaseVersion)

                    ./gradlew lint
                  script: |-
                    ./gradlew check
                - run: ./gradlew --continue \\
                    check
                  run: mkdir -p build && ./gradlew check
                """, SOME_TASKS)).isEmpty();
    }

    @Test
    void aGradleCallThatYamlJoinsWithAnotherLineIsRefused() {
        var joins = "YAML joins this ./gradlew call";
        for (var joined : List.of("run: ./gradlew check\n  -F off", "- run: ./gradlew check\n\n    -p build-logic",
                "  run: ./gradlew check \\\n    lint\n    -F off", "run: >\n  ./gradlew check\n  -F off",
                "run: >-\n  ./gradlew\n  -M sha256 check", "run:\n  ./gradlew check\n  -F off",
                "./gradlew check\n-F off", "run: |\n  if true; then\n    ./gradlew check\n  fi")) {
            assertThat(problems("a.yml", joined, SOME_TASKS)).as(joined).anySatisfy(problem -> assertThat(problem)
                    .contains(joins));
        }
    }

    @Test
    void aSourceFolderNamedBuildIsReadAndABuildsOutputIsNot(@TempDir Path android) throws IOException {
        var source = android.resolve(Path.of("build-logic", "src", "main", "kotlin", "build", "Weaker.kt"));
        var hidden = android.resolve(Path.of("build-logic", "src", "main", "kotlin", ".gradle", "Hidden.kt"));
        var decoy = android.resolve(Path.of("build-logic", "src", "main", "kotlin", "build.gradle"));
        var output = android.resolve(Path.of("build-logic", "build", "generated", "Made.kt"));
        for (var file : List.of(source, hidden, decoy, output,
                android.resolve(Path.of("build-logic", "build.gradle.kts")))) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "");
        }

        assertThat(buildSources(android)).contains(source, hidden).doesNotContain(output);
    }

    @Test
    void theWrapperDownloadsGradleFromGradleWithARecordedChecksum() throws IOException {
        var wrapper = new Properties();
        try (var in = Files.newInputStream(Path.of("android", "gradle", "wrapper", "gradle-wrapper.properties"))) {
            wrapper.load(in);
        }
        assertThat(wrapper.getProperty("distributionUrl"))
                .matches("https://services[.]gradle[.]org/distributions/gradle-[0-9.]+-bin[.]zip");
        assertThat(wrapper.getProperty("distributionSha256Sum")).matches("[0-9a-f]{64}");
    }

    @Test
    void aCallThatDoesNotStartACommandAnotherActionAndAChangeOfTheChecksInputsAreRefused() {
        var call = "\nrun: ./gradlew check";
        assertRefused("run: echo -F off | xargs ./gradlew check", 1, "does not start a command");
        assertRefused("run: xargs -a ci/args ./gradlew check", 1, "does not start a command");
        assertRefused("run: env X=1 ./gradlew check", 1, "does not start a command");
        assertRefused("- uses: eskatos/gradle-command-action@v1" + call, 1, "eskatos/gradle-command-action");
        assertRefused("  uses: ./.github/actions/build" + call, 1, "./.github/actions/build");
        assertRefused("  \"uses\": 'burrunan/gradle-cache-action@v1'" + call, 1, "burrunan/gradle-cache-action");
        assertRefused("run: rm gradle/verification-metadata.xml", 1, "verification-metadata");
        assertRefused("run: mkdir -p ~/.gradle && cp ci/x.properties ~/.gradle/", 1, ".gradle", ".gradle");
        assertRefused("run: cp -r ci/home/. \"$HOME/.gradle\"", 1, ".gradle");
        assertRefused("  BASH_ENV: ci/env.sh", 1, "BASH_ENV");
        assertRefused("  JAVA_TOOL_OPTIONS: -Duser.home=ci/home", 1, "user.home");
        assertRefused("- run: echo x:|\n    ./gradlew check\n    -F off", 2, "YAML joins");
        assertRefused("- name: x\n  a: |\n    ./gradlew check", 3, "YAML joins");
        assertRefused("- run: \"echo\n    run: |\n     ./gradlew check\n    -F off\"", 1, "hides from this test");
        assertRefused("- ? uses\n  : x/y@v1", 1, "hides from this test");
        assertRefused("run: xargs -a ci/args -E run: ./gradlew check", 1, "does not start a command");
        assertRefused("run: xargs -a ci/args -E \\; ./gradlew check", 1, "does not start a command");
        assertRefused("run: xargs -a ci/args -E \\( ./gradlew check", 1, "does not start a command");
        assertRefused("  arguments: check -F off", 1, "arguments");
        assertRefused("  \"build-root-directory\": android", 1, "build-root-directory");
        assertRefused("- uses: eskatos/gradle-command-action@v1", 1, "eskatos/gradle-command-action");
        assertRefused("    uses: x/y/.github/workflows/g.yml@v1", 1, "x/y/.github/workflows/g.yml");
        assertRefused("run: cp ci/x.properties ~/.gradle/caches/../gradle.properties", 1, ".gradle");
        assertRefused("run: cp ci/x.properties \"$HOME/\".gradle/", 1, ".gradle");
        assertRefused("run: rm android/gradle/verification-metadata.*", 1, "verification-metadata");

        assertThat(problems("a.yml", """
                - uses: actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd # v6.0.2
                - uses: gradle/actions/setup-gradle@8da46b539cd8fe6ab27ca75128919a18d757604c
                  path: ~/.gradle/caches
                  key: ${{ hashFiles('android/**/*.gradle', 'android/gradle/libs.versions.toml') }}
                - run: |
                    out=$(./gradlew -q check)
                    cd android; ./gradlew lint
                    mkdir -p build || ./gradlew check
                """, SOME_TASKS)).isEmpty();
        assertThat(problems("a.yml", "- uses: some/other-action@v1\n    uses: ./.github/workflows/build.yml\n",
                SOME_TASKS)).isEmpty();
    }

    @Test
    void gradleNamedAnyOtherWayAsACommandIsRefused() {
        for (var other : List.of("\"./gradlew\"", "gradlew", "android/gradlew", "../gradlew", "gradlew.bat",
                "./gradlew.bat", "./GRADLEW", "gradle", "/opt/gradle/bin/gradle", "gradle.bat")) {
            assertRefused("run: sh " + other + " check", 1, other);
        }
        assertRefused("run: java -jar gradle/wrapper/gradle-wrapper.jar -F off check", 1, "gradle-wrapper.jar");
        assertRefused("run: java -cp x org.gradle.wrapper.GradleWrapperMain check", 1, "GradleWrapperMain");
        assertRefused("- uses: gradle/gradle-build-action@v3", 1, "gradle-build-action");
        assertRefused("run: out=$(gradle -q help)", 1, "out=$(gradle");

        assertThat(problems("a.yml", """
                - uses: gradle/actions/setup-gradle@8da46b539cd8fe6ab27ca75128919a18d757604c # v6.4.1
                  key: gradle-${{ hashFiles('android/**/*.gradle.kts') }}
                  path: ~/.gradle/caches
                run: cp android/gradle/libs.versions.toml build/reports/
                  cat android/gradle.properties
                # run: gradle check
                - name: Build with Gradle
                - run: echo "run ./gradlew check, or gradle check"
                  echo "::error::It failed Gradle's verification," \\
                    "run ./gradlew check"
                run: ./gradlew check
                """, SOME_TASKS)).isEmpty();
    }

    @Test
    void aFileThatCallsGradleRunsItsStepsOnlyInTheKnownFolders() {
        var call = "\nrun: ./gradlew check";
        assertRefused("run: cd android/build-logic && ./gradlew check", 1, "android/build-logic");
        assertRefused("run: cd android; cd build-logic; ./gradlew check", 1, "build-logic");
        assertRefused("run: cd \"$DIR\" && ./gradlew check", 1, "\"$DIR\"");
        assertRefused("run: cd && ./gradlew check", 1, "''");
        assertRefused("run: pushd android/build-logic" + call, 1, "android/build-logic");
        assertRefused("working-directory: android/build-logic" + call, 1, "android/build-logic");
        assertRefused("  working-directory: ./android" + call, 1, "./android");
        assertRefused("  working-directory: ${{ inputs.directory }}" + call, 1, "${{ inputs.directory }}");
        assertRefused("\"working-directory\": android/build-logic" + call, 1, "android/build-logic");
        assertRefused("'working-directory' : android/build-logic" + call, 1, "android/build-logic");

        assertThat(problems("a.yml", """
                    working-directory: android
                  working-directory: .
                run: |
                  cd release
                run: mkdir -p build && cd android && ./gradlew check
                """, SOME_TASKS)).isEmpty();
        assertThat(problems("a.yml", "  working-directory: desktop\nrun: cd target && ls\n", SOME_TASKS)).isEmpty();
    }

    @Test
    void theModesPropertyAnotherHomeAnInitScriptAndTheRefreshScriptAreRefused() {
        for (var weaker : Map.of("org.gradle.dependency.verification=lenient", "dependency.verification",
                "  GRADLE_OPTS: -Dorg.gradle.dependency.verification=strict", "dependency.verification",
                "  ORG_GRADLE_PROJECT_org.gradle.dependency.verification: off", "dependency.verification",
                "  ORG_GRADLE_DEPENDENCY_VERIFICATION: off", "DEPENDENCY_VERIFICATION",
                "  dependencyVerification: lenient", "dependencyVerification",
                "  ARGS: --dependency-verification=off", "--dependency-verification",
                "  ARGS: --write-verification-metadata sha256", "--write-verification-metadata",
                "  ARGS: --project-dir build-logic", "--project-dir",
                "  ARGS: --init-script weaker.gradle", "--init-script").entrySet()) {
            assertRefused(weaker.getKey(), 1, weaker.getValue());
        }
        for (var weaker : Map.of("run: cp weaker.gradle home/init.d/", "init.d",
                "run: cp weaker.gradle \"$DIR/init.gradle\"", "init.gradle",
                "run: cp weaker.gradle.kts home/init.gradle.kts", "init.gradle",
                "  GRADLE_USER_HOME: ${{ runner.temp }}/home", "GRADLE_USER_HOME",
                "  GRADLE_OPTS: -Dgradle.user.home=home", "gradle.user.home",
                "run: sh tools/refresh-android-verification.sh", "refresh-android-verification",
                "run: ../tools/refresh-android-verification.sh --clean", "refresh-android-verification",
                "run: echo \"refresh\" && sh tools/refresh-android-verification.sh", "refresh-android-verification",
                "run: echo \"$(sh tools/refresh-android-verification.sh)\"", "refresh-android-verification",
                "      - \"tools/refresh-android-verification.sh\"", "refresh-android-verification").entrySet()) {
            assertRefused(weaker.getKey(), 1, weaker.getValue());
        }

        assertThat(problems("a.yml", """
                # org.gradle.dependency.verification=lenient
                    # ./gradlew -F off
                  if [ -d build/reports/dependency-verification ]; then
                  run: sudo /etc/init.d/docker restart
                - run: echo "run sh tools/refresh-android-verification.sh"
                    echo "::error::run sh tools/refresh-android-verification.sh," \\
                      "read the diff"
                  run: mvn -B -f pom.xml && grep -Fqx "$ref" tags
                """, SOME_TASKS)).isEmpty();
    }

    /**
     * {@code disableDependencyVerification()} of a configuration, and {@code gradle.startParameter}'s
     * {@code dependencyVerificationMode}, both found by the mode's pattern, in comments too.
     */
    @Test
    void noAndroidBuildFileSetsTheModeOrSwitchesTheVerificationOffForAConfiguration() throws IOException {
        var sources = buildSources(Path.of("android"));
        assertThat(sources).contains(Path.of("android", "app", "build.gradle.kts"),
                Path.of("android", "settings.gradle.kts"), Path.of("android", "build-logic", "build.gradle.kts"),
                Path.of("android", "gradlew"), Path.of("android", "gradlew.bat"),
                Path.of("android", "gradle", "wrapper", "gradle-wrapper.properties"));
        for (var file : sources) {
            assertThat(modeLines(Files.readString(file))).as("lines of %s that set the verification mode", file)
                    .isEmpty();
        }
        for (var weaker : List.of("    resolutionStrategy.disableDependencyVerification()",
                "gradle.startParameter.dependencyVerificationMode = DependencyVerificationMode.OFF",
                "    // startParameter.setDependencyVerificationMode(LENIENT)",
                "DEFAULT_JVM_OPTS='\"-Xmx64m\" \"-Dorg.gradle.dependency.verification=off\"'",
                "systemProp.org.gradle.dependency.verification=lenient")) {
            assertThat(modeLines(weaker)).as(weaker).hasSize(1);
        }
    }

    @Test
    void theMetadataChecksPomsWithSha256OnlyAndHasNoKeys() throws Exception {
        var metadata = metadata();
        var configuration = single(metadata, "configuration");

        assertThat(single(configuration, "verify-metadata").getTextContent().strip()).isEqualTo("true");
        assertThat(single(configuration, "verify-signatures").getTextContent().strip()).isEqualTo("false");
        for (var name : List.of("trusted-keys", "trusted-key", "ignored-keys", "ignored-key", "also-trust", "pgp",
                "md5", "sha1", "sha512", "key-servers")) {
            assertThat(metadata.getElementsByTagNameNS("*", name).getLength()).as(name).isZero();
        }
        var artifacts = elements(metadata, "artifact");
        assertThat(elements(metadata, "component")).isNotEmpty();
        assertThat(artifacts).isNotEmpty().allSatisfy(artifact -> assertThat(children(artifact))
                .as(artifact.getAttribute("name")).isNotEmpty()
                .allSatisfy(checksum -> assertThat(checksum.getLocalName()).isEqualTo("sha256")));
    }

    @Test
    void onlyTheSourcesAndJavadocOfALibraryAreTrustedWithoutAChecksum() throws Exception {
        var configuration = single(metadata(), "configuration");
        var trusted = children(single(configuration, "trusted-artifacts")).stream().map(trust -> {
            assertThat(trust.getLocalName()).isEqualTo("trust");
            return IntStream.range(0, trust.getAttributes().getLength()).mapToObj(trust.getAttributes()::item)
                    .filter(attribute -> !attribute.getNodeName().equals("reason"))
                    .map(attribute -> attribute.getNodeName() + "=" + attribute.getNodeValue()).sorted()
                    .collect(Collectors.joining(" "));
        }).toList();

        assertThat(trusted).containsExactlyInAnyOrder("file=.*-javadoc[.]jar regex=true",
                "file=.*-sources[.]jar regex=true");
        assertThat(elements(configuration, "trusted-artifacts")).hasSize(1);
    }

    /** {@code text}, as file {@code a.yml}, breaks the rules once for each of {@code words}, on line {@code line}. */
    private static void assertRefused(String text, int line, String... words) {
        var problems = problems("a.yml", text, SOME_TASKS);
        assertThat(problems).as(text).hasSize(words.length);
        for (int i = 0; i < words.length; i++) {
            assertThat(problems.get(i)).as(text).contains("a.yml line " + line + ": ", words[i]);
        }
    }

    /**
     * What in {@code text}, the file {@code name}, breaks a rule, each naming the line and the word; {@code tasks} are
     * the tasks a Gradle call may name.
     */
    private static List<String> problems(String name, String text, Set<String> tasks) {
        var raw = text.lines().toList();
        var lines = commandLines(text);
        var callsGradle = lines.values().stream().anyMatch(line -> GRADLE.matcher(line).find());
        var problems = new ArrayList<String>();
        lines.forEach((number, line) -> {
            var at = "%s line %d: ".formatted(name, number);
            Stream.of(WEAKENS, ELSEWHERE, REFRESH, INPUTS).flatMap(pattern -> pattern.matcher(line).results())
                    .forEach(found -> problems.add(at + found.group()
                            + " weakens or gets around the dependency verification, or writes the checksums"));
            var calls = CALL.matcher(line).results().toList();
            var wrappers = calls.stream().map(call -> call.start() + "./".length()).collect(Collectors.toSet());
            GRADLE.matcher(line).results().filter(found -> !wrappers.contains(found.start()))
                    .forEach(found -> problems.add(at + wordAround(line, found)
                            + " names Gradle otherwise than as a plain ./gradlew call"));
            if (!calls.isEmpty() && joinedByYaml(raw, number - 1)) {
                problems.add(at + "YAML joins this ./gradlew call with another line: write it on its run: line with"
                        + " nothing deeper below it, or in a literal block (run: |)");
            }
            for (var call : calls) {
                if (!startsCommand(line.substring(0, call.start()))) {
                    problems.add(at + "./gradlew does not start a command, so what stands before it may hand it"
                            + " arguments");
                }
                var rest = line.substring(call.end());
                var end = rest.indexOf(')');
                for (var word : (end < 0 ? rest : rest.substring(0, end)).strip().split("\\s+")) {
                    if (!word.isEmpty() && !OPTIONS.contains(word) && !tasks.contains(word)) {
                        problems.add(at + ("./gradlew is followed by %s, which is neither an option this test allows"
                                + " nor a task %s runs: add the option to OPTIONS of this test, or the task to the"
                                + " script's Gradle call and refresh the checksums").formatted(word, REFRESH_SCRIPT));
                    }
                }
            }
            USES.matcher(line).results().map(found -> found.group(1)).filter(action -> !ACTIONS.contains(action))
                    .filter(action -> callsGradle || action.toLowerCase(Locale.ROOT).contains("gradle")
                            || action.contains(".github/workflows/") && !action.startsWith("./"))
                    .forEach(action -> problems.add(at + ("'%s' is used, not one of the actions %s, and it may run"
                            + " Gradle itself").formatted(action, ACTIONS)));
            if (HIDES_STRUCTURE.matcher(line).find()) {
                problems.add(at + "a value in quotes over several lines, or a key written with a ?, hides from this"
                        + " test what YAML reads");
            }
            if (callsGradle) {
                Stream.of(CD, WORKING_DIRECTORY).flatMap(pattern -> pattern.matcher(line).results())
                        .map(found -> found.group(1).strip()).filter(folder -> !DIRECTORIES.contains(folder))
                        .forEach(folder -> problems.add(at + ("a step of a file that calls Gradle runs in '%s', not"
                                + " in one of the folders %s").formatted(folder, DIRECTORIES)));
            }
        });
        return problems;
    }

    /** Whether a call after {@code before} starts a command: first, in a {@code (} or after a separator. */
    private static boolean startsCommand(String before) {
        var words = before.strip().split("\\s+");
        var last = words[words.length - 1];
        if (last.isEmpty() || before.strip().matches("(?:-\\s+)?run:")) {
            return true;
        }
        var separated = last.matches(".*(?<!\\\\)[;(]");
        return last.endsWith("(") ? separated : separated || BEFORE_A_COMMAND.contains(last);
    }

    /**
     * Whether YAML may read the command that starts on line {@code index} of {@code lines} together with another line:
     * it is on its {@code run:} line with a deeper line below it, or in anything but a literal block.
     */
    private static boolean joinedByYaml(List<String> lines, int index) {
        var line = lines.get(index);
        if (RUN.matcher(line).matches()) {
            int next = index;
            while (lines.get(next).endsWith("\\") && next + 1 < lines.size()) {
                next++;
            }
            return lines.stream().skip(next + 1).filter(below -> !below.isBlank()).findFirst()
                    .filter(below -> indent(below) > line.indexOf("run:")).isPresent();
        }
        return IntStream.iterate(index - 1, above -> above >= 0, above -> above - 1).mapToObj(lines::get)
                .filter(above -> !above.isBlank() && indent(above) < indent(line)).findFirst()
                .filter(above -> LITERAL_BLOCK.matcher(above).matches()).isEmpty();
    }

    private static int indent(String line) {
        return line.length() - line.stripLeading().length();
    }

    /** The word of {@code line}, up to whitespace on either side, that holds {@code found}. */
    private static String wordAround(String line, MatchResult found) {
        int start = found.start();
        while (start > 0 && !Character.isWhitespace(line.charAt(start - 1))) {
            start--;
        }
        int end = found.end();
        while (end < line.length() && !Character.isWhitespace(line.charAt(end))) {
            end++;
        }
        return line.substring(start, end);
    }

    /**
     * The lines of {@code text} by their number, a line continued with a backslash joined with the next, without
     * comment lines and messages.
     */
    private static Map<Integer, String> commandLines(String text) {
        var raw = text.lines().toList();
        var lines = new LinkedHashMap<Integer, String>();
        for (int i = 0; i < raw.size(); i++) {
            int first = i;
            var line = new StringBuilder(raw.get(i));
            while (line.toString().endsWith("\\") && i + 1 < raw.size()) {
                line.setLength(line.length() - 1);
                line.append(' ').append(raw.get(++i));
            }
            var joined = line.toString();
            if (!joined.strip().startsWith("#") && !MESSAGE.matcher(joined).matches()) {
                lines.put(first + 1, joined);
            }
        }
        return lines;
    }

    /** The lines of a build script or source that name the verification mode or switch it off, comments too. */
    private static List<String> modeLines(String text) {
        return text.lines().filter(line -> WEAKENS.matcher(line).find()).toList();
    }

    /**
     * The tasks of the refresh script's Gradle call, the one that writes the checksums: its words that name a task,
     * without the values of its options. {@code :app:connectedDebugAndroidTest} needs a device, so the script runs
     * {@code :app:connectedTestInputs}, which resolves that task's input files, in its place.
     */
    private static Set<String> scriptTasks(String script) {
        var calls = commandLines(script).values().stream()
                .filter(line -> line.contains("./gradlew") && line.contains("--write-verification-metadata")).toList();
        assertThat(calls).as("the Gradle call of the refresh script").hasSize(1);
        var call = calls.getFirst();
        var tasks = new HashSet<String>();
        var previous = "";
        for (var word : call.substring(call.indexOf("./gradlew")).split("\\s+")) {
            if (TASK.matcher(word).matches() && !SCRIPT_OPTIONS_WITH_VALUE.contains(previous)) {
                tasks.add(word);
            }
            previous = word;
        }
        if (tasks.contains(":app:connectedTestInputs")) {
            tasks.add(":app:connectedDebugAndroidTest");
        }
        return tasks;
    }

    /**
     * Every workflow and composite action under {@code root}, and every {@code gradle.properties} of the Android build
     * and its included builds.
     */
    private static List<Path> commandFiles(Path root) throws IOException {
        var files = new ArrayList<Path>();
        var yaml = Pattern.compile(".*[.]ya?ml");
        for (var folder : List.of(root.resolve(Path.of(".github", "workflows")),
                root.resolve(Path.of(".github", "actions")))) {
            if (Files.isDirectory(folder)) {
                try (Stream<Path> found = Files.find(folder, Integer.MAX_VALUE,
                        (file, attributes) -> attributes.isRegularFile()
                                && yaml.matcher(file.getFileName().toString()).matches())) {
                    found.sorted().forEach(files::add);
                }
            }
        }
        // android/ and the folders of its projects and included builds
        try (Stream<Path> android = Files.find(root.resolve("android"), 2,
                (file, attributes) -> file.getFileName().toString().equals("gradle.properties"))) {
            android.sorted().forEach(files::add);
        }
        return files;
    }

    /**
     * The Android build's Gradle scripts, the Java and Kotlin sources of its builds, its properties files in any folder
     * and the wrapper's scripts, whose JVM options reach Gradle, without what a build wrote: a {@code build},
     * {@code .gradle} or {@code .kotlin} folder beside a build or settings script and not below a {@code src} folder.
     */
    private static List<Path> buildSources(Path android) throws IOException {
        var kinds = Pattern.compile(".*\\.(gradle|gradle\\.kts|java|kt|properties)|gradlew(\\.bat)?");
        var written = List.of("build", ".gradle", ".kotlin");
        var project = List.of("build.gradle.kts", "settings.gradle.kts", "build.gradle", "settings.gradle");
        var sources = new ArrayList<Path>();
        Files.walkFileTree(android, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path folder, BasicFileAttributes attributes) {
                var name = folder.getFileName().toString();
                var output = written.contains(name)
                        && StreamSupport.stream(android.relativize(folder).spliterator(), false)
                                .noneMatch(part -> part.toString().equals("src"))
                        && project.stream().anyMatch(file -> Files.exists(folder.resolveSibling(file)));
                return output ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (kinds.matcher(file.getFileName().toString()).matches()) {
                    sources.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return sources.stream().sorted().toList();
    }

    /** {@code verification-metadata.xml}, parsed without a document type, so no entity of its own is read. */
    private static Document metadata() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(METADATA.toFile());
    }

    private static List<Element> elements(Node root, String name) {
        var nodes = root instanceof Document document ? document.getElementsByTagNameNS("*", name)
                : ((Element) root).getElementsByTagNameNS("*", name);
        return IntStream.range(0, nodes.getLength()).mapToObj(i -> (Element) nodes.item(i)).toList();
    }

    private static Element single(Node root, String name) {
        var found = elements(root, name);
        assertThat(found).as(name).hasSize(1);
        return found.getFirst();
    }

    private static List<Element> children(Element parent) {
        var nodes = parent.getChildNodes();
        return IntStream.range(0, nodes.getLength()).mapToObj(nodes::item).filter(Element.class::isInstance)
                .map(Element.class::cast).toList();
    }
}
