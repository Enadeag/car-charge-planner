package nl.enadeag.carchargeplanner.buildlogic;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Fails when {@code android/legal} does not match what the app brings: see {@link LegalFiles}. It reads the jars'
 * {@code META-INF}, the legal files and the app's sources when it runs; it has no outputs, so it always runs.
 */
public abstract class LegalFilesCheck extends DefaultTask {

    /** Makes the task; Gradle fills in its properties. */
    public LegalFilesCheck() {
        setGroup("verification");
        setDescription("Checks that android/legal names exactly the libraries and copied files the app brings.");
    }

    /**
     * Per configuration's name, the {@code group:artifact} of each library it brings, without the build's own
     * projects.
     *
     * @return the libraries
     */
    @Input
    public abstract MapProperty<String, List<String>> getLibraries();

    /**
     * Per {@code group:artifact}, the paths of its jars, whose {@code META-INF/NOTICE*} the task looks for; its aars
     * are not in it.
     *
     * @return the jars
     */
    @Input
    public abstract MapProperty<String, List<String>> getJars();

    /**
     * The folder of the legal files, {@code android/legal}.
     *
     * @return the folder
     */
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getLegal();

    /**
     * The app's sources, every file of which is looked at for a third party's header.
     *
     * @return the folder, such as {@code android/app/src}
     */
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getSources();

    /**
     * The repository's root, which the paths of a {@code Copied:} block start from.
     *
     * @return the folder
     */
    @Internal
    public abstract DirectoryProperty getRepository();

    /**
     * Compares.
     *
     * @throws GradleException when the files do not match, naming each problem and its fix, or when
     *     {@code android/legal} holds anything but plain {@code .txt} files
     */
    @TaskAction
    public void check() {
        var root = getRepository().get().getAsFile().toPath().toAbsolutePath().normalize();
        var problems = LegalFiles.problems(getLibraries().get(), notices(getJars().get()),
                LegalFolder.texts(getLegal().get().getAsFile().toPath()),
                withHeader(root, getSources().get().getAsFile().toPath()), inRepository(root));
        if (!problems.isEmpty()) {
            throw new GradleException("android/legal does not match what the app brings:\n  "
                    + String.join("\n  ", problems));
        }
    }

    /**
     * Per library, the {@code META-INF/NOTICE*} entries of its jars: any case, right in {@code META-INF}, not in a
     * folder under it.
     *
     * @param jars per {@code group:artifact}, the paths of its jars; never null
     * @return per library that has any, the entries' names as the jar has them; a library without one is left out
     * @throws UncheckedIOException when a jar cannot be read
     */
    static Map<String, List<String>> notices(Map<String, List<String>> jars) {
        var notices = new TreeMap<String, List<String>>();
        jars.forEach((module, paths) -> {
            for (var path : paths) {
                try (var jar = new ZipFile(new File(path))) {
                    jar.stream().map(ZipEntry::getName)
                            .filter(name -> name.toUpperCase(Locale.ROOT).startsWith("META-INF/NOTICE")
                                    && name.indexOf('/', "META-INF/".length()) < 0)
                            .forEach(name -> notices.computeIfAbsent(module, ignored -> new ArrayList<>()).add(name));
                } catch (IOException e) {
                    throw new UncheckedIOException("cannot read " + path, e);
                }
            }
        });
        return notices;
    }

    /**
     * The sources that carry a third party's header: every file under the sources, whatever its kind.
     *
     * @param root the repository's root, absolute and normalised; never null
     * @param sources the folder of the sources, such as {@code android/app/src}; never null
     * @return their paths from the root, with {@code /} on every system, in no particular order
     * @throws UncheckedIOException when a file cannot be read
     */
    static List<String> withHeader(Path root, Path sources) {
        try (Stream<Path> files = Files.walk(sources)) {
            var found = new ArrayList<String>();
            for (var file : files.filter(Files::isRegularFile).toList()) {
                // the String constructor replaces what is no UTF-8, as in an image, and keeps a UTF-8 ©
                if (LegalFiles.hasThirdPartyHeader(new String(Files.readAllBytes(file), StandardCharsets.UTF_8))) {
                    found.add(root.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/'));
                }
            }
            return found;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + sources, e);
        }
    }

    /**
     * Whether a path that a {@code Copied:} block names is a file in the repository.
     *
     * @param root the repository's root, absolute and normalised; never null
     * @return true for a path, from the root, of a regular file under it; false for a folder, a missing file or a path
     *     that leads out of the repository
     */
    static Predicate<String> inRepository(Path root) {
        return path -> {
            var file = root.resolve(path).normalize();
            return file.startsWith(root) && Files.isRegularFile(file);
        };
    }
}
