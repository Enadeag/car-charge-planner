package nl.enadeag.carchargeplanner.buildlogic;

import java.io.IOException;
import java.util.List;
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
 * Fails when the engine's libraries in the Android build differ from those in its Maven build, in artifact or in
 * version (ADR 0010): the Maven poms are the source, and a Maven update of one of them updates the version catalog in
 * the same pull request.
 */
public abstract class MavenDriftCheck extends DefaultTask {

    /** Makes the task; Gradle fills in its properties. */
    public MavenDriftCheck() {
        setGroup("verification");
        setDescription("Checks that the engine's libraries are the same in Maven and in the version catalog.");
    }

    /**
     * The parent {@code pom.xml}, with the versions.
     *
     * @return the file
     */
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getParentPom();

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
     * What the Android build declares for the engine: its libraries and BOMs, and its test libraries of the
     * {@link #getTestGroups()}.
     *
     * @return the libraries
     */
    @Input
    public abstract ListProperty<Declared> getGradleLibraries();

    /**
     * Compares the two builds.
     *
     * @throws IOException when a pom cannot be read
     * @throws GradleException when they differ, naming each difference
     */
    @TaskAction
    public void check() throws IOException {
        var maven = MavenPoms.read(getParentPom().get().getAsFile().toPath(), getModulePom().get().getAsFile().toPath(),
                getTestGroups().get());
        List<String> differences = Pins.differences(Pins.of(maven), Pins.of(getGradleLibraries().get()));
        if (!differences.isEmpty()) {
            throw new GradleException("The engine's libraries differ between Maven and the Android build. Update "
                    + "android/gradle/libs.versions.toml (or the engine's build.gradle.kts) to match the poms:\n  "
                    + String.join("\n  ", differences));
        }
    }
}
