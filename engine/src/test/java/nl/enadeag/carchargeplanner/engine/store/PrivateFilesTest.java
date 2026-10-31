package nl.enadeag.carchargeplanner.engine.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.LogCapture;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the engine does to write a file safely on every platform; how private the files are is the platform's
 * {@link FilePrivacy}, which the desktop's tests check with real file modes.
 */
class PrivateFilesTest {

    @TempDir
    Path folder;

    private final RecordingFilePrivacy privacy = new RecordingFilePrivacy();
    private final PrivateFiles files = new PrivateFiles(privacy);

    @Test
    void appendingAddsToTheEndOfWhatIsThere() throws IOException {
        var file = Files.writeString(folder.resolve("app.log"), "first\n");

        try (var out = files.append(file)) {
            out.write("second\n".getBytes(StandardCharsets.UTF_8));
        }

        assertThat(Files.readString(file)).isEqualTo("first\nsecond\n");
    }

    @Test
    void aFileMadeToAppendToAndItsMissingFolderAreMadeWithThePlatformsAttributes() throws IOException {
        var data = folder.resolve("data");
        var file = data.resolve("app.log");

        try (var out = files.append(file)) {
            out.write('x');
        }

        assertThat(privacy.foldersMade()).containsExactly(data.toAbsolutePath());
        assertThat(privacy.filesMade()).containsExactly(file.toAbsolutePath());
    }

    @Test
    void theWholeTextReplacesTheFileAndNoTemporaryFileIsLeft() throws IOException {
        var file = folder.resolve("data.json");
        Files.writeString(file, "a much longer old content than the new one");

        files.write(file, "{\"ü\": 1}");

        assertThat(file).hasContent("{\"ü\": 1}");
        assertThat(folder.resolve("data.json.tmp")).doesNotExist();
    }

    @Test
    void aWrittenFileAndItsMissingFoldersAreMadeWithThePlatformsAttributes() throws IOException {
        var app = folder.resolve("config").resolve("app");

        files.write(app.resolve("data.json"), "{}");

        assertThat(privacy.foldersMade()).containsExactly(app.toAbsolutePath());
        assertThat(privacy.filesMade()).singleElement().satisfies(made -> assertThat(made.getParent())
                .isEqualTo(app.toAbsolutePath()));
    }

    @Test
    void anExistingFolderIsNotMadeAgain() throws IOException {
        files.write(folder.resolve("data.json"), "{}");

        assertThat(privacy.foldersMade()).isEmpty();
    }

    @Test
    void aLeftoverTemporaryFileIsNotReused() throws IOException {
        Files.writeString(folder.resolve("data.json.tmp"), "a much longer old leftover");

        files.write(folder.resolve("data.json"), "{}");

        assertThat(folder.resolve("data.json")).hasContent("{}");
        assertThat(privacy.filesMade()).hasSize(1);
        assertThat(folder.resolve("data.json.tmp")).doesNotExist();
    }

    @Test
    void aFileThatCannotBeWrittenIsLeftAsItWas() throws IOException {
        var file = Files.writeString(folder.resolve("data.json"), "old");
        Files.createDirectory(folder.resolve("data.json.tmp"));

        assertThatThrownBy(() -> files.write(file, "new")).isInstanceOf(IOException.class);
        assertThat(file).hasContent("old");
    }

    @Test
    void aFileWhereTheFolderShouldBeIsReported() throws IOException {
        Files.writeString(folder.resolve("data"), "a file where the folder should be");

        assertThatThrownBy(() -> files.write(folder.resolve("data").resolve("away.json"), "{}"))
                .isInstanceOf(IOException.class);
    }

    @Test
    void aMoveThatFailsLeavesNoTemporaryFile() throws IOException {
        // a folder that is not empty in the file's place cannot be replaced
        var file = Files.createDirectory(folder.resolve("data.json"));
        Files.writeString(file.resolve("inside"), "kept");

        assertThatThrownBy(() -> files.write(file, "new")).isInstanceOf(IOException.class);
        assertThat(folder.resolve("data.json.tmp")).doesNotExist();
        assertThat(file.resolve("inside")).hasContent("kept");
    }

    @Test
    void whereAFileCannotBeMovedInOneStepItIsReplacedAndThatIsLogged() throws IOException {
        var fileSystem = new NoAtomicMoveFileSystem();
        var file = fileSystem.wrap(folder.resolve("data.json"));
        Files.writeString(folder.resolve("data.json"), "old");

        try (var log = LogCapture.of(PrivateFiles.class)) {
            files.write(file, "new");

            assertThat(log.lines()).singleElement().asString()
                    .startsWith("WARNING could not replace " + file.toAbsolutePath() + " in one step");
        }
        assertThat(folder.resolve("data.json")).hasContent("new");
        assertThat(folder.resolve("data.json.tmp")).doesNotExist();
    }

    @Test
    void aFileThatIsReplacedByAPlainMoveAgainAndAgainIsLoggedOnce() throws IOException {
        var fileSystem = new NoAtomicMoveFileSystem();
        var file = fileSystem.wrap(folder.resolve("data.json"));
        var other = fileSystem.wrap(folder.resolve("other.json"));

        try (var log = LogCapture.of(PrivateFiles.class)) {
            files.write(file, "first");
            files.write(file, "second");
            files.write(other, "first");

            assertThat(log.lines()).hasSize(2)
                    .anySatisfy(line -> assertThat(line).contains(file.toAbsolutePath() + " in one step"))
                    .anySatisfy(line -> assertThat(line).contains(other.toAbsolutePath() + " in one step"));
        }
        assertThat(folder.resolve("data.json")).hasContent("second");
    }

    @Test
    void whenThePlainMoveFailsAfterTheFileIsGoneTheTextIsKeptInTheTemporaryFile() throws IOException {
        var file = NoAtomicMoveFileSystem.whosePlainMoveLosesTheFile().wrap(folder.resolve("data.json"));
        Files.writeString(folder.resolve("data.json"), "old");

        assertThatThrownBy(() -> files.write(file, "new"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("the move stopped");
        assertThat(folder.resolve("data.json")).doesNotExist();
        assertThat(folder.resolve("data.json.tmp")).hasContent("new");
    }

    @Test
    void whenThePlainMoveFailsWithTheFileStillThereNoTemporaryFileIsLeft() throws IOException {
        var file = new NoAtomicMoveFileSystem().wrap(folder.resolve("data.json"));
        // a folder that is not empty in the file's place cannot be replaced
        Files.createDirectory(folder.resolve("data.json"));
        Files.writeString(folder.resolve("data.json").resolve("inside"), "kept");

        assertThatThrownBy(() -> files.write(file, "new")).isInstanceOf(IOException.class);
        assertThat(folder.resolve("data.json").resolve("inside")).hasContent("kept");
        assertThat(folder.resolve("data.json.tmp")).doesNotExist();
    }

    @Test
    void aMoveInOneStepThatIsRefusedIsNotReplacedByAPlainMove() throws IOException {
        var refusal = new AccessDeniedException("data.json");
        var file = NoAtomicMoveFileSystem.refusingToMove(refusal).wrap(folder.resolve("data.json"));
        Files.writeString(folder.resolve("data.json"), "old");

        try (var log = LogCapture.of(PrivateFiles.class)) {
            assertThatThrownBy(() -> files.write(file, "new")).isSameAs(refusal);

            assertThat(log.lines()).isEmpty();
        }
        assertThat(folder.resolve("data.json")).hasContent("old");
        assertThat(folder.resolve("data.json.tmp")).doesNotExist();
    }

    @Test
    void anExistingFolderOpenToOthersIsMadePrivateAndThatIsLoggedOnce() throws IOException {
        var app = Files.createDirectory(folder.resolve("app"));
        privacy.openToOthers(app);
        try (var log = LogCapture.of(PrivateFiles.class)) {
            files.tighten(app);
            assertThat(log.lines()).containsExactly("INFO made " + app + " readable only by you");

            files.tighten(app);
            assertThat(log.lines()).hasSize(1);
        }
        assertThat(privacy.isOpenToOthers(app)).isFalse();
    }

    @Test
    void aFolderThePlatformCannotMakePrivateIsLoggedAndLeft() throws IOException {
        var app = Files.createDirectory(folder.resolve("app"));
        privacy.refuse(new AccessDeniedException(app.toString()));
        try (var log = LogCapture.of(PrivateFiles.class)) {
            files.tighten(app);

            assertThat(log.lines()).containsExactly("WARNING could not make " + app + " readable only by you: "
                    + app);
        }
        assertThat(app).isDirectory();
    }

    @Test
    void aMissingFolderIsNotMadeByTightening() {
        files.tighten(folder.resolve("missing"));

        assertThat(folder.resolve("missing")).doesNotExist();
    }

    @Test
    void aFileIsNotTightenedAsAFolder() throws IOException {
        var file = Files.writeString(folder.resolve("data.json"), "{}");
        privacy.openToOthers(file);

        files.tighten(file);

        assertThat(privacy.isOpenToOthers(file)).isTrue();
    }

    @Test
    void tighteningAFileSaysWhetherThePlatformChangedIt() throws IOException {
        var file = Files.writeString(folder.resolve("secrets.json"), "{}");
        privacy.openToOthers(file);

        assertThat(files.tightenFile(file)).isTrue();
        assertThat(files.tightenFile(file)).isFalse();
    }

    @Test
    void aFileThePlatformCannotTightenIsReported() throws IOException {
        var file = Files.writeString(folder.resolve("secrets.json"), "{}");
        var refusal = new AccessDeniedException(file.toString());
        privacy.refuse(refusal);

        assertThatThrownBy(() -> files.tightenFile(file)).isSameAs(refusal);
    }

    @Test
    void aRemovedFileIsGoneAndAMissingOneIsSaidToBe() throws IOException {
        var file = Files.writeString(folder.resolve("old.json"), "{}");

        assertThat(files.remove(file)).isTrue();
        assertThat(file).doesNotExist();
        assertThat(files.remove(file)).isFalse();
    }
}
