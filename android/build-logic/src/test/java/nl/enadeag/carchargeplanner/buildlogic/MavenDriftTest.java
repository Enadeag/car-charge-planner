package nl.enadeag.carchargeplanner.buildlogic;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MavenDriftTest {

    private static final Set<String> TEST_GROUPS = MavenDriftPlugin.TEST_GROUPS;

    @TempDir
    Path folder;

    private static final String PARENT = """
            <project>
              <properties>
                <jackson.version>3.2.3</jackson.version>
                <slf4j.version>2.0.20</slf4j.version>
                <junit.version>6.1.3</junit.version>
                <assertj.version>3.27.7</assertj.version>
                <archunit.version>1.5.1</archunit.version>
              </properties>
              <dependencyManagement>
                <dependencies>
                  <dependency>
                    <groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>${slf4j.version}</version>
                  </dependency>
                  <dependency>
                    <groupId>org.slf4j</groupId><artifactId>slf4j-jdk14</artifactId><version>${slf4j.version}</version>
                  </dependency>
                  <dependency>
                    <groupId>tools.jackson</groupId><artifactId>jackson-bom</artifactId>
                    <version>${jackson.version}</version><type>pom</type><scope>import</scope>
                  </dependency>
                  <dependency>
                    <groupId>org.junit</groupId><artifactId>junit-bom</artifactId>
                    <version>${junit.version}</version><type>pom</type><scope>import</scope>
                  </dependency>
                  <dependency>
                    <groupId>org.assertj</groupId><artifactId>assertj-core</artifactId>
                    <version>${assertj.version}</version>
                  </dependency>
                  <dependency>
                    <groupId>com.tngtech.archunit</groupId><artifactId>archunit</artifactId>
                    <version>${archunit.version}</version>
                  </dependency>
                </dependencies>
              </dependencyManagement>
            </project>
            """;

    private static final String ENGINE = """
            <project>
              <dependencies>
                <dependency><groupId>tools.jackson.core</groupId><artifactId>jackson-databind</artifactId></dependency>
                <dependency><groupId>tools.jackson.core</groupId><artifactId>jackson-core</artifactId></dependency>
                <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId></dependency>
                <dependency>
                  <groupId>org.slf4j</groupId><artifactId>slf4j-jdk14</artifactId><scope>test</scope>
                </dependency>
                <dependency>
                  <groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter-api</artifactId><scope>test</scope>
                </dependency>
                <dependency>
                  <groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter-engine</artifactId><scope>test</scope>
                </dependency>
                <dependency>
                  <groupId>org.assertj</groupId><artifactId>assertj-core</artifactId><scope>test</scope>
                </dependency>
                <dependency>
                  <groupId>com.tngtech.archunit</groupId><artifactId>archunit</artifactId><scope>test</scope>
                </dependency>
              </dependencies>
            </project>
            """;

    /** The Android build as it matches {@link #ENGINE}: the same libraries, BOMs and versions. */
    private static List<Declared> gradle() {
        return new ArrayList<>(List.of(
                new Declared("tools.jackson", "jackson-bom", "3.2.3", true),
                new Declared("tools.jackson.core", "jackson-databind", null, false),
                new Declared("tools.jackson.core", "jackson-core", null, false),
                new Declared("org.slf4j", "slf4j-api", "2.0.20", false),
                new Declared("org.junit", "junit-bom", "6.1.3", true),
                new Declared("org.junit.jupiter", "junit-jupiter-api", null, false),
                new Declared("org.junit.jupiter", "junit-jupiter-engine", null, false),
                new Declared("org.assertj", "assertj-core", "3.27.7", false)));
    }

    private List<String> differences(String parent, String engine, List<Declared> gradle) throws IOException {
        var parentPom = Files.writeString(folder.resolve("pom.xml"), parent);
        var enginePom = Files.writeString(folder.resolve("engine-pom.xml"), engine);
        return Pins.differences(Pins.of(MavenPoms.read(parentPom, enginePom, TEST_GROUPS)), Pins.of(gradle));
    }

    @Test
    void theSameLibrariesAndVersionsInBothBuildsAgree() throws IOException {
        assertThat(differences(PARENT, ENGINE, gradle())).isEmpty();
    }

    @Test
    void anotherVersionOfAPinnedLibraryIsADifference() throws IOException {
        var parent = PARENT.replace("<slf4j.version>2.0.20<", "<slf4j.version>2.0.21<");

        assertThat(differences(parent, ENGINE, gradle()))
                .containsExactly("org.slf4j:slf4j-api: Maven 2.0.21, Gradle 2.0.20");
    }

    @Test
    void anotherVersionOfABomIsADifferenceForEachLibraryItManages() throws IOException {
        var parent = PARENT.replace("<jackson.version>3.2.3<", "<jackson.version>3.2.4<");

        assertThat(differences(parent, ENGINE, gradle())).containsExactly(
                "tools.jackson.core:jackson-core: Maven BOM tools.jackson:jackson-bom:3.2.4, "
                        + "Gradle BOM tools.jackson:jackson-bom:3.2.3",
                "tools.jackson.core:jackson-databind: Maven BOM tools.jackson:jackson-bom:3.2.4, "
                        + "Gradle BOM tools.jackson:jackson-bom:3.2.3");
    }

    @Test
    void aTestLibraryOfTheContractTestsIsComparedToo() throws IOException {
        var parent = PARENT.replace("<junit.version>6.1.3<", "<junit.version>6.2.0<");

        assertThat(differences(parent, ENGINE, gradle())).hasSize(2)
                .allSatisfy(line -> assertThat(line).startsWith("org.junit.jupiter:"));
    }

    @Test
    void aLibraryTheEngineAddsInMavenOnlyIsADifference() throws IOException {
        var engine = ENGINE.replace("<dependencies>", """
                <dependencies>
                  <dependency>
                    <groupId>com.example</groupId><artifactId>extra</artifactId><version>1.0</version>
                  </dependency>
                """);

        assertThat(differences(PARENT, engine, gradle())).containsExactly("com.example:extra: only in Maven (1.0)");
    }

    @Test
    void aLibraryTheAndroidBuildAddsIsADifference() throws IOException {
        var gradle = gradle();
        gradle.add(new Declared("tools.jackson.core", "jackson-annotations", null, false));

        assertThat(differences(PARENT, ENGINE, gradle)).containsExactly(
                "tools.jackson.core:jackson-annotations: only in Gradle (BOM tools.jackson:jackson-bom:3.2.3)");
    }

    @Test
    void aVersionGivenOutsideTheBomIsADifference() throws IOException {
        var gradle = gradle();
        gradle.set(1, new Declared("tools.jackson.core", "jackson-databind", "3.2.3", false));

        assertThat(differences(PARENT, ENGINE, gradle)).containsExactly(
                "tools.jackson.core:jackson-databind: Maven BOM tools.jackson:jackson-bom:3.2.3, Gradle 3.2.3");
    }

    @Test
    void testLibrariesOfOtherGroupsAreLeftOut() throws IOException {
        var parentPom = Files.writeString(folder.resolve("pom.xml"), PARENT);
        var enginePom = Files.writeString(folder.resolve("engine-pom.xml"), ENGINE);

        assertThat(MavenPoms.read(parentPom, enginePom, TEST_GROUPS)).extracting(Declared::module)
                .doesNotContain("org.slf4j:slf4j-jdk14", "com.tngtech.archunit:archunit");
    }

    @Test
    void theRepositorysOwnPomsAgreeWithTheLibrariesTheyAreKnownToDeclare() throws IOException {
        var root = Path.of("../..").toAbsolutePath().normalize();

        var maven = Pins.of(MavenPoms.read(root.resolve("pom.xml"), root.resolve("engine/pom.xml"), TEST_GROUPS));

        assertThat(maven).containsOnlyKeys("tools.jackson.core:jackson-databind", "tools.jackson.core:jackson-core",
                "org.slf4j:slf4j-api", "org.junit.jupiter:junit-jupiter-api", "org.junit.jupiter:junit-jupiter-params",
                "org.junit.jupiter:junit-jupiter-engine", "org.assertj:assertj-core");
    }
}
