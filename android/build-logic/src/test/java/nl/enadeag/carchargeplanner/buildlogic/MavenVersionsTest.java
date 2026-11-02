package nl.enadeag.carchargeplanner.buildlogic;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A Gradle build of its own, laid out as the repository is: the pom at the root, the Gradle build in android/. */
class MavenVersionsTest {

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

    private static final String SETTINGS = """
            plugins {
                id("ccp.maven-versions")
            }

            dependencyResolutionManagement {
                versionCatalogs {
                    create("libs") {
                        library("jackson-bom", "tools.jackson", "jackson-bom").versionRef("jackson")
                        library("slf4j-api", "org.slf4j", "slf4j-api").versionRef("slf4j")
                        mavenVersions.addTo(this, file("../pom.xml"))
                    }
                }
            }
            """;

    // printed while configuring, so a build that reuses its configuration cache prints nothing
    private static final String BUILD = """
            println("jackson-bom " + libs.jackson.bom.get().version)
            println("slf4j-api " + libs.slf4j.api.get().version)
            println("versions " + with(libs.versions) { listOf(jackson, slf4j, junit, assertj, archunit) }
                    .map { it.get() })
            """;

    @TempDir
    Path repository;

    private Path android;

    @BeforeEach
    void layOut() throws IOException {
        Files.writeString(repository.resolve("pom.xml"), PARENT);
        android = Files.createDirectory(repository.resolve("android"));
        Files.writeString(android.resolve("settings.gradle.kts"), SETTINGS);
        Files.writeString(android.resolve("build.gradle.kts"), BUILD);
        Files.writeString(android.resolve("gradle.properties"), "org.gradle.jvmargs=-Xmx256m\n");
    }

    private GradleRunner gradle() {
        return GradleRunner.create().withProjectDir(android.toFile()).withPluginClasspath()
                .withArguments("help", "--configuration-cache", "--quiet");
    }

    @Test
    void aVersionInThePomReachesTheLibrariesEvenFromTheConfigurationCache() throws IOException {
        assertThat(gradle().build().getOutput()).contains("jackson-bom 3.2.3", "slf4j-api 2.0.20",
                "versions [3.2.3, 2.0.20, 6.1.3, 3.27.7, 1.5.1]");
        assertThat(gradle().build().getOutput()).doesNotContain("jackson-bom");

        Files.writeString(repository.resolve("pom.xml"), PARENT.replace(">3.2.3<", ">3.2.4<"));

        assertThat(gradle().build().getOutput()).contains("jackson-bom 3.2.4", "slf4j-api 2.0.20");
    }

    @Test
    void aPropertyThePomLacksFailsTheBuildNamingItAndThePom() throws IOException {
        Files.writeString(repository.resolve("pom.xml"), PARENT.replace("archunit.version>", "archunit.v>"));

        assertThat(gradle().buildAndFail().getOutput()).contains("archunit.version",
                repository.resolve("pom.xml").toString());
    }

    @Test
    void aLibraryThePomLacksFailsTheBuildNamingItAndThePom() throws IOException {
        Files.writeString(repository.resolve("pom.xml"), PARENT.replace("<artifactId>junit-bom<", "<artifactId>bom<"));

        assertThat(gradle().buildAndFail().getOutput()).contains("org.junit:junit-bom",
                repository.resolve("pom.xml").toString());
    }

    @Test
    void aMissingPomFailsTheBuildNamingIt() throws IOException {
        Files.delete(repository.resolve("pom.xml"));

        assertThat(gradle().buildAndFail().getOutput()).contains(repository.resolve("pom.xml").toString());
    }
}
