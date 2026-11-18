package nl.enadeag.carchargeplanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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
 * Gradle's dependency verification of the Android build stays strict, its default (#484). Gradle's strict default is
 * the real check; this test is a tripwire beside it, in the Maven build, so every pull request runs it.
 *
 * <p>It checks that {@code verification-metadata.xml} records a SHA-256 for every file, poms included, without keys,
 * and trusts only an IDE's sources and Javadoc; that no build file of the Android build sets the verification mode or
 * switches the check off; that the wrapper's scripts and jar are the ones recorded in {@link #WRAPPER} and it
 * downloads Gradle from Gradle with a checksum; that no line of a workflow or composite action under
 * {@code .github} gives Gradle an option that weakens the check or rewrites the metadata; and that the repository's
 * root and {@code release/} hold no build of their own, which would run without the metadata.
 *
 * <p>It catches a mistake, not intent. A workflow can switch the check off in ways this test does not see, such as a
 * script it runs or a variable it builds. The review of a pull request that touches {@code .github/}, the Android
 * build's files or this test is what protects against that.
 */
class AndroidDependencyVerificationTest {

    private static final Path METADATA = Path.of("android", "gradle", "verification-metadata.xml");

    /**
     * The wrapper's files, by their SHA-256: its scripts with every line end as LF, as a checkout may give them other
     * line ends, and its jar as it is. A Gradle update records the new values.
     */
    private static final Map<Path, String> WRAPPER = Map.of(
            Path.of("android", "gradlew"), "a5a5c199ba02189ae8c46a334223371a20599d9c298ef65e7540ede4a3f72d59",
            Path.of("android", "gradlew.bat"), "31ec098bd961c460b62875950c3eff5c8213bcb0bbde38d769b88512877fd21f",
            Path.of("android", "gradle", "wrapper", "gradle-wrapper.jar"),
            "621008afc4f5d59357fcf06ffa644dd5aade5e091bdc363742710445f73cdaf8");

    /** The verification mode in a build file, by its flag, its property or its API, in comments too. */
    private static final Pattern MODE = Pattern.compile(
            "--dependency-verification|--write-verification-metadata|(?i:dependency[._]?verification)");

    /**
     * Gradle's long options that weaken the check or rewrite the metadata, and the mode's property. The names of the
     * metadata file and the refresh script, which the workflows' hint after a failed check gives, are not among them.
     */
    private static final Pattern WEAKENS = Pattern.compile("--dependency-verification|--write-verification-metadata"
            + "|--refresh-keys|--export-keys|org[.]gradle[.]dependency[.]verification");
    /** The short forms of the mode and write options, which count only on a line that names Gradle. */
    private static final Pattern SHORT = Pattern.compile("(?<!\\S)-[FM](?=[\\s=]|$|lenient|off|strict|sha|pgp|md5)");
    /** The wrapper, or Gradle as a command, but not a path such as {@code gradle/actions} or {@code build.gradle}. */
    private static final Pattern GRADLE = Pattern.compile("gradlew|(?<![\\w./-])gradle(?![\\w./-])");
    private static final Pattern YAML = Pattern.compile("(?i).*[.]ya?ml");
    private static final List<String> ROOT_BUILD =
            List.of("gradlew", "gradlew.bat", "settings.gradle", "settings.gradle.kts");

    @Test
    void theWorkflowsUnderGithubAreFoundAndRead() throws IOException {
        var files = workflowFiles(Path.of(""));
        var workflows = Path.of(".github", "workflows");
        assertThat(files).contains(workflows.resolve("android.yml"), workflows.resolve("android-release.yml"),
                workflows.resolve("mutation.yml"));
        assertThat(Files.readString(workflows.resolve("android.yml"))).contains("./gradlew");
    }

    @Test
    void noWorkflowOrCompositeActionWeakensTheCheckOrRewritesTheMetadata() throws IOException {
        for (var file : workflowFiles(Path.of(""))) {
            assertThat(problems(file.toString(), Files.readString(file))).as(file.toString()).isEmpty();
        }
    }

    @Test
    void aLineThatWeakensTheCheckOrRewritesTheMetadataIsRefusedByFileAndLine() {
        for (var line : List.of("run: ./gradlew -F lenient build", "run: ./gradlew -Foff check",
                "run: ./gradlew --dependency-verification=off check", "run: ./gradlew -M sha256 help",
                "run: ./gradlew --write-verification-metadata sha256 help", "run: ./gradlew --refresh-keys check",
                "run: ./gradlew --export-keys", "GRADLE_OPTS: -Dorg.gradle.dependency.verification=lenient",
                "ORG_GRADLE_PROJECT_org.gradle.dependency.verification: off")) {
            assertThat(problems("a.yml", "on: push\n" + line + "\n")).as(line).singleElement().asString()
                    .startsWith("a.yml line 2: ");
        }
        // a call continued over lines is one line to the shell, so a short flag on the second still counts
        assertThat(problems("a.yml", "run: |\n  ./gradlew \\\n    -F off check\n")).singleElement().asString()
                .startsWith("a.yml line 2: ").contains("-F");

        for (var line : List.of("run: ./gradlew --continue testDebugUnitTest", "run: tar -xf x.tar",
                "echo \"run sh tools/refresh-android-verification.sh, read android/gradle/verification-metadata.xml\"",
                "if [ -d build/reports/dependency-verification ]; then", "grep -F x app/build.gradle.kts",
                "uses: gradle/actions/setup-gradle@v5", "run: grep -Fqx \"$ref\" tags", "curl -F file=@x.txt host")) {
            assertThat(problems("a.yml", line + "\n")).as(line).isEmpty();
        }
    }

    /** The workflows run steps in the root and in {@code release/}; a build there has no checksums of its own. */
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
        Files.writeString(lf, script.replace("exec \"$JAVACMD\" \"$@\"\n", "exec \"$JAVACMD\" \"$@\" -F off\n"));
        assertThat(wrapperHash(lf)).isNotEqualTo(WRAPPER.get(Path.of("android", "gradlew")));
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
        var android = Path.of("android");
        var sources = buildSources(android);
        assertThat(sources).contains(android.resolve(Path.of("app", "build.gradle.kts")),
                android.resolve("settings.gradle.kts"), android.resolve(Path.of("build-logic", "build.gradle.kts")),
                android.resolve("gradle.properties"), android.resolve("gradlew"), android.resolve("gradlew.bat"),
                android.resolve(Path.of("gradle", "wrapper", "gradle-wrapper.properties")));
        for (var file : sources) {
            assertThat(modeLines(Files.readString(file))).as("lines of %s that set the verification mode", file)
                    .isEmpty();
        }
        for (var weaker : List.of("    resolutionStrategy.disableDependencyVerification()",
                "gradle.startParameter.dependencyVerificationMode = DependencyVerificationMode.OFF",
                "    // startParameter.setDependencyVerificationMode(LENIENT)",
                "DEFAULT_JVM_OPTS='\"-Xmx64m\" \"-Dorg.gradle.dependency.verification=off\"'",
                "org.gradle.dependency.verification=lenient",
                "systemProp.org.gradle.dependency.verification=lenient")) {
            assertThat(modeLines(weaker)).as(weaker).hasSize(1);
        }
        assertThat(modeLines("org.gradle.jvmargs=-Xmx2g\norg.gradle.configuration-cache=true\n")).isEmpty();
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
     * The lines of {@code text}, the file {@code name}, that give Gradle an option that weakens the check or rewrites
     * the metadata, each by the file and its line number. A line a backslash continues is read with the next, as the
     * shell reads it, and counted by its first line.
     */
    private static List<String> problems(String name, String text) {
        var lines = text.lines().toList();
        var problems = new ArrayList<String>();
        for (int i = 0; i < lines.size(); i++) {
            int first = i;
            var line = new StringBuilder(lines.get(i));
            while (line.toString().endsWith("\\") && i + 1 < lines.size()) {
                line.append('\n').append(lines.get(++i));
            }
            var found = new ArrayList<>(WEAKENS.matcher(line).results().map(MatchResult::group).toList());
            if (GRADLE.matcher(line).find()) {
                SHORT.matcher(line).results().map(MatchResult::group).forEach(found::add);
            }
            found.forEach(option -> problems.add(("%s line %d: %s weakens the dependency verification or rewrites"
                    + " its metadata").formatted(name, first + 1, option)));
        }
        return problems;
    }

    /** The workflows, composite actions and other YAML files under {@code .github} of {@code root}. */
    private static List<Path> workflowFiles(Path root) throws IOException {
        try (Stream<Path> found = Files.find(root.resolve(".github"), Integer.MAX_VALUE,
                (file, attributes) -> attributes.isRegularFile() && YAML.matcher(file.getFileName().toString())
                        .matches())) {
            return found.sorted().toList();
        }
    }

    /** The wrapper and settings files in {@code root} and its {@code release/}, each the start of another build. */
    private static List<Path> otherRootBuilds(Path root) {
        return Stream.of(root, root.resolve("release")).flatMap(folder -> ROOT_BUILD.stream().map(folder::resolve))
                .filter(Files::exists).toList();
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

    /** The lines of a build script or source that name the verification mode or switch it off, comments too. */
    private static List<String> modeLines(String text) {
        return text.lines().filter(line -> MODE.matcher(line).find()).toList();
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
