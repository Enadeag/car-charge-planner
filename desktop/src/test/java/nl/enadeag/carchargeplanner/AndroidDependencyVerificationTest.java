package nl.enadeag.carchargeplanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
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
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;
import org.snakeyaml.engine.v2.schema.CoreSchema;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Gradle's dependency verification of the Android build stays strict, its default, everywhere (#484). Gradle's strict
 * default is the real check; this test is a tripwire beside it, here in the Maven build, so every pull request that
 * changes a workflow runs it.
 *
 * <p>The workflows and composite actions are read with a YAML parser, as line patterns kept having ways around; a file
 * that does not parse fails, and the rules hold the parsed data, with aliases and merge keys resolved. The command
 * strings are the {@code run} of each step and the {@code script} and {@code pre-emulator-launch-script} inputs of the
 * emulator action, text with no control character but tab and line feed, read as shell lines split at line feeds with
 * a backslash's continuation joined. A shell line that names Gradle is, as a whole, {@code ./gradlew WORDS} or
 * {@code name=$(./gradlew WORDS)}, each word one of {@link #OPTIONS} or a task the refresh script runs too; a line that
 * only echoes plain double-quoted text is a message and is left out. No other key or value names Gradle as a command.
 *
 * <p>In a file that calls Gradle: a step that calls it runs in {@code android}, by its own {@code working-directory},
 * its job's default or the workflow's (the emulator action by its input), and does not change its folder; every other
 * {@code working-directory} and {@code cd} goes to a folder of {@link #DIRECTORIES}; only the actions of
 * {@link #ACTIONS} are used, each with the inputs of {@link #INPUTS_OF}; no job sets a container or services, no step
 * another shell than those of {@link #SHELLS}; no command holds a here-document or a line that ends in an expression.
 * In every file: no other action with "gradle" in its name, no workflow of another repository, no local action outside
 * {@code .github/actions} or {@code .github/workflows}, no key {@code arguments} or {@code build-root-directory}. Every
 * key, value and shell line is held to the deny patterns: the verification flags and the mode's property in any
 * spelling, another Gradle user home, an init script, the wrapper's jar, the refresh script, which writes the checksums
 * instead of checking them, and the check's inputs (the metadata file, a Gradle user home's folder, {@code BASH_ENV},
 * {@code user.home}).
 *
 * <p>Beside that: no {@code gradle.properties} names those either, in its lines or its decoded keys and values; no
 * build script, source, properties file or wrapper script of the Android build sets the mode; the wrapper's scripts
 * and jar are the ones recorded in {@link #WRAPPER}, and it downloads Gradle from Gradle with a checksum into Gradle's
 * own home; no other folder holds a root build with a wrapper; and {@code verification-metadata.xml} checks poms too,
 * SHA-256 only, without keys, and trusts only an IDE's sources and Javadoc.
 *
 * <p>Not seen, and left to the maintainer's review of a workflow or build change: a Gradle call through a shell
 * variable or from a script file a workflow runs; a step that changes the build's files or environment before the call
 * by a path this test does not know ({@code cat file >> "$GITHUB_ENV"}, a sourced script,
 * {@code curl ... >> gradle.properties}); build scripts that apply code from outside {@code android/}, from a build's
 * output folder, or use {@code files(...)} dependencies. Text written to hide from a reviewer is out of scope.
 */
class AndroidDependencyVerificationTest {

    private static final Path METADATA = Path.of("android", "gradle", "verification-metadata.xml");
    private static final Path REFRESH_SCRIPT = Path.of("tools", "refresh-android-verification.sh");

    /** The options a workflow's Gradle call may carry: those the workflows use, none with a value of its own. */
    private static final Set<String> OPTIONS = Set.of("--continue", "--stacktrace", "-q", "--console=plain");
    /**
     * The folders a step of a file that calls Gradle may run in or {@code cd} to when it does not call Gradle itself:
     * the root build's, the repository's root, and {@code android-release.yml}'s folder of the signed APK.
     */
    private static final Set<String> DIRECTORIES = Set.of("android", ".", "release");
    /** The actions a file that calls Gradle may use: those the workflows use; another one may run Gradle itself. */
    private static final Set<String> ACTIONS = Set.of("actions/checkout", "actions/setup-java",
            "actions/upload-artifact", "actions/download-artifact", "actions/attest-build-provenance",
            "gradle/actions/setup-gradle", "reactivecircus/android-emulator-runner");
    private static final String EMULATOR = "reactivecircus/android-emulator-runner";
    /** The emulator action's inputs that it runs as shell lines. */
    private static final Set<String> SCRIPT_INPUTS = Set.of("script", "pre-emulator-launch-script");
    /** The inputs of Gradle's former actions that hand Gradle its arguments or another root build. */
    private static final Set<String> GRADLE_INPUTS = Set.of("arguments", "build-root-directory");
    /**
     * The inputs each action of {@link #ACTIONS} may be given in a file that calls Gradle: those the workflows give it.
     * Another input may change what Gradle runs or checks, such as {@code validate-wrappers} or an init script of
     * {@code setup-gradle}, or {@code repository} and {@code ref} of {@code checkout}.
     */
    private static final Map<String, Set<String>> INPUTS_OF = Map.of(
            "actions/checkout", Set.of("fetch-depth", "persist-credentials"),
            "actions/setup-java", Set.of("cache", "distribution", "java-version"),
            "actions/upload-artifact", Set.of("if-no-files-found", "name", "overwrite", "path", "retention-days"),
            "actions/download-artifact", Set.of("name", "path"),
            "actions/attest-build-provenance", Set.of("subject-path"),
            "gradle/actions/setup-gradle", Set.of("cache-disabled", "cache-provider", "cache-read-only"),
            EMULATOR, Set.of("api-level", "arch", "disable-animations", "emulator-options", "force-avd-creation",
                    "target", "working-directory", "script", "pre-emulator-launch-script"));
    /** The shells a step of a file that calls Gradle may name: the one GitHub uses by default on Linux. */
    private static final Set<String> SHELLS = Set.of("bash");
    /** What sets a job's environment from outside the runner: its image, and the images it runs beside it. */
    private static final Set<String> JOB_ENVIRONMENTS = Set.of("container", "services");
    /** A local action or workflow in the folders this test reads. */
    private static final Pattern LOCAL_ACTION = Pattern.compile("\\./\\.github/(?:actions|workflows)/.+");
    /**
     * The wrapper's files, by their SHA-256: its scripts with every line end as LF, as a checkout may give them other
     * line ends, and its jar as it is. A wrapper update records the new values.
     */
    private static final Map<Path, String> WRAPPER = Map.of(
            Path.of("android", "gradlew"), "a5a5c199ba02189ae8c46a334223371a20599d9c298ef65e7540ede4a3f72d59",
            Path.of("android", "gradlew.bat"), "31ec098bd961c460b62875950c3eff5c8213bcb0bbde38d769b88512877fd21f",
            Path.of("android", "gradle", "wrapper", "gradle-wrapper.jar"),
            "621008afc4f5d59357fcf06ffa644dd5aade5e091bdc363742710445f73cdaf8");
    /** The files that make a folder a root build Gradle can run, with a wrapper to run it. */
    private static final List<String> ROOT_BUILD = List.of("gradlew", "gradlew.bat", "settings.gradle",
            "settings.gradle.kts");

    /**
     * YAML 1.2's core schema, in which {@code on} is a key, as GitHub reads it; a duplicate key fails, as on GitHub; no
     * variables are filled in. The parser makes maps, lists and scalars only, never a type a document names.
     */
    private static final LoadSettings YAML = LoadSettings.builder().setSchema(new CoreSchema())
            .setAllowDuplicateKeys(false).setEnvConfig(Optional.empty()).build();

    /** A shell line that calls Gradle as a whole: the wrapper and words, alone or in an assignment's {@code $(...)}. */
    private static final Pattern CALL =
            Pattern.compile("(?<name>[a-z][a-z0-9_]*=\\$\\()?\\./gradlew"
                    + "(?<words>(?:[ \\t]+[^\\s)]+)*)[ \\t]*(?<close>\\))?");
    /** The wrapper in any form, and Gradle as a command, but not a path through a folder such as {@code gradle/}. */
    private static final Pattern GRADLE = Pattern.compile("(?i:gradlew)|(?<![\\w.-])gradle(?:\\.bat)?(?![\\w/.-])");
    /** A {@code cd} or {@code pushd} and its target, up to the end of its command. */
    private static final Pattern CD = Pattern.compile("(?<![^\\s;&|(])(?:cd|pushd)(?![^\\s;&|)])\\s*([^\\s;&|)]*)");

    /**
     * The long flags, and the property {@code org.gradle.dependency.verification} however it is given: {@code -D},
     * {@code -P}, {@code GRADLE_OPTS}, {@code ORG_GRADLE_PROJECT_}, {@code gradle.properties}.
     */
    private static final Pattern WEAKENS = Pattern.compile(
            "--dependency-verification|--write-verification-metadata|(?i:dependency[._]?verification)");
    /**
     * Another root build, another Gradle user home, or an init script: the long flags, the variable and the property of
     * the user home, an {@code init.d} folder or {@code init.gradle} file, but not the system's {@code /etc/init.d}.
     * And Gradle run without its name as a command: the wrapper's jar.
     */
    private static final Pattern ELSEWHERE = Pattern.compile("--project-dir|--init-script|--gradle-user-home"
            + "|gradle-wrapper[.]jar|GRADLE_USER_HOME|gradle[.]user[.]home"
            + "|(?<![\\w-])(?<!/etc/)init[.](?:d|gradle)\\b");
    /** The refresh script, which runs Gradle in write mode. */
    private static final Pattern REFRESH = Pattern.compile("refresh-android-verification");
    /**
     * What a step would name to change the check's inputs before a call: the metadata file, a folder of a Gradle user
     * home other than its caches, a script the shell reads first, the JVM's user home.
     */
    private static final Pattern INPUTS = Pattern.compile("(?<!--write-)verification-metadata"
            + "|(?<![\\w.*-])[.]gradle(?![\\w.-]|/caches(?![/\\w.-]))|BASH_ENV|(?<!gradle[.])user[.]home");
    /** An {@code echo} of plain double-quoted text only: a message, which runs nothing it names. */
    private static final Pattern MESSAGE = Pattern.compile("\\s*echo(\\s+\"[^\"$`\\\\]*\")+\\s*");
    /** A task name, with or without a project path. */
    private static final Pattern TASK = Pattern.compile(":?[A-Za-z][\\w:-]*");
    /** The options of the refresh script's Gradle call whose value is the next word. */
    private static final Set<String> SCRIPT_OPTIONS_WITH_VALUE =
            Set.of("--write-verification-metadata", "--init-script");

    private static final String WEAKENS_MESSAGE =
            " weakens or gets around the dependency verification, or writes the checksums";

    private static final Set<String> SOME_TASKS = Set.of("check", "lint", ":app:printReleaseVersion");

    /** A position in a parsed list; any other part of a path is a key of a map. */
    private record Index(int value) {
    }

    /** A key of a parsed map, with the value it holds, or a scalar value, each with the path that leads to it. */
    private record Found(List<Object> path, String text, boolean key, Object value) {
    }

    /** A line of a command string, continued lines joined, by the number of its first line. */
    private record ShellLine(int number, String text) {
    }

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
        assertRefused(run("./gradlew -p$DIR check"), "-p$DIR");
        assertRefused(run("./gradlew -F${MODE} check"), "-F${MODE}");
        assertRefused(run("./gradlew -pbuild-logic check"), "-pbuild-logic");
        assertRefused(run("./gradlew -Iweaker.gradle check"), "-Iweaker.gradle");
        assertRefused(run("./gradlew -I weaker.gradle check"), "-I", "weaker.gradle");
        assertRefused(run("./gradlew -F off check"), "-F", "off");
        assertRefused(run("./gradlew -M sha256 help"), "-M", "sha256", "help");
        assertRefused(run("./gradlew -Dorg.gradle.caching=false check"), "-Dorg.gradle.caching=false");
        assertRefused(run("./gradlew -PpitClasses=x check"), "-PpitClasses=x");
        assertRefused(run("./gradlew --console plain check"), "--console", "plain");
        assertRefused(run("./gradlew -x test check"), "-x", "test");
        assertRefused(run("./gradlew --offline check"), "--offline");
        assertRefused(run("./gradlew \"check\""), "\"check\"");
        assertRefused(run("./gradlew 'lint'"), "'lint'");
        assertRefused(run("./gradlew ${{ matrix.task }}"), "ends in an expression", "${{", "matrix.task", "}}");
        assertRefused(run("./gradlew $TASKS"), "$TASKS");
        assertRefused(run("./gradlew :app:lint"), ":app:lint");
        assertRefused(run("./gradlew check # -F off"), "#", "-F", "off");
        assertRefused(run("./gradlew check || true"), "||", "true");
        assertRefused(run("./gradlew check && echo ok"), "&&", "echo", "ok");
        assertRefused(run("./gradlew check | tee out"), "|", "tee", "out");
        assertRefused(run("./gradlew check \\\n    -F off"), "-F", "off");
        assertRefused(run("out=$(./gradlew -q --info :app:printReleaseVersion)"), "--info");
        assertThat(problems("a.yml", run("./gradlew --stacktrace assembleRelease"), SOME_TASKS)).singleElement()
                .asString().contains("assembleRelease", REFRESH_SCRIPT.toString(), "refresh the checksums");

        assertThat(problems("a.yml", workflow("run: ./gradlew --continue --stacktrace check\nif: failure()", """
                run: |
                  out=$(./gradlew -q --console=plain :app:printReleaseVersion)

                  ./gradlew lint
                  ./gradlew --continue \\
                    check""", """
                uses: reactivecircus/android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d
                with:
                  working-directory: android
                  pre-emulator-launch-script: ./gradlew check
                  script: |-
                    ./gradlew --continue --stacktrace check"""), SOME_TASKS)).isEmpty();
    }

    @Test
    void aShellLineThatNamesGradleIsOneOfTheAllowedFormsAsAWhole() {
        var form = "is not ./gradlew WORDS";
        for (var line : List.of("echo -F off | xargs ./gradlew check", "xargs -a ci/args ./gradlew check",
                "env X=1 ./gradlew check", "sudo ./gradlew check", "time ./gradlew check", "`./gradlew check`",
                "xargs -a ci/args -E run: ./gradlew check", "xargs -a ci/args -E \\; ./gradlew check",
                "xargs -a ci/args -E \\( ./gradlew check", "if true; then ./gradlew check -F off; fi",
                "mkdir -p build && ./gradlew check", "mkdir -p build || ./gradlew check",
                "./gradlew check)", "out=$(./gradlew check", "./gradlew $(echo -F)",
                "GRADLE_OPTS=$(./gradlew check)", "Out=$(./gradlew check)")) {
            assertRefused(run(line), form);
        }
        assertRefused(run("cd android; ./gradlew lint"), form, "changes its folder");
        assertRefused(run("if true; then\n  ./gradlew check -F off\nfi"), "-F", "off");
    }

    /** A value over several lines is one command when YAML reads it as one line, whatever it looks like. */
    @Test
    void aGradleCallIsReadAsYamlReadsIt() {
        assertRefused(workflow("run: ./gradlew check\n  -F off"), "-F", "off");
        assertRefused(workflow("run: ./gradlew check \\\n  lint\n  -F off"), "\\", "-F", "off");
        assertRefused(workflow("run: >\n  ./gradlew check\n  -F off"), "-F", "off");
        assertRefused(workflow("run: >-\n  ./gradlew\n  -M sha256 check"), "-M", "sha256");
        assertRefused(workflow("run:\n  ./gradlew check\n  -F off"), "-F", "off");
        assertRefused(workflow("run: \"./gradlew check\n  -F off\""), "-F", "off");
        assertRefused(workflow("\"run\": ./gradlew check -F off"), "-F", "off");
        assertRefused(workflow("? run\n: ./gradlew check -F off"), "-F", "off");
        assertRefused(workflow("{run: ./gradlew check -F off}"), "-F", "off");
        assertRefused(workflow("run: echo x:|\n  ./gradlew check\n  -F off"), "is not ./gradlew WORDS");
        assertRefused(workflow("run: \"echo\n  run: |\n   ./gradlew check\n  -F off\""), "is not ./gradlew WORDS");
        assertRefused(workflow("env:\n  CALL: &call ./gradlew check -F off\nrun: *call"), "outside a command string",
                "-F", "off");
        assertRefused("./gradlew check\n-F off", "outside a command string");
        assertRefused(workflow("name: x\na: |\n  ./gradlew check"), "outside a command string");

        // a blank line keeps the lines apart: the second runs on its own, without Gradle
        assertThat(problems("a.yml", workflow("run: ./gradlew check\n\n  -p build-logic"), SOME_TASKS)).isEmpty();
    }

    @Test
    void aFileThatDoesNotParseOrHidesWhatItHoldsIsRefused() {
        assertRefused("run: [./gradlew check", "does not parse");
        assertRefused(workflow("run: xargs -E run: ./gradlew check"), "does not parse");
        assertRefused(workflow("run: ./gradlew check\nrun: ./gradlew check -F off"), "does not parse");
        assertRefused(workflow("with: !gradle {arguments: -F off}"), "does not parse");
        assertRefused(workflow("env: &loop [*loop]"), "holds itself");
        assertRefused(workflow("uses: [x/y@v1]"), "holds no plain text");
        assertRefused(workflow("run: ./gradlew check\nworking-directory: [android/build-logic]"),
                "holds no plain text", "not in android");
        assertRefused(workflow("run: !!float 1.5"), "no text but Double");
        assertRefused(workflow("uses: !!int 5"), "no text but Integer");
        assertRefused(workflow("working-directory: !!int 1\nrun: echo"), "no text but Integer");
        assertRefused(workflow("run: !!binary aGk="), "a.yml");
        assertRefused(workflow("""
                uses: reactivecircus/android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d
                with:
                  working-directory: android
                  script: !!int 1"""), "no text but Integer");
    }

    @Test
    void aCommandStringHoldsNoOtherControlCharacterThanTabAndLineFeed() {
        assertRefused(workflow("run: \"echo -F off | xargs -E \\r ./gradlew check\""), "U+000D",
                "is not ./gradlew WORDS");
        assertRefused(workflow("run: \"./gradlew check\\u000b\""), "U+000B");
        assertRefused(workflow("run: \"echo x\\u0085y\""), "U+0085");

        assertThat(problems("a.yml", workflow("run: \"./gradlew\\tcheck\"", "run: \"echo a\\nb\""), SOME_TASKS))
                .isEmpty();
    }

    @Test
    void aFileThatCallsGradleHasNoHereDocumentNorALineEndingInAnExpression() {
        assertRefused(run("xargs env <<'EOF'\n./gradlew check\n-F off\nEOF"), "<<");
        assertRefused(run("cat > x <<EOF\nEOF\n./gradlew check"), "<<");
        assertRefused(run("echo -F off | xargs ${{ '\\' }}\n./gradlew check"), "ends in an expression");
        assertRefused(workflow("run: echo ${{ inputs.x }}", "run: ./gradlew check"), "ends in an expression");

        assertThat(problems("a.yml", workflow("run: |\n  cat <<EOF\n  x\n  EOF", "run: echo ${{ github.sha }}"),
                SOME_TASKS)).isEmpty();
        assertThat(problems("a.yml", workflow("run: echo \"${{ github.sha }}\" > sha", "run: ./gradlew check"),
                SOME_TASKS)).isEmpty();
    }

    @Test
    void aFileThatCallsGradleGivesItsActionsOnlyTheInputsTheWorkflowsUse() {
        var call = "run: ./gradlew check";
        var setupGradle = "uses: gradle/actions/setup-gradle@8da46b539cd8fe6ab27ca75128919a18d757604c\nwith:\n  ";
        assertRefused(workflow(setupGradle + "validate-wrappers: false", call), "'validate-wrappers'");
        assertRefused(workflow(setupGradle + "develocity-injection-enabled: true", call),
                "'develocity-injection-enabled'");
        assertRefused(workflow("uses: actions/checkout@v6\nwith:\n  repository: x/y\n  ref: weaker", call),
                "'repository'", "'ref'");
        assertRefused(workflow("with:\n  x: y\n" + call), "'' is given the input 'x'");
        assertRefused(workflow("""
                uses: reactivecircus/android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d
                with:
                  working-directory: android
                  emulator-options: -no-window; export GRADLE_OPTS=-Dorg.gradle.dependency.verification=off
                  script: ./gradlew check"""), "dependency.verification");

        assertThat(problems("a.yml", workflow(setupGradle + "validate-wrappers: false"), SOME_TASKS)).isEmpty();
        assertThat(problems("a.yml", workflow(setupGradle + "cache-provider: basic\n  cache-read-only: true",
                "uses: actions/checkout@v6\nwith:\n  fetch-depth: 0\n  persist-credentials: false", call), SOME_TASKS))
                .isEmpty();
    }

    @Test
    void aFileThatCallsGradleRunsNoJobInAnImageAndNoStepInAnotherShell() {
        var call = "    defaults:\n      run:\n        working-directory: android\n    steps:\n"
                + "      - run: ./gradlew check\n";
        assertRefused("jobs:\n  build:\n    container: ubuntu:24.04\n" + call, "container");
        assertRefused("jobs:\n  build:\n    services:\n      db:\n        image: x\n" + call, "services");
        assertRefused(workflow("shell: sh\nrun: ./gradlew check"), "the shell 'sh'");
        assertRefused("defaults:\n  run:\n    shell: pwsh\n" + workflow("run: ./gradlew check"), "the shell 'pwsh'");

        assertThat(problems("a.yml", workflow("shell: bash\nrun: ./gradlew check"), SOME_TASKS)).isEmpty();
        assertThat(problems("a.yml", "jobs:\n  build:\n    container: ubuntu:24.04\n    defaults:\n      run:\n"
                + "        shell: powershell\n    steps:\n      - run: mvn verify\n", SOME_TASKS)).isEmpty();
    }

    @Test
    void aLocalActionIsOneThisTestReads() {
        assertRefused(workflow("uses: ./tools/build"), "'./tools/build' is a local action");
        assertRefused(workflow("uses: ./.github/actions/../../tools/build"), "is a local action");
        assertRefused("jobs:\n  call:\n    uses: ./tools/build.yml\n", "is a local action");

        assertThat(problems("a.yml", workflow("uses: ./.github/actions/build"), SOME_TASKS)).isEmpty();
        assertThat(problems("a.yml", "jobs:\n  call:\n    uses: ./.github/workflows/build.yml\n", SOME_TASKS))
                .isEmpty();
    }

    @Test
    void aFileThatCallsGradleUsesOnlyTheListedActionsAndNoFileUsesAGradleAction() {
        var call = "run: ./gradlew check";
        assertRefused(workflow("uses: eskatos/gradle-command-action@v1", call), "eskatos/gradle-command-action");
        assertRefused(workflow("uses: ./.github/actions/build", call), "./.github/actions/build");
        assertRefused(workflow("\"uses\": 'burrunan/gradle-cache-action@v1'", call), "burrunan/gradle-cache-action");
        assertRefused(workflow("? uses\n: x/y@v1", call), "'x/y'");
        assertRefused(workflow("uses: eskatos/gradle-command-action@v1"), "eskatos/gradle-command-action");
        assertRefused(workflow("uses: gradle/gradle-build-action@v3"), "gradle-build-action");
        assertRefused("jobs:\n  call:\n    uses: x/y/.github/workflows/g.yml@v1\n", "x/y/.github/workflows/g.yml");
        assertRefused(workflow("uses: some/action@v1\nwith:\n  arguments: check -F off"), "arguments");
        assertRefused(workflow("uses: some/action@v1\nwith:\n  \"build-root-directory\": android"),
                "build-root-directory");

        assertThat(problems("a.yml", workflow("""
                uses: actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd # v6.0.2""", """
                uses: gradle/actions/setup-gradle@8da46b539cd8fe6ab27ca75128919a18d757604c
                with:
                  cache-read-only: true""", "run: ./gradlew check"), SOME_TASKS)).isEmpty();
        assertThat(problems("a.yml", workflow("uses: some/other-action@v1", "? uses\n: x/y@v1"), SOME_TASKS)).isEmpty();
        assertThat(problems("a.yml", "jobs:\n  call:\n    uses: ./.github/workflows/build.yml\n", SOME_TASKS))
                .isEmpty();
    }

    @Test
    void aStepThatChangesTheChecksInputsIsRefused() {
        assertRefused(run("rm gradle/verification-metadata.xml"), "verification-metadata");
        assertRefused(run("mkdir -p ~/.gradle && cp ci/x.properties ~/.gradle/"), ".gradle", ".gradle");
        assertRefused(run("cp -r ci/home/. \"$HOME/.gradle\""), ".gradle");
        assertRefused(workflow("env:\n  BASH_ENV: ci/env.sh"), "BASH_ENV");
        assertRefused(workflow("env:\n  JAVA_TOOL_OPTIONS: -Duser.home=ci/home"), "user.home");
        assertRefused(run("cp ci/x.properties ~/.gradle/caches/../gradle.properties"), ".gradle");
        assertRefused(run("cp ci/x.properties \"$HOME/\".gradle/"), ".gradle");
        assertRefused(run("rm android/gradle/verification-metadata.*"), "verification-metadata");
        assertRefused(run("java -jar gradle/wrapper/gradle-wrapper.jar -F off check"), "gradle-wrapper.jar");

        assertThat(problems("a.yml", workflow("""
                uses: gradle/actions/setup-gradle@8da46b539cd8fe6ab27ca75128919a18d757604c
                with:
                  path: ~/.gradle/caches
                  key: ${{ hashFiles('android/**/*.gradle', 'android/gradle/libs.versions.toml') }}"""),
                SOME_TASKS)).isEmpty();
    }

    @Test
    void gradleNamedAnyOtherWayAsACommandIsRefused() {
        for (var other : List.of("\"./gradlew\"", "gradlew", "android/gradlew", "../gradlew", "gradlew.bat",
                "./gradlew.bat", "./GRADLEW", "gradle", "/opt/gradle/bin/gradle", "gradle.bat")) {
            assertRefused(run("sh " + other + " check"), other);
        }
        assertRefused(run("java -cp x org.gradle.wrapper.GradleWrapperMain check"), "GradleWrapperMain");
        assertRefused(run("out=$(gradle -q help)"), "out=$(gradle");
        assertRefused(workflow("env:\n  CALL: ./gradlew check"), "outside a command string");
        assertRefused(workflow("name: then run gradle check"), "outside a command string");
        assertRefused(workflow("uses: some/action@v1\nwith:\n  script: ./gradlew check"), "outside a command string");
        assertRefused(workflow("run:\n  ./gradlew: check"), "outside a command string");
        assertRefused(workflow("env:\n  run: ./gradlew check"), "outside a command string");
        assertRefused("jobs:\n  build:\n    run: ./gradlew check\n", "outside a command string");

        assertThat(problems("a.yml", workflow("""
                uses: gradle/actions/setup-gradle@8da46b539cd8fe6ab27ca75128919a18d757604c # v6.4.1
                env:
                  KEY: gradle-${{ hashFiles('android/**/*.gradle.kts') }}
                  CACHE: ~/.gradle/caches""", """
                name: Build with Gradle
                run: |
                  cp android/gradle/libs.versions.toml build/reports/
                  cat android/gradle.properties
                  echo "run ./gradlew check, or gradle check"
                  echo "::error::It failed Gradle's verification," \\
                    "run ./gradlew check"
                  ./gradlew check"""), SOME_TASKS)).isEmpty();
    }

    @Test
    void aFileThatCallsGradleRunsItsStepsOnlyInTheKnownFolders() {
        var call = "run: ./gradlew check";
        assertRefused(workflow("run: cd android/build-logic", call), "android/build-logic");
        assertRefused(workflow("run: cd android; cd build-logic", call), "build-logic");
        assertRefused(workflow("run: cd \"$DIR\"", call), "\"$DIR\"");
        assertRefused(workflow("run: cd", call), "''");
        assertRefused(workflow("run: cd ./android", call), "./android");
        assertRefused(workflow("run: cd android/", call), "android/");
        assertRefused(workflow("run: pushd android/build-logic", call), "android/build-logic");
        assertRefused(workflow("working-directory: android/build-logic", call), "android/build-logic");
        assertRefused(workflow("working-directory: ${{ inputs.directory }}", call), "${{ inputs.directory }}");
        assertRefused(workflow("\"working-directory\": android/build-logic", call), "android/build-logic");
        assertRefused(workflow("'working-directory' : android/build-logic", call), "android/build-logic");
        assertRefused("defaults:\n  run:\n    working-directory: build-logic\n" + workflow(call), "build-logic");

        assertThat(problems("a.yml", "defaults:\n  run:\n    working-directory: android\n" + workflow(
                "working-directory: .\nrun: |\n  cd release", "working-directory: release\nrun: ls", call),
                SOME_TASKS)).isEmpty();
        assertThat(problems("a.yml", workflow("working-directory: desktop\nrun: cd target && ls"), SOME_TASKS))
                .isEmpty();
    }

    @Test
    void aStepThatCallsGradleRunsInAndroidWithoutChangingFolder() {
        var noDefaults = "jobs:\n  build:\n    steps:\n      - run: ./gradlew check\n";
        assertRefused(noDefaults, "the workspace (no working-directory)");
        assertRefused(workflow("working-directory: .\nrun: ./gradlew check"), "'.'");
        assertRefused(workflow("working-directory: release\nrun: ./gradlew check"), "'release'");
        assertRefused(workflow("working-directory: ./android\nrun: ./gradlew check"), "'./android'", "'./android'");
        assertRefused(workflow("working-directory: android/build-logic\nrun: ./gradlew check"),
                "android/build-logic", "'android/build-logic'");
        assertRefused("jobs:\n  build:\n    defaults:\n      run:\n        working-directory: build-logic\n"
                + "    steps:\n      - run: ./gradlew check\n", "build-logic", "'build-logic'");
        assertRefused(run("cd android\n./gradlew check"), "changes its folder ('android')");
        assertRefused(run("pushd ..\n./gradlew check"), "changes its folder ('..')");
        assertRefused(workflow("""
                uses: reactivecircus/android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d
                with:
                  script: ./gradlew check"""), "the workspace");
        assertRefused(workflow("""
                uses: reactivecircus/android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d
                with:
                  working-directory: .
                  script: ./gradlew check"""), "'.'");
        assertRefused("runs:\n  using: composite\n  steps:\n    - shell: bash\n      run: ./gradlew check\n",
                "the workspace");
        var merged = """
                x-folder: &folder
                  working-directory: android/build-logic
                jobs:
                  build:
                    steps:
                      - <<: *folder
                        run: ./gradlew check
                """;
        assertRefused(merged, "android/build-logic", "android/build-logic", "'android/build-logic'");
        assertThat(problems("a.yml", merged, SOME_TASKS)).anySatisfy(problem -> assertThat(problem)
                .contains("job build, step 1, working-directory"));

        assertThat(problems("a.yml", "defaults:\n  run:\n    working-directory: android\n"
                + noDefaults.replace("jobs:", "on: push\njobs:"), SOME_TASKS)).isEmpty();
        assertThat(problems("a.yml", "jobs:\n  build:\n    defaults:\n      run:\n        working-directory: release\n"
                + "    steps:\n      - working-directory: android\n        run: ./gradlew check\n", SOME_TASKS))
                .isEmpty();
        assertThat(problems("action.yml", "runs:\n  using: composite\n  steps:\n    - shell: bash\n"
                + "      working-directory: android\n      run: ./gradlew check\n", SOME_TASKS)).isEmpty();
    }

    @Test
    void noOtherFolderThanAndroidHoldsARootBuildWithAWrapper(@TempDir Path root) throws IOException {
        assertThat(otherRootBuilds(Path.of(""))).isEmpty();

        assertThat(otherRootBuilds(root)).isEmpty();
        for (var file : List.of(root.resolve("gradlew"), root.resolve(Path.of("release", "settings.gradle.kts")),
                root.resolve("settings.gradle"), root.resolve(Path.of("release", "gradlew.bat")))) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "");
        }
        assertThat(otherRootBuilds(root)).hasSize(4);
    }

    @Test
    void theModesPropertyAnotherHomeAnInitScriptAndTheRefreshScriptAreRefused() {
        for (var weaker : Map.of("GRADLE_OPTS: -Dorg.gradle.dependency.verification=strict", "dependency.verification",
                "ORG_GRADLE_PROJECT_org.gradle.dependency.verification: off", "dependency.verification",
                "ORG_GRADLE_DEPENDENCY_VERIFICATION: off", "DEPENDENCY_VERIFICATION",
                "dependencyVerification: lenient", "dependencyVerification",
                "ARGS: --dependency-verification=off", "--dependency-verification",
                "ARGS: --write-verification-metadata sha256", "--write-verification-metadata",
                "ARGS: --project-dir build-logic", "--project-dir",
                "ARGS: --init-script weaker.gradle", "--init-script",
                "GRADLE_USER_HOME: ${{ runner.temp }}/home", "GRADLE_USER_HOME",
                "GRADLE_OPTS: -Dgradle.user.home=home", "gradle.user.home").entrySet()) {
            assertRefused(workflow("env:\n  " + weaker.getKey()), weaker.getValue());
        }
        for (var weaker : Map.of("echo org.gradle.dependency.verification=lenient >> x", "dependency.verification",
                "cp weaker.gradle home/init.d/", "init.d",
                "cp weaker.gradle \"$DIR/init.gradle\"", "init.gradle",
                "cp weaker.gradle.kts home/init.gradle.kts", "init.gradle",
                "sh tools/refresh-android-verification.sh", "refresh-android-verification",
                "../tools/refresh-android-verification.sh --clean", "refresh-android-verification",
                "echo \"refresh\" && sh tools/refresh-android-verification.sh", "refresh-android-verification",
                "echo \"$(sh tools/refresh-android-verification.sh)\"", "refresh-android-verification",
                "# sh tools/refresh-android-verification.sh", "refresh-android-verification").entrySet()) {
            assertRefused(run(weaker.getKey()), weaker.getValue());
        }
        assertRefused("on:\n  push:\n    paths:\n      - \"tools/refresh-android-verification.sh\"\n",
                "refresh-android-verification");
        assertRefused("dependencyVerification: off\n", "dependencyVerification");
        // the shell drops a backslash and its line break, so the two halves are one word
        assertRefused(run("export GRADLE_OPTS=-Dorg.gradle.dependency\\\n.verification=off"),
                "dependency.verification");

        assertThat(problems("a.yml", run("""
                if [ -d build/reports/dependency-verification ]; then
                  echo "run sh tools/refresh-android-verification.sh"
                  echo "::error::run sh tools/refresh-android-verification.sh," \\
                    "read the diff of android/gradle/verification-metadata.xml"
                fi
                sudo /etc/init.d/docker restart
                mvn -B -f pom.xml && grep -Fqx "$ref" tags"""), SOME_TASKS)).isEmpty();
    }

    @Test
    void aGradlePropertiesFileNamesNoneOfIt() {
        assertThat(problems("gradle.properties", "org.gradle.dependency.verification=lenient\n", SOME_TASKS))
                .singleElement().asString().contains("gradle.properties line 1: ", "dependency.verification");
        assertThat(problems("gradle.properties", "a=1\norg.gradle.dependency.\\\n    verification=off\n", SOME_TASKS))
                .singleElement().asString().contains("gradle.properties line 2: ", "dependency.verification");
        assertThat(problems("gradle.properties", "org.gradle.jvmargs=-Duser.home=ci\n", SOME_TASKS))
                .singleElement().asString().contains("user.home");
        assertThat(problems("gradle.properties", "org.gradle.dependency.verificatio\\u006e=off\n", SOME_TASKS))
                .singleElement().asString().contains("gradle.properties, key org.gradle.dependency.verification: ",
                        "dependency.verification", "once its escapes are read");
        assertThat(problems("gradle.properties", "org.gradle.dependency.ver\\ification=off\n", SOME_TASKS))
                .singleElement().asString().contains("once its escapes are read");
        assertThat(problems("gradle.properties", "org.gradle.jvmargs=-Duser.ho\\u006de=ci\n", SOME_TASKS))
                .singleElement().asString().contains("user.home", "once its escapes are read");
        assertThat(problems("gradle.properties", "a=\\u00\n", SOME_TASKS)).singleElement().asString()
                .contains("does not load");

        assertThat(problems("gradle.properties", "# org.gradle.dependency.verification=lenient\n"
                + "org.gradle.jvmargs=-Xmx2g\norg.gradle.configuration-cache=true\n", SOME_TASKS)).isEmpty();
    }

    /** What the line patterns refused although YAML reads it as a plain, allowed call (#494). */
    @Test
    void whatYamlReadsAsAnAllowedCallIsAccepted() {
        assertThat(problems("a.yml", workflow("working-directory: \"android\"\nrun: ./gradlew check",
                "working-directory: android # the root build\nrun: ./gradlew lint", """
                run: |
                  if [ -n "$CI" ]; then
                    ./gradlew check
                  fi
                  for attempt in 1 2; do
                    ./gradlew --continue lint
                  done""", "run: |2\n    ./gradlew check", "\"run\": ./gradlew check", "? run\n: ./gradlew lint",
                "run: ./gradlew check\n    # a comment below the call", """
                uses: reactivecircus/android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d
                with:
                  working-directory: android
                  script: ./gradlew check"""), SOME_TASKS)).isEmpty();
        // a step's name and a message are no call, so the rules for the actions and folders of a file that calls
        // Gradle stay off
        assertThat(problems("a.yml", workflow("name: Build with Gradle\nuses: some/other-action@v1",
                "run: echo \"run ./gradlew check\"",
                "working-directory: desktop\nrun: mvn verify"), SOME_TASKS)).isEmpty();
    }

    @Test
    void aProblemNamesTheFileTheJobTheStepAndTheLine() {
        assertThat(problems("a.yml", workflow("name: Lint\nrun: |\n  echo hi\n  ./gradlew -F"), SOME_TASKS))
                .singleElement().asString().startsWith("a.yml, job build, step 1 (Lint), run, line 2: ");
        assertThat(problems("action.yml", "runs:\n  using: composite\n  steps:\n    - shell: bash\n      run: echo\n"
                + "    - shell: bash\n      working-directory: android\n      run: ./gradlew -F\n", SOME_TASKS))
                .singleElement().asString().startsWith("action.yml, step 2, run, line 1: ");
        assertThat(problems("a.yml", workflow("run: echo", "env:\n  CALL: gradle"), SOME_TASKS))
                .singleElement().asString().startsWith("a.yml, job build, step 2, env.CALL: ");
        assertThat(problems("a.yml", "on:\n  push:\n    paths: [x, tools/refresh-android-verification.sh]\n",
                SOME_TASKS)).singleElement().asString().startsWith("a.yml, on.push.paths[1]: ");
    }

    @Test
    void aSourceFolderNamedBuildIsReadAndABuildsOutputIsNot(@TempDir Path android) throws IOException {
        var source = android.resolve(Path.of("build-logic", "src", "main", "kotlin", "build", "Weaker.kt"));
        var hidden = android.resolve(Path.of("build-logic", "src", "main", "kotlin", ".gradle", "Hidden.kt"));
        var decoy = android.resolve(Path.of("build-logic", "src", "main", "kotlin", "build.gradle"));
        var output = android.resolve(Path.of("build-logic", "build", "generated", "Made.kt"));
        var groovy = android.resolve(Path.of("build-logic", "src", "main", "groovy", "Weaker.groovy"));
        var script = android.resolve(Path.of("gradle", "weaker.main.kts"));
        for (var file : List.of(source, hidden, decoy, output, groovy, script,
                android.resolve(Path.of("build-logic", "build.gradle.kts")))) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "");
        }

        assertThat(buildSources(android)).contains(source, hidden, decoy, groovy, script).doesNotContain(output);
    }

    @Test
    void theWrapperDownloadsGradleFromGradleWithARecordedChecksumIntoGradlesHome() throws IOException {
        var wrapper = new Properties();
        try (var in = Files.newInputStream(Path.of("android", "gradle", "wrapper", "gradle-wrapper.properties"))) {
            wrapper.load(in);
        }
        assertThat(wrapper.getProperty("distributionUrl"))
                .matches("https://services[.]gradle[.]org/distributions/gradle-[0-9.]+-bin[.]zip");
        assertThat(wrapper.getProperty("distributionSha256Sum")).matches("[0-9a-f]{64}");
        assertThat(wrapper.getProperty("distributionBase")).isEqualTo("GRADLE_USER_HOME");
        assertThat(wrapper.getProperty("zipStoreBase")).isEqualTo("GRADLE_USER_HOME");
        assertThat(wrapper.getProperty("distributionPath")).isEqualTo("wrapper/dists");
        assertThat(wrapper.getProperty("zipStorePath")).isEqualTo("wrapper/dists");
    }

    /** The scripts and the jar run before Gradle reads any setting, so none of them may change unseen. */
    @Test
    void theWrappersScriptsAndJarAreTheRecordedOnes() throws Exception {
        for (var file : WRAPPER.entrySet()) {
            assertThat(wrapperHash(file.getKey())).as(file.getKey().toString()).isEqualTo(file.getValue());
        }
    }

    @Test
    void aWrapperScriptIsHashedWithLfLineEndsAndTheJarAsItIs(@TempDir Path folder) throws Exception {
        var script = Files.readString(Path.of("android", "gradlew"));
        var lf = folder.resolve("gradlew");
        Files.writeString(lf, script);
        var crlf = folder.resolve(Path.of("crlf", "gradlew"));
        Files.createDirectories(crlf.getParent());
        Files.writeString(crlf, script.replace("\n", "\r\n"));
        var cr = folder.resolve(Path.of("cr", "gradlew"));
        Files.createDirectories(cr.getParent());
        Files.writeString(cr, script.replace("\n", "\r"));
        assertThat(List.of(wrapperHash(lf), wrapperHash(crlf), wrapperHash(cr)))
                .containsOnly(WRAPPER.get(Path.of("android", "gradlew")));

        assertThat(script).contains("exec \"$JAVACMD\" \"$@\"\n");
        for (var weaker : List.of("exec \"$JAVACMD\" \"$@\" -F off\n",
                "set -- --init-script x -p build-logic \"$@\"\nexec \"$JAVACMD\" \"$@\"\n")) {
            Files.writeString(lf, script.replace("exec \"$JAVACMD\" \"$@\"\n", weaker));
            assertThat(wrapperHash(lf)).as(weaker).isNotEqualTo(WRAPPER.get(Path.of("android", "gradlew")));
        }
        var jar = folder.resolve("gradle-wrapper.jar");
        Files.write(jar, new byte[] {'a', '\r', '\n'});
        assertThat(wrapperHash(jar)).isEqualTo(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(new byte[] {'a', '\r', '\n'})));
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

    /** {@code text}, as file {@code a.yml}, breaks the rules once for each of {@code words}, in that order. */
    private static void assertRefused(String text, String... words) {
        var problems = problems("a.yml", text, SOME_TASKS);
        assertThat(problems).as(text).hasSize(words.length);
        for (int i = 0; i < words.length; i++) {
            assertThat(problems.get(i)).as(text).startsWith("a.yml").contains(words[i]);
        }
    }

    /**
     * A workflow with one job, whose steps run in {@code android} by default, and whose steps are {@code steps}, each a
     * step's map written at the left margin.
     */
    private static String workflow(String... steps) {
        var text = new StringBuilder("on: push\njobs:\n  build:\n    runs-on: ubuntu-latest\n"
                + "    defaults:\n      run:\n        working-directory: android\n    steps:\n");
        for (var step : steps) {
            var lines = step.lines().toList();
            text.append("      - ").append(lines.getFirst()).append('\n');
            lines.stream().skip(1).forEach(line -> text.append(line.isEmpty() ? "" : "        ").append(line)
                    .append('\n'));
        }
        return text.toString();
    }

    /** A workflow with one step that runs {@code command}, written as a literal block. */
    private static String run(String command) {
        return workflow("run: |\n" + command.lines().map(line -> "  " + line).collect(Collectors.joining("\n")));
    }

    /**
     * What in {@code text}, the file {@code name}, breaks a rule, each naming the file, where in it, and the word; a
     * properties file is read by its lines, any other file as YAML. {@code tasks} are the tasks a Gradle call may name.
     */
    private static List<String> problems(String name, String text, Set<String> tasks) {
        if (name.endsWith(".properties")) {
            return propertiesProblems(name, text);
        }
        var documents = new ArrayList<>();
        try {
            new Load(YAML).loadAllFromString(text).forEach(documents::add);
        } catch (YamlEngineException e) {
            return List.of(name + ": does not parse as YAML, so this test cannot read it: "
                    + String.valueOf(e.getMessage()).lines().findFirst().orElse(""));
        }
        var problems = new ArrayList<String>();
        for (var document : documents) {
            var found = new ArrayList<Found>();
            walk(document, List.of(), Collections.newSetFromMap(new IdentityHashMap<>()), name, found, problems);
            problems.addAll(documentProblems(name, document, found, tasks));
        }
        return problems;
    }

    /**
     * Each key and scalar of {@code node}, at {@code path}, into {@code found}, aliases followed: an alias stands for
     * what its anchor holds, so it is read once in each place it is used. {@code open} holds the maps and lists the
     * path goes through.
     */
    private static void walk(Object node, List<Object> path, Set<Object> open, String name, List<Found> found,
            List<String> problems) {
        if (node instanceof Map<?, ?> || node instanceof List<?>) {
            if (!open.add(node)) {
                problems.add(name + ", " + keyPath(path) + ": an alias holds itself, which no workflow needs");
                return;
            }
            if (node instanceof Map<?, ?> map) {
                map.forEach((key, value) -> {
                    var at = append(path, key);
                    found.add(new Found(at, String.valueOf(key), true, value));
                    walk(value, at, open, name, found, problems);
                });
            } else {
                var list = (List<?>) node;
                for (int i = 0; i < list.size(); i++) {
                    walk(list.get(i), append(path, new Index(i)), open, name, found, problems);
                }
            }
            open.remove(node);
        } else {
            found.add(new Found(path, node == null ? "" : node.toString(), false, node));
        }
    }

    private static List<String> documentProblems(String name, Object document, List<Found> found, Set<String> tasks) {
        var commands = new HashSet<Found>();
        for (var scalar : found) {
            if (!scalar.key() && isCommand(document, scalar.path())) {
                commands.add(scalar);
            }
        }
        var callsGradle = commands.stream().anyMatch(command -> callsGradle(command.text()));
        var problems = new ArrayList<String>();
        for (var item : found) {
            var place = name + ", " + where(document, item.path());
            var at = place + ": ";
            var text = item.text();
            var key = item.path().isEmpty() ? null : item.path().getLast();
            if (item.key()) {
                problems.addAll(denied(at, text));
                gradleNamed(text).forEach(word -> problems.add(at + word + " names Gradle outside a command string"));
                if (GRADLE_INPUTS.contains(text)) {
                    problems.add(at + "an input '" + text + "' would hand Gradle its arguments or another root build");
                }
                if ((text.equals("uses") || text.equals("working-directory"))
                        && (item.value() instanceof Map<?, ?> || item.value() instanceof List<?>)) {
                    problems.add(at + text + " holds no plain text, so this test cannot read it");
                }
                if (callsGradle && item.path().size() == 3 && "jobs".equals(item.path().getFirst())
                        && JOB_ENVIRONMENTS.contains(text)) {
                    problems.add(at + "a job of a file that calls Gradle sets its " + text + ", whose image may set"
                            + " Gradle's home and environment");
                }
                continue;
            }
            if (item.value() != null && !(item.value() instanceof String) && (commands.contains(item)
                    || "uses".equals(key) || "working-directory".equals(key))) {
                problems.add(at + "the value is no text but " + item.value().getClass().getSimpleName()
                        + ", so this test does not read it as written");
            }
            if (commands.contains(item)) {
                problems.addAll(commandProblems(place, text, callsGradle, tasks));
                continue;
            }
            problems.addAll(denied(at, text));
            gradleNamed(text).forEach(word -> problems.add(at + word + " names Gradle outside a command string (a"
                    + " step's run, or the emulator action's script), where this test does not check a call"));
            if ("uses".equals(key)) {
                problems.addAll(usesProblems(at, action(text), callsGradle));
            }
            if ("working-directory".equals(key) && callsGradle && !DIRECTORIES.contains(text)) {
                problems.add(at + outsideTheFolders(text));
            }
            if ("shell".equals(key) && callsGradle && !SHELLS.contains(text)) {
                problems.add(at + "a file that calls Gradle runs a step in the shell '%s', not one of %s"
                        .formatted(text, SHELLS));
            }
        }
        if (callsGradle) {
            steps(document).forEach((path, step) -> problems.addAll(stepProblems(name, document, path, step,
                    commands)));
        }
        return problems;
    }

    /** What is wrong with the command string {@code text}, at {@code place}, by its shell lines. */
    private static List<String> commandProblems(String place, String text, boolean fileCallsGradle,
            Set<String> tasks) {
        var problems = new ArrayList<String>();
        text.chars().filter(c -> Character.isISOControl(c) && c != '\t' && c != '\n').distinct()
                .forEach(c -> problems.add(place + ": the command holds the control character U+%04X, which YAML and"
                        .formatted(c) + " the shell may read as different line breaks"));
        var calls = callsGradle(text);
        if (fileCallsGradle && text.contains("<<")) {
            problems.add(place + ": a command of a file that calls Gradle holds <<, whose here-document may hand a"
                    + " later line to another command");
        }
        for (var line : shellLines(text)) {
            var lineAt = place + ", line " + line.number() + ": ";
            if (fileCallsGradle && line.text().strip().endsWith("}}")) {
                problems.add(lineAt + "the line ends in an expression, which GitHub may fill in with a backslash that"
                        + " joins the next line");
            }
            if (MESSAGE.matcher(line.text()).matches()) {
                continue;
            }
            problems.addAll(denied(lineAt, line.text()));
            if (GRADLE.matcher(line.text()).find()) {
                callProblems(line.text(), tasks).forEach(problem -> problems.add(lineAt + problem));
            }
            for (var cd : CD.matcher(line.text()).results().map(found -> found.group(1)).toList()) {
                if (calls) {
                    problems.add(lineAt + "a command that calls Gradle changes its folder ('%s'): a step that calls"
                            .formatted(cd) + " Gradle runs in android by its working-directory");
                } else if (fileCallsGradle && !DIRECTORIES.contains(cd)) {
                    problems.add(lineAt + outsideTheFolders(cd));
                }
            }
        }
        return problems;
    }

    /** Whether the command string {@code text} names Gradle on a line that is not a message. */
    private static boolean callsGradle(String text) {
        return shellLines(text).stream().anyMatch(line -> !MESSAGE.matcher(line.text()).matches()
                && GRADLE.matcher(line.text()).find());
    }

    /** What is wrong with the action {@code action} a {@code uses} names, at {@code at}. */
    private static List<String> usesProblems(String at, String action, boolean callsGradle) {
        var problems = new ArrayList<String>();
        if (!ACTIONS.contains(action) && (callsGradle || action.toLowerCase(Locale.ROOT).contains("gradle")
                || action.contains(".github/workflows/") && !action.startsWith("./"))) {
            problems.add(at + "'%s' is used, not one of the actions %s, and it may run Gradle itself"
                    .formatted(action, ACTIONS));
        }
        if (action.startsWith("./") && (!LOCAL_ACTION.matcher(action).matches()
                || Arrays.asList(action.split("/")).contains(".."))) {
            problems.add(at + "'%s' is a local action outside .github/actions and .github/workflows, which this test"
                    .formatted(action) + " does not read");
        }
        return problems;
    }

    /**
     * What is wrong with a step of a file that calls Gradle: a Gradle call outside {@code android}, or an input its
     * action is not allowed.
     */
    private static List<String> stepProblems(String name, Object document, List<Object> path, Map<?, ?> step,
            Set<Found> commands) {
        var problems = new ArrayList<String>();
        var at = name + ", " + where(document, path) + ": ";
        var calls = commands.stream().filter(command -> command.path().size() > path.size()
                && command.path().subList(0, path.size()).equals(path))
                .anyMatch(command -> callsGradle(command.text()));
        if (calls) {
            var folder = workingDirectory(document, path, step);
            if (!"android".equals(folder)) {
                problems.add(at + "the step calls Gradle in %s, not in android, the one root build with checksums"
                        .formatted(folder == null ? "the workspace (no working-directory)" : "'" + folder + "'"));
            }
        }
        if (step.get("with") instanceof Map<?, ?> with) {
            var action = step.get("uses") instanceof String uses ? action(uses) : "";
            var allowed = INPUTS_OF.getOrDefault(action, Set.of());
            with.keySet().stream().map(String::valueOf).filter(input -> !allowed.contains(input))
                    .forEach(input -> problems.add(at + "'%s' is given the input '%s', not one of %s"
                            .formatted(action, input, new TreeSet<>(allowed))));
        }
        return problems;
    }

    /**
     * The folder a step that calls Gradle runs it in: for the emulator action its {@code working-directory} input;
     * otherwise the step's own {@code working-directory}, else its job's default, else the workflow's; a step of a
     * composite action has only its own.
     */
    private static Object workingDirectory(Object document, List<Object> path, Map<?, ?> step) {
        if (step.get("uses") instanceof String uses && action(uses).equals(EMULATOR)) {
            return get(step.get("with"), "working-directory");
        }
        var own = step.get("working-directory");
        if (own != null || "runs".equals(path.getFirst())) {
            return own;
        }
        var job = get(get(document, "jobs"), path.get(1));
        var ofJob = get(get(get(job, "defaults"), "run"), "working-directory");
        return ofJob != null ? ofJob : get(get(get(document, "defaults"), "run"), "working-directory");
    }

    /** The steps of the jobs of a workflow and of a composite action, by their path. */
    private static Map<List<Object>, Map<?, ?>> steps(Object document) {
        var steps = new LinkedHashMap<List<Object>, Map<?, ?>>();
        if (get(document, "jobs") instanceof Map<?, ?> jobs) {
            jobs.forEach((id, job) -> addSteps(get(job, "steps"), append(append(List.of("jobs"), id), "steps"), steps));
        }
        addSteps(get(get(document, "runs"), "steps"), List.of("runs", "steps"), steps);
        return steps;
    }

    private static void addSteps(Object list, List<Object> path, Map<List<Object>, Map<?, ?>> steps) {
        if (list instanceof List<?> items) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i) instanceof Map<?, ?> step) {
                    steps.put(append(path, new Index(i)), step);
                }
            }
        }
    }

    private static Object get(Object map, Object key) {
        return map instanceof Map<?, ?> entries ? entries.get(key) : null;
    }

    /** What is wrong with {@code line}, a shell line that names Gradle, against the allowed forms of a call. */
    private static List<String> callProblems(String line, Set<String> tasks) {
        var call = CALL.matcher(line.strip());
        if (!call.matches() || (call.group("name") == null) != (call.group("close") == null)) {
            return List.of(("'%s' names Gradle, but the line is not ./gradlew WORDS or NAME=$(./gradlew WORDS) as a"
                    + " whole, so what else is on it may change the call").formatted(line.strip()));
        }
        var problems = new ArrayList<String>();
        for (var word : call.group("words").strip().split("\\s+")) {
            if (!word.isEmpty() && !OPTIONS.contains(word) && !tasks.contains(word)) {
                problems.add(("./gradlew is followed by %s, which is neither an option this test allows nor a task %s"
                        + " runs: add the option to OPTIONS of this test, or the task to the script's Gradle call and"
                        + " refresh the checksums").formatted(word, REFRESH_SCRIPT));
            }
        }
        return problems;
    }

    private static List<String> denied(String at, String text) {
        return denials(text).stream().map(word -> at + word + WEAKENS_MESSAGE).toList();
    }

    /** What of {@code text} the deny patterns find. */
    private static List<String> denials(String text) {
        return Stream.of(WEAKENS, ELSEWHERE, REFRESH, INPUTS).flatMap(pattern -> pattern.matcher(text).results())
                .map(MatchResult::group).toList();
    }

    /** The words of {@code text} that name the wrapper or Gradle as a command. */
    private static List<String> gradleNamed(String text) {
        return GRADLE.matcher(text).results().map(found -> wordAround(text, found)).toList();
    }

    private static String outsideTheFolders(String folder) {
        return "a step of a file that calls Gradle runs in '%s', not in one of the folders %s"
                .formatted(folder, DIRECTORIES);
    }

    /**
     * Whether the scalar at {@code path} is a command string: the {@code run} of a step, or a script input of the
     * emulator action.
     */
    private static boolean isCommand(Object document, List<Object> path) {
        int step = stepLength(path);
        if (step == 0) {
            return false;
        }
        var rest = path.subList(step, path.size());
        if (rest.equals(List.of("run"))) {
            return true;
        }
        return rest.size() == 2 && "with".equals(rest.getFirst()) && SCRIPT_INPUTS.contains(rest.getLast())
                && node(document, path.subList(0, step)) instanceof Map<?, ?> map
                && map.get("uses") instanceof String uses && action(uses).equals(EMULATOR);
    }

    /** How long the part of {@code path} is that leads to a step of a job or of a composite action; 0 for none. */
    private static int stepLength(List<Object> path) {
        if (path.size() >= 4 && "jobs".equals(path.get(0)) && "steps".equals(path.get(2))
                && path.get(3) instanceof Index) {
            return 4;
        }
        if (path.size() >= 3 && "runs".equals(path.get(0)) && "steps".equals(path.get(1))
                && path.get(2) instanceof Index) {
            return 3;
        }
        return 0;
    }

    /** Where {@code path} is, for a message: the job and the step, with its name, then the keys below it. */
    private static String where(Object document, List<Object> path) {
        int step = stepLength(path);
        if (step == 0) {
            return keyPath(path);
        }
        var name = node(document, path.subList(0, step)) instanceof Map<?, ?> map && map.get("name") instanceof String s
                ? " (" + s + ")" : "";
        var job = step == 4 ? "job " + path.get(1) + ", " : "";
        var rest = path.subList(step, path.size());
        return job + "step " + (((Index) path.get(step - 1)).value() + 1) + name
                + (rest.isEmpty() ? "" : ", " + keyPath(rest));
    }

    private static String keyPath(List<Object> path) {
        var text = new StringBuilder();
        for (var part : path) {
            if (part instanceof Index index) {
                text.append('[').append(index.value()).append(']');
            } else {
                text.append(text.isEmpty() ? "" : ".").append(part);
            }
        }
        return text.isEmpty() ? "the document" : text.toString();
    }

    private static Object node(Object document, List<Object> path) {
        var node = document;
        for (var part : path) {
            node = part instanceof Index index ? ((List<?>) node).get(index.value()) : ((Map<?, ?>) node).get(part);
        }
        return node;
    }

    private static String action(String uses) {
        int at = uses.indexOf('@');
        return at < 0 ? uses : uses.substring(0, at);
    }

    private static List<Object> append(List<Object> path, Object part) {
        var longer = new ArrayList<>(path);
        longer.add(part);
        return Collections.unmodifiableList(longer);
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
     * The lines of a shell text: a line that ends in a backslash joined with the next, as the shell drops both, with
     * the number of its first line.
     */
    private static List<ShellLine> shellLines(String text) {
        var raw = List.of(text.split("\n", -1));
        var lines = new ArrayList<ShellLine>();
        for (int i = 0; i < raw.size(); i++) {
            int first = i;
            var line = new StringBuilder(raw.get(i));
            while (line.toString().endsWith("\\") && i + 1 < raw.size()) {
                line.setLength(line.length() - 1);
                line.append(raw.get(++i));
            }
            lines.add(new ShellLine(first + 1, line.toString()));
        }
        return lines;
    }

    /**
     * What in a properties file breaks a rule: a line, continued as {@link Properties} reads it, that names a deny
     * pattern or Gradle as a command, comment lines left out; and a key or value, as {@link Properties} decodes its
     * escapes, that names a deny pattern the lines do not show.
     */
    private static List<String> propertiesProblems(String name, String text) {
        var raw = text.lines().toList();
        var problems = new ArrayList<String>();
        var seen = new HashSet<String>();
        for (int i = 0; i < raw.size(); i++) {
            int first = i;
            var line = new StringBuilder(raw.get(i));
            while (line.toString().endsWith("\\") && i + 1 < raw.size()) {
                line.setLength(line.length() - 1);
                line.append(raw.get(++i).stripLeading());
            }
            if (line.toString().strip().startsWith("#")) {
                continue;
            }
            var at = "%s line %d: ".formatted(name, first + 1);
            denials(line.toString()).forEach(word -> {
                seen.add(word);
                problems.add(at + word + WEAKENS_MESSAGE);
            });
            gradleNamed(line.toString()).forEach(word -> problems.add(at + word + " names Gradle as a command"));
        }
        var decoded = new Properties();
        try {
            decoded.load(new StringReader(text));
        } catch (IOException | IllegalArgumentException e) {
            problems.add(name + ": does not load as a properties file: " + e.getMessage());
            return problems;
        }
        decoded.forEach((key, value) -> Stream.of(key, value).map(String::valueOf)
                .flatMap(part -> denials(part).stream()).filter(word -> !seen.contains(word))
                .forEach(word -> problems.add("%s, key %s: %s".formatted(name, key, word) + WEAKENS_MESSAGE
                        + ", once its escapes are read")));
        return problems;
    }

    /** The SHA-256 of a wrapper file: a jar as it is, a script with CRLF and a lone CR read as LF. */
    private static String wrapperHash(Path file) throws Exception {
        var bytes = Files.readAllBytes(file);
        if (!file.getFileName().toString().endsWith(".jar")) {
            bytes = new String(bytes, StandardCharsets.ISO_8859_1).replace("\r\n", "\n").replace('\r', '\n')
                    .getBytes(StandardCharsets.ISO_8859_1);
        }
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    /**
     * The files under {@code root}, in the repository's root and in {@code release/}, that would make a root build
     * beside {@code android/}, which has no checksums of its own.
     */
    private static List<Path> otherRootBuilds(Path root) {
        return Stream.of(root, root.resolve("release")).flatMap(folder -> ROOT_BUILD.stream().map(folder::resolve))
                .filter(Files::exists).toList();
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
        var calls = shellLines(script).stream().map(ShellLine::text).filter(line -> !line.strip().startsWith("#"))
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
     * The Android build's Gradle and Kotlin scripts, the Java, Kotlin and Groovy sources of its builds, its properties
     * files in any folder and the wrapper's scripts, whose JVM options reach Gradle, without what a build wrote: a
     * {@code build}, {@code .gradle} or {@code .kotlin} folder beside a build or settings script and not below a
     * {@code src} folder.
     */
    private static List<Path> buildSources(Path android) throws IOException {
        var kinds = Pattern.compile(".*\\.(gradle|kts|groovy|java|kt|properties)|gradlew(\\.bat)?");
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
