package nl.enadeag.carchargeplanner.buildlogic;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MavenDriftTest {

    private static final Set<String> TEST_GROUPS = MavenDriftPlugin.TEST_GROUPS;

    @TempDir
    Path folder;

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

    /** The Android build as it matches {@link #ENGINE}: the same libraries, BOMs left out. */
    private static List<Declared> gradle() {
        return new ArrayList<>(List.of(
                new Declared("tools.jackson.core", "jackson-databind"),
                new Declared("tools.jackson.core", "jackson-core"),
                new Declared("org.slf4j", "slf4j-api"),
                new Declared("org.junit.jupiter", "junit-jupiter-api"),
                new Declared("org.junit.jupiter", "junit-jupiter-engine"),
                new Declared("org.assertj", "assertj-core")));
    }

    private List<String> differences(String engine, List<Declared> gradle) throws IOException {
        var enginePom = Files.writeString(folder.resolve("engine-pom.xml"), engine);
        return MavenDriftCheck.differences(MavenPoms.libraries(enginePom, TEST_GROUPS), gradle);
    }

    @Test
    void theSameLibrariesInBothBuildsAgree() throws IOException {
        assertThat(differences(ENGINE, gradle())).isEmpty();
    }

    @Test
    void theSameLibrariesInAnotherOrderAgree() throws IOException {
        var gradle = gradle();
        Collections.reverse(gradle);

        assertThat(differences(ENGINE, gradle)).isEmpty();
    }

    @Test
    void aLibraryTheEngineAddsInMavenOnlyIsADifference() throws IOException {
        var engine = ENGINE.replace("<dependencies>", """
                <dependencies>
                  <dependency>
                    <groupId>com.example</groupId><artifactId>extra</artifactId><version>1.0</version>
                  </dependency>
                """);

        assertThat(differences(engine, gradle())).containsExactly("com.example:extra: only in Maven");
    }

    @Test
    void aLibraryTheAndroidBuildAddsIsADifference() throws IOException {
        var gradle = gradle();
        gradle.add(new Declared("tools.jackson.core", "jackson-annotations"));

        assertThat(differences(ENGINE, gradle))
                .containsExactly("tools.jackson.core:jackson-annotations: only in Gradle");
    }

    @Test
    void aLibraryOfAnotherGroupIsADifferenceOnBothSides() throws IOException {
        var gradle = gradle();
        gradle.set(2, new Declared("org.slf4j.x", "slf4j-api"));

        assertThat(differences(ENGINE, gradle)).containsExactly("org.slf4j.x:slf4j-api: only in Gradle",
                "org.slf4j:slf4j-api: only in Maven");
    }

    @Test
    void aTestLibraryOfTheContractTestsIsComparedToo() throws IOException {
        var gradle = gradle();
        gradle.remove(new Declared("org.junit.jupiter", "junit-jupiter-engine"));

        assertThat(differences(ENGINE, gradle))
                .containsExactly("org.junit.jupiter:junit-jupiter-engine: only in Maven");
    }

    @Test
    void testLibrariesOfOtherGroupsAreLeftOut() throws IOException {
        var enginePom = Files.writeString(folder.resolve("engine-pom.xml"), ENGINE);

        assertThat(MavenPoms.libraries(enginePom, TEST_GROUPS)).extracting(Declared::module)
                .doesNotContain("org.slf4j:slf4j-jdk14", "com.tngtech.archunit:archunit");
    }

    @Test
    void theRepositorysEnginePomDeclaresTheLibrariesItIsKnownToDeclare() throws IOException {
        var root = Path.of("../..").toAbsolutePath().normalize();

        assertThat(MavenPoms.libraries(root.resolve("engine/pom.xml"), TEST_GROUPS)).extracting(Declared::module)
                .containsExactlyInAnyOrder("tools.jackson.core:jackson-databind", "tools.jackson.core:jackson-core",
                        "org.slf4j:slf4j-api", "org.junit.jupiter:junit-jupiter-api",
                        "org.junit.jupiter:junit-jupiter-params", "org.junit.jupiter:junit-jupiter-engine",
                        "org.assertj:assertj-core");
    }
}
