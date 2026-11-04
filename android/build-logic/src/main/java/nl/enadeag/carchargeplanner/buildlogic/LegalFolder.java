package nl.enadeag.carchargeplanner.buildlogic;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.TreeMap;
import org.gradle.api.GradleException;

/**
 * Reads {@code android/legal}, for {@link LegalFilesCheck} and {@link LicenceAssets} alike: it holds the licence texts
 * only, each a plain {@code .txt} file, as the APK carries every file in it.
 */
final class LegalFolder {

    private LegalFolder() {
    }

    /**
     * Each legal file's text, by its name, in name order, with {@code \n} line endings.
     *
     * @param folder {@code android/legal}; never null
     * @return per file name, such as {@code apache-2.0.txt}, its text
     * @throws GradleException when the folder holds anything but plain {@code .txt} files, such as a folder, a link
     *     or another file, or a text that is no UTF-8: one message names each such entry, in name order, with its
     *     fix
     * @throws UncheckedIOException when the folder or a file cannot be read
     */
    static TreeMap<String, String> texts(Path folder) {
        var texts = new TreeMap<String, String>();
        var problems = new ArrayList<String>();
        try (var files = Files.list(folder)) {
            for (var file : files.sorted().toList()) {
                var name = file.getFileName().toString();
                if (!name.endsWith(".txt") || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    problems.add(LegalFiles.FOLDER + name + " is no plain .txt file: remove it, or move it out of "
                            + "android/legal, which holds only the licence texts, each a .txt file, as the APK "
                            + "carries every file in it");
                    continue;
                }
                try {
                    texts.put(name, Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n")
                            .replace('\r', '\n'));
                } catch (MalformedInputException e) {
                    problems.add(LegalFiles.FOLDER + name + " is no UTF-8 text: save it as UTF-8");
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + folder, e);
        }
        if (!problems.isEmpty()) {
            throw new GradleException(String.join("\n", problems));
        }
        return texts;
    }
}
