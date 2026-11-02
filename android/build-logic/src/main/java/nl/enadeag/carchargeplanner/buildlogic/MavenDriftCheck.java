package nl.enadeag.carchargeplanner.buildlogic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Fails when the Android build's list of the engine's libraries names other libraries than the engine's
 * {@code pom.xml} (ADR 0010). Only the names: the versions come from the poms already ({@link MavenVersions}).
 */
public abstract class MavenDriftCheck extends DefaultTask {

    /** Makes the task; Gradle fills in its properties. */
    public MavenDriftCheck() {
        setGroup("verification");
        setDescription("Checks that the engine's libraries are the same in its pom.xml and in its build.gradle.kts.");
    }

    /**
     * The engine's {@code pom.xml}, with its libraries.
     *
     * @return the file
     */
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getModulePom();

    /**
     * The groups of the test libraries both builds must declare alike, such as {@code org.junit.jupiter}.
     *
     * @return the groups
     */
    @Input
    public abstract SetProperty<String> getTestGroups();

    /**
     * What the Android build declares for the engine: its libraries, and its test libraries of the
     * {@link #getTestGroups()}; no BOMs.
     *
     * @return the libraries
     */
    @Input
    public abstract ListProperty<Declared> getGradleLibraries();

    /**
     * Compares the two builds.
     *
     * @throws IOException when the pom cannot be read
     * @throws GradleException when they differ, naming each library only one of them declares
     */
    @TaskAction
    public void check() throws IOException {
        var maven = MavenPoms.libraries(getModulePom().get().getAsFile().toPath(), getTestGroups().get());
        List<String> differences = differences(maven, getGradleLibraries().get());
        if (!differences.isEmpty()) {
            throw new GradleException("The engine's libraries differ between engine/pom.xml and the Android build. "
                    + "Declare the same ones in android/engine/build.gradle.kts (and in the catalog's engine bundle, "
                    + "android/gradle/libs.versions.toml):\n  " + String.join("\n  ", differences));
        }
    }

    /**
     * The libraries that only one of the two builds declares.
     *
     * @param maven the libraries of the engine's pom
     * @param gradle the libraries of the Android build
     * @return one line per library, in name order; empty when they agree
     */
    static List<String> differences(List<Declared> maven, List<Declared> gradle) {
        var inMaven = new TreeSet<String>();
        maven.forEach(library -> inMaven.add(library.module()));
        var inGradle = new TreeSet<String>();
        gradle.forEach(library -> inGradle.add(library.module()));
        var modules = new TreeSet<String>(inMaven);
        modules.addAll(inGradle);
        var differences = new ArrayList<String>();
        for (var module : modules) {
            if (!inGradle.contains(module)) {
                differences.add(module + ": only in Maven");
            } else if (!inMaven.contains(module)) {
                differences.add(module + ": only in Gradle");
            }
        }
        return differences;
    }
}
