package nl.enadeag.carchargeplanner.buildlogic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
                <dependency>
                  <groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>${slf4j.version}</version>
                </dependency>
                <dependency>
                  <groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter-api</artifactId><scope>test</scope>
                </dependency>
              </dependencies>
            </project>
            """;

    private static final List<Declared> DECLARED = List.of(
            new Declared("org.junit", "junit-bom", "6.1.3", true),
            new Declared("org.slf4j", "slf4j-api", "2.0.20", false),
            new Declared("org.junit.jupiter", "junit-jupiter-api", null, false));

    @TempDir
    Path folder;

    private List<Declared> read(String parent, String module) throws IOException {
        return MavenPoms.read(Files.writeString(folder.resolve("pom.xml"), parent),
                Files.writeString(folder.resolve("module-pom.xml"), module), Set.of("org.junit.jupiter"));
    }

    @Test
    void thePomsAreReadWithTheirBomAndTheirVersions() throws IOException {
        assertThat(read(PARENT, MODULE)).containsExactlyElementsOf(DECLARED);
    }

    @Test
    void whitespaceAroundAValueIsNotPartOfIt() throws IOException {
        var parent = PARENT.replace("<slf4j.version>2.0.20<", "<slf4j.version>\n  2.0.20\n<")
                .replace("<version>6.1.3<", "<version>\n  6.1.3 <").replace("<scope>import<", "<scope> import\n<");
        var module = MODULE.replace("<scope>test<", "<scope>\n  test <")
                .replace("<version>${slf4j.version}<", "<version> ${slf4j.version}\n<");

        assertThat(read(parent, module)).containsExactlyElementsOf(DECLARED);
    }

    @Test
    void anUnknownPropertyStaysAsItIsAndShowsAsADifference() throws IOException {
        var module = MODULE.replace("${slf4j.version}", "${nope}");

        var maven = read(PARENT, module);

        assertThat(maven).contains(new Declared("org.slf4j", "slf4j-api", "${nope}", false));
        assertThat(Pins.differences(Pins.of(maven), Pins.of(DECLARED)))
                .containsExactly("org.slf4j:slf4j-api: Maven ${nope}, Gradle 2.0.20");
    }

    @Test
    void aPomWithADoctypeIsRefused() {
        var parent = "<!DOCTYPE project [<!ENTITY x \"y\">]>\n" + PARENT;

        assertThatIOException().isThrownBy(() -> read(parent, MODULE));
    }

    @Test
    void aFileThatIsNotAPomIsRefused() {
        assertThatIOException().isThrownBy(() -> read(PARENT, "<foo/>")).withMessageContaining("not a Maven pom");
    }
}
