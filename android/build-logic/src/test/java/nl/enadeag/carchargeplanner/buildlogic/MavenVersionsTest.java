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
                    <groupId>org.slf4j</groupId><artifactId>slf4j-jdk14</artifactId><version>${slf4j.version}</version>
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
                <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId></dependency>
              </dependencies>
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
        Files.writeString(Files.createDirectory(repository.resolve("engine")).resolve("pom.xml"), ENGINE);
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
    void aVersionManagedOutsideTheOnesAndroidReadsFailsTheBuildNamingTheLibraryAndThePom() throws IOException {
        Files.writeString(repository.resolve("pom.xml"), PARENT.replace("</dependencies>\n  </dependencyManagement>",
                "<dependency><groupId>tools.jackson.core</groupId><artifactId>jackson-core</artifactId>"
                        + "<version>3.0.1</version></dependency></dependencies></dependencyManagement>"));

        assertThat(gradle().buildAndFail().getOutput()).contains("Android does not read",
                "tools.jackson.core:jackson-core", repository.resolve("pom.xml").toString());
    }

    @Test
    void aVersionTheEngineDependencySetsItselfFailsTheBuildNamingTheLibraryAndThePom() throws IOException {
        var engine = repository.resolve("engine/pom.xml");
        Files.writeString(engine, ENGINE.replace("slf4j-api</artifactId>", "slf4j-api</artifactId><version>1</version>"));

        assertThat(gradle().buildAndFail().getOutput()).contains("Android does not read", "org.slf4j:slf4j-api",
                engine.toString());
    }

    private static final String PROFILE_END = "</profile></profiles>";

    private void engineHas(String addition) throws IOException {
        Files.writeString(repository.resolve("engine/pom.xml"), ENGINE.replace("</project>", addition + "</project>"));
    }

    private void parentHas(String addition) throws IOException {
        Files.writeString(repository.resolve("pom.xml"), PARENT.replace("</project>", addition + "</project>"));
    }

    @Test
    void aPropertyTheEngineSetsFailsTheBuildNamingItAndThePom() throws IOException {
        engineHas("<properties><jackson.version>3.2.4</jackson.version></properties>");

        assertThat(gradle().buildAndFail().getOutput()).contains("Android does not read", "jackson.version",
                repository.resolve("engine/pom.xml").toString());
    }

    @Test
    void theEngineMaySetTheCompilerRelease() throws IOException {
        engineHas("<properties><maven.compiler.release>17</maven.compiler.release></properties>");

        assertThat(gradle().build().getOutput()).contains("jackson-bom 3.2.3");
    }

    @Test
    void aDependencyManagementInTheEngineFailsTheBuildNamingThePom() throws IOException {
        engineHas("<dependencyManagement><dependencies><dependency><groupId>org.slf4j</groupId>"
                + "<artifactId>slf4j-api</artifactId><version>2.0.18</version></dependency></dependencies>"
                + "</dependencyManagement>");

        assertThat(gradle().buildAndFail().getOutput()).contains("Android does not read",
                "dependencyManagement", repository.resolve("engine/pom.xml").toString());
    }

    @Test
    void anEngineProfileWithPropertiesFailsTheBuildNamingItAndThePom() throws IOException {
        engineHas("<profiles><profile><id>odd</id><properties><jackson.version>3.2.4</jackson.version></properties>"
                + PROFILE_END);

        assertThat(gradle().buildAndFail().getOutput()).contains("Android does not read", "odd",
                repository.resolve("engine/pom.xml").toString());
    }

    @Test
    void anEngineProfileThatOnlyConfiguresPluginsPasses() throws IOException {
        engineHas("<profiles><profile><id>coverage</id><build><plugins><plugin><artifactId>x</artifactId></plugin>"
                + "</plugins></build>" + PROFILE_END);

        assertThat(gradle().build().getOutput()).contains("jackson-bom 3.2.3");
    }

    @Test
    void aParentProfileThatSetsAVersionPropertyFailsTheBuildNamingItAndThePom() throws IOException {
        parentHas("<profiles><profile><id>odd</id><properties><jackson.version>3.2.4</jackson.version></properties>"
                + PROFILE_END);

        assertThat(gradle().buildAndFail().getOutput()).contains("Android does not read", "jackson.version", "odd",
                repository.resolve("pom.xml").toString());
    }

    @Test
    void aParentProfileThatPinsALibraryOfTheGroupsFailsTheBuildNamingItAndThePom() throws IOException {
        parentHas("<profiles><profile><id>odd</id><dependencyManagement><dependencies><dependency>"
                + "<groupId>org.assertj</groupId><artifactId>assertj-core</artifactId><version>1</version>"
                + "</dependency></dependencies></dependencyManagement>" + PROFILE_END);

        assertThat(gradle().buildAndFail().getOutput()).contains("Android does not read",
                "org.assertj:assertj-core", "odd", repository.resolve("pom.xml").toString());
    }

    @Test
    void aParentProfileThatOnlySetsOtherPropertiesPasses() throws IOException {
        parentHas("<profiles><profile><id>live</id><properties><groups>live</groups></properties>" + PROFILE_END);

        assertThat(gradle().build().getOutput()).contains("jackson-bom 3.2.3");
    }

    @Test
    void aJacksonTwoLibraryManagedInTheParentFailsTheBuildNamingItAndThePom() throws IOException {
        Files.writeString(repository.resolve("pom.xml"), PARENT.replace("</dependencies>\n  </dependencyManagement>",
                "<dependency><groupId>com.fasterxml.jackson.core</groupId><artifactId>jackson-annotations</artifactId>"
                        + "<version>2.20</version></dependency></dependencies></dependencyManagement>"));

        assertThat(gradle().buildAndFail().getOutput()).contains("Android does not read",
                "com.fasterxml.jackson.core:jackson-annotations", repository.resolve("pom.xml").toString());
    }

    @Test
    void anotherBomImportOfAGuardedGroupWithAnotherVersionFailsTheBuildNamingItAndThePom() throws IOException {
        Files.writeString(repository.resolve("pom.xml"), PARENT.replace("</dependencies>\n  </dependencyManagement>",
                "<dependency><groupId>org.junit</groupId><artifactId>other-bom</artifactId><version>1</version>"
                        + "<type>pom</type><scope>import</scope></dependency></dependencies>"
                        + "</dependencyManagement>"));

        assertThat(gradle().buildAndFail().getOutput()).contains("Android does not read", "org.junit:other-bom",
                repository.resolve("pom.xml").toString());
    }

    private static final String FOREIGN_BOM = "<dependency><groupId>org.springframework.boot</groupId>"
            + "<artifactId>spring-boot-dependencies</artifactId><version>4.0.0</version><type>pom</type>"
            + "<scope>import</scope></dependency>";

    @Test
    void aForeignBomImportedBeforeJacksonsFailsTheBuildNamingItAndThePom() throws IOException {
        Files.writeString(repository.resolve("pom.xml"), PARENT.replace("<dependencyManagement>\n    <dependencies>",
                "<dependencyManagement><dependencies>" + FOREIGN_BOM));

        assertThat(gradle().buildAndFail().getOutput()).contains("Android does not read",
                "org.springframework.boot:spring-boot-dependencies", repository.resolve("pom.xml").toString());
    }

    @Test
    void aForeignBomImportedInAParentProfileFailsTheBuildNamingItAndThePom() throws IOException {
        parentHas("<profiles><profile><id>odd</id><dependencyManagement><dependencies>" + FOREIGN_BOM
                + "</dependencies></dependencyManagement>" + PROFILE_END);

        assertThat(gradle().buildAndFail().getOutput()).contains("Android does not read",
                "org.springframework.boot:spring-boot-dependencies", "odd", repository.resolve("pom.xml").toString());
    }

    @Test
    void anAllowedBomImportedInAParentProfileFailsTheBuildNamingItAndThePom() throws IOException {
        parentHas("<profiles><profile><id>odd</id><dependencyManagement><dependencies><dependency>"
                + "<groupId>org.junit</groupId><artifactId>junit-bom</artifactId><version>6.0.0</version>"
                + "<type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement>"
                + PROFILE_END);

        assertThat(gradle().buildAndFail().getOutput()).contains("Android does not read", "org.junit:junit-bom",
                "odd", repository.resolve("pom.xml").toString());
    }

    @Test
    void aDependencyOfTheParentFailsTheBuildNamingThePom() throws IOException {
        parentHas("<dependencies><dependency><groupId>com.fasterxml.jackson.core</groupId>"
                + "<artifactId>jackson-annotations</artifactId><version>2.21</version></dependency></dependencies>");

        assertThat(gradle().buildAndFail().getOutput()).contains("Android does not read", "dependencies of",
                repository.resolve("pom.xml").toString());
    }

    @Test
    void aDependencyInAParentProfileFailsTheBuildNamingItAndThePom() throws IOException {
        parentHas("<profiles><profile><id>odd</id><dependencies><dependency><groupId>org.example</groupId>"
                + "<artifactId>x</artifactId><version>1</version></dependency></dependencies>" + PROFILE_END);

        assertThat(gradle().buildAndFail().getOutput()).contains("Android does not read", "odd",
                repository.resolve("pom.xml").toString());
    }

    @Test
    void aParentProfileWithOnlyPluginDependenciesPasses() throws IOException {
        parentHas("<profiles><profile><id>pit</id><build><plugins><plugin><artifactId>x</artifactId><dependencies>"
                + "<dependency><groupId>org.example</groupId><artifactId>y</artifactId><version>1</version>"
                + "</dependency></dependencies></plugin></plugins></build>" + PROFILE_END);

        assertThat(gradle().build().getOutput()).contains("jackson-bom 3.2.3");
    }

    @Test
    void aVersionPropertySetInMavensConfigFailsTheBuildNamingItAndTheFile() throws IOException {
        var config = Files.createDirectory(repository.resolve(".mvn")).resolve("maven.config");
        Files.writeString(config, "--strict-checksums\n-Djackson.version=3.1.5\n");

        assertThat(gradle().buildAndFail().getOutput()).contains("Android does not read", "jackson.version",
                config.toString());
    }

    @Test
    void aMavenConfigWithOtherOptionsPasses() throws IOException {
        Files.writeString(Files.createDirectory(repository.resolve(".mvn")).resolve("maven.config"),
                "--strict-checksums\n-Dgroups=live\n");

        assertThat(gradle().build().getOutput()).contains("jackson-bom 3.2.3");
    }

    @Test
    void aMissingPomFailsTheBuildNamingIt() throws IOException {
        Files.delete(repository.resolve("pom.xml"));

        assertThat(gradle().buildAndFail().getOutput()).contains(repository.resolve("pom.xml").toString());
    }
}
