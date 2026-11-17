package nl.enadeag.carchargeplanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Gradle's dependency verification of the Android build stays strict, its default, everywhere (#484). No workflow, no
 * composite action and no {@code gradle.properties} of the Android build names the verification flags
 * ({@code --dependency-verification}, {@code -F}, {@code --write-verification-metadata}, {@code -M}) or the property
 * that sets the mode, in any spelling and with any value, outside a comment line: they have no reason to. Nor do they
 * run Gradle on another root build, which has no metadata file of its own ({@code -p}, {@code --project-dir}, a
 * {@code working-directory} inside {@code android/}), with an init script ({@code -I}, {@code --init-script}, an
 * {@code init.d} or {@code init.gradle}), or run the refresh script, which writes the checksums instead of checking
 * them. No build file or build source of the Android build sets the mode or switches it off for a configuration. Every
 * task a workflow's Gradle call names is one the refresh script runs too, so its files are recorded. And
 * {@code android/gradle/verification-metadata.xml} checks poms too, SHA-256 only, without keys, and trusts only an
 * IDE's sources and Javadoc. Here, with the Maven build, so every pull request that changes a workflow runs it.
 */
class AndroidDependencyVerificationTest {

    private static final Path METADATA = Path.of("android", "gradle", "verification-metadata.xml");
    private static final Path REFRESH_SCRIPT = Path.of("tools", "refresh-android-verification.sh");

    /**
     * The long flags, and the property {@code org.gradle.dependency.verification} however it is given: {@code -D},
     * {@code -P}, {@code GRADLE_OPTS}, {@code ORG_GRADLE_PROJECT_}, {@code gradle.properties}.
     */
    private static final Pattern WEAKENS = Pattern.compile(
            "--dependency-verification|--write-verification-metadata|(?i:dependency[._]?verification)");
    /** What follows {@code gradlew} or {@code gradle} up to the end of its command, where a short flag is Gradle's. */
    private static final Pattern GRADLE_COMMAND = Pattern.compile("\\bgradlew?\\b([^;&|)]*)");
    /**
     * The short flags {@code -F} and {@code -M} of the mode, {@code -p} of another root build and {@code -I} of an init
     * script, as a word of their own or with their value attached; Gradle reads the value in any case.
     */
    private static final Pattern SHORT_FLAG = Pattern.compile("(?<![\\w-])-[FMpI](?=$|[\\s=\"'a-zA-Z0-9./])");
    /**
     * Another root build or an init script: the long flags, an {@code init.d} folder or {@code init.gradle} file of a
     * Gradle home, and a {@code working-directory} below {@code android/} ({@code build-logic} is no root build).
     */
    private static final Pattern ELSEWHERE = Pattern.compile(
            "--project-dir|--init-script|(?<![\\w-])init[.](d|gradle)\\b|working-directory:.*\\bandroid/+[\\w.$-]");
    /** The refresh script, which runs Gradle in write mode. */
    private static final Pattern REFRESH = Pattern.compile("refresh-android-verification");
    /** An {@code echo} of plain double-quoted text only: a message, which runs nothing it names. */
    private static final Pattern MESSAGE = Pattern.compile("\\s*(run:\\s*)?echo(\\s+\"[^\"$`\\\\]*\")+\\s*");
    /** A call of the Gradle wrapper and what follows it up to the end of its command or a comment. */
    private static final Pattern WRAPPER_CALL = Pattern.compile("\\bgradlew(?:\\.bat)?(?=\\s|$)([^;&|)#]*)");
    /** A task name, with or without a project path; an option starts with a hyphen and is none. */
    private static final Pattern TASK = Pattern.compile(":?[A-Za-z][\\w:-]*");

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
    void noWorkflowAndNoPropertiesFileWeakensTheCheck() throws IOException {
        for (var file : commandFiles(Path.of(""))) {
            assertThat(weakeningLines(Files.readString(file))).as("lines of %s that weaken the verification", file)
                    .isEmpty();
        }
    }

    @Test
    void everyKnownWayIsFoundAndOnlyACommentLineOrAMessageIsLeftOut() {
        for (var weaker : List.of("org.gradle.dependency.verification=lenient",
                "run: ./gradlew -Dorg.gradle.dependency.verification=off check",
                "run: ./gradlew -Dx=\" #\" -Dorg.gradle.dependency.verification=lenient check",
                "  GRADLE_OPTS: -Dorg.gradle.dependency.verification=strict",
                "  ORG_GRADLE_PROJECT_org.gradle.dependency.verification: off",
                "  ORG_GRADLE_DEPENDENCY_VERIFICATION: off", "  dependencyVerification: lenient",
                "run: ./gradlew --dependency-verification=off check",
                "run: ./gradlew --dependency-verification lenient",
                "run: ./gradlew -F lenient check", "run: ./gradlew -F=off check", "run: ./gradlew -Flenient check",
                "run: ./gradlew -FOFF check", "run: ./gradlew -FLENIENT check",
                "run: ./gradlew check -F", "run: ./gradlew --write-verification-metadata sha256 help",
                "run: ./gradlew -M sha256 help", "run: ./gradlew -Msha256 help", "  run: ./gradlew check \\\n  -F off",
                "  run: cd android && ./gradlew help -F=off # lenient",
                "run: ./gradlew -p build-logic check", "run: ./gradlew -pbuild-logic check",
                "run: ./gradlew --project-dir=build-logic check", "run: ../gradlew --project-dir build-logic check",
                "        working-directory: android/build-logic", "  working-directory: ./android/maven-poms",
                "run: ./gradlew -I weaker.gradle check", "run: ./gradlew -Iweaker.gradle check",
                "run: ./gradlew --init-script weaker.gradle check",
                "run: mkdir -p ~/.gradle/init.d && cp weaker.gradle ~/.gradle/init.d/",
                "run: cp weaker.gradle \"$GRADLE_USER_HOME/init.gradle\"",
                "run: sh tools/refresh-android-verification.sh",
                "run: ../tools/refresh-android-verification.sh --clean",
                "run: echo \"refresh\" && sh tools/refresh-android-verification.sh")) {
            assertThat(weakeningLines(weaker)).as(weaker).hasSize(1);
        }
        assertThat(weakeningLines("# org.gradle.dependency.verification=lenient\n    # -F off\n"
                + "  if [ -d build/reports/dependency-verification ]; then\n  cache: off\n"
                + "  run: mvn -B -f pom.xml\n  if printf '%s\\n' \"$tags\" | grep -Fqx \"$ref\"; then\n"
                + "  run: ./gradlew check && grep -F -- x y\n  run: ./gradlew -PpitClasses=x check\n"
                + "        working-directory: android\n  working-directory: android/\n  working-directory: .\n"
                + "  run: mkdir -p build && ./gradlew check\n"
                + "    echo \"::error::run sh tools/refresh-android-verification.sh,\" \\\n      \"read the diff\"\n"))
                .isEmpty();
    }

    /**
     * {@code disableDependencyVerification()} of a configuration, and {@code gradle.startParameter}'s
     * {@code dependencyVerificationMode}, both found by the mode's pattern, in comments too.
     */
    @Test
    void noAndroidBuildFileSetsTheModeOrSwitchesTheVerificationOffForAConfiguration() throws IOException {
        var sources = buildSources();
        assertThat(sources).contains(Path.of("android", "app", "build.gradle.kts"),
                Path.of("android", "settings.gradle.kts"), Path.of("android", "build-logic", "build.gradle.kts"));
        for (var file : sources) {
            assertThat(modeLines(Files.readString(file))).as("lines of %s that set the verification mode", file)
                    .isEmpty();
        }
        for (var weaker : List.of("    resolutionStrategy.disableDependencyVerification()",
                "gradle.startParameter.dependencyVerificationMode = DependencyVerificationMode.OFF",
                "    // startParameter.setDependencyVerificationMode(LENIENT)")) {
            assertThat(modeLines(weaker)).as(weaker).hasSize(1);
        }
    }

    @Test
    void everyTaskOfAWorkflowsGradleCallIsOneTheRefreshScriptRuns() throws IOException {
        var workflows = new LinkedHashMap<String, String>();
        for (var file : commandFiles(Path.of(""))) {
            if (file.startsWith(".github")) {
                workflows.put(file.toString(), Files.readString(file));
            }
        }
        var script = Files.readString(REFRESH_SCRIPT);

        assertThat(gradleTasks(script)).contains("check", ":app:pitestDebug", ":app:connectedTestInputs");
        assertThat(gradleTasks(workflows.get(Path.of(".github", "workflows", "android.yml").toString())))
                .contains("assembleDebug", ":app:connectedDebugAndroidTest");
        assertThat(tasksTheScriptLacks(workflows, script)).isEmpty();
    }

    @Test
    void aTaskTheScriptLacksIsNamed() {
        var script = "GRADLE_USER_HOME=\"$home\" ./gradlew --stop > /dev/null 2>&1 || true\n"
                + "./gradlew --no-daemon --init-script \"$home/x.gradle\" \\\n"
                + "  assembleDebug lint :app:connectedTestInputs \\\n"
                + "  -PpitClasses='x*'\n";
        var workflow = "      - run: ./gradlew --stacktrace assembleDebug lint\n"
                + "      # ./gradlew bundleRelease, in a comment\n"
                + "      - run: out=$(./gradlew -q --console=plain :app:printReleaseVersion)\n"
                + "        script: ./gradlew --continue :app:connectedDebugAndroidTest && ./gradlew lint\n";

        assertThat(tasksTheScriptLacks(Map.of("a.yml", workflow), script)).hasSize(1).allSatisfy(message -> assertThat(
                message).contains("a.yml", ":app:printReleaseVersion", REFRESH_SCRIPT.toString(), "refresh"));
        assertThat(tasksTheScriptLacks(Map.of("a.yml", workflow), script.replace(" lint ", " "))).hasSize(2)
                .anySatisfy(message -> assertThat(message).contains("a.yml", "lint"));
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

    /**
     * The lines of {@code text} that name a flag or the property of the verification mode, another root build, an init
     * script or the refresh script, but not comment lines, and the refresh script not in a message. A line continued
     * with a backslash counts as one with the next; the short flags count only in a Gradle command, as
     * {@code grep -F} or {@code mkdir -p} is something else.
     */
    private static List<String> weakeningLines(String text) {
        return text.replace("\\\n", " ").lines().filter(line -> !line.strip().startsWith("#"))
                .filter(line -> WEAKENS.matcher(line).find() || ELSEWHERE.matcher(line).find()
                        || REFRESH.matcher(line).find() && !MESSAGE.matcher(line).matches()
                        || GRADLE_COMMAND.matcher(line).results()
                                .anyMatch(command -> SHORT_FLAG.matcher(command.group(1)).find()))
                .toList();
    }

    /** The lines of a build script or source that name the verification mode or switch it off, comments too. */
    private static List<String> modeLines(String text) {
        return text.lines().filter(line -> WEAKENS.matcher(line).find()).toList();
    }

    /**
     * What to do for each task a Gradle call of a workflow names that the refresh script's Gradle call does not.
     * {@code :app:connectedDebugAndroidTest} needs a device, so the script runs {@code :app:connectedTestInputs}, which
     * resolves that task's input files, in its place.
     */
    private static List<String> tasksTheScriptLacks(Map<String, String> workflows, String script) {
        var covered = new HashSet<>(gradleTasks(script));
        if (covered.contains(":app:connectedTestInputs")) {
            covered.add(":app:connectedDebugAndroidTest");
        }
        return workflows.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .flatMap(workflow -> gradleTasks(workflow.getValue()).stream().filter(task -> !covered.contains(task))
                        .map(task -> ("%s runs the Gradle task %s, which %s does not: add it to the script's"
                                + " Gradle call and refresh the checksums").formatted(workflow.getKey(), task,
                                        REFRESH_SCRIPT)))
                .toList();
    }

    /** The tasks the Gradle wrapper calls of {@code text} name, outside comment lines, without options. */
    private static List<String> gradleTasks(String text) {
        return text.replace("\\\n", " ").lines().filter(line -> !line.strip().startsWith("#"))
                .flatMap(line -> WRAPPER_CALL.matcher(line).results())
                .flatMap(call -> Arrays.stream(call.group(1).strip().split("\\s+")))
                .filter(word -> TASK.matcher(word).matches()).distinct().toList();
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

    /** The Android build's Gradle scripts and the Java and Kotlin sources of its builds, without what a build wrote. */
    private static List<Path> buildSources() throws IOException {
        var kinds = Pattern.compile(".*\\.(gradle|gradle\\.kts|java|kt)");
        var written = List.of("build", ".gradle", ".kotlin");
        var sources = new ArrayList<Path>();
        Files.walkFileTree(Path.of("android"), new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path folder, BasicFileAttributes attributes) {
                return written.contains(folder.getFileName().toString()) ? FileVisitResult.SKIP_SUBTREE
                        : FileVisitResult.CONTINUE;
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
