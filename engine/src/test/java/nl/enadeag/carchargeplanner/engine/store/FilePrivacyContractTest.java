package nl.enadeag.carchargeplanner.engine.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What every {@link FilePrivacy} must do, as its Javadoc lists it: a platform's implementation passes when its test
 * extends this class and hands it the implementation. It checks only what holds on every platform, through plain
 * files in a folder of the test's own: that the attributes are accepted and leave the owner their file, that what is
 * made with them needs no tightening, and that tightening keeps the owner's access. Whether others are kept out
 * depends on how the platform protects files, so each platform's own test checks that, such as the desktop's
 * file modes.
 */
public abstract class FilePrivacyContractTest {

    /** A folder of the test's own, empty at the start of each test. */
    @TempDir
    protected Path folder;

    /**
     * The implementation under test.
     *
     * @return the implementation
     */
    protected abstract FilePrivacy privacy();

    private static FileAttribute<?>[] array(List<FileAttribute<?>> attributes) {
        return attributes.toArray(new FileAttribute<?>[0]);
    }

    /**
     * Makes a file with the attributes the implementation gives for it.
     *
     * @param file where the file is made; its folder exists
     * @return the file, empty
     * @throws IOException when it could not be made
     */
    protected final Path newFile(Path file) throws IOException {
        return Files.createFile(file, array(privacy().newFileAttributes(file)));
    }

    /**
     * Makes a folder and the missing ones above it with the attributes the implementation gives for it.
     *
     * @param made the folder to make
     * @return the folder
     * @throws IOException when it could not be made
     */
    protected final Path newFolder(Path made) throws IOException {
        return Files.createDirectories(made, array(privacy().newFolderAttributes(made)));
    }

    @Test
    void aFileMadeWithItsAttributesIsTheOwnersToReadAndWrite() throws IOException {
        var file = newFile(folder.resolve("data.json"));

        Files.writeString(file, "kept");

        assertThat(file).isReadable().isWritable().hasContent("kept");
    }

    @Test
    void aFolderMadeWithItsAttributesIsTheOwnersToEnterListAndAddTo() throws IOException {
        var made = newFolder(folder.resolve("config").resolve("app"));

        Files.writeString(made.resolve("data.json"), "kept");
        Files.writeString(made.getParent().resolve("beside.json"), "kept too");

        assertThat(made).isDirectoryContaining(path -> path.getFileName().toString().equals("data.json"));
        assertThat(made.resolve("data.json")).hasContent("kept");
        assertThat(made.getParent().resolve("beside.json")).hasContent("kept too");
    }

    @Test
    void aFileMadeWithItsAttributesNeedsNoTightening() throws IOException {
        var file = newFile(folder.resolve("data.json"));

        assertThat(privacy().tighten(file)).isFalse();
    }

    @Test
    void aFolderMadeWithItsAttributesNeedsNoTightening() throws IOException {
        var made = newFolder(folder.resolve("app"));

        assertThat(privacy().tighten(made)).isFalse();
    }

    @Test
    void aTightenedFileStaysTheOwnersAndIsNotChangedTwice() throws IOException {
        var file = Files.writeString(folder.resolve("data.json"), "kept");

        privacy().tighten(file);

        assertThat(file).isReadable().isWritable().hasContent("kept");
        Files.writeString(file, "changed");
        assertThat(file).hasContent("changed");
        assertThat(privacy().tighten(file)).isFalse();
    }

    @Test
    void aTightenedFolderStaysTheOwnersAndIsNotChangedTwice() throws IOException {
        var app = Files.createDirectory(folder.resolve("app"));
        Files.writeString(app.resolve("data.json"), "kept");

        privacy().tighten(app);

        assertThat(app.resolve("data.json")).hasContent("kept");
        Files.writeString(app.resolve("new.json"), "added");
        assertThat(app.resolve("new.json")).hasContent("added");
        assertThat(privacy().tighten(app)).isFalse();
    }

    @Test
    void aMissingPathIsNeverMadeByTightening() {
        var missing = folder.resolve("missing.json");

        try {
            assertThat(privacy().tighten(missing)).isFalse();
        } catch (IOException refused) {
            // refusing a missing path is allowed
        }

        assertThat(missing).doesNotExist();
    }

    @Test
    void itCanBeUsedFromSeveralThreadsAtOnce() throws Exception {
        var tasks = new ArrayList<Callable<Boolean>>();
        for (int i = 0; i < 40; i++) {
            var file = folder.resolve("file-" + i + ".json");
            var made = folder.resolve("folder-" + i);
            tasks.add(() -> {
                newFile(file);
                newFolder(made);
                return privacy().tighten(file) || privacy().tighten(made);
            });
        }

        var threads = Executors.newFixedThreadPool(4);
        try {
            for (Future<Boolean> changed : threads.invokeAll(tasks)) {
                assertThat(changed.get()).isFalse();
            }
        } finally {
            threads.shutdownNow();
        }
    }
}
