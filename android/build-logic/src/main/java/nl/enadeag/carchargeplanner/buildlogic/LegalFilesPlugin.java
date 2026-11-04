package nl.enadeag.carchargeplanner.buildlogic;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;

/**
 * Adds {@code checkLegalFiles}, a {@link LegalFilesCheck} that {@code check} runs, over what the app's release build
 * brings: {@code releaseRuntimeClasspath}, the engine's libraries included, and {@code coreLibraryDesugaring}, whose
 * library the build puts in the APK too. A library counts when it brings a file, so a module that only points to its
 * Android variant, or a BOM, does not. The legal files are {@code android/legal}, the sources the project's
 * {@code src} and the engine's {@code engine/src/main} at the repository's root, which {@code :engine} compiles into
 * the APK.
 */
public final class LegalFilesPlugin implements Plugin<Project> {

    /** The configurations whose libraries reach the release APK. */
    static final Set<String> CONFIGURATIONS = Set.of("releaseRuntimeClasspath", "coreLibraryDesugaring");

    /** Makes the plugin; Gradle calls it. */
    public LegalFilesPlugin() {
    }

    @Override
    public void apply(Project project) {
        var check = project.getTasks().register("checkLegalFiles", LegalFilesCheck.class, task -> {
            task.getLegal().convention(project.getRootProject().getLayout().getProjectDirectory().dir("legal"));
            task.getRepository().convention(project.getRootProject().getLayout().getProjectDirectory().dir(".."));
            task.getSources().from(project.getLayout().getProjectDirectory().dir("src"),
                    project.getRootProject().getLayout().getProjectDirectory().dir("../engine/src/main"));
            project.getConfigurations().matching(configuration -> CONFIGURATIONS.contains(configuration.getName()))
                    .all(configuration -> {
                        var artifacts = configuration.getIncoming().getArtifacts().getResolvedArtifacts();
                        task.getLibraries().put(configuration.getName(), artifacts.map(LegalFilesPlugin::modules));
                        task.getJars().putAll(artifacts.map(LegalFilesPlugin::jars));
                    });
        });
        project.getPluginManager().withPlugin("lifecycle-base", ignored ->
                project.getTasks().named("check").configure(task -> task.dependsOn(check)));
    }

    /** The {@code group:artifact} of each library that brings a file, sorted; a project of the build is none. */
    private static List<String> modules(Set<ResolvedArtifactResult> artifacts) {
        var modules = new TreeSet<String>();
        for (var artifact : artifacts) {
            if (artifact.getId().getComponentIdentifier() instanceof ModuleComponentIdentifier module) {
                modules.add(module.getGroup() + ":" + module.getModule());
            }
        }
        return List.copyOf(modules);
    }

    /** Per library, the paths of its jars. */
    private static TreeMap<String, List<String>> jars(Set<ResolvedArtifactResult> artifacts) {
        var jars = new TreeMap<String, List<String>>();
        for (var artifact : artifacts) {
            if (artifact.getId().getComponentIdentifier() instanceof ModuleComponentIdentifier module
                    && artifact.getFile().getName().endsWith(".jar")) {
                jars.computeIfAbsent(module.getGroup() + ":" + module.getModule(), ignored -> new ArrayList<>())
                        .add(artifact.getFile().getAbsolutePath());
            }
        }
        jars.replaceAll((module, paths) -> paths.stream().sorted().distinct().toList());
        return jars;
    }
}
