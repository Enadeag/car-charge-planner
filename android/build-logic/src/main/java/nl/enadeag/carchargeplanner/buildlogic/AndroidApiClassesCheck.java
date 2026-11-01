package nl.enadeag.carchargeplanner.buildlogic;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Fails when a compiled class refers to a {@code java.*} or {@code javax.*} class that no Android version has: one
 * missing from the compileSdk's {@code android.jar}. Lint's NewApi is silent on such a class, such as
 * {@code ProcessHandle}; core library desugaring adds none that a program compiled against the JDK can name.
 */
public abstract class AndroidApiClassesCheck extends DefaultTask {

    /** Makes the task; Gradle fills in its properties. */
    public AndroidApiClassesCheck() {
        setGroup("verification");
        setDescription("Checks that every JDK class the compiled classes use is in Android's API.");
    }

    /**
     * The folders of compiled classes to check.
     *
     * @return the folders
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getClasses();

    /**
     * The compileSdk's boot classpath, {@code android.jar} among it.
     *
     * @return the jars
     */
    @Classpath
    public abstract ConfigurableFileCollection getAndroidApi();

    /**
     * Reads the classes and the jars and compares them.
     *
     * @throws GradleException when a class refers to a JDK class Android lacks, naming each and where it is used
     * @throws UncheckedIOException when a file cannot be read
     */
    @TaskAction
    public void check() {
        try {
            var android = classesIn(getAndroidApi().getFiles());
            var references = new TreeMap<String, Set<String>>();
            getClasses().getAsFileTree().matching(tree -> tree.include("**/*.class")).visit(file -> {
                if (!file.isDirectory()) {
                    var name = file.getRelativePath().getPathString();
                    try (var in = file.open()) {
                        references.put(name.substring(0, name.length() - ".class".length()).replace('/', '.'),
                                JdkClassReferences.in(in));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            });
            var missing = missing(references, android);
            if (!missing.isEmpty()) {
                throw new GradleException("JDK classes that no Android version has (ADR 0010):\n  "
                        + String.join("\n  ", missing));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The classes in some jars.
     *
     * @param jars the jars; never null
     * @return the names of their classes in internal form, such as {@code java/lang/String}
     * @throws IOException when a jar cannot be read
     */
    static Set<String> classesIn(Iterable<File> jars) throws IOException {
        var classes = new HashSet<String>();
        for (var jar : jars) {
            try (var zip = new ZipFile(jar)) {
                zip.stream().map(ZipEntry::getName).filter(name -> name.endsWith(".class"))
                        .forEach(name -> classes.add(name.substring(0, name.length() - ".class".length())));
            }
        }
        return classes;
    }

    /**
     * The referenced classes that Android lacks.
     *
     * @param references per compiled class's name, the JDK classes it refers to; never null
     * @param android the classes in Android's API, in internal form; never null
     * @return one line per missing class and class that uses it, in the order of {@code references}
     */
    static List<String> missing(Map<String, Set<String>> references, Set<String> android) {
        var missing = new ArrayList<String>();
        references.forEach((user, used) -> used.stream().filter(name -> !android.contains(name))
                .forEach(name -> missing.add(name.replace('/', '.') + ", used by " + user)));
        return missing;
    }
}
