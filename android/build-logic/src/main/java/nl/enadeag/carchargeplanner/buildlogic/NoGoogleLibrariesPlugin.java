package nl.enadeag.carchargeplanner.buildlogic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;

/**
 * Adds {@code checkNoGoogleLibraries}, a {@link NoGoogleLibrariesCheck} that {@code check} runs, over every
 * configuration of the project that can be resolved: what is compiled, packed, tested, linted and desugared with.
 */
public final class NoGoogleLibrariesPlugin implements Plugin<Project> {

    /** Makes the plugin; Gradle calls it. */
    public NoGoogleLibrariesPlugin() {
    }

    @Override
    public void apply(Project project) {
        var check = project.getTasks().register("checkNoGoogleLibraries", NoGoogleLibrariesCheck.class, task ->
                project.getConfigurations().matching(Configuration::isCanBeResolved).all(configuration ->
                        task.getResolvedModules().put(configuration.getName(),
                                configuration.getIncoming().getResolutionResult().getRootComponent()
                                        .map(NoGoogleLibrariesPlugin::modules))));
        project.getPluginManager().withPlugin("lifecycle-base", ignored ->
                project.getTasks().named("check").configure(task -> task.dependsOn(check)));
    }

    /** Every module in the resolved graph, walked from its root; a project of the build is not a module. */
    private static List<String> modules(ResolvedComponentResult root) {
        var modules = new ArrayList<String>();
        var seen = new HashSet<ResolvedComponentResult>();
        var next = new ArrayList<ResolvedComponentResult>(List.of(root));
        while (!next.isEmpty()) {
            var component = next.remove(next.size() - 1);
            if (!seen.add(component)) {
                continue;
            }
            if (component.getId() instanceof ModuleComponentIdentifier module) {
                modules.add(module.getGroup() + ":" + module.getModule() + ":" + module.getVersion());
            }
            for (var dependency : component.getDependencies()) {
                if (dependency instanceof ResolvedDependencyResult resolved) {
                    next.add(resolved.getSelected());
                }
            }
        }
        return modules;
    }
}
