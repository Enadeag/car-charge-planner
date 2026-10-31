package nl.enadeag.carchargeplanner.buildlogic;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.attributes.Category;

/**
 * Adds {@code checkMavenDrift}, a {@link MavenDriftCheck} that {@code check} runs, to the engine's Gradle module. The
 * module's own libraries are those of {@code api} and {@code implementation}; its test libraries those of the
 * {@code testFixtures} configurations, where the contract tests are. The build file names the poms.
 */
public final class MavenDriftPlugin implements Plugin<Project> {

    /** The groups of the contract tests' libraries, which both builds keep alike. */
    static final Set<String> TEST_GROUPS = Set.of("org.junit.jupiter", "org.assertj");

    private static final List<String> MAIN = List.of("api", "implementation", "runtimeOnly");
    private static final List<String> TEST = List.of("testFixturesApi", "testFixturesImplementation",
            "testFixturesRuntimeOnly");

    /** Makes the plugin; Gradle calls it. */
    public MavenDriftPlugin() {
    }

    @Override
    public void apply(Project project) {
        var check = project.getTasks().register("checkMavenDrift", MavenDriftCheck.class, task -> {
            task.getTestGroups().set(TEST_GROUPS);
            task.getGradleLibraries().set(project.provider(() -> declared(project)));
        });
        project.getPluginManager().withPlugin("lifecycle-base", ignored ->
                project.getTasks().named("check").configure(task -> task.dependsOn(check)));
    }

    private static List<Declared> declared(Project project) {
        var declared = new ArrayList<Declared>();
        for (var name : MAIN) {
            declared.addAll(declaredIn(project, name, false));
        }
        for (var name : TEST) {
            declared.addAll(declaredIn(project, name, true));
        }
        return declared;
    }

    private static List<Declared> declaredIn(Project project, String configuration, boolean onlyTestGroups) {
        var found = new ArrayList<Declared>();
        var named = project.getConfigurations().findByName(configuration);
        if (named == null) {
            return found;
        }
        for (var dependency : named.getDependencies().withType(ExternalModuleDependency.class)) {
            var category = dependency.getAttributes().getAttribute(Category.CATEGORY_ATTRIBUTE);
            boolean bom = category != null && (Category.REGULAR_PLATFORM.equals(category.getName())
                    || Category.ENFORCED_PLATFORM.equals(category.getName()));
            if (onlyTestGroups && !bom
                    && TEST_GROUPS.stream().noneMatch(group -> MavenPoms.inGroup(dependency.getGroup(), group))) {
                continue;
            }
            // a catalog library without a version has none here, or an empty one
            var version = dependency.getVersion() == null || dependency.getVersion().isEmpty() ? null
                    : dependency.getVersion();
            found.add(new Declared(dependency.getGroup(), dependency.getName(), version, bom));
        }
        return found;
    }
}
