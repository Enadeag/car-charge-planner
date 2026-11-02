package nl.enadeag.carchargeplanner.buildlogic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MavenPomsTest {

    private static final String PARENT = """
            <project>
              <properties><slf4j.version>2.0.20</slf4j.version></properties>
              <dependencyManagement>
                <dependencies>
                  <dependency>
                    <groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>${slf4j.version}</version>
                  </dependency>
                  <dependency>
                    <groupId>org.junit</groupId><artifactId>junit-bom</artifactId>
                    <version>6.1.3</version><type>pom</type><scope>import</scope>
                  </dependency>
                </dependencies>
              </dependencyManagement>
            </project>
            """;

    private static final String MODULE = """
            <project>
              <dependencies>
                <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId></dependency>
                <dependency>
                  <groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter-api</artifactId><scope>test</scope>
                </dependency>
                <dependency>
                  <groupId>org.slf4j</groupId><artifactId>slf4j-jdk14</artifactId><scope>test</scope>
                </dependency>
              </dependencies>
            </project>
            """;

    private static final Declared SLF4J = new Declared("org.slf4j", "slf4j-api");
    private static final Declared JUNIT_BOM = new Declared("org.junit", "junit-bom");

    @TempDir
    Path folder;

    private static String version(String parent, Declared library) throws IOException {
        return MavenPoms.managedVersion(parent.getBytes(StandardCharsets.UTF_8), "parent/pom.xml", library);
    }

    @Test
    void aManagedVersionIsReadWithItsPropertyFilledIn() throws IOException {
        assertThat(version(PARENT, SLF4J)).isEqualTo("2.0.20");
        assertThat(version(PARENT, JUNIT_BOM)).isEqualTo("6.1.3");
    }

    @Test
    void whitespaceAroundAValueIsNotPartOfIt() throws IOException {
        var parent = PARENT.replace("<slf4j.version>2.0.20<", "<slf4j.version>\n  2.0.20\n<")
                .replace("<version>6.1.3<", "<version>\n  6.1.3 <")
                .replace("<artifactId>junit-bom<", "<artifactId> junit-bom\n<");

        assertThat(version(parent, SLF4J)).isEqualTo("2.0.20");
        assertThat(version(parent, JUNIT_BOM)).isEqualTo("6.1.3");
    }

    @Test
    void aPropertyThePomDoesNotSetIsRefusedNamingItAndThePom() {
        var parent = PARENT.replace("<slf4j.version>2.0.20</slf4j.version>", "<slf4j.v>2.0.20</slf4j.v>");

        assertThatIllegalArgumentException().isThrownBy(() -> version(parent, SLF4J))
                .withMessageContaining("parent/pom.xml").withMessageContaining("slf4j.version");
    }

    @Test
    void aLibraryThePomDoesNotManageIsRefusedNamingItAndThePom() {
        var parent = PARENT.replace("<artifactId>slf4j-api<", "<artifactId>slf4j-api2<");

        assertThatIllegalArgumentException().isThrownBy(() -> version(parent, SLF4J))
                .withMessageContaining("parent/pom.xml").withMessageContaining("org.slf4j:slf4j-api");
    }

    @Test
    void aLibraryOnlyTheModuleDeclaresHasNoManagedVersion() {
        assertThatIllegalArgumentException().isThrownBy(() -> version(MODULE, SLF4J))
                .withMessageContaining("dependencyManagement");
    }

    @Test
    void aManagedLibraryWithoutAVersionIsRefused() {
        var parent = PARENT.replace("<version>${slf4j.version}</version>", "");

        assertThatIllegalArgumentException().isThrownBy(() -> version(parent, SLF4J))
                .withMessageContaining("org.slf4j:slf4j-api has no version");
    }

    @Test
    void theModulesLibrariesAreItsOwnAndItsTestLibrariesOfTheGroupsAsked() throws IOException {
        var module = Files.writeString(folder.resolve("pom.xml"), MODULE);

        assertThat(MavenPoms.libraries(module, Set.of("org.junit"))).containsExactly(SLF4J,
                new Declared("org.junit.jupiter", "junit-jupiter-api"));
        assertThat(MavenPoms.libraries(module, Set.of())).containsExactly(SLF4J);
    }

    @Test
    void aPomWithADoctypeIsRefused() {
        var parent = "<!DOCTYPE project [<!ENTITY x \"y\">]>\n" + PARENT;

        assertThatIOException().isThrownBy(() -> version(parent, SLF4J));
    }

    @Test
    void aFileThatIsNotAPomIsRefused() throws IOException {
        var notAPom = Files.writeString(folder.resolve("pom.xml"), "<foo/>");

        assertThatIOException().isThrownBy(() -> version("<foo/>", SLF4J)).withMessageContaining("not a Maven pom");
        assertThatIOException().isThrownBy(() -> MavenPoms.libraries(notAPom, Set.of()))
                .withMessageContaining("not a Maven pom");
    }
}
