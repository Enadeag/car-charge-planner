package nl.enadeag.carchargeplanner.buildlogic;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Puts the licence texts in the app's assets (#331), from the repository's files only: {@code NOTICE},
 * {@code LICENSE} and {@code ADDITIONAL-TERMS.md} of its root, then each file of {@code android/legal} in name order,
 * each as an asset of its own in {@code licences/}, with {@code licences/index.txt}, which the app's screen of the
 * licences reads. The same files always give the same bytes: UTF-8, {@code \n} line endings, a fixed order, no
 * timestamp, so the APK stays reproducible (#272, #280).
 */
public abstract class LicenceAssets extends DefaultTask {

    /** The folder in the assets. */
    public static final String FOLDER = "licences/";

    /** The index's name in {@link #FOLDER}. */
    public static final String INDEX = "index.txt";

    /** Makes the task; Gradle fills in its properties. */
    public LicenceAssets() {
        setDescription("Puts the licence texts of the app and its libraries in the app's assets.");
    }

    /**
     * The app's notice, whose first line is its name and whose copyright line the screen shows.
     *
     * @return the repository's {@code NOTICE}
     */
    @InputFile
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract RegularFileProperty getNotice();

    /**
     * The app's licence, the GPL.
     *
     * @return the repository's {@code LICENSE}
     */
    @InputFile
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract RegularFileProperty getLicence();

    /**
     * The additional terms under the GPL's section 7, whose first line is their heading.
     *
     * @return the repository's {@code ADDITIONAL-TERMS.md}
     */
    @InputFile
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract RegularFileProperty getAdditionalTerms();

    /**
     * The libraries' licence texts, each file starting with its title.
     *
     * @return {@code android/legal}
     */
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getLegal();

    /**
     * Where the assets go, emptied first; {@link #FOLDER} in it.
     *
     * @return the generated assets folder
     */
    @OutputDirectory
    public abstract DirectoryProperty getAssets();

    /** Writes the assets. */
    @TaskAction
    public void write() {
        var legal = LegalFolder.texts(getLegal().get().getAsFile().toPath());
        try {
            var assets = getAssets().get().getAsFile().toPath();
            delete(assets);
            var folder = Files.createDirectories(assets.resolve(FOLDER));
            for (var asset : contents(read(getNotice()), read(getLicence()), read(getAdditionalTerms()), legal)
                    .entrySet()) {
                Files.writeString(folder.resolve(asset.getKey()), asset.getValue(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The assets' names and texts, in the order the screen shows them, the index last: the app's notice, its licence
     * and its additional terms, then the libraries' files by name. Each line of the index is either
     * {@code copyright<TAB>line}, the notice's copyright line, or {@code part<TAB>asset<TAB>title}. The texts keep
     * their lines, with {@code \n} endings and one at the end.
     *
     * @param notice the text of {@code NOTICE}: its first line is the app's name, and one line starts with
     *     "Copyright"; never null
     * @param licence the text of {@code LICENSE}; never null
     * @param terms the text of {@code ADDITIONAL-TERMS.md}, whose first line is its Markdown heading; never null
     * @param legal per name of a file of {@code android/legal}, its text, whose first line is its title; never null
     * @return per asset's name in {@link #FOLDER}, its text
     * @throws IllegalArgumentException when the notice has no copyright line or its copyright line has a tab, a
     *     text is empty, a title line has a tab, or a file's name has a tab or a line break
     */
    public static LinkedHashMap<String, String> contents(String notice, String licence, String terms,
            Map<String, String> legal) {
        var assets = new LinkedHashMap<String, String>();
        var index = new StringBuilder();
        var copyright = normalised(notice).lines().filter(line -> line.startsWith("Copyright")).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("NOTICE has no line that starts with Copyright"));
        if (copyright.indexOf('\t') >= 0) {
            throw new IllegalArgumentException("NOTICE has a tab in its copyright line, but the index separates its "
                    + "fields by tabs: replace the tab with spaces");
        }
        index.append("copyright\t").append(copyright).append('\n');
        add(assets, index, "NOTICE", firstLine("NOTICE", notice) + " (GPL-3.0-or-later)", notice);
        add(assets, index, "LICENSE", "GNU General Public License, version 3", licence);
        add(assets, index, "ADDITIONAL-TERMS.md", firstLine("ADDITIONAL-TERMS.md", terms).replaceFirst("^#+\\s*", ""),
                terms);
        new TreeMap<>(legal).forEach((name, text) -> {
            if (name.indexOf('\t') >= 0 || name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0) {
                throw new IllegalArgumentException("a file of android/legal has a tab or a line break in its name, "
                        + "but the index has one line per file, its fields separated by tabs: rename the file");
            }
            add(assets, index, name, firstLine(name, text), text);
        });
        assets.put(INDEX, index.toString());
        return assets;
    }

    private static void add(Map<String, String> assets, StringBuilder index, String name, String title, String text) {
        assets.put(name, normalised(text));
        index.append("part\t").append(name).append('\t').append(title).append('\n');
    }

    private static String firstLine(String name, String text) {
        var first = normalised(text).lines().findFirst().orElse("").strip();
        if (first.isEmpty()) {
            throw new IllegalArgumentException(name + " does not start with its title");
        }
        if (first.indexOf('\t') >= 0) {
            throw new IllegalArgumentException(name + " has a tab in its title line, but the index separates its "
                    + "fields by tabs: replace the tab with spaces");
        }
        return first;
    }

    /** The text with {@code \n} line endings, ending in one. */
    private static String normalised(String text) {
        var lines = text.replace("\r\n", "\n").replace('\r', '\n');
        return lines.endsWith("\n") ? lines : lines + "\n";
    }

    private static String read(RegularFileProperty file) throws IOException {
        return read(file.get().getAsFile().toPath());
    }

    /**
     * @param file the file of {@code NOTICE}, {@code LICENSE} or {@code ADDITIONAL-TERMS.md}; never null
     * @throws GradleException when the file is no UTF-8 text
     * @throws IOException when the file cannot be read
     */
    static String read(Path file) throws IOException {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (MalformedInputException e) {
            throw new GradleException(file.getFileName() + " is no UTF-8 text: save it as UTF-8");
        }
    }

    private static void delete(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(folder)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (!path.equals(folder)) {
                    Files.delete(path);
                }
            }
        }
    }
}
