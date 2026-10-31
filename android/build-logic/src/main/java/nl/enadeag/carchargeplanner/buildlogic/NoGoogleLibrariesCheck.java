package nl.enadeag.carchargeplanner.buildlogic;

import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

/** Fails when a configuration of the project resolved to one of {@link GoogleLibraries#GROUPS}. */
public abstract class NoGoogleLibrariesCheck extends DefaultTask {

    /** Makes the task; Gradle fills in its properties. */
    public NoGoogleLibrariesCheck() {
        setGroup("verification");
        setDescription("Checks that no configuration resolves to Play Services, Firebase or the Play libraries.");
    }

    /**
     * Per configuration's name, the {@code group:artifact:version} of each module it resolved to.
     *
     * @return the modules
     */
    @Input
    public abstract MapProperty<String, List<String>> getResolvedModules();

    /**
     * Looks for the refused libraries.
     *
     * @throws GradleException when there is one, naming each and where it came in
     */
    @TaskAction
    public void check() {
        var found = GoogleLibraries.findIn(getResolvedModules().get());
        if (!found.isEmpty()) {
            throw new GradleException("Google's libraries are not allowed (ADR 0010):\n  "
                    + String.join("\n  ", found));
        }
    }
}
