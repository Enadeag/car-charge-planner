package nl.enadeag.carchargeplanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Gradle's dependency verification of the Android build stays strict, its default, everywhere (#484). No workflow and no
 * {@code gradle.properties} of the Android build names the verification flags ({@code --dependency-verification},
 * {@code -F}, {@code --write-verification-metadata}, {@code -M}) or the property that sets the mode, in any spelling and
 * with any value, outside a comment line: they have no reason to. No build file or build source of the Android build
 * switches it off for a configuration. And {@code android/gradle/verification-metadata.xml} checks poms too, SHA-256
 * only, without keys, and trusts only an IDE's sources and Javadoc. Here, with the Maven build, so every pull request
 * that changes a workflow runs it.
 */
class AndroidDependencyVerificationTest {

    private static final Path METADATA = Path.of("android", "gradle", "verification-metadata.xml");

    /**
     * The long flags, and the property {@code org.gradle.dependency.verification} however it is given: {@code -D},
     * {@code -P}, {@code GRADLE_OPTS}, {@code ORG_GRADLE_PROJECT_}, {@code gradle.properties}.
     */
    private static final Pattern WEAKENS = Pattern.compile(
            "--dependency-verification|--write-verification-metadata|(?i:dependency[._]?verification)");
    /** What follows {@code gradlew} or {@code gradle} up to the end of its command, where a short flag is Gradle's. */
    private static final Pattern GRADLE_COMMAND = Pattern.compile("\\bgradlew?\\b([^;&|)]*)");
    /** The short flags {@code -F} and {@code -M}, as a word of their own or with their value attached. */
    private static final Pattern SHORT_FLAG = Pattern.compile("(?<![\\w-])-[FM](?=$|[\\s=\"'a-z0-9])");

    @Test
    void theWorkflowsAndTheAndroidBuildsPropertiesAreRead() throws IOException {
        assertThat(commandFiles()).contains(Path.of(".github", "workflows", "android.yml"),
                Path.of(".github", "workflows", "android-release.yml"), Path.of(".github", "workflows", "mutation.yml"),
                Path.of("android", "gradle.properties"));
    }

    @Test
    void noWorkflowAndNoPropertiesFileSetsTheVerificationMode() throws IOException {
        for (var file : commandFiles()) {
            assertThat(weakeningLines(Files.readString(file))).as("lines of %s that set the verification mode", file)
                    .isEmpty();
        }
    }

    @Test
    void theModeIsFoundInEveryFormAndOnlyACommentLineIsLeftOut() {
        for (var weaker : List.of("org.gradle.dependency.verification=lenient",
                "run: ./gradlew -Dorg.gradle.dependency.verification=off check",
                "run: ./gradlew -Dx=\" #\" -Dorg.gradle.dependency.verification=lenient check",
                "  GRADLE_OPTS: -Dorg.gradle.dependency.verification=strict",
                "  ORG_GRADLE_PROJECT_org.gradle.dependency.verification: off",
                "  ORG_GRADLE_DEPENDENCY_VERIFICATION: off", "  dependencyVerification: lenient",
                "run: ./gradlew --dependency-verification=off check", "run: ./gradlew --dependency-verification lenient",
                "run: ./gradlew -F lenient check", "run: ./gradlew -F=off check", "run: ./gradlew -Flenient check",
                "run: ./gradlew check -F", "run: ./gradlew --write-verification-metadata sha256 help",
                "run: ./gradlew -M sha256 help", "run: ./gradlew -Msha256 help", "  run: ./gradlew check \\\n  -F off",
                "  run: cd android && ./gradlew help -F=off # lenient")) {
            assertThat(weakeningLines(weaker)).as(weaker).hasSize(1);
        }
        assertThat(weakeningLines("# org.gradle.dependency.verification=lenient\n    # -F off\n"
                + "  if [ -d build/reports/dependency-verification ]; then\n  cache: off\n"
                + "  run: mvn -B -f pom.xml\n  if printf '%s\\n' \"$tags\" | grep -Fqx \"$ref\"; then\n"
                + "  run: ./gradlew check && grep -F -- x y\n")).isEmpty();
    }

    @Test
    void noAndroidBuildFileSwitchesTheVerificationOffForAConfiguration() throws IOException {
        var sources = buildSources();
        assertThat(sources).contains(Path.of("android", "app", "build.gradle.kts"),
                Path.of("android", "settings.gradle.kts"), Path.of("android", "build-logic", "build.gradle.kts"));
        for (var file : sources) {
            assertThat(Files.readString(file)).as(file.toString()).doesNotContain("disableDependencyVerification");
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

    /**
     * The lines of {@code text} that name a flag or the property of the verification mode, but not comment lines. A
     * line continued with a backslash counts as one with the next; {@code -F} and {@code -M} count only in a Gradle
     * command, as {@code grep -F} is something else.
     */
    private static List<String> weakeningLines(String text) {
        return text.replace("\\\n", " ").lines().filter(line -> !line.strip().startsWith("#"))
                .filter(line -> WEAKENS.matcher(line).find() || GRADLE_COMMAND.matcher(line).results()
                        .anyMatch(command -> SHORT_FLAG.matcher(command.group(1)).find()))
                .toList();
    }

    /** Every workflow, and every {@code gradle.properties} of the Android build and its included builds. */
    private static List<Path> commandFiles() throws IOException {
        var files = new ArrayList<Path>();
        try (Stream<Path> workflows = Files.list(Path.of(".github", "workflows"))) {
            workflows.filter(file -> file.toString().endsWith(".yml") || file.toString().endsWith(".yaml"))
                    .sorted().forEach(files::add);
        }
        // android/ and the folders of its projects and included builds
        try (Stream<Path> android = Files.find(Path.of("android"), 2,
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
