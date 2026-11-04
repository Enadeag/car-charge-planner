package nl.enadeag.carchargeplanner.buildlogic;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegalFilesPluginTest {

    @TempDir
    Path dir;

    /** Puts {@code org.example:<name>:1}, a jar with a notice, in the folder's Maven repository. */
    private Path library(String name) throws IOException {
        var folder = Files.createDirectories(dir.resolve("m2/org/example/" + name + "/1"));
        Files.writeString(folder.resolve(name + "-1.pom"), "<project><modelVersion>4.0.0</modelVersion><groupId>"
                + "org.example</groupId><artifactId>" + name + "</artifactId><version>1</version></project>", UTF_8);
        var jar = folder.resolve(name + "-1.jar");
        try (var out = new ZipOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new ZipEntry("META-INF/NOTICE"));
            out.closeEntry();
        }
        return jar;
    }

    private Project app() {
        var android = ProjectBuilder.builder().withProjectDir(dir.resolve("android").toFile()).build();
        var app = ProjectBuilder.builder().withName("app").withParent(android)
                .withProjectDir(dir.resolve("android/app").toFile()).build();
        app.getPluginManager().apply("base");
        app.getPluginManager().apply(LegalFilesPlugin.class);
        app.getRepositories().maven(repository -> repository.setUrl(dir.resolve("m2").toUri()));
        return app;
    }

    @Test
    void theCheckLooksAtTheReleaseBuildAndTheDesugaredLibraryOnly() throws IOException {
        var core = library("core");
        var desugar = library("desugar");
        library("debug");
        var app = app();
        app.getConfigurations().create("releaseRuntimeClasspath");
        app.getConfigurations().create("coreLibraryDesugaring");
        app.getConfigurations().create("debugRuntimeClasspath");
        app.getDependencies().add("releaseRuntimeClasspath", "org.example:core:1");
        app.getDependencies().add("coreLibraryDesugaring", "org.example:desugar:1");
        app.getDependencies().add("debugRuntimeClasspath", "org.example:debug:1");

        var check = (LegalFilesCheck) app.getTasks().getByName("checkLegalFiles");

        assertThat(check.getLibraries().get()).isEqualTo(Map.of("releaseRuntimeClasspath",
                List.of("org.example:core"), "coreLibraryDesugaring", List.of("org.example:desugar")));
        assertThat(check.getJars().get()).isEqualTo(Map.of("org.example:core", List.of(core.toString()),
                "org.example:desugar", List.of(desugar.toString())));
    }

    @Test
    void checkRunsItOverTheAppsSourcesAndAndroidLegal() {
        var app = app();

        var check = (LegalFilesCheck) app.getTasks().getByName("checkLegalFiles");

        assertThat(List.<Object>copyOf(app.getTasks().getByName("check").getTaskDependencies().getDependencies(null)))
                .contains(check);
        assertThat(check.getSources().get().getAsFile().toPath()).isEqualTo(dir.resolve("android/app/src"));
        assertThat(check.getLegal().get().getAsFile().toPath()).isEqualTo(dir.resolve("android/legal"));
        assertThat(check.getRepository().get().getAsFile().toPath().normalize()).isEqualTo(dir);
    }
}
